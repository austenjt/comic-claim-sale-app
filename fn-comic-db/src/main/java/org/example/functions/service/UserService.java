package org.example.functions.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.client.CosmosDbClient;
import org.example.functions.model.User;
import org.example.functions.util.EnvHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Slf4j
public class UserService {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
        .build();
    private final CosmosContainer usersContainer;
    private static UserService SERVICE_INSTANCE;

    public static UserService getServiceInstance() {
        if (Objects.isNull(SERVICE_INSTANCE)) {
            SERVICE_INSTANCE = new UserService();
        }
        return SERVICE_INSTANCE;
    }

    public UserService() {
        this.usersContainer = CosmosDbClient.getInstance().getUsersContainer();
    }

    public User registerUser(String name, String email, String address, String phone, String paymentNotes) {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setName(name);
        user.setEmail(email);
        user.setAddress(address);
        user.setPhone(phone);
        user.setPaymentNotes(paymentNotes);
        user.setStatus("PENDING");
        user.setCreatedDate(Instant.now().toString());

        String adminEmail = EnvHelper.getAdminEmail();
        if (adminEmail != null && adminEmail.equalsIgnoreCase(email)) {
            user.setAdmin(true);
            user.setStatus("APPROVED");
            user.setApprovedDate(Instant.now().toString());
        }

        ObjectNode node = OBJECT_MAPPER.valueToTree(user);
        usersContainer.createItem(node, new PartitionKey(user.getId()), new CosmosItemRequestOptions());
        log.info("Registered user: {}", email);
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
     * Approves user, generates a 7-digit PIN, stores hash+salt, returns plain PIN.
     * Plain PIN is returned once and never stored.
     */
    public String approveUser(String userId) {
        Optional<User> optUser = findById(userId);
        if (optUser.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        User user = optUser.get();

        String pin = generatePin();
        String salt = UUID.randomUUID().toString();
        String hash = hashPin(pin, salt);

        user.setStatus("APPROVED");
        user.setApprovedDate(Instant.now().toString());
        user.setPinSalt(salt);
        user.setPinHash(hash);

        ObjectNode node = OBJECT_MAPPER.valueToTree(user);
        usersContainer.replaceItem(node, userId, new PartitionKey(userId), new CosmosItemRequestOptions());
        log.info("Approved user: {}", userId);
        return pin;
    }

    public User updateContactDetails(String userId, String name, String address, String phone, String paymentNotes) {
        Optional<User> optUser = findById(userId);
        if (optUser.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        User user = optUser.get();
        if (name != null) user.setName(name);
        if (address != null) user.setAddress(address);
        if (phone != null) user.setPhone(phone);
        if (paymentNotes != null) user.setPaymentNotes(paymentNotes);
        ObjectNode node = OBJECT_MAPPER.valueToTree(user);
        usersContainer.replaceItem(node, userId, new PartitionKey(userId), new CosmosItemRequestOptions());
        log.info("Updated contact details for user: {}", userId);
        return user;
    }

    public boolean verifyPin(String email, String pin) {
        Optional<User> optUser = findByEmail(email);
        if (optUser.isEmpty()) return false;
        User user = optUser.get();
        if (!"APPROVED".equals(user.getStatus())) return false;
        String hash = hashPin(pin, user.getPinSalt());
        return hash.equals(user.getPinHash());
    }

    /** Generates a new PIN for the user, stores hash+salt, returns plain PIN. */
    public String resetPin(String userId) {
        User user = findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        String pin = generatePin();
        String salt = UUID.randomUUID().toString();
        String hash = hashPin(pin, salt);
        user.setPinSalt(salt);
        user.setPinHash(hash);
        ObjectNode node = OBJECT_MAPPER.valueToTree(user);
        usersContainer.replaceItem(node, userId, new PartitionKey(userId), new CosmosItemRequestOptions());
        log.info("Reset PIN for user: {}", userId);
        return pin;
    }

    public void suspendUser(String userId) {
        User user = findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setStatus("SUSPENDED");
        ObjectNode node = OBJECT_MAPPER.valueToTree(user);
        usersContainer.replaceItem(node, userId, new PartitionKey(userId), new CosmosItemRequestOptions());
        log.info("Suspended user: {}", userId);
    }

    public void reactivateUser(String userId) {
        User user = findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setStatus("APPROVED");
        ObjectNode node = OBJECT_MAPPER.valueToTree(user);
        usersContainer.replaceItem(node, userId, new PartitionKey(userId), new CosmosItemRequestOptions());
        log.info("Reactivated user: {}", userId);
    }

    private String generatePin() {
        Random rand = new Random();
        int pin = 1000000 + rand.nextInt(9000000); // 7 digits: 1000000–9999999
        return String.valueOf(pin);
    }

    private String hashPin(String pin, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((pin + salt).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
