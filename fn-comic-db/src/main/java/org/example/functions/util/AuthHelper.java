package org.example.functions.util;

import com.microsoft.azure.functions.HttpRequestMessage;
import org.example.functions.model.User;
import org.example.functions.service.SessionService;
import org.example.functions.service.UserService;

/** Shared authentication helpers used by all trigger classes. */
public final class AuthHelper {

    private AuthHelper() {}

    /** Returns the authenticated User, or null if the session token is missing/invalid. */
    public static User requireSession(HttpRequestMessage<?> request) {
        String token = request.getHeaders().get("x-session-token");
        String userId = SessionService.getServiceInstance().validateSession(token);
        if (userId == null) return null;
        return UserService.getServiceInstance().findById(userId).orElse(null);
    }

    /** Returns the authenticated User only if they are APPROVED (or admin), otherwise null. */
    public static User requireApproved(HttpRequestMessage<?> request) {
        User user = requireSession(request);
        if (user == null) return null;
        if (!"APPROVED".equals(user.getStatus()) && !user.isAdmin()) return null;
        return user;
    }

    /** Returns the authenticated User only if they are an admin, otherwise null. */
    public static User requireAdmin(HttpRequestMessage<?> request) {
        User user = requireSession(request);
        if (user == null || !user.isAdmin()) return null;
        return user;
    }

    /** Returns true if the request carries a valid admin session token. */
    public static boolean isAdminRequest(HttpRequestMessage<?> request) {
        User user = requireSession(request);
        return user != null && user.isAdmin();
    }
}
