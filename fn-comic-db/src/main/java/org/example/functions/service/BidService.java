package org.example.functions.service;

import lombok.extern.slf4j.Slf4j;
import org.example.functions.model.BidEntry;
import org.example.functions.model.BiddingState;
import org.example.functions.model.Cart;
import org.example.functions.model.ComicBook;
import org.example.functions.model.User;
import org.example.functions.util.EnvHelper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Objects;

@Slf4j
public class BidService {

    private static BidService SERVICE_INSTANCE;

    public static BidService getServiceInstance() {
        if (Objects.isNull(SERVICE_INSTANCE)) {
            SERVICE_INSTANCE = new BidService();
        }
        return SERVICE_INSTANCE;
    }

    /**
     * Admin: cancel an opened bid before any user has placed a bid.
     * Clears bidOpenedAt so the comic returns to its pre-opened state.
     * Throws if bidding has already started (first user bid placed).
     */
    public ComicBook cancelBid(String comicId) {
        ComicBook comic = ComicService.getServiceInstance().getComicById(Integer.parseInt(comicId))
            .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + comicId));

        BiddingState bid = comic.getBiddingState();
        if (bid.getBidStartedAt() != null) {
            throw new IllegalStateException("Cannot cancel — bidding is already in progress for comic " + comicId + ".");
        }

        bid.setBidOpenedAt(null);
        ComicService.getServiceInstance().updateComic(comic, "system:bid-cancel");
        log.info("Bidding cancelled (before first bid) on comic {} by admin", comicId);
        return comic;
    }

    /**
     * Admin: open a bid-enabled comic for bidding. Sets bidOpenedAt so users can see the Bid button.
     * The countdown timer does not start until the first user actually places a bid.
     */
    public ComicBook openBid(String comicId, BigDecimal startingBid) {
        if (!EnvHelper.isBiddingModeEnabled()) {
            throw new IllegalStateException("Bidding mode is not enabled.");
        }
        ComicBook comic = ComicService.getServiceInstance().getComicById(Integer.parseInt(comicId))
            .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + comicId));

        if (!Boolean.TRUE.equals(comic.getEnableBid())) {
            throw new IllegalArgumentException("Comic " + comicId + " does not have bidding enabled.");
        }

        BiddingState bid = comic.getBiddingState();
        if (bid.isSold()) {
            throw new IllegalStateException("Comic " + comicId + " has already been sold via bidding.");
        }
        if (bid.getBidOpenedAt() != null) {
            return comic; // Already opened — idempotent
        }

        bid.setBidOpenedAt(Instant.now().toString());
        if (startingBid != null) {
            bid.setHighBid(startingBid);
        }
        ComicService.getServiceInstance().updateComic(comic, "system:bid-open");
        log.info("Bidding opened on comic {} by admin, startingBid={}", comicId, startingBid);
        return comic;
    }

    /**
     * Start bidding on an enableBid comic. Sets the first user as the initial leader
     * at the comic's existing highBid (or $0 if none). Requires admin to have opened
     * bidding first (bidOpenedAt must be set). If bidding is already in progress,
     * returns the current comic state without modification.
     */
    public ComicBook startBidding(User user, String comicId) {
        if (!EnvHelper.isBiddingModeEnabled()) {
            throw new IllegalStateException("Bidding mode is not enabled.");
        }
        ComicBook comic = ComicService.getServiceInstance().getComicById(Integer.parseInt(comicId))
            .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + comicId));

        if (!Boolean.TRUE.equals(comic.getEnableBid())) {
            throw new IllegalArgumentException("Comic " + comicId + " does not have bidding enabled.");
        }

        Cart activeCart = CartService.getServiceInstance().getActiveCart(user.getId()).orElse(null);
        if (activeCart != null && ("FINALIZING".equals(activeCart.getStatus()) || "FINALIZED".equals(activeCart.getStatus()))) {
            throw new IllegalStateException("Your order has been submitted — you cannot bid until it is fulfilled.");
        }

        BiddingState bid = comic.getBiddingState();

        if (bid.isSold()) {
            throw new IllegalStateException("Comic " + comicId + " has already been sold via bidding.");
        }

        if (bid.getBidOpenedAt() == null) {
            throw new IllegalStateException("Admin has not opened bidding for comic " + comicId + " yet.");
        }

        if (bid.getBidStartedAt() != null) {
            // Bidding already in progress — return current state
            return comic;
        }

        String now = Instant.now().toString();
        bid.setBidStartedAt(now);
        bid.setCurrentBidderId(user.getId());
        bid.setCurrentBidderName(user.getName());

        BigDecimal startingBid = comic.getSalePrice() != null
            ? comic.getSalePrice().max(BigDecimal.ONE)
            : BigDecimal.ONE;
        bid.setHighBid(startingBid);

        if (bid.getBidHistory() == null) {
            bid.setBidHistory(new ArrayList<>());
        }
        bid.getBidHistory().add(BidEntry.builder()
            .userId(user.getId())
            .userName(user.getName())
            .amount(startingBid)
            .placedAt(now)
            .build());

        ComicService.getServiceInstance().updateComic(comic, "system:bid-start");
        log.info("Bidding started on comic {} by user {} at ${}", comicId, user.getId(), startingBid);
        return comic;
    }

    /**
     * Place a bid. Amount must strictly exceed the current highBid.
     * Throws if bidding has not started or has already expired.
     */
    public ComicBook placeBid(User user, String comicId, BigDecimal amount) {
        if (!EnvHelper.isBiddingModeEnabled()) {
            throw new IllegalStateException("Bidding mode is not enabled.");
        }
        ComicBook comic = ComicService.getServiceInstance().getComicById(Integer.parseInt(comicId))
            .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + comicId));

        if (!Boolean.TRUE.equals(comic.getEnableBid())) {
            throw new IllegalArgumentException("Comic " + comicId + " does not have bidding enabled.");
        }

        Cart activeCart = CartService.getServiceInstance().getActiveCart(user.getId()).orElse(null);
        if (activeCart != null && ("FINALIZING".equals(activeCart.getStatus()) || "FINALIZED".equals(activeCart.getStatus()))) {
            throw new IllegalStateException("Your order has been submitted — you cannot bid until it is fulfilled.");
        }

        BiddingState bid = comic.getBiddingState();

        if (bid.isSold()) {
            throw new IllegalStateException("Comic " + comicId + " has already been sold via bidding.");
        }

        if (bid.getBidStartedAt() == null) {
            throw new IllegalStateException("Bidding has not started for comic " + comicId + ".");
        }

        Instant bidEnd = Instant.parse(bid.getBidStartedAt())
            .plus(EnvHelper.getBiddingCycleMins(), ChronoUnit.MINUTES);
        if (Instant.now().isAfter(bidEnd)) {
            throw new IllegalStateException("Bidding period has ended for comic " + comicId + ".");
        }

        BigDecimal currentHigh = bid.getHighBid() != null ? bid.getHighBid() : BigDecimal.ZERO;
        if (amount.compareTo(currentHigh) <= 0) {
            throw new IllegalArgumentException(
                "Bid must be greater than the current high bid of $" + currentHigh + ".");
        }

        // Minimum bid is $1.00
        if (amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Bid must be at least $1.00.");
        }

        // Must be in $0.25 increments
        BigDecimal[] divRem = amount.divideAndRemainder(new BigDecimal("0.25"));
        if (divRem[1].compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(
                "Bid must be in whole dollar or $0.25 increments (e.g. $1.00, $1.25, $1.50).");
        }


        String now = Instant.now().toString();
        bid.setHighBid(amount);
        bid.setCurrentBidderId(user.getId());
        bid.setCurrentBidderName(user.getName());
        // Reset the clock so each bid gets a fresh countdown window
        bid.setBidStartedAt(now);

        if (bid.getBidHistory() == null) {
            bid.setBidHistory(new ArrayList<>());
        }
        bid.getBidHistory().add(BidEntry.builder()
            .userId(user.getId())
            .userName(user.getName())
            .amount(amount)
            .placedAt(now)
            .build());

        ComicService.getServiceInstance().updateComic(comic, "system:bid");
        log.info("Bid of ${} placed on comic {} by user {}", amount, comicId, user.getId());
        return comic;
    }

    /**
     * Finalize an expired bid. Adds the comic to the winning bidder's cart at the highBid price
     * and clears bidding state from the comic (bidHistory is retained).
     * Throws if bidding hasn't started, hasn't expired yet, or winner can't be found.
     */
    public Cart finalizeBid(String comicId) {
        ComicBook comic = ComicService.getServiceInstance().getComicById(Integer.parseInt(comicId))
            .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + comicId));

        BiddingState bid = comic.getBiddingState();

        if (bid.getBidStartedAt() == null) {
            throw new IllegalStateException("No active bidding found for comic " + comicId + ".");
        }

        Instant bidEnd = Instant.parse(bid.getBidStartedAt())
            .plus(EnvHelper.getBiddingCycleMins(), ChronoUnit.MINUTES);
        if (Instant.now().isBefore(bidEnd)) {
            throw new IllegalStateException("Bidding period has not yet ended for comic " + comicId + ".");
        }

        String winnerId = bid.getCurrentBidderId();
        if (winnerId == null) {
            // Nobody bid — clear all bidding state including bidOpenedAt
            bid.setBidOpenedAt(null);
            bid.setBidStartedAt(null);
            bid.setCurrentBidderId(null);
            bid.setCurrentBidderName(null);
            ComicService.getServiceInstance().updateComic(comic, "system:bid-expire-no-winner");
            throw new IllegalStateException("No bids were placed; bidding cancelled for comic " + comicId + ".");
        }

        // Log the win event in bid history
        String finalizedAt = Instant.now().toString();
        if (bid.getBidHistory() == null) {
            bid.setBidHistory(new ArrayList<>());
        }
        bid.getBidHistory().add(BidEntry.builder()
            .userId(bid.getCurrentBidderId())
            .userName(bid.getCurrentBidderName())
            .amount(bid.getHighBid())
            .placedAt(finalizedAt)
            .note("WON")
            .build());

        // Clear active bidding state (keep bidHistory) and mark as sold
        // sold=true overrides enableBid=true — the item is no longer biddable
        bid.setBidOpenedAt(null);
        bid.setBidStartedAt(null);
        bid.setCurrentBidderId(null);
        bid.setCurrentBidderName(null);
        bid.setSold(true);
        ComicService.getServiceInstance().updateComic(comic, "system:bid-finalized");

        // Add to winner's cart
        User winner = UserService.getServiceInstance().findById(winnerId)
            .orElseThrow(() -> new IllegalStateException("Winning bidder not found: " + winnerId));

        Cart cart = CartService.getServiceInstance().addBidWonItem(winner, comicId, bid.getHighBid());
        log.info("Bid finalized for comic {} — winner: {} at ${}", comicId, winner.getName(), bid.getHighBid());
        return cart;
    }
}
