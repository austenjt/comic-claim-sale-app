package org.example.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.example.functions.service.UserService;
import org.example.functions.util.AuthHelper;
import org.example.functions.util.HttpHelper;
import org.example.functions.util.Mappers;

import java.util.List;
import java.util.Optional;

@Slf4j
public class UserTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;


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
        User user = AuthHelper.requireSession(request);
        if (user == null) {
            return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Invalid or expired token")
                .build();
        }
        try {
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(safeUserNode(user)))
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
        User caller = AuthHelper.requireSession(request);
        if (caller == null) {
            return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Invalid or expired token")
                .build();
        }
        try {
            String body = request.getBody().orElse("{}");
            JsonNode json = OBJECT_MAPPER.readTree(body);
            String name          = HttpHelper.getString(json, "name");
            String address       = HttpHelper.getString(json, "address");
            String phone         = HttpHelper.getString(json, "phone");
            String notes         = HttpHelper.getString(json, "notes");
            String preferences   = HttpHelper.getString(json, "preferences");
            String venmoHandle   = HttpHelper.getString(json, "venmoHandle");
            String paypalHandle  = HttpHelper.getString(json, "paypalHandle");
            String ebayUsername  = HttpHelper.getString(json, "ebayUsername");
            String cashAppHandle = HttpHelper.getString(json, "cashAppHandle");
            User updated = UserService.getServiceInstance()
                .updateContactDetails(caller.getId(), name, address, phone, notes, preferences,
                    venmoHandle, paypalHandle, ebayUsername, cashAppHandle);
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
        User caller = AuthHelper.requireAdmin(request);
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
        User caller = AuthHelper.requireAdmin(request);
        if (caller == null) {
            return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                .body("Admin access required")
                .build();
        }
        try {
            UserService.getServiceInstance().approveUser(id);
            ObjectNode resp = OBJECT_MAPPER.createObjectNode();
            resp.put("userId", id);
            resp.put("status", "APPROVED");
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
        User caller = AuthHelper.requireAdmin(request);
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
        User caller = AuthHelper.requireAdmin(request);
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
        User caller = AuthHelper.requireAdmin(request);
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

    private ObjectNode safeUserNode(User user) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("id",            user.getId());
        node.put("name",          user.getName());
        node.put("email",         user.getEmail());
        node.put("address",       user.getAddress());
        node.put("phone",         user.getPhone());
        node.put("notes",         user.getNotes());
        node.put("preferences",   user.getPreferences());
        node.put("venmoHandle",   user.getVenmoHandle());
        node.put("paypalHandle",  user.getPaypalHandle());
        node.put("ebayUsername",  user.getEbayUsername());
        node.put("cashAppHandle", user.getCashAppHandle());
        node.put("status",        user.getStatus() != null ? user.getStatus().getValue() : null);
        node.put("isAdmin",       user.isAdmin());
        node.put("createdDate",   user.getCreatedDate());
        node.put("approvedDate",  user.getApprovedDate());
        return node;
    }

    /** Thin wrapper delegating to {@link HttpHelper#cors}. */
    private HttpResponseMessage.Builder cors(HttpResponseMessage.Builder builder) {
        return HttpHelper.cors(builder);
    }
}
