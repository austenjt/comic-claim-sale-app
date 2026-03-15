package org.example.functions;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.model.User;
import org.example.functions.service.EmailService;
import org.example.functions.service.SessionService;
import org.example.functions.service.UserService;

import java.util.List;
import java.util.Optional;

@Slf4j
public class UserTriggers {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
        .build();

    private static final String CORS_ORIGIN = "*";
    private static final String CORS_HEADERS = "X-Session-Token, Content-Type";

    // ─── POST /api/users/register ────────────────────────────────────────────

    @FunctionName("registerUser")
    public HttpResponseMessage registerUser(
        @HttpTrigger(
            name = "registerUser",
            route = "users/register",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing registerUser.");
        try {
            String body = request.getBody().orElse("{}");
            JsonNode json = OBJECT_MAPPER.readTree(body);
            String name = getString(json, "name");
            String email = getString(json, "email");
            String address = getString(json, "address");
            String phone = getString(json, "phone");
            String notes = getString(json, "notes");

            if (name == null || email == null) {
                return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST))
                    .body("name and email are required")
                    .build();
            }

            UserService userService = UserService.getServiceInstance();
            if (userService.findByEmail(email).isPresent()) {
                return cors(request.createResponseBuilder(HttpStatus.CONFLICT))
                    .body("Email already registered")
                    .build();
            }

            User user = userService.registerUser(name, email, address, phone, notes);

            // Notify all admins of the new account request
            List<String> adminEmails = userService.getAdminUsers().stream()
                .map(User::getEmail).collect(java.util.stream.Collectors.toList());
            String subject = "New Account Request: " + name;
            String emailBody = "A new user has requested an account.\n\n"
                + "Name:    " + name + "\n"
                + "Email:   " + email + "\n"
                + (address != null ? "Address: " + address + "\n" : "")
                + (phone   != null ? "Phone:   " + phone   + "\n" : "")
                + "\nLog in to approve or reject this request.";
            EmailService.getServiceInstance().send(adminEmails, email, subject, emailBody);

            ObjectNode resp = OBJECT_MAPPER.createObjectNode();
            resp.put("id", user.getId());
            resp.put("status", user.getStatus());
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(resp))
                .build();
        } catch (Exception e) {
            log.error("Error in registerUser", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body(e.getMessage())
                .build();
        }
    }

    // ─── POST /api/users/login ───────────────────────────────────────────────

    @FunctionName("loginUser")
    public HttpResponseMessage loginUser(
        @HttpTrigger(
            name = "loginUser",
            route = "users/login",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing loginUser.");
        try {
            String body = request.getBody().orElse("{}");
            JsonNode json = OBJECT_MAPPER.readTree(body);
            String email = getString(json, "email");
            String pin = getString(json, "pin");

            if (email == null || pin == null) {
                return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST))
                    .body("email and pin are required")
                    .build();
            }

            UserService userService = UserService.getServiceInstance();
            // Check for suspended account before PIN verification to give specific error
            java.util.Optional<User> optUser = userService.findByEmail(email);
            if (optUser.isPresent() && "SUSPENDED".equals(optUser.get().getStatus())) {
                return cors(request.createResponseBuilder(HttpStatus.FORBIDDEN))
                    .body("Account suspended")
                    .build();
            }
            if (!userService.verifyPin(email, pin)) {
                return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                    .body("Invalid credentials")
                    .build();
            }

            User user = userService.findByEmail(email).get();
            String token = SessionService.getServiceInstance().createSession(user.getId());

            ObjectNode resp = OBJECT_MAPPER.createObjectNode();
            resp.put("token", token);
            resp.set("user", safeUserNode(user));

            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(resp))
                .build();
        } catch (Exception e) {
            log.error("Error in loginUser", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body(e.getMessage())
                .build();
        }
    }

    // ─── POST /api/users/logout ──────────────────────────────────────────────

    @FunctionName("logoutUser")
    public HttpResponseMessage logoutUser(
        @HttpTrigger(
            name = "logoutUser",
            route = "users/logout",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing logoutUser.");
        String token = request.getHeaders().get("x-session-token");
        if (token != null && !token.isBlank()) {
            SessionService.getServiceInstance().deleteSession(token);
        }
        return cors(request.createResponseBuilder(HttpStatus.OK))
            .body("Logged out")
            .build();
    }

    // ─── GET /api/users/me ───────────────────────────────────────────────────

    @FunctionName("getCurrentUser")
    public HttpResponseMessage getCurrentUser(
        @HttpTrigger(
            name = "getCurrentUser",
            route = "users/me",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing getCurrentUser.");
        User user = requireSession(request);
        if (user == null) {
            return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Invalid or expired session")
                .build();
        }
        try {
            ObjectNode userNode = safeUserNode(user);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(userNode))
                .build();
        } catch (Exception e) {
            log.error("Error in getCurrentUser", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body(e.getMessage())
                .build();
        }
    }

    // ─── PUT /api/users/me ───────────────────────────────────────────────────

    @FunctionName("updateCurrentUser")
    public HttpResponseMessage updateCurrentUser(
        @HttpTrigger(
            name = "updateCurrentUser",
            route = "users/me",
            methods = {HttpMethod.PUT},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing updateCurrentUser.");
        User caller = requireSession(request);
        if (caller == null) {
            return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Invalid or expired session")
                .build();
        }
        try {
            String body = request.getBody().orElse("{}");
            JsonNode json = OBJECT_MAPPER.readTree(body);
            String name = getString(json, "name");
            String address = getString(json, "address");
            String phone = getString(json, "phone");
            String notes = getString(json, "notes");
            String preferences = getString(json, "preferences");
            User updated = UserService.getServiceInstance()
                .updateContactDetails(caller.getId(), name, address, phone, notes, preferences);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(safeUserNode(updated)))
                .build();
        } catch (Exception e) {
            log.error("Error in updateCurrentUser", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body(e.getMessage())
                .build();
        }
    }

    // ─── GET /api/users/pending ──────────────────────────────────────────────

    @FunctionName("getPendingUsers")
    public HttpResponseMessage getPendingUsers(
        @HttpTrigger(
            name = "getPendingUsers",
            route = "users/pending",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing getPendingUsers.");
        User caller = requireAdmin(request);
        if (caller == null) {
            return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Admin access required")
                .build();
        }
        try {
            List<User> pending = UserService.getServiceInstance().getPendingUsers();
            String json = OBJECT_MAPPER.writeValueAsString(
                pending.stream().map(this::safeUserNode).toList());
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(json)
                .build();
        } catch (Exception e) {
            log.error("Error in getPendingUsers", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body(e.getMessage())
                .build();
        }
    }

    // ─── POST /api/users/{id}/approve ────────────────────────────────────────

    @FunctionName("approveUser")
    public HttpResponseMessage approveUser(
        @HttpTrigger(
            name = "approveUser",
            route = "users/{id}/approve",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("id") String id)
    {
        log.info("Processing approveUser for id: {}", id);
        User caller = requireAdmin(request);
        if (caller == null) {
            return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Admin access required")
                .build();
        }
        try {
            String pin = UserService.getServiceInstance().approveUser(id);
            ObjectNode resp = OBJECT_MAPPER.createObjectNode();
            resp.put("pin", pin);
            resp.put("userId", id);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(resp))
                .build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND))
                .body(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Error in approveUser", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body(e.getMessage())
                .build();
        }
    }

    // ─── POST /api/users/{id}/reset-pin ──────────────────────────────────────

    @FunctionName("resetUserPin")
    public HttpResponseMessage resetUserPin(
        @HttpTrigger(
            name = "resetUserPin",
            route = "users/{id}/reset-pin",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("id") String id)
    {
        User caller = requireAdmin(request);
        if (caller == null) {
            return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Admin access required")
                .build();
        }
        try {
            String pin = UserService.getServiceInstance().resetPin(id);
            ObjectNode resp = OBJECT_MAPPER.createObjectNode();
            resp.put("pin", pin);
            resp.put("userId", id);
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(resp))
                .build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND))
                .body(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Error in resetUserPin", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body(e.getMessage())
                .build();
        }
    }

    // ─── POST /api/users/{id}/suspend ─────────────────────────────────────────

    @FunctionName("suspendUser")
    public HttpResponseMessage suspendUser(
        @HttpTrigger(
            name = "suspendUser",
            route = "users/{id}/suspend",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("id") String id)
    {
        User caller = requireAdmin(request);
        if (caller == null) {
            return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Admin access required")
                .build();
        }
        try {
            UserService.getServiceInstance().suspendUser(id);
            return cors(request.createResponseBuilder(HttpStatus.OK)).body("User suspended").build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Error in suspendUser", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)).body(e.getMessage()).build();
        }
    }

    // ─── POST /api/users/{id}/reactivate ─────────────────────────────────────

    @FunctionName("reactivateUser")
    public HttpResponseMessage reactivateUser(
        @HttpTrigger(
            name = "reactivateUser",
            route = "users/{id}/reactivate",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        @BindingName("id") String id)
    {
        User caller = requireAdmin(request);
        if (caller == null) {
            return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Admin access required")
                .build();
        }
        try {
            UserService.getServiceInstance().reactivateUser(id);
            return cors(request.createResponseBuilder(HttpStatus.OK)).body("User reactivated").build();
        } catch (IllegalArgumentException e) {
            return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND)).body(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Error in reactivateUser", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)).body(e.getMessage()).build();
        }
    }

    // ─── GET /api/users ──────────────────────────────────────────────────────

    @FunctionName("getApprovedUsers")
    public HttpResponseMessage getApprovedUsers(
        @HttpTrigger(
            name = "getApprovedUsers",
            route = "users",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing getApprovedUsers.");
        User caller = requireAdmin(request);
        if (caller == null) {
            return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Admin access required")
                .build();
        }
        try {
            List<User> approved = UserService.getServiceInstance().getApprovedUsers();
            String json = OBJECT_MAPPER.writeValueAsString(
                approved.stream().map(this::safeUserNode).toList());
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(json)
                .build();
        } catch (Exception e) {
            log.error("Error in getApprovedUsers", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body(e.getMessage())
                .build();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private User requireSession(HttpRequestMessage<?> request) {
        String token = request.getHeaders().get("x-session-token");
        String userId = SessionService.getServiceInstance().validateSession(token);
        if (userId == null) return null;
        return UserService.getServiceInstance().findById(userId).orElse(null);
    }

    private User requireAdmin(HttpRequestMessage<?> request) {
        User user = requireSession(request);
        if (user == null || !user.isAdmin()) return null;
        return user;
    }

    private ObjectNode safeUserNode(User user) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("id", user.getId());
        node.put("name", user.getName());
        node.put("email", user.getEmail());
        node.put("address", user.getAddress());
        node.put("phone", user.getPhone());
        node.put("notes", user.getNotes());
        node.put("preferences", user.getPreferences());
        node.put("status", user.getStatus());
        node.put("isAdmin", user.isAdmin());
        node.put("createdDate", user.getCreatedDate());
        node.put("approvedDate", user.getApprovedDate());
        return node;
    }

    private HttpResponseMessage.Builder cors(HttpResponseMessage.Builder builder) {
        return builder
            .header("Access-Control-Allow-Origin", CORS_ORIGIN)
            .header("Access-Control-Allow-Headers", CORS_HEADERS)
            .header("Access-Control-Allow-Methods", "*");
    }

    private String getString(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        String s = val.asText().trim();
        return s.isEmpty() ? null : s;
    }
}
