package org.example.functions.client;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.example.functions.util.EnvHelper;

/**
 * This class is a Cosmos DB client class.
 *
 *   It is designed to attach to a Serverless instance of Cosmos DB, and so it should expect a slight delay before connection sometimes.
 */
public class CosmosDbClient {

    private static final String DATABASE_ID = "comic-db";
    private static final String COMICS_CONTAINER = "comics";
    private static final String IMAGES_CONTAINER = "images";
    private static final String USERS_CONTAINER = "users";
    private static final String SESSIONS_CONTAINER = "sessions";
    private static final String CARTS_CONTAINER = "carts";
    private static final String DISCOUNTS_CONTAINER = "discounts";
    private static final String ARCHIVED_ORDERS_CONTAINER = "archived-orders";
    private static final String RETURN_EVENTS_CONTAINER = "return-events";
    private static final String AUDIT_LOGS_CONTAINER = "audit-logs";

    private static CosmosDbClient INSTANCE;

    private final CosmosContainer comicsContainer;
    private final CosmosContainer imagesContainer;
    private final CosmosContainer usersContainer;
    private final CosmosContainer sessionsContainer;
    private final CosmosContainer cartsContainer;
    private final CosmosContainer discountsContainer;
    private final CosmosContainer archivedOrdersContainer;
    private final CosmosContainer returnEventsContainer;
    private final CosmosContainer auditLogsContainer;

    private CosmosDbClient() {
        CosmosClient client = new CosmosClientBuilder()
            .endpoint(EnvHelper.getCosmosEndpoint())
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
        CosmosDatabase database = client.getDatabase(DATABASE_ID);
        this.comicsContainer = database.getContainer(COMICS_CONTAINER);
        this.imagesContainer = database.getContainer(IMAGES_CONTAINER);
        this.usersContainer = database.getContainer(USERS_CONTAINER);
        this.sessionsContainer = database.getContainer(SESSIONS_CONTAINER);
        this.cartsContainer = database.getContainer(CARTS_CONTAINER);
        this.discountsContainer = database.getContainer(DISCOUNTS_CONTAINER);
        this.archivedOrdersContainer = database.getContainer(ARCHIVED_ORDERS_CONTAINER);
        this.returnEventsContainer = database.getContainer(RETURN_EVENTS_CONTAINER);
        this.auditLogsContainer = database.getContainer(AUDIT_LOGS_CONTAINER);
    }

    public static CosmosDbClient getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CosmosDbClient();
        }
        return INSTANCE;
    }

    public CosmosContainer getComicsContainer() { return comicsContainer; }
    public CosmosContainer getImagesContainer() { return imagesContainer; }
    public CosmosContainer getUsersContainer() { return usersContainer; }
    public CosmosContainer getSessionsContainer() { return sessionsContainer; }
    public CosmosContainer getCartsContainer() { return cartsContainer; }
    public CosmosContainer getDiscountsContainer() { return discountsContainer; }
    public CosmosContainer getArchivedOrdersContainer() { return archivedOrdersContainer; }
    public CosmosContainer getReturnEventsContainer() { return returnEventsContainer; }
    public CosmosContainer getAuditLogsContainer() { return auditLogsContainer; }
}
