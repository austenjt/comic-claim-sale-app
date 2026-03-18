package org.example.functions.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.client.CosmosDbClient;
import org.example.functions.model.ArchivedOrder;
import org.example.functions.model.ArchivedOrderItem;
import org.example.functions.model.Cart;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class ArchiveService {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
        .build();
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
                .comicTitle(i.getComicTitle())
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
            .createdAt(cart.getCreatedAt())
            .fulfilledAt(cart.getFulfilledAt())
            .build();
    }
}
