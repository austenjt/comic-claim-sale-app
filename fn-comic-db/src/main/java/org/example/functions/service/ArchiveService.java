package org.example.functions.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.client.CosmosDbClient;
import org.example.functions.model.ArchivedOrder;
import org.example.functions.util.Mappers;
import org.example.functions.model.ArchivedOrderItem;
import org.example.functions.model.Cart;
import org.example.functions.model.enums.PaymentStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class ArchiveService {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;
    private final CosmosContainer archivedOrdersContainer;
    private static ArchiveService SERVICE_INSTANCE;

    public static ArchiveService getServiceInstance() {
        if (Objects.isNull(SERVICE_INSTANCE)) {
            SERVICE_INSTANCE = new ArchiveService();
        }
        return SERVICE_INSTANCE;
    }

    public ArchiveService() {
        this.archivedOrdersContainer = CosmosDbClient.getInstance().getArchivedOrdersContainer();
    }

    /** Archive a fulfilled cart as an ArchivedOrder (idempotent — skips if already archived). */
    public void archiveCart(Cart cart) {
        try {
            archivedOrdersContainer.readItem(cart.getId(), new PartitionKey(cart.getId()), ObjectNode.class);
            log.info("Cart {} already archived, skipping.", cart.getId());
            return;
        } catch (Exception ignored) {
            // not found — proceed to archive
        }

        ArchivedOrder order = toArchivedOrder(cart);
        ObjectNode node = OBJECT_MAPPER.valueToTree(order);
        archivedOrdersContainer.createItem(node, new PartitionKey(order.getId()), new CosmosItemRequestOptions());
        log.info("Archived order for cart {}, user {}", cart.getId(), cart.getUserId());
    }

    /** Returns all archived orders for a single user, newest first. */
    public List<ArchivedOrder> getArchivedOrdersForUser(String userId) {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.userId = @userId ORDER BY c.fulfilledAt DESC",
            List.of(new SqlParameter("@userId", userId)));
        return queryOrders(query);
    }

    /** Returns all archived orders across all users, newest first. */
    public List<ArchivedOrder> getAllArchivedOrders() {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c ORDER BY c.fulfilledAt DESC");
        return queryOrders(query);
    }

    /** Admin: update internal notes on an archived order. */
    public ArchivedOrder updateAdminNotes(String orderId, String adminNotes) {
        ArchivedOrder order = getArchivedOrderById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Archived order not found: " + orderId));
        order.setAdminNotes(adminNotes != null && !adminNotes.isBlank() ? adminNotes.trim() : null);
        ObjectNode node = OBJECT_MAPPER.valueToTree(order);
        archivedOrdersContainer.replaceItem(node, orderId, new PartitionKey(orderId), new CosmosItemRequestOptions());
        log.info("Admin notes updated for archived order {}", orderId);
        return order;
    }

    /** Admin: update the payment status on an archived order. Valid values: UNPAID, PARTIAL, PAID. */
    public ArchivedOrder updatePaymentStatus(String orderId, PaymentStatus status) {
        ArchivedOrder order = getArchivedOrderById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Archived order not found: " + orderId));
        order.setPaymentStatus(status);
        ObjectNode node = OBJECT_MAPPER.valueToTree(order);
        archivedOrdersContainer.replaceItem(node, orderId, new PartitionKey(orderId), new CosmosItemRequestOptions());
        log.info("Payment status for archived order {} set to {}", orderId, status);
        if (status == PaymentStatus.PAID) {
            sendArchivedPaymentReceivedEmail(order);
        }
        return order;
    }

    private void sendArchivedPaymentReceivedEmail(ArchivedOrder order) {
        if (order.getUserEmail() == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            double subtotal = 0;
            for (ArchivedOrderItem item : order.getItems()) {
                if (item.getPrice() == 0) continue;
                String num = item.getComicNumber() != null ? " " + item.getComicNumber() : "";
                sb.append(String.format("  %s%s — $%.2f%n", item.getComicTitle(), num, item.getPrice()));
                subtotal += item.getPrice();
            }
            sb.append(String.format("%nSubtotal: $%.2f%n", subtotal));
            if (order.getShippingCost() > 0) {
                sb.append(String.format("Shipping: $%.2f%n", order.getShippingCost()));
            }
            if (order.getDiscountAmount() > 0) {
                String desc = order.getDiscountDescription() != null ? " (" + order.getDiscountDescription() + ")" : "";
                sb.append(String.format("Discount%s: -$%.2f%n", desc, order.getDiscountAmount()));
            }
            double total = subtotal + order.getShippingCost() - order.getDiscountAmount();
            sb.append(String.format("Total: $%.2f%n", total));

            String body = "Hi " + order.getUserName() + ",\n\n"
                + "Great news! We've received your payment for your recent order.\n\n"
                + "ORDER RECEIPT\n"
                + "=============\n"
                + sb + "\n"
                + "Thank you for your payment!\n\n"
                + "Lightning Comics\n";
            EmailService.getServiceInstance().send(
                java.util.List.of(order.getUserEmail()), null, null,
                "Payment Received", body);
        } catch (Exception e) {
            log.warn("Failed to send payment received email to {}: {}", order.getUserEmail(), e.getMessage());
        }
    }

    /** Admin: update shipped status and tracking number on an archived order. */
    public ArchivedOrder updateShipping(String orderId, boolean shipped, String trackingNumber) {
        ArchivedOrder order = getArchivedOrderById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Archived order not found: " + orderId));
        order.setShipped(shipped);
        order.setTrackingNumber(trackingNumber);
        ObjectNode node = OBJECT_MAPPER.valueToTree(order);
        archivedOrdersContainer.replaceItem(node, orderId, new PartitionKey(orderId), new CosmosItemRequestOptions());
        log.info("Shipping updated for archived order {}: shipped={}, tracking={}", orderId, shipped, trackingNumber);
        return order;
    }

    /** Permanently deletes an archived order by ID. */
    public void deleteArchivedOrder(String orderId) {
        archivedOrdersContainer.deleteItem(orderId, new PartitionKey(orderId), new CosmosItemRequestOptions());
        log.info("Deleted archived order {}", orderId);
    }

    /** Returns a single archived order by ID, or empty if not found. */
    public java.util.Optional<ArchivedOrder> getArchivedOrderById(String orderId) {
        try {
            ObjectNode node = archivedOrdersContainer.readItem(orderId, new PartitionKey(orderId), ObjectNode.class).getItem();
            return java.util.Optional.ofNullable(OBJECT_MAPPER.treeToValue(node, ArchivedOrder.class));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    /** Returns the highest collectionGroup value seen across all archived orders, or 0 if none. */
    public int getMaxCollectionGroup() {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT item.collectionGroup FROM c JOIN item IN c.items " +
            "WHERE IS_DEFINED(item.collectionGroup) AND item.collectionGroup != null AND item.collectionGroup > 0");
        int max = 0;
        for (ObjectNode node : archivedOrdersContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                int val = node.get("collectionGroup").asInt(0);
                if (val > max) max = val;
            } catch (Exception ignored) {}
        }
        return max;
    }

    /** Returns true if any archived order contains an item with the given collectionGroup. */
    public boolean hasArchivedOrderForGroup(int collectionGroup) {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT c.id FROM c JOIN item IN c.items WHERE item.collectionGroup = @group",
            List.of(new SqlParameter("@group", collectionGroup)));
        return archivedOrdersContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)
            .iterator().hasNext();
    }

    /** Migrate any fulfilled carts that are not yet in archived-orders (one-time safe call). */
    public void migrateAll(List<Cart> fulfilledCarts) {
        for (Cart cart : fulfilledCarts) {
            try {
                archiveCart(cart);
            } catch (Exception e) {
                log.error("Failed to migrate cart {} to archived-orders: {}", cart.getId(), e.getMessage());
            }
        }
        log.info("Migration complete: processed {} fulfilled carts.", fulfilledCarts.size());
    }

    private List<ArchivedOrder> queryOrders(SqlQuerySpec query) {
        List<ArchivedOrder> result = new ArrayList<>();
        for (ObjectNode node : archivedOrdersContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                result.add(OBJECT_MAPPER.treeToValue(node, ArchivedOrder.class));
            } catch (Exception e) {
                log.error("Error parsing archived order", e);
            }
        }
        return result;
    }

    private ArchivedOrder toArchivedOrder(Cart cart) {
        List<ArchivedOrderItem> items = cart.getItems().stream()
            .map(i -> ArchivedOrderItem.builder()
                .comicId(i.getComicId())
                .comicTitle(i.getComicTitle())
                .comicNumber(i.getComicNumber())
                .price(i.getPrice())
                .claimedAt(i.getClaimedAt())
                .collectionGroup(i.getCollectionGroup())
                .build())
            .collect(Collectors.toList());

        return ArchivedOrder.builder()
            .id(cart.getId())
            .userId(cart.getUserId())
            .userName(cart.getUserName())
            .userEmail(cart.getUserEmail())
            .items(items)
            .discountAmount(cart.getDiscountAmount())
            .discountDescription(cart.getDiscountDescription())
            .shippingCost(cart.getShippingCost())
            .createdAt(cart.getCreatedAt())
            .fulfilledAt(cart.getFulfilledAt())
            .paymentStatus(cart.getPaymentStatus())
            .shipped(cart.getShipped())
            .trackingNumber(cart.getTrackingNumber())
            .customerNotes(cart.getCustomerNotes())
            .adminNotes(cart.getAdminNotes())
            .build();
    }
}
