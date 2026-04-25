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
import org.example.functions.model.ComicAuditLog;
import org.example.functions.util.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class AuditService {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;

    private final CosmosContainer auditLogsContainer;

    /** Thread-safe lazy singleton via the initialization-on-demand holder idiom. */
    private static class Holder {
        private static final AuditService INSTANCE = new AuditService();
    }

    public static AuditService getServiceInstance() {
        return Holder.INSTANCE;
    }

    public AuditService() {
        this.auditLogsContainer = CosmosDbClient.getInstance().getAuditLogsContainer();
    }

    public void writeAuditLog(ComicAuditLog entry) {
        try {
            ObjectNode node = OBJECT_MAPPER.valueToTree(entry);
            auditLogsContainer.createItem(node, new PartitionKey(entry.getComicId()), new CosmosItemRequestOptions());
            log.info("Audit log written for comic {} by {}", entry.getComicId(), entry.getEditedBy());
        } catch (Exception e) {
            log.warn("Failed to write audit log for comic {}: {}", entry.getComicId(), e.getMessage());
        }
    }

    public List<ComicAuditLog> getAuditLogsForComic(String comicId) {
        SqlQuerySpec query = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.comicId = @comicId ORDER BY c.editedAt DESC",
            List.of(new SqlParameter("@comicId", comicId)));
        List<ComicAuditLog> results = new ArrayList<>();
        for (ObjectNode node : auditLogsContainer.queryItems(query, new CosmosQueryRequestOptions(), ObjectNode.class)) {
            try {
                results.add(OBJECT_MAPPER.treeToValue(node, ComicAuditLog.class));
            } catch (Exception e) {
                log.error("Error parsing audit log entry", e);
            }
        }
        return results;
    }
}
