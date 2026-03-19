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
import org.example.functions.model.User;
import org.example.functions.util.EnvHelper;
import org.example.functions.util.Mappers;

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

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;
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

    public User registerUser(String name, String email, String address, String phone, String notes) {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setName(name);
        user.setEmail(email);
        user.setAddress(address);
        user.setPhone(phone);
        user.setNotes(notes);
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

        if (EnvHelper.isEmailEnabled()) {
            String siteUrl = EnvHelper.getSiteUrl();
            String adminEmail = EnvHelper.getAdminEmail();
            String subject = "Your account has been approved!";
            String body = "Hi " + user.getName() + ",\n\n"
                + "Your account request has been approved. You can now log in using the details below.\n\n"
                + "Email: " + user.getEmail() + "\n"
                + "PIN:   " + pin + "\n\n"
                + "Log in at: " + siteUrl + "\n\n"
                + "Keep your PIN private. If you ever need it reset, contact the seller.\n\n"
                + "Happy collecting!";
            try {
                EmailService.getServiceInstance().send(List.of(user.getEmail()), adminEmail, adminEmail, subject, body);
            } catch (Exception e) {
                log.warn("Failed to send approval email to {}: {}", user.getEmail(), e.getMessage());
            }
        }

        return pin;
    }

    public User updateContactDetails(String userId, String name, String address, String phone, String notes, String preferences) {
        Optional<User> optUser = findById(userId);
        if (optUser.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        User user = optUser.get();
        if (name != null) user.setName(name);
        if (address != null) user.setAddress(address);
        if (phone != null) user.setPhone(phone);
        if (notes != null) user.setNotes(notes);
        if (preferences != null) user.setPreferences(preferences);
        ObjectNode node = OBJECT_MAPPER.valueToTree(user);
        usersContainer.replaceItem(node, userId, new PartitionKey(userId), new CosmosItemRequestOptions());
        log.info("Updated contact details for user: {}", userId);
        return user;
    }

    private static final int MAX_FAILED_ATTEMPTS = 10;
    private static final int LOCKOUT_MINUTES = 15;

    public boolean verifyPin(String email, String pin) {
        Optional<User> optUser = findByEmail(email);
        if (optUser.isEmpty()) return false;
        User user = optUser.get();
        if (!"APPROVED".equals(user.getStatus())) return false;

        // Check lockout
        if (user.getLockedUntil() != null) {
            java.time.Instant lockExpiry = java.time.Instant.parse(user.getLockedUntil());
            if (java.time.Instant.now().isBefore(lockExpiry)) {
                throw new IllegalStateException("Account temporarily locked due to too many failed attempts. "
                    + "Try again after " + java.time.ZonedDateTime.ofInstant(lockExpiry, java.time.ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm 'UTC'")) + ".");
            }
            // Lockout has expired — clear it
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }

        String hash = hashPin(pin, user.getPinSalt());
        if (hash.equals(user.getPinHash())) {
            // Successful login — reset counters
            if (user.getFailedLoginAttempts() > 0) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                saveUser(user);
            }
            return true;
        }

        // Failed login — increment counter
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(java.time.Instant.now()
                .plus(LOCKOUT_MINUTES, java.time.temporal.ChronoUnit.MINUTES).toString());
            log.warn("User {} locked out after {} failed login attempts", email, attempts);
        }
        saveUser(user);
        return false;
    }

    private void saveUser(User user) {
        ObjectNode node = OBJECT_MAPPER.valueToTree(user);
        usersContainer.replaceItem(node, user.getId(), new PartitionKey(user.getId()), new CosmosItemRequestOptions());
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

        if (EnvHelper.isEmailEnabled()) {
            String siteUrl = EnvHelper.getSiteUrl();
            String adminEmail = EnvHelper.getAdminEmail();
            String subject = "Your PIN has been reset";
            String body = "Hi " + user.getName() + ",\n\n"
                + "Your PIN has been reset. Use the details below to log in.\n\n"
                + "Email: " + user.getEmail() + "\n"
                + "PIN:   " + pin + "\n\n"
                + "Log in at: " + siteUrl + "\n\n"
                + "If you did not request a PIN reset, please contact the seller immediately.";
            try {
                EmailService.getServiceInstance().send(List.of(user.getEmail()), adminEmail, adminEmail, subject, body);
            } catch (Exception e) {
                log.warn("Failed to send PIN reset email to {}: {}", user.getEmail(), e.getMessage());
            }
        }

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
