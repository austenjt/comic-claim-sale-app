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
import org.example.functions.util.Mappers;
import org.example.functions.model.CartItem;
import org.example.functions.model.Discount;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class DiscountService {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;
    private final CosmosContainer discountsContainer;
    private static DiscountService SERVICE_INSTANCE;

    public static DiscountService getServiceInstance() {
        if (Objects.isNull(SERVICE_INSTANCE)) {
            SERVICE_INSTANCE = new DiscountService();
        }
        return SERVICE_INSTANCE;
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
        List<Discount> active = getActiveDiscounts();
        if (active.isEmpty()) {
            return new DiscountResult(0.0, null, false);
        }

        // Bid-won items count toward tier thresholds but never receive a discount themselves.
        // Awarded (isAwarded flag) and free ($0.00) items are excluded from both counting and discount application.
        List<CartItem> baseItems = cart.getItems().stream()
            .filter(i -> !i.isAwarded() && i.getPrice() > 0)
            .collect(Collectors.toList());

        double totalSavings = 0.0;
        boolean anySetsExcluded = false;
        List<String> descriptions = new ArrayList<>();
        double baseSubtotal = baseItems.stream()
            .filter(i -> !i.isWonViaBid())
            .mapToDouble(CartItem::getPrice)
            .sum();

        for (Discount d : active) {
            if (Boolean.TRUE.equals(d.getExcludeSets())) {
                anySetsExcluded = true;
            }
            // When excludeSets is true, set member items (collectionGroup > 0) are excluded from
            // both the threshold count and discount eligibility for this rule.
            List<CartItem> countableItems = baseItems.stream()
                .filter(i -> !(Boolean.TRUE.equals(d.getExcludeSets()) && i.getCollectionGroup() != null && i.getCollectionGroup() > 0))
                .collect(Collectors.toList());

            List<CartItem> discountableItems = countableItems.stream()
                .filter(i -> !i.isWonViaBid())
                .collect(Collectors.toList());

            double subtotal = discountableItems.stream()
                .mapToDouble(CartItem::getPrice)
                .sum();
            int itemCount = countableItems.size();

            String excludeNote = Boolean.TRUE.equals(d.getExcludeSets()) ? " (sets excluded)" : "";

            switch (d.getType()) {
                case "RAW_PERCENTAGE": {
                    double savings = subtotal * d.getPercentageOff() / 100.0;
                    totalSavings += savings;
                    descriptions.add(String.format("%.0f%% off ($-%.2f)%s", d.getPercentageOff(), savings, excludeNote));
                    break;
                }
                case "BUY_X_GET_ONE_FREE": {
                    int freeCount = itemCount / (d.getXBooks() + 1);
                    if (freeCount > 0) {
                        List<Double> prices = discountableItems.stream()
                            .map(CartItem::getPrice)
                            .sorted(Comparator.naturalOrder())
                            .collect(Collectors.toList());
                        double savings = prices.subList(0, Math.min(freeCount, prices.size()))
                            .stream().mapToDouble(Double::doubleValue).sum();
                        totalSavings += savings;
                        descriptions.add(String.format("Buy %d get 1 free (%d free, $-%.2f)%s", d.getXBooks(), freeCount, savings, excludeNote));
                    }
                    break;
                }
                case "PERCENTAGE_PER_X_BOOKS": {
                    int groups = itemCount / d.getXBooks();
                    double effectivePct = Math.min(groups * d.getPercentageOff(), 100.0);
                    if (effectivePct > 0) {
                        double savings = subtotal * effectivePct / 100.0;
                        totalSavings += savings;
                        descriptions.add(String.format("%.0f%% off per %d books (%.0f%% total, $-%.2f)%s", d.getPercentageOff(), d.getXBooks(), effectivePct, savings, excludeNote));
                    }
                    break;
                }
                default:
                    log.warn("Unknown discount type: {}", d.getType());
            }
        }

        totalSavings = Math.min(totalSavings, baseSubtotal);
        String description = descriptions.isEmpty() ? null : String.join("; ", descriptions);
        return new DiscountResult(totalSavings, description, anySetsExcluded);
    }

    public static class DiscountResult {
        private final double amount;
        private final String description;
        private final boolean excludedSets;

        public DiscountResult(double amount, String description, boolean excludedSets) {
            this.amount = amount;
            this.description = description;
            this.excludedSets = excludedSets;
        }

        public double getAmount() { return amount; }
        public String getDescription() { return description; }
        public boolean isExcludedSets() { return excludedSets; }
    }
}
