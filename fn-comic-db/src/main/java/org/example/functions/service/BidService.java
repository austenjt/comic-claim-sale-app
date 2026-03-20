package org.example.functions.service;

import lombok.extern.slf4j.Slf4j;
import org.example.functions.model.BidEntry;
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
     * Start bidding on an enableBid comic. Sets the first user as the initial leader
     * at the comic's existing highBid (or $0 if none). If bidding is already in progress,
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
        if (comic.getBidStartedAt() != null) {
            // Bidding already in progress — return current state
            return comic;
        }

        String now = Instant.now().toString();
        comic.setBidStartedAt(now);
        comic.setCurrentBidderId(user.getId());
        comic.setCurrentBidderName(user.getName());

        BigDecimal startingBid = comic.getHighBid() != null ? comic.getHighBid() : BigDecimal.ZERO;
        comic.setHighBid(startingBid);

        if (comic.getBidHistory() == null) {
            comic.setBidHistory(new ArrayList<>());
        }
        comic.getBidHistory().add(BidEntry.builder()
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
        if (comic.getBidStartedAt() == null) {
            throw new IllegalStateException("Bidding has not started for comic " + comicId + ".");
        }

        Instant bidEnd = Instant.parse(comic.getBidStartedAt())
            .plus(EnvHelper.getBiddingCycleMins(), ChronoUnit.MINUTES);
        if (Instant.now().isAfter(bidEnd)) {
            throw new IllegalStateException("Bidding period has ended for comic " + comicId + ".");
        }

        BigDecimal currentHigh = comic.getHighBid() != null ? comic.getHighBid() : BigDecimal.ZERO;
        if (amount.compareTo(currentHigh) <= 0) {
            throw new IllegalArgumentException(
                "Bid must be greater than the current high bid of $" + currentHigh + ".");
        }

        String now = Instant.now().toString();
        comic.setHighBid(amount);
        comic.setCurrentBidderId(user.getId());
        comic.setCurrentBidderName(user.getName());

        if (comic.getBidHistory() == null) {
            comic.setBidHistory(new ArrayList<>());
        }
        comic.getBidHistory().add(BidEntry.builder()
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

        if (comic.getBidStartedAt() == null) {
            throw new IllegalStateException("No active bidding found for comic " + comicId + ".");
        }

        Instant bidEnd = Instant.parse(comic.getBidStartedAt())
            .plus(EnvHelper.getBiddingCycleMins(), ChronoUnit.MINUTES);
        if (Instant.now().isBefore(bidEnd)) {
            throw new IllegalStateException("Bidding period has not yet ended for comic " + comicId + ".");
        }

        String winnerId = comic.getCurrentBidderId();
        if (winnerId == null) {
            // Nobody bid — just clear bidding state
            comic.setBidStartedAt(null);
            comic.setCurrentBidderId(null);
            comic.setCurrentBidderName(null);
            ComicService.getServiceInstance().updateComic(comic, "system:bid-expire-no-winner");
            throw new IllegalStateException("No bids were placed; bidding cancelled for comic " + comicId + ".");
        }

        // Stamp targetPrice with the winning bid so CartService picks it up
        if (comic.getHighBid() != null && comic.getHighBid().compareTo(BigDecimal.ZERO) > 0) {
            comic.setTargetPrice(comic.getHighBid());
        }

        // Clear active bidding state (keep bidHistory)
        comic.setBidStartedAt(null);
        comic.setCurrentBidderId(null);
        comic.setCurrentBidderName(null);
        ComicService.getServiceInstance().updateComic(comic, "system:bid-finalized");

        // Add to winner's cart
        User winner = UserService.getServiceInstance().findById(winnerId)
            .orElseThrow(() -> new IllegalStateException("Winning bidder not found: " + winnerId));

        Cart cart = CartService.getServiceInstance().addItem(winner, comicId);
        log.info("Bid finalized for comic {} — winner: {} at ${}", comicId, winner.getName(), comic.getHighBid());
        return cart;
    }
}
