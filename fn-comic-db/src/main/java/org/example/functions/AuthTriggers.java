package org.example.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.auth.EntraJwtValidator;
import org.example.functions.model.User;
import org.example.functions.model.UserIdentity;
import org.example.functions.model.UserStatus;
import org.example.functions.service.UserService;
import org.example.functions.util.Mappers;

import java.util.Optional;

/**
 * Handles MSAL authentication endpoints.
 *
 * GET /api/auth/me
 *   - Validates the Entra Bearer JWT
 *   - Looks up the user in CosmosDB by email
 *   - If no record exists, creates a PENDING user and returns 202
 *   - If PENDING or SUSPENDED, returns 403
 *   - If APPROVED, returns 200 with the user object
 */
@Slf4j
public class AuthTriggers {

    private static final ObjectMapper OBJECT_MAPPER = Mappers.STANDARD;
    private static final String CORS_ORIGIN  = "*";
    private static final String CORS_HEADERS = "Authorization, Content-Type";

    @FunctionName("authMe")
    public HttpResponseMessage authMe(
        @HttpTrigger(
            name = "authMe",
            route = "auth/me",
            methods = {HttpMethod.GET},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request)
    {
        log.info("Processing GET /api/auth/me");
        try {
            // Extract and validate Bearer token
            String authHeader = request.getHeaders().get("authorization");
            if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
                return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                    .body("Missing or invalid Authorization header")
                    .build();
            }
            String token = authHeader.substring(7).trim();

            UserIdentity identity;
            try {
                identity = EntraJwtValidator.getInstance().validate(token);
            } catch (Exception e) {
                log.warn("JWT validation failed: {}", e.getMessage());
                return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
                    .body("Invalid token: " + e.getMessage())
                    .build();
            }

            UserService userService = UserService.getServiceInstance();
            Optional<User> optUser = userService.findByEmail(identity.getEmail());

            if (optUser.isEmpty()) {
                // First login — create a PENDING record so the admin can approve
                User newUser = userService.createFromIdentity(identity);
                log.info("Created PENDING user for {} (oid={})", identity.getEmail(), identity.getOid());

                ObjectNode resp = OBJECT_MAPPER.createObjectNode();
                resp.put("status", "PENDING");
                resp.put("id", newUser.getId());
                return cors(request.createResponseBuilder(HttpStatus.ACCEPTED))
                    .header("Content-Type", "application/json")
                    .body(OBJECT_MAPPER.writeValueAsString(resp))
                    .build();
            }

            User user = optUser.get();

            // Persist the entraOid if it wasn't stored yet (e.g. migrated record)
            if (user.getEntraOid() == null && identity.getOid() != null) {
                userService.setEntraOid(user.getId(), identity.getOid());
                user.setEntraOid(identity.getOid());
            }

            if (UserStatus.SUSPENDED == user.getStatus()) {
                return cors(request.createResponseBuilder(HttpStatus.FORBIDDEN))
                    .body("Account suspended")
                    .build();
            }

            if (UserStatus.PENDING == user.getStatus()) {
                return cors(request.createResponseBuilder(HttpStatus.FORBIDDEN))
                    .body("Account pending approval")
                    .build();
            }

            // APPROVED — return full user object
            return cors(request.createResponseBuilder(HttpStatus.OK))
                .header("Content-Type", "application/json")
                .body(OBJECT_MAPPER.writeValueAsString(safeUserNode(user)))
                .build();

        } catch (Exception e) {
            log.error("Error in GET /api/auth/me", e);
            return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
                .body(e.getMessage())
                .build();
        }
    }

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

    private HttpResponseMessage.Builder cors(HttpResponseMessage.Builder builder) {
        return builder
            .header("Access-Control-Allow-Origin",  CORS_ORIGIN)
            .header("Access-Control-Allow-Headers", CORS_HEADERS)
            .header("Access-Control-Allow-Methods", "*");
    }
}
