package org.example.functions.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.client.CosmosDbClient;
import org.example.functions.util.EnvHelper;
import org.example.functions.util.Mappers;
import org.example.functions.util.ShippingCalculator;
import org.example.functions.model.Cart;
import org.example.functions.model.CartItem;
import org.example.functions.model.ClaimNotification;
import org.example.functions.model.ComicBook;
import org.example.functions.model.ComicNumber;
import org.example.functions.model.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class CartService {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;
    private final CosmosContainer cartsContainer;
    private final CosmosContainer returnEventsContainer;
    private static CartService SERVICE_INSTANCE;

    public static CartService getServiceInstance() {
        if (Objects.isNull(SERVICE_INSTANCE)) {
            SERVICE_INSTANCE = new CartService();
        }
        return SERVICE_INSTANCE;
    }

    public CartService() {
        this.cartsContainer = CosmosDbClient.getInstance().getCartsContainer();
        this.returnEventsContainer = CosmosDbClient.getInstance().getReturnEventsContainer();
    }

    /** Returns the user's active (non-FULFILLED, non-DELETED) cart, lazily finalizing if past the deadline. */
    public Optional<Cart> getActiveCart(String userId) {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.userId = @userId AND c.status NOT IN ('FULFILLED', 'DELETED') ORDER BY c.createdAt DESC OFFSET 0 LIMIT 1",
            List.of(new SqlParameter("@userId", userId)));
        for (ObjectNode node : cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                Cart cart = OBJECT_MAPPER.treeToValue(node, Cart.class);
                return Optional.of(checkAndFinalize(cart));
            } catch (Exception e) {
                log.error("Error parsing cart", e);
            }
        }
        return Optional.empty();
    }

    /** Get or create an OPEN cart for the user. */
    public Cart getOrCreateCart(User user) {
        Optional<Cart> existing = getActiveCart(user.getId());
        if (existing.isPresent()) return existing.get();

        Cart cart = new Cart();
        cart.setId(UUID.randomUUID().toString());
        cart.setUserId(user.getId());
        cart.setUserName(user.getName());
        cart.setUserEmail(user.getEmail());
        cart.setStatus("OPEN");
        cart.setCreatedAt(Instant.now().toString());
        save(cart);
        log.info("Created new cart {} for user {}", cart.getId(), user.getId());
        return cart;
    }

    /** Add a comic to the user's cart via a normal claim. Throws if comic is already claimed or cart is not OPEN. */
    public Cart addItem(User user, String comicId) {
        return addItemInternal(user, comicId, false, null);
    }

    /** Add a comic to the user's cart as the winner of a completed bid cycle.
     *  The item will be marked {@code wonViaBid=true} and cannot be returned by the user. */
    public Cart addBidWonItem(User user, String comicId, java.math.BigDecimal winningBid) {
        return addItemInternal(user, comicId, true, winningBid);
    }

    private Cart addItemInternal(User user, String comicId, boolean wonViaBid, java.math.BigDecimal overridePrice) {
        if (isComicClaimed(comicId)) {
            throw new IllegalStateException("Comic " + comicId + " is already claimed.");
        }
        Cart cart = getOrCreateCart(user);
        if (!"OPEN".equals(cart.getStatus())) {
            throw new IllegalStateException("Cart is not open for new claims (status: " + cart.getStatus() + ").");
        }
        ComicBook comic = ComicService.getServiceInstance().getComicById(Integer.parseInt(comicId))
            .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + comicId));

        CartItem item = new CartItem();
        item.setComicId(comicId);
        item.setComicTitle(comic.getTitle());
        item.setComicNumber(formatComicNumber(comic.getNumber()));
        item.setPrice(overridePrice != null ? overridePrice.doubleValue()
                : comic.getSalePrice() != null ? comic.getSalePrice().doubleValue()
                : comic.getTargetPrice() != null ? comic.getTargetPrice().doubleValue() : 0.0);
        item.setClaimedAt(Instant.now().toString());
        item.setWonViaBid(wonViaBid);

        cart.getItems().add(item);
        save(cart);
        return cart;
    }

    /** Add all comics in a set to the user's cart. containerId must reference a docType="SET" comic. */
    public Cart addSet(User user, String containerId) {
        ComicBook container = ComicService.getServiceInstance().getComicById(Integer.parseInt(containerId))
            .orElseThrow(() -> new IllegalArgumentException("Container comic not found: " + containerId));
        if (!"SET".equals(container.getDocType())) {
            throw new IllegalArgumentException("Comic " + containerId + " is not a set container.");
        }
        Integer collectionGroup = container.getCollectionGroup();
        if (collectionGroup == null) {
            throw new IllegalArgumentException("Container comic has no collectionGroup set.");
        }

        List<ComicBook> setMembers = ComicService.getServiceInstance().getComicsByCollectionGroup(collectionGroup);
        if (setMembers.isEmpty()) {
            throw new IllegalArgumentException("No comics found in collection group: " + collectionGroup);
        }

        // Pre-validate: none of the actual set members (non-container) are already in an active cart
        for (ComicBook member : setMembers) {
            if ("SET".equals(member.getDocType())) continue; // skip the container entry
            String memberId = String.valueOf(member.getId());
            if (isComicClaimed(memberId)) {
                throw new IllegalStateException("Comic " + memberId + " in the set is already claimed.");
            }
        }

        Cart cart = getOrCreateCart(user);
        if (!"OPEN".equals(cart.getStatus())) {
            throw new IllegalStateException("Cart is not open for new claims (status: " + cart.getStatus() + ").");
        }

        String claimedAt = Instant.now().toString();
        long memberCount = 0;
        for (ComicBook member : setMembers) {
            CartItem item = new CartItem();
            item.setComicId(String.valueOf(member.getId()));
            item.setComicTitle(member.getTitle());
            item.setComicNumber(formatComicNumber(member.getNumber()));
            item.setClaimedAt(claimedAt);
            item.setCollectionGroup(collectionGroup);
            if ("SET".equals(member.getDocType())) {
                // Container row: kept in cart for reference but priced at $0 and flagged
                item.setPrice(0.0);
                item.setSetContainer(true);
            } else {
                item.setPrice(member.getSalePrice() != null ? member.getSalePrice().doubleValue()
                        : member.getTargetPrice() != null ? member.getTargetPrice().doubleValue() : 0.0);
                memberCount++;
            }
            cart.getItems().add(item);
        }
        save(cart);
        log.info("User {} claimed set (collectionGroup={}) with {} items", user.getId(), collectionGroup, memberCount);
        return cart;
    }

    /** Remove an item from the cart. If the item belongs to a set (collectionGroup > 0),
     *  all other items in the same set are also removed. Allowed in OPEN or FINALIZING status. */
    public Cart removeItem(String userId, String comicId) {
        Cart cart = getActiveCart(userId)
            .orElseThrow(() -> new IllegalStateException("No active cart found."));
        if ("FINALIZED".equals(cart.getStatus()) || "FULFILLED".equals(cart.getStatus())) {
            throw new IllegalStateException("Cannot remove items from a " + cart.getStatus() + " cart.");
        }

        CartItem target = cart.getItems().stream()
            .filter(i -> comicId.equals(i.getComicId())).findFirst().orElse(null);

        // Bid-won items cannot be returned by the user
        if (target != null && target.isWonViaBid()) {
            throw new IllegalStateException("Items won via bidding cannot be returned.");
        }

        // Collect all items to remove (cascade for set members, single for standalone)
        List<CartItem> toRemove;
        if (target != null && target.getCollectionGroup() != null && target.getCollectionGroup() > 0) {
            final int group = target.getCollectionGroup();
            toRemove = cart.getItems().stream()
                .filter(i -> i.getCollectionGroup() != null && group == i.getCollectionGroup())
                .collect(java.util.stream.Collectors.toList());
            log.info("Set removal: removing {} items with collectionGroup={} from cart {}", toRemove.size(), group, cart.getId());
        } else {
            toRemove = target != null ? List.of(target) : List.of();
        }

        java.util.Set<String> idsToRemove = toRemove.stream()
            .map(CartItem::getComicId)
            .collect(java.util.stream.Collectors.toSet());
        cart.getItems().removeIf(i -> idsToRemove.contains(i.getComicId()));

        if (cart.getItems().isEmpty()) {
            cart.setStatus("DELETED");
            cart.setFinalizeAfter(null);
            cart.setFinalizedAt(null);
            log.info("Cart {} is now empty after user removal, marked as DELETED", cart.getId());
        }
        save(cart);
        for (CartItem removed : toRemove) {
            writeReturnEvent(removed);
        }
        return cart;
    }

    /** Admin: award a comic to a user's cart at $0.00. Works for OPEN, FINALIZING, or FINALIZED carts; creates a new cart if needed.
     *  Awarded items are flagged {@code isAwarded=true} and excluded from discount calculations. */
    public Cart awardItem(User user, String comicId) {
        if (isComicClaimed(comicId)) {
            throw new IllegalStateException("Comic " + comicId + " is already claimed by another user.");
        }
        Cart cart = getOrCreateCart(user);
        if (!"OPEN".equals(cart.getStatus()) && !"FINALIZING".equals(cart.getStatus()) && !"FINALIZED".equals(cart.getStatus())) {
            throw new IllegalStateException("Cannot award to a cart with status: " + cart.getStatus());
        }
        ComicBook comic = ComicService.getServiceInstance().getComicById(Integer.parseInt(comicId))
            .orElseThrow(() -> new IllegalArgumentException("Comic not found: " + comicId));

        CartItem item = new CartItem();
        item.setComicId(comicId);
        item.setComicTitle(comic.getTitle());
        item.setComicNumber(formatComicNumber(comic.getNumber()));
        item.setPrice(0.0);
        item.setClaimedAt(Instant.now().toString());
        item.setAwarded(true);

        cart.getItems().add(item);
        save(cart);
        log.info("Awarded comic {} to user {} at $0 (cart status: {})", comicId, user.getId(), cart.getStatus());
        return cart;
    }

    /** Submit an order. Cart moves to FINALIZING; fulfillment is unlocked when the admin marks payment as PAID. */
    public Cart submitOrder(String userId, String customerNotes) {
        Cart cart = getActiveCart(userId)
            .orElseThrow(() -> new IllegalStateException("No active cart found."));
        if (!"OPEN".equals(cart.getStatus())) {
            throw new IllegalStateException("Order can only be submitted from an OPEN cart.");
        }
        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot submit an empty cart.");
        }
        DiscountService.DiscountResult discountResult = DiscountService.getServiceInstance().applyDiscounts(cart);
        cart.setDiscountAmount(discountResult.getAmount());
        cart.setDiscountDescription(discountResult.getDescription());
        cart.setDiscountExcludesSets(discountResult.isExcludedSets() ? Boolean.TRUE : null);
        cart.setDiscountBreakdown(discountResult.getBreakdown());
        int bookCount = (int) cart.getItems().stream().filter(i -> !i.isSetContainer()).count();
        cart.setShippingCost(ShippingCalculator.estimate(bookCount).getEstimatedCost());
        cart.setStatus("FINALIZING");
        cart.setPaymentStatus("UNPAID");
        if (customerNotes != null && !customerNotes.isBlank()) {
            cart.setCustomerNotes(customerNotes.trim());
        }
        save(cart);
        log.info("Cart {} submitted", cart.getId());
        sendOrderSubmittedEmail(cart);
        return cart;
    }

    /** Admin: update internal notes on an active cart. */
    public Cart updateAdminNotes(String cartId, String adminNotes) {
        Cart cart = findCartById(cartId);
        cart.setAdminNotes(adminNotes != null && !adminNotes.isBlank() ? adminNotes.trim() : null);
        save(cart);
        log.info("Admin notes updated for cart {}", cartId);
        return cart;
    }

    /** Admin: update the payment status of an active cart. Valid values: UNPAID, PARTIAL, PAID. */
    public Cart updatePaymentStatus(String cartId, String status) {
        Cart cart = findCartById(cartId);
        cart.setPaymentStatus(status);
        save(cart);
        log.info("Payment status for cart {} set to {}", cartId, status);
        if ("PAID".equals(status)) {
            sendPaymentReceivedEmail(cart);
        }
        return cart;
    }

    /** Admin: revert a submitted cart back to OPEN so the user can add more items. */
    public Cart unsubmitOrder(String cartId) {
        Cart cart = findCartById(cartId);
        if (!"FINALIZING".equals(cart.getStatus()) && !"FINALIZED".equals(cart.getStatus())) {
            throw new IllegalStateException("Can only unsubmit a FINALIZING or FINALIZED cart (current: " + cart.getStatus() + ").");
        }
        cart.setStatus("OPEN");
        cart.setFinalizeAfter(null);
        cart.setFinalizedAt(null);
        cart.setDiscountAmount(0.0);
        cart.setDiscountDescription(null);
        cart.setShippingCost(0.0);
        save(cart);
        log.info("Cart {} reverted to OPEN by admin", cartId);
        return cart;
    }

    /** Admin: mark a cart as FULFILLED and stamp soldTo/dateSold on each comic. */
    public Cart fulfillCart(String cartId) {
        Cart cart = findCartById(cartId);
        cart.setStatus("FULFILLED");
        cart.setFulfilledAt(Instant.now().toString());
        save(cart);
        log.info("Cart {} marked as FULFILLED", cartId);
        ArchiveService.getServiceInstance().archiveCart(cart);
        sendFulfillmentEmail(cart);
        // Stamp soldTo/dateSold on each comic (skip set container rows)
        String soldDate = Instant.now().toString();
        for (CartItem item : cart.getItems()) {
            if (item.isSetContainer()) continue;
            try {
                ComicService.getServiceInstance().getComicById(Integer.parseInt(item.getComicId()))
                    .ifPresent(comic -> {
                        comic.setSoldTo(cart.getUserName());
                        comic.setDateSold(soldDate);
                        ComicService.getServiceInstance().updateComic(comic, "system:fulfill");
                    });
            } catch (Exception e) {
                log.warn("Could not stamp soldTo on comic {}: {}", item.getComicId(), e.getMessage());
            }
        }
        return cart;
    }

    /** Admin: all OPEN carts that have at least one item. */
    public List<Cart> getAllOpenCarts() {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.status = 'OPEN' ORDER BY c.createdAt DESC");
        List<Cart> result = new ArrayList<>();
        for (ObjectNode node : cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                Cart cart = OBJECT_MAPPER.treeToValue(node, Cart.class);
                if (!cart.getItems().isEmpty()) {
                    result.add(cart);
                }
            } catch (Exception e) {
                log.error("Error parsing open cart", e);
            }
        }
        return result;
    }

    /** Admin: submitted/finalized carts only (FINALIZING or FINALIZED), auto-finalizing as needed. */
    public List<Cart> getAllActiveCarts() {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.status IN ('FINALIZING', 'FINALIZED') ORDER BY c.createdAt DESC");
        List<Cart> result = new ArrayList<>();
        for (ObjectNode node : cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                Cart cart = OBJECT_MAPPER.treeToValue(node, Cart.class);
                result.add(checkAndFinalize(cart));
            } catch (Exception e) {
                log.error("Error parsing cart in getAllActiveCarts", e);
            }
        }
        return result;
    }

    /** Returns a map of comicId → claimedAt for all items in any active cart (excluding FULFILLED and DELETED). */
    public java.util.Map<String, String> getClaimedComicMap() {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.status NOT IN ('FULFILLED', 'DELETED') ORDER BY c.createdAt DESC");
        java.util.Map<String, String> claimed = new java.util.LinkedHashMap<>();
        for (ObjectNode node : cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                Cart cart = OBJECT_MAPPER.treeToValue(node, Cart.class);
                for (CartItem item : cart.getItems()) {
                    claimed.putIfAbsent(item.getComicId(), item.getClaimedAt());
                }
            } catch (Exception e) {
                log.error("Error parsing cart in getClaimedComicMap", e);
            }
        }
        return claimed;
    }

    /** Returns true if any item with the given collectionGroup is in an active (non-FULFILLED, non-DELETED) cart. */
    public boolean isSetClaimed(int collectionGroup) {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT c.id FROM c JOIN item IN c.items WHERE item.collectionGroup = @group AND c.status NOT IN ('FULFILLED', 'DELETED')",
            List.of(new SqlParameter("@group", collectionGroup)));
        return cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)
            .iterator().hasNext();
    }

    /** Returns true if the comic is in any active (non-FULFILLED, non-DELETED) cart. */
    public boolean isComicClaimed(String comicId) {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT c.id FROM c JOIN item IN c.items WHERE item.comicId = @comicId AND c.status NOT IN ('FULFILLED', 'DELETED')",
            List.of(new SqlParameter("@comicId", comicId)));
        return cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)
            .iterator().hasNext();
    }

    /** No-op: time-based auto-finalization has been removed. Fulfillment is unlocked by admin marking payment as PAID. */
    private Cart checkAndFinalize(Cart cart) {
        return cart;
    }

    /** Returns ALL FULFILLED carts across all users (for migration). */
    public List<Cart> getAllFulfilledCarts() {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.status = 'FULFILLED' ORDER BY c.fulfilledAt DESC");
        List<Cart> result = new ArrayList<>();
        for (ObjectNode node : cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                result.add(OBJECT_MAPPER.treeToValue(node, Cart.class));
            } catch (Exception e) {
                log.error("Error parsing fulfilled cart in getAllFulfilledCarts", e);
            }
        }
        return result;
    }

    /** Sweeps all FINALIZING carts and transitions any past their deadline to FINALIZED. Returns count finalized. */
    public int finalizeOverdueCarts() {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.status = 'FINALIZING'");
        int count = 0;
        for (ObjectNode node : cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                Cart cart = OBJECT_MAPPER.treeToValue(node, Cart.class);
                String before = cart.getStatus();
                checkAndFinalize(cart);
                if ("FINALIZED".equals(cart.getStatus()) && !"FINALIZED".equals(before)) {
                    count++;
                }
            } catch (Exception e) {
                log.error("Error during finalization sweep: {}", e.getMessage());
            }
        }
        return count;
    }

    /** Expires OPEN carts older than CART_EXPIRY_DAYS, returning all claimed comics to available inventory. */
    public int expireAbandonedCarts() {
        String cutoff = Instant.now()
            .minus(EnvHelper.getCartExpiryDays(), java.time.temporal.ChronoUnit.DAYS)
            .toString();
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.status = 'OPEN' AND c.createdAt <= @cutoff",
            List.of(new SqlParameter("@cutoff", cutoff)));
        int expired = 0;
        for (ObjectNode node : cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                Cart cart = OBJECT_MAPPER.treeToValue(node, Cart.class);
                if (cart.getItems().isEmpty()) {
                    cart.setStatus("DELETED");
                    save(cart);
                    continue;
                }
                for (CartItem item : cart.getItems()) {
                    writeReturnEvent(item);
                }
                cart.setStatus("DELETED");
                save(cart);
                log.info("Expired abandoned cart {} for user {} ({} items returned)",
                    cart.getId(), cart.getUserId(), cart.getItems().size());
                expired++;
            } catch (Exception e) {
                log.error("Error expiring cart in node: {}", e.getMessage());
            }
        }
        return expired;
    }

    /** Deletes every document in the carts container. Used during database reset. */
    public void deleteAllCarts() {
        SqlQuerySpec query = new SqlQuerySpec("SELECT c.id FROM c");
        for (ObjectNode node : cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            String id = node.get("id").asText();
            try {
                cartsContainer.deleteItem(id, new PartitionKey(id), new CosmosItemRequestOptions());
            } catch (Exception e) {
                log.warn("Failed to delete cart {}: {}", id, e.getMessage());
            }
        }
        log.info("All carts deleted.");
    }

    /** Returns all FULFILLED carts for a user, newest first. */
    public List<Cart> getFulfilledCarts(String userId) {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.userId = @userId AND c.status = 'FULFILLED' ORDER BY c.fulfilledAt DESC",
            List.of(new SqlParameter("@userId", userId)));
        List<Cart> result = new ArrayList<>();
        for (ObjectNode node : cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                result.add(OBJECT_MAPPER.treeToValue(node, Cart.class));
            } catch (Exception e) {
                log.error("Error parsing fulfilled cart", e);
            }
        }
        return result;
    }

    /** Admin: remove a comic from whichever active cart contains it.
     *  If the cart becomes empty, resets it to OPEN so the user can make new claims. */
    public Cart removeItemAdmin(String comicId) {
        List<Cart> allCarts = new ArrayList<>();
        allCarts.addAll(getAllActiveCarts()); // FINALIZING and FINALIZED
        allCarts.addAll(getAllOpenCarts());   // OPEN with items
        for (Cart cart : allCarts) {
            CartItem found = cart.getItems().stream()
                .filter(i -> comicId.equals(i.getComicId())).findFirst().orElse(null);
            if (found != null) {
                // Cascade: if the item belongs to a set, remove all items in that set
                List<CartItem> toRemove;
                if (found.getCollectionGroup() != null && found.getCollectionGroup() > 0) {
                    final int group = found.getCollectionGroup();
                    toRemove = cart.getItems().stream()
                        .filter(i -> i.getCollectionGroup() != null && group == i.getCollectionGroup())
                        .collect(java.util.stream.Collectors.toList());
                    log.info("Admin set release: removing {} items with collectionGroup={} from cart {}", toRemove.size(), group, cart.getId());
                } else {
                    toRemove = List.of(found);
                }
                java.util.Set<String> idsToRemove = toRemove.stream()
                    .map(CartItem::getComicId)
                    .collect(java.util.stream.Collectors.toSet());
                cart.getItems().removeIf(i -> idsToRemove.contains(i.getComicId()));
                if (cart.getItems().isEmpty()) {
                    cart.setStatus("DELETED");
                    cart.setFinalizeAfter(null);
                    cart.setFinalizedAt(null);
                    log.info("Cart {} is now empty after admin release, marked as DELETED", cart.getId());
                }
                save(cart);
                // For set releases, write a single return event using the container item
                CartItem eventItem = toRemove.stream()
                    .filter(CartItem::isSetContainer)
                    .findFirst()
                    .orElse(found);
                writeReturnEvent(eventItem);
                log.info("Admin removed comic {} (and {} set siblings) from cart {}", comicId, toRemove.size() - 1, cart.getId());
                return cart;
            }
        }
        throw new IllegalArgumentException("Comic " + comicId + " not found in any active cart.");
    }

    /** Returns claim/award/return events from the last {@code secondsBack} seconds. */
    public List<ClaimNotification> getRecentClaimEvents(int secondsBack) {
        String cutoff = Instant.now().minus(secondsBack, java.time.temporal.ChronoUnit.SECONDS).toString();
        List<ClaimNotification> result = new ArrayList<>();

        // Claim / award events from active carts
        SqlQuerySpec claimQuery = new SqlQuerySpec(
            "SELECT c.userName, item.comicId, item.comicTitle, item.comicNumber, item.price, item.claimedAt " +
            "FROM c JOIN item IN c.items " +
            "WHERE c.status NOT IN ('FULFILLED', 'DELETED') AND item.claimedAt >= @cutoff",
            List.of(new SqlParameter("@cutoff", cutoff)));
        for (ObjectNode node : cartsContainer.queryItems(claimQuery, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                ClaimNotification n = OBJECT_MAPPER.treeToValue(node, ClaimNotification.class);
                n.setEventType(n.getPrice() == 0.0 ? "AWARD" : "CLAIM");
                result.add(n);
            } catch (Exception e) {
                log.error("Error parsing claim notification", e);
            }
        }

        // Return events
        SqlQuerySpec returnQuery = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.returnedAt >= @cutoff",
            List.of(new SqlParameter("@cutoff", cutoff)));
        for (ObjectNode node : returnEventsContainer.queryItems(returnQuery, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                org.example.functions.model.ReturnEvent re =
                    OBJECT_MAPPER.treeToValue(node, org.example.functions.model.ReturnEvent.class);
                ClaimNotification n = ClaimNotification.builder()
                    .eventType("RETURN")
                    .comicId(re.getComicId())
                    .comicTitle(re.getComicTitle())
                    .comicNumber(re.getComicNumber())
                    .claimedAt(re.getReturnedAt())
                    .build();
                result.add(n);
            } catch (Exception e) {
                log.error("Error parsing return event", e);
            }
        }

        return result;
    }

    private void writeReturnEvent(CartItem item) {
        try {
            org.example.functions.model.ReturnEvent event = org.example.functions.model.ReturnEvent.builder()
                .id(UUID.randomUUID().toString())
                .comicId(item.getComicId())
                .comicTitle(item.getComicTitle())
                .comicNumber(item.getComicNumber())
                .returnedAt(Instant.now().toString())
                .build();
            ObjectNode node = OBJECT_MAPPER.valueToTree(event);
            returnEventsContainer.createItem(node, new PartitionKey(event.getId()), new CosmosItemRequestOptions());
            log.info("Wrote return event for comic {}", item.getComicId());
        } catch (Exception e) {
            log.warn("Failed to write return event for comic {}: {}", item.getComicId(), e.getMessage());
        }
    }

    private static String formatComicNumber(ComicNumber n) {
        if (n == null) return null;
        if (n.getNumber() != null) return "#" + n.getNumber();
        if (n.getSentinel() != null) return "#" + n.getSentinel().toString();
        return null;
    }

    /**
     * Deletes return events older than the given number of days.
     * @return number of events deleted
     */
    public int pruneOldReturnEvents(int daysOld) {
        String cutoff = Instant.now().minus(daysOld, ChronoUnit.DAYS).toString();
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT c.id FROM c WHERE c.returnedAt < @cutoff",
            List.of(new SqlParameter("@cutoff", cutoff)));
        int count = 0;
        for (ObjectNode node : returnEventsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                String id = node.get("id").asText();
                returnEventsContainer.deleteItem(id, new PartitionKey(id), new CosmosItemRequestOptions());
                count++;
            } catch (Exception e) {
                log.warn("Failed to delete return event {}: {}", node.get("id"), e.getMessage());
            }
        }
        log.info("Pruned {} return events older than {} days", count, daysOld);
        return count;
    }

    private static String buildOrderItemsText(Cart cart) {
        StringBuilder sb = new StringBuilder();
        double subtotal = 0;
        for (CartItem item : cart.getItems()) {
            if (item.isSetContainer()) continue;
            String num = item.getComicNumber() != null ? " " + item.getComicNumber() : "";
            sb.append(String.format("  %s%s — $%.2f%n", item.getComicTitle(), num, item.getPrice()));
            subtotal += item.getPrice();
        }
        sb.append(String.format("%nSubtotal: $%.2f%n", subtotal));
        if (cart.getShippingCost() > 0) {
            sb.append(String.format("Shipping: $%.2f%n", cart.getShippingCost()));
        }
        if (cart.getDiscountAmount() > 0) {
            String desc = cart.getDiscountDescription() != null ? " (" + cart.getDiscountDescription() + ")" : "";
            sb.append(String.format("Discount%s: -$%.2f%n", desc, cart.getDiscountAmount()));
        }
        double total = subtotal + cart.getShippingCost() - cart.getDiscountAmount();
        sb.append(String.format("Total: $%.2f%n", total));
        return sb.toString();
    }

    private void sendOrderSubmittedEmail(Cart cart) {
        if (cart.getUserEmail() == null) return;
        try {
            String adminEmail = EnvHelper.getAdminEmail();
            String body = "Hi " + cart.getUserName() + ",\n\n"
                + "Thank you for your order! We've received your claim and it is currently being processed.\n\n"
                + "A Lightning Comics team member will contact you shortly to arrange payment.\n\n"
                + "ORDER SUMMARY\n"
                + "=============\n"
                + buildOrderItemsText(cart) + "\n"
                + (cart.getCustomerNotes() != null && !cart.getCustomerNotes().isBlank()
                    ? "Your notes: " + cart.getCustomerNotes() + "\n\n" : "")
                + "Thank you for shopping with Lightning Comics!\n";
            EmailService.getServiceInstance().send(
                List.of(cart.getUserEmail()), adminEmail, adminEmail,
                "Your Order is Being Processed", body);
        } catch (Exception e) {
            log.warn("Failed to send order submitted email to {}: {}", cart.getUserEmail(), e.getMessage());
        }
    }

    private void sendPaymentReceivedEmail(Cart cart) {
        if (cart.getUserEmail() == null) return;
        try {
            String body = "Hi " + cart.getUserName() + ",\n\n"
                + "Great news! We've received your payment for your recent order.\n\n"
                + "ORDER RECEIPT\n"
                + "=============\n"
                + buildOrderItemsText(cart) + "\n"
                + "Thank you for your payment!\n\n"
                + "Lightning Comics\n";
            EmailService.getServiceInstance().send(
                List.of(cart.getUserEmail()), null, null,
                "Payment Received", body);
        } catch (Exception e) {
            log.warn("Failed to send payment received email to {}: {}", cart.getUserEmail(), e.getMessage());
        }
    }

    private void sendFulfillmentEmail(Cart cart) {
        if (cart.getUserEmail() == null) return;
        try {
            StringBuilder itemsText = new StringBuilder();
            for (CartItem item : cart.getItems()) {
                if (item.isSetContainer()) continue;
                String num = item.getComicNumber() != null ? " " + item.getComicNumber() : "";
                itemsText.append(String.format("  %s%s%n", item.getComicTitle(), num));
            }
            String body = "Hi " + cart.getUserName() + ",\n\n"
                + "Lightning Comics has fulfilled your order and it's on the way!\n\n"
                + itemsText
                + "\nThank you for your business. We hope you enjoy your comics!\n\n"
                + "Lightning Comics\n";
            String adminEmail = EnvHelper.getAdminEmail();
            EmailService.getServiceInstance().send(
                List.of(cart.getUserEmail()), adminEmail, adminEmail,
                "Your Order Has Shipped", body);
        } catch (Exception e) {
            log.warn("Failed to send fulfillment email to {}: {}", cart.getUserEmail(), e.getMessage());
        }
    }

    private Cart findCartById(String cartId) {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.id = @cartId",
            List.of(new SqlParameter("@cartId", cartId)));
        for (ObjectNode node : cartsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                return OBJECT_MAPPER.treeToValue(node, Cart.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse cart: " + cartId, e);
            }
        }
        throw new IllegalArgumentException("Cart not found: " + cartId);
    }

    private void save(Cart cart) {
        ObjectNode node = OBJECT_MAPPER.valueToTree(cart);
        try {
            cartsContainer.readItem(cart.getId(), new PartitionKey(cart.getId()), ObjectNode.class);
            cartsContainer.replaceItem(node, cart.getId(), new PartitionKey(cart.getId()), new CosmosItemRequestOptions());
        } catch (Exception e) {
            cartsContainer.createItem(node, new PartitionKey(cart.getId()), new CosmosItemRequestOptions());
        }
    }
}
