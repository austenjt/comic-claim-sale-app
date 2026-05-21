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
import org.example.functions.model.ShippingAddress;
import org.example.functions.model.User;
import org.example.functions.model.UserIdentity;
import org.example.functions.model.UserStatus;
import org.example.functions.util.EnvHelper;
import org.example.functions.util.Mappers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class UserService {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;
    private final CosmosContainer usersContainer;
    /** Thread-safe lazy singleton via the initialization-on-demand holder idiom. */
    private static class Holder {
        private static final UserService INSTANCE = new UserService();
    }

    public static UserService getServiceInstance() {
        return Holder.INSTANCE;
    }

    public UserService() {
        this.usersContainer = CosmosDbClient.getInstance().getUsersContainer();
    }

    /**
     * Called on first login via Entra — creates an APPROVED user record from the JWT identity.
     * If the email matches the configured admin email, sets isAdmin=true.
     */
    public User createFromIdentity(UserIdentity identity) {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setName(identity.getDisplayName() != null ? identity.getDisplayName() : identity.getEmail());
        user.setEmail(identity.getEmail());
        user.setEntraOid(identity.getOid());
        user.setStatus(UserStatus.APPROVED);
        user.setCreatedDate(Instant.now().toString());
        user.setApprovedDate(Instant.now().toString());

        String adminEmail = EnvHelper.getAdminEmail();
        if (adminEmail != null && adminEmail.equalsIgnoreCase(identity.getEmail())) {
            user.setAdmin(true);
        }

        ObjectNode node = OBJECT_MAPPER.valueToTree(user);
        usersContainer.createItem(node, new PartitionKey(user.getId()), new CosmosItemRequestOptions());
        log.info("Created user from Entra identity: {}", identity.getEmail());
        return user;
    }

    public Optional<User> findByEmail(String email) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
            "SELECT * FROM c WHERE c.email = @email",
            List.of(new SqlParameter("@email", email)));
        CosmosPagedIterable<ObjectNode> items = usersContainer.queryItems(
            querySpec, new CosmosQueryRequestOptions(), ObjectNode.class);
        for (ObjectNode node : items) {
            try {
                return Optional.ofNullable(OBJECT_MAPPER.treeToValue(node, User.class));
            } catch (Exception e) {
                log.error("Error parsing user from Cosmos", e);
            }
        }
        return Optional.empty();
    }

    public Optional<User> findById(String id) {
        try {
            ObjectNode node = usersContainer.readItem(id, new PartitionKey(id), ObjectNode.class).getItem();
            return Optional.ofNullable(OBJECT_MAPPER.treeToValue(node, User.class));
        } catch (Exception e) {
            log.warn("findById({}) not found: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    public List<User> getPendingUsers() {
        SqlQuerySpec querySpec = new SqlQuerySpec("SELECT * FROM c WHERE c.status = 'PENDING'");
        CosmosPagedIterable<ObjectNode> items = usersContainer.queryItems(
            querySpec, new CosmosQueryRequestOptions(), ObjectNode.class);
        List<User> result = new ArrayList<>();
        for (ObjectNode node : items) {
            try {
                result.add(OBJECT_MAPPER.treeToValue(node, User.class));
            } catch (Exception e) {
                log.error("Error parsing pending user", e);
            }
        }
        return result;
    }

    public List<User> getApprovedUsers() {
        SqlQuerySpec querySpec = new SqlQuerySpec("SELECT * FROM c WHERE c.status = 'APPROVED' OR c.status = 'SUSPENDED'");
        CosmosPagedIterable<ObjectNode> items = usersContainer.queryItems(
            querySpec, new CosmosQueryRequestOptions(), ObjectNode.class);
        List<User> result = new ArrayList<>();
        for (ObjectNode node : items) {
            try {
                result.add(OBJECT_MAPPER.treeToValue(node, User.class));
            } catch (Exception e) {
                log.error("Error parsing approved user", e);
            }
        }
        return result;
    }

    /**
     * Approves a pending user. Sends an approval email notifying them to log in via Entra.
     */
    public void approveUser(String userId) {
        Optional<User> optUser = findById(userId);
        if (optUser.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        User user = optUser.get();
        user.setStatus(UserStatus.APPROVED);
        user.setApprovedDate(Instant.now().toString());

        saveUser(user);
        log.info("Approved user: {}", userId);

        if (EnvHelper.isEmailEnabled()) {
            String siteUrl    = EnvHelper.getSiteUrl();
            String adminEmail = EnvHelper.getAdminEmail();
            String subject    = "Your account has been approved!";
            String body       = "Hi " + user.getName() + ",\n\n"
                + "Your account request has been approved. You can now log in at:\n\n"
                + siteUrl + "\n\n"
                + "Sign in using the LightningComics.rocks account associated with " + user.getEmail() + ".\n\n"
                + "Happy collecting!";
            try {
                EmailService.getServiceInstance().send(List.of(user.getEmail()), adminEmail, adminEmail, subject, body);
            } catch (Exception e) {
                log.warn("Failed to send approval email to {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    public User updateContactDetails(String userId, String name, ShippingAddress shippingAddress, String phone,
                                      String notes, String preferences, String venmoHandle, String paypalHandle,
                                      String ebayUsername, String cashAppHandle) {
        Optional<User> optUser = findById(userId);
        if (optUser.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        User user = optUser.get();
        if (name != null)            user.setName(name);
        if (shippingAddress != null) user.setShippingAddress(shippingAddress);
        if (phone != null)           user.setPhone(phone);
        if (notes != null)         user.setNotes(notes);
        if (preferences != null)   user.setPreferences(preferences);
        if (venmoHandle != null)   user.setVenmoHandle(venmoHandle);
        if (paypalHandle != null)  user.setPaypalHandle(paypalHandle);
        if (ebayUsername != null)  user.setEbayUsername(ebayUsername);
        if (cashAppHandle != null) user.setCashAppHandle(cashAppHandle);
        saveUser(user);
        log.info("Updated contact details for user: {}", userId);
        return user;
    }

    public void setEntraOid(String userId, String oid) {
        Optional<User> optUser = findById(userId);
        optUser.ifPresent(user -> {
            user.setEntraOid(oid);
            saveUser(user);
            log.info("Set entraOid for user: {}", userId);
        });
    }

    public void suspendUser(String userId) {
        User user = findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setStatus(UserStatus.SUSPENDED);
        saveUser(user);
        log.info("Suspended user: {}", userId);
    }

    public void reactivateUser(String userId) {
        User user = findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setStatus(UserStatus.APPROVED);
        saveUser(user);
        log.info("Reactivated user: {}", userId);
    }

    private void saveUser(User user) {
        ObjectNode node = OBJECT_MAPPER.valueToTree(user);
        usersContainer.replaceItem(node, user.getId(), new PartitionKey(user.getId()), new CosmosItemRequestOptions());
    }
}
