package org.example.functions.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.client.CosmosDbClient;
import org.example.functions.model.Cart;
import org.example.functions.model.CartDiscount;
import org.example.functions.util.Mappers;
import org.example.functions.model.CartItem;
import org.example.functions.model.Discount;
import org.example.functions.model.enums.DiscountType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class DiscountService {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;
    private final CosmosContainer discountsContainer;
    /** Thread-safe lazy singleton via the initialization-on-demand holder idiom. */
    private static class Holder {
        private static final DiscountService INSTANCE = new DiscountService();
    }

    public static DiscountService getServiceInstance() {
        return Holder.INSTANCE;
    }

    public DiscountService() {
        this.discountsContainer = CosmosDbClient.getInstance().getDiscountsContainer();
    }

    public List<Discount> getAllDiscounts() {
        List<Discount> result = new ArrayList<>();
        for (ObjectNode node : discountsContainer.queryItems(
                new SqlQuerySpec("SELECT * FROM c"), new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                result.add(OBJECT_MAPPER.treeToValue(node, Discount.class));
            } catch (Exception e) {
                log.error("Error parsing discount", e);
            }
        }
        return result;
    }

    public List<Discount> getActiveDiscounts() {
        List<Discount> result = new ArrayList<>();
        for (ObjectNode node : discountsContainer.queryItems(
                new SqlQuerySpec("SELECT * FROM c WHERE c.isActive = true"),
                new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                result.add(OBJECT_MAPPER.treeToValue(node, Discount.class));
            } catch (Exception e) {
                log.error("Error parsing active discount", e);
            }
        }
        return result;
    }

    public Discount createDiscount(Discount discount) {
        discount.setId(UUID.randomUUID().toString());
        discount.setCreatedAt(Instant.now().toString());
        ObjectNode node = OBJECT_MAPPER.valueToTree(discount);
        discountsContainer.createItem(node, new PartitionKey(discount.getId()), new CosmosItemRequestOptions());
        return discount;
    }

    public Discount updateDiscount(Discount discount) {
        ObjectNode node = OBJECT_MAPPER.valueToTree(discount);
        discountsContainer.replaceItem(node, discount.getId(), new PartitionKey(discount.getId()), new CosmosItemRequestOptions());
        return discount;
    }

    public void deleteDiscount(String id) {
        discountsContainer.deleteItem(id, new PartitionKey(id), new CosmosItemRequestOptions());
    }

    /** Deletes all discount rules. Used during database reset. */
    public void deleteAllDiscounts() {
        SqlQuerySpec query = new SqlQuerySpec("SELECT c.id FROM c");
        for (ObjectNode node : discountsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            String id = node.get("id").asText();
            try {
                discountsContainer.deleteItem(id, new PartitionKey(id), new CosmosItemRequestOptions());
            } catch (Exception e) {
                log.warn("Failed to delete discount {}: {}", id, e.getMessage());
            }
        }
        log.info("All discounts deleted.");
    }

    /**
     * Calculates total discount savings for the given cart based on all active rules.
     * All rules stack; total savings are capped at the cart subtotal.
     */
    public DiscountResult applyDiscounts(Cart cart) {
        return computeDiscounts(cart, getActiveDiscounts());
    }

    /**
     * Pure-functional discount calculation, separated from the Cosmos read so unit tests can
     * exercise the math in isolation. Package-private on purpose.
     */
    static DiscountResult computeDiscounts(Cart cart, List<Discount> active) {
        if (active == null || active.isEmpty()) {
            return new DiscountResult(0.0, null, false, false, false, new ArrayList<>());
        }

        // Awarded (isAwarded flag) and free ($0.00) items are excluded from both counting and discount application.
        List<CartItem> baseItems = cart.getItems().stream()
            .filter(i -> !i.isAwarded() && i.getPrice() > 0)
            .collect(Collectors.toList());

        // Pre-compute free-item picks for all BUY_X_GET_ONE_FREE rules. Rules with smaller
        // xBooks are processed first so they claim the cheapest items, then larger-X rules
        // dedupe against those picks. Without this, two rules can both pick the same cheap
        // book — the per-row math caps the second "free" assignment at zero, silently losing
        // discount value the customer was promised.
        Map<String, List<CartItem>> buyXFreePicks = computeBuyXFreePicks(baseItems, active);

        double totalSavings = 0.0;
        boolean anySetsExcluded = false;
        boolean anyGradedExcluded = false;
        boolean freeShipping = false;
        List<String> descriptions = new ArrayList<>();
        List<CartDiscount> breakdown = new ArrayList<>();
        double baseSubtotal = baseItems.stream()
            .mapToDouble(CartItem::getPrice)
            .sum();

        for (Discount d : active) {
            boolean excludeSets = Boolean.TRUE.equals(d.getExcludeSets());
            boolean excludeGraded = Boolean.TRUE.equals(d.getExcludeGraded());
            if (excludeSets) anySetsExcluded = true;
            if (excludeGraded) anyGradedExcluded = true;

            // Each enabled exclude flag drops the matching items from BOTH the threshold count
            // and the discount-eligible pool for this rule.
            List<CartItem> countableItems = baseItems.stream()
                .filter(i -> !(excludeSets && i.getCollectionGroup() != null && i.getCollectionGroup() > 0))
                .filter(i -> !(excludeGraded && i.isGraded()))
                .collect(Collectors.toList());

            List<CartItem> discountableItems = countableItems;

            double subtotal = discountableItems.stream()
                .mapToDouble(CartItem::getPrice)
                .sum();
            int itemCount = countableItems.size();

            String excludeNote = buildExcludeNote(excludeSets, excludeGraded);

            switch (d.getType()) {
                case RAW_PERCENTAGE: {
                    double savings = subtotal * d.getPercentageOff() / 100.0;
                    if (savings > 0) {
                        totalSavings += savings;
                        String desc = String.format("%.0f%% off ($-%.2f)%s", d.getPercentageOff(), savings, excludeNote);
                        descriptions.add(desc);
                        breakdown.add(new CartDiscount(savings, desc, excludeSets, excludeGraded));
                    }
                    break;
                }
                case BUY_X_GET_ONE_FREE: {
                    // Picks were pre-computed with cross-rule dedupe; just look up this rule's items.
                    List<CartItem> picked = buyXFreePicks.getOrDefault(d.getId(), java.util.Collections.emptyList());
                    if (!picked.isEmpty()) {
                        double savings = picked.stream().mapToDouble(CartItem::getPrice).sum();
                        if (savings > 0) {
                            totalSavings += savings;
                            String desc = String.format("Buy %d get 1 free (%d free, $-%.2f)%s",
                                d.getXBooks(), picked.size(), savings, excludeNote);
                            descriptions.add(desc);
                            breakdown.add(new CartDiscount(savings, desc, excludeSets, excludeGraded));
                        }
                    }
                    break;
                }
                case PERCENT_OFF_OVER_X_BOOKS: {
                    if (itemCount > d.getXBooks()) {
                        double savings = subtotal * d.getPercentageOff() / 100.0;
                        if (savings > 0) {
                            totalSavings += savings;
                            String desc = String.format("%.0f%% off (over %d books, $-%.2f)%s", d.getPercentageOff(), d.getXBooks(), savings, excludeNote);
                            descriptions.add(desc);
                            breakdown.add(new CartDiscount(savings, desc, excludeSets, excludeGraded));
                        }
                    }
                    break;
                }
                case FREE_SHIPPING_OVER_X_BOOKS: {
                    if (itemCount >= d.getXBooks()) {
                        freeShipping = true;
                        String desc = String.format("Free shipping (%d books)", itemCount);
                        descriptions.add(desc);
                        breakdown.add(new CartDiscount(0.0, desc, excludeSets, excludeGraded));
                    }
                    break;
                }
            }
        }

        totalSavings = Math.min(totalSavings, baseSubtotal);
        String description = descriptions.isEmpty() ? null : String.join("; ", descriptions);
        return new DiscountResult(totalSavings, description, anySetsExcluded, anyGradedExcluded, freeShipping, breakdown);
    }

    /** Builds the human-readable parenthetical for which categories were excluded by a rule. */
    private static String buildExcludeNote(boolean sets, boolean graded) {
        if (!sets && !graded) return "";
        List<String> parts = new ArrayList<>();
        if (sets) parts.add("sets");
        if (graded) parts.add("graded");
        return " (" + String.join(", ", parts) + " excluded)";
    }

    /**
     * Determines which items each {@code BUY_X_GET_ONE_FREE} rule will mark as free, with
     * cross-rule deduping so the same physical book is never counted as free under more
     * than one rule.
     *
     * <p>Algorithm:</p>
     * <ol>
     *   <li>Filter {@code active} to BUY_X rules and sort by {@code xBooks} ascending
     *       (with discount id as a stable tiebreaker). Smaller-X rules trigger more often
     *       and on more carts, so they claim the cheapest books first; larger-X rules
     *       pick from what's left.</li>
     *   <li>For each rule in that order, count free books from its <em>full</em> eligible
     *       pool ({@code freeCount = pool.size() / (xBooks + 1)}) — this matches the
     *       customer's mental model: "you have N books, this rule frees floor(N/(X+1))".</li>
     *   <li>Pick that many items from the rule's eligible pool, sorted by price ascending,
     *       skipping any item already freed by a prior rule. Add the picks to the global
     *       already-freed set and record them in the result map keyed by discount id.</li>
     * </ol>
     *
     * <p>Without this step, two rules can both pick the same $0.50 book; the customer
     * sees only that book go to zero, and the second rule's promised savings vanish.</p>
     */
    private static Map<String, List<CartItem>> computeBuyXFreePicks(
            List<CartItem> baseItems, List<Discount> active) {
        List<Discount> buyXRules = active.stream()
            .filter(d -> d.getType() == DiscountType.BUY_X_GET_ONE_FREE)
            .sorted(Comparator
                .<Discount>comparingInt(d -> d.getXBooks())
                .thenComparing(Discount::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());

        Map<String, List<CartItem>> picks = new HashMap<>();
        Set<String> alreadyFreedIds = new HashSet<>();

        for (Discount d : buyXRules) {
            boolean excludeSets = Boolean.TRUE.equals(d.getExcludeSets());
            boolean excludeGraded = Boolean.TRUE.equals(d.getExcludeGraded());

            // The rule's eligible pool — same filters used in the main computeDiscounts loop.
            List<CartItem> ruleEligible = baseItems.stream()
                .filter(i -> !(excludeSets && i.getCollectionGroup() != null && i.getCollectionGroup() > 0))
                .filter(i -> !(excludeGraded && i.isGraded()))
                .collect(Collectors.toList());

            // freeCount uses the rule's full eligible pool, independent of other rules' picks.
            int freeCount = ruleEligible.size() / (d.getXBooks() + 1);
            if (freeCount == 0) continue;

            // Pick from items not already freed by a prior BUY_X rule.
            List<CartItem> pickPool = ruleEligible.stream()
                .filter(i -> !alreadyFreedIds.contains(i.getComicId()))
                .sorted(Comparator.comparingDouble(CartItem::getPrice))
                .collect(Collectors.toList());

            int actualPick = Math.min(freeCount, pickPool.size());
            if (actualPick == 0) continue;

            List<CartItem> picked = new ArrayList<>(pickPool.subList(0, actualPick));
            for (CartItem item : picked) alreadyFreedIds.add(item.getComicId());
            picks.put(d.getId(), picked);
        }

        return picks;
    }

    public static class DiscountResult {
        private final double amount;
        private final String description;
        private final boolean excludedSets;
        private final boolean excludedGraded;
        private final boolean freeShippingApplied;
        private final List<CartDiscount> breakdown;

        public DiscountResult(double amount, String description,
                              boolean excludedSets, boolean excludedGraded,
                              boolean freeShippingApplied,
                              List<CartDiscount> breakdown) {
            this.amount = amount;
            this.description = description;
            this.excludedSets = excludedSets;
            this.excludedGraded = excludedGraded;
            this.freeShippingApplied = freeShippingApplied;
            this.breakdown = breakdown;
        }

        public double getAmount() { return amount; }
        public String getDescription() { return description; }
        public boolean isExcludedSets() { return excludedSets; }
        public boolean isExcludedGraded() { return excludedGraded; }
        public boolean isFreeShippingApplied() { return freeShippingApplied; }
        public List<CartDiscount> getBreakdown() { return breakdown; }
    }
}
