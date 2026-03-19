package org.example.functions.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.client.CosmosDbClient;
import org.example.functions.model.Session;
import org.example.functions.util.Mappers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class SessionService {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;
    private final CosmosContainer sessionsContainer;
    private static SessionService SERVICE_INSTANCE;

    public static SessionService getServiceInstance() {
        if (Objects.isNull(SERVICE_INSTANCE)) {
            SERVICE_INSTANCE = new SessionService();
        }
        return SERVICE_INSTANCE;
    }

    public SessionService() {
        this.sessionsContainer = CosmosDbClient.getInstance().getSessionsContainer();
    }

    public String createSession(String userId) {
        Session session = new Session();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS).toString());

        ObjectNode node = OBJECT_MAPPER.valueToTree(session);
        sessionsContainer.createItem(node, new PartitionKey(session.getId()), new CosmosItemRequestOptions());
        log.info("Created session for userId: {}", userId);
        return session.getId();
    }

    /**
     * Validates a session token. Returns userId if valid, null if invalid or expired.
     */
    public String validateSession(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            ObjectNode node = sessionsContainer.readItem(token, new PartitionKey(token), ObjectNode.class).getItem();
            Session session = OBJECT_MAPPER.treeToValue(node, Session.class);
            Instant expiry = Instant.parse(session.getExpiresAt());
            if (Instant.now().isAfter(expiry)) {
                log.info("Session {} is expired.", token);
                return null;
            }
            return session.getUserId();
        } catch (Exception e) {
            log.warn("Session not found or invalid: {}", token);
            return null;
        }
    }

    public void deleteSession(String token) {
        try {
            sessionsContainer.deleteItem(token, new PartitionKey(token), new CosmosItemRequestOptions());
            log.info("Deleted session: {}", token);
        } catch (Exception e) {
            log.warn("Failed to delete session {}: {}", token, e.getMessage());
        }
    }

    /** Deletes all sessions except the one with the given token. Used during database reset. */
    public void deleteAllExcept(String keepToken) {
        com.azure.cosmos.models.SqlQuerySpec query = new com.azure.cosmos.models.SqlQuerySpec("SELECT c.id FROM c");
        for (com.fasterxml.jackson.databind.node.ObjectNode node :
                sessionsContainer.queryItems(query, new com.azure.cosmos.models.CosmosQueryRequestOptions(),
                    com.fasterxml.jackson.databind.node.ObjectNode.class)) {
            String id = node.get("id").asText();
            if (!id.equals(keepToken)) {
                try {
                    sessionsContainer.deleteItem(id, new PartitionKey(id), new CosmosItemRequestOptions());
                } catch (Exception e) {
                    log.warn("Failed to delete session {}: {}", id, e.getMessage());
                }
            }
        }
        log.info("All sessions deleted except {}.", keepToken);
    }
}
