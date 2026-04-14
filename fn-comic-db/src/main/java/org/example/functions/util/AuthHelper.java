package org.example.functions.util;

import com.microsoft.azure.functions.HttpRequestMessage;
import org.example.functions.auth.EntraJwtValidator;
import org.example.functions.model.User;
import org.example.functions.model.UserIdentity;
import org.example.functions.model.UserStatus;
import org.example.functions.service.UserService;

/** Shared authentication helpers used by all trigger classes. */
public final class AuthHelper {

    private AuthHelper() {}

    /**
     * Validates the Bearer token in the Authorization header and returns the
     * corresponding CosmosDB User, or null if the token is missing/invalid or
     * the user record does not exist / is not approved.
     */
    public static User requireSession(HttpRequestMessage<?> request) {
        String authHeader = request.getHeaders().get("authorization");
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();

        UserIdentity identity;
        try {
            identity = EntraJwtValidator.getInstance().validate(token);
        } catch (Exception e) {
            return null;
        }

        return UserService.getServiceInstance().findByEmail(identity.getEmail()).orElse(null);
    }

    /** Returns the authenticated User only if they are APPROVED (or admin), otherwise null. */
    public static User requireApproved(HttpRequestMessage<?> request) {
        User user = requireSession(request);
        if (user == null) return null;
        if (UserStatus.APPROVED != user.getStatus() && !user.isAdmin()) return null;
        return user;
    }

    /** Returns the authenticated User only if they are an admin, otherwise null. */
    public static User requireAdmin(HttpRequestMessage<?> request) {
        User user = requireSession(request);
        if (user == null || !user.isAdmin()) return null;
        return user;
    }

    /** Returns true if the request carries a valid admin Bearer token. */
    public static boolean isAdminRequest(HttpRequestMessage<?> request) {
        User user = requireSession(request);
        return user != null && user.isAdmin();
    }
}
