package org.example.functions.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.client.CosmosDbClient;
import org.example.functions.model.ActivityLog;
import org.example.functions.util.Mappers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class ActivityLogService {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;

    private final CosmosContainer container;

    /** Thread-safe lazy singleton via the initialization-on-demand holder idiom. */
    private static class Holder {
        private static final ActivityLogService INSTANCE = new ActivityLogService();
    }

    public static ActivityLogService getServiceInstance() {
        return Holder.INSTANCE;
    }

    public ActivityLogService() {
        this.container = CosmosDbClient.getInstance().getActivityLogsContainer();
    }

    public ActivityLog writeLog(String message, boolean isError) {
        ActivityLog entry = ActivityLog.builder()
            .id(UUID.randomUUID().toString())
            .message(message)
            .timestamp(Instant.now().toString())
            .isError(isError)
            .ttl(604800)
            .build();
        try {
            ObjectNode node = OBJECT_MAPPER.valueToTree(entry);
            container.createItem(node, new PartitionKey(entry.getId()), new CosmosItemRequestOptions());
        } catch (Exception e) {
            log.warn("Failed to write activity log: {}", e.getMessage());
        }
        return entry;
    }

    /** Returns the most recent {@code limit} log entries, newest first. */
    public List<ActivityLog> getRecentLogs(int limit) {
        String sql = "SELECT * FROM c ORDER BY c._ts DESC OFFSET 0 LIMIT " + limit;
        List<ActivityLog> results = new ArrayList<>();
        for (ObjectNode node : container.queryItems(sql, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                results.add(OBJECT_MAPPER.treeToValue(node, ActivityLog.class));
            } catch (Exception e) {
                log.error("Error parsing activity log entry", e);
            }
        }
        return results;
    }
}
