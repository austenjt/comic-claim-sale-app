package org.example.functions.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

/**
 * Centralized helpers for Azure Function HTTP triggers.
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>CORS-aware response builders so triggers don't have to repeat the same headers.</li>
 *   <li>Safe extraction helpers for {@link JsonNode} request bodies.</li>
 *   <li>A consistent exception-to-status mapping for service-layer errors so
 *       triggers no longer fall back to {@link HttpStatus#I_AM_A_TEAPOT} (418) on
 *       generic failures.</li>
 * </ul>
 */
public class HttpHelper {

    private static final String CORS_ORIGIN = "*";
    private static final String CORS_HEADERS = "Authorization, Content-Type";
    private static final String CORS_METHODS = "*";

    private HttpHelper() {}

    // ─── Response builders ────────────────────────────────────────────────────

    /** Adds the standard CORS headers to a response builder. */
    public static HttpResponseMessage.Builder cors(HttpResponseMessage.Builder b) {
        return b.header("Access-Control-Allow-Origin", CORS_ORIGIN)
                .header("Access-Control-Allow-Headers", CORS_HEADERS)
                .header("Access-Control-Allow-Methods", CORS_METHODS);
    }

    /** 200 OK with a JSON body. */
    public static HttpResponseMessage getOkResponse(HttpRequestMessage<?> request, String body) {
        return cors(request.createResponseBuilder(HttpStatus.OK))
            .header("Content-Type", "application/json")
            .body(body)
            .build();
    }

    /** 500 Internal Server Error with a plain-text body. */
    public static HttpResponseMessage getErrorResponse(HttpRequestMessage<?> request, String body) {
        return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
            .header("Content-Type", "text/plain")
            .body(body)
            .build();
    }

    /** 400 Bad Request with a plain-text body. */
    public static HttpResponseMessage badRequest(HttpRequestMessage<?> request, String message) {
        return cors(request.createResponseBuilder(HttpStatus.BAD_REQUEST))
            .header("Content-Type", "text/plain")
            .body(message)
            .build();
    }

    /** 404 Not Found with a plain-text body. */
    public static HttpResponseMessage notFound(HttpRequestMessage<?> request, String message) {
        return cors(request.createResponseBuilder(HttpStatus.NOT_FOUND))
            .header("Content-Type", "text/plain")
            .body(message)
            .build();
    }

    /** 409 Conflict with a plain-text body. */
    public static HttpResponseMessage conflict(HttpRequestMessage<?> request, String message) {
        return cors(request.createResponseBuilder(HttpStatus.CONFLICT))
            .header("Content-Type", "text/plain")
            .body(message)
            .build();
    }

    /** 401 Unauthorized with a generic plain-text body. */
    public static HttpResponseMessage unauthorized(HttpRequestMessage<?> request) {
        return cors(request.createResponseBuilder(HttpStatus.UNAUTHORIZED))
            .header("Content-Type", "text/plain")
            .body("Unauthorized")
            .build();
    }

    /**
     * 500 Internal Server Error with a generic message. The exception is intentionally not
     * included in the response body so internals don't leak — callers should log the
     * exception with full stack trace before invoking this helper.
     */
    public static HttpResponseMessage serverError(HttpRequestMessage<?> request, Throwable e) {
        return cors(request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR))
            .header("Content-Type", "text/plain")
            .body("An internal error occurred.")
            .build();
    }

    /**
     * Map a service-layer exception to a sensible HTTP response. This replaces the
     * bag of {@code I_AM_A_TEAPOT} responses scattered through triggers.
     *
     * <ul>
     *   <li>{@link IllegalArgumentException} → 400 Bad Request</li>
     *   <li>{@link IllegalStateException} → 409 Conflict</li>
     *   <li>{@link NumberFormatException} → 400 Bad Request</li>
     *   <li>everything else → 500 Internal Server Error with a generic message</li>
     * </ul>
     *
     * <p>The exception's message is included for 4xx responses only; 5xx responses
     * deliberately return a generic body so internal details don't leak to clients.
     * Callers should still log the original exception with full stack trace.</p>
     */
    public static HttpResponseMessage errorResponse(HttpRequestMessage<?> request, Throwable e) {
        if (e instanceof IllegalArgumentException || e instanceof NumberFormatException) {
            return badRequest(request, e.getMessage());
        }
        if (e instanceof IllegalStateException) {
            return conflict(request, e.getMessage());
        }
        return getErrorResponse(request, "An internal error occurred.");
    }

    // ─── JSON parsing helpers ─────────────────────────────────────────────────

    /**
     * Returns the trimmed string at {@code field}, or {@code null} if missing/blank.
     * Treats explicit JSON {@code null} the same as a missing field.
     */
    public static String getString(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        String s = val.asText().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Like {@link #getString(JsonNode, String)} but throws {@link IllegalArgumentException}
     * if the field is missing or blank. Use when the field is required to satisfy the
     * request — the resulting 400 response will name the missing field.
     */
    public static String requireString(JsonNode node, String field) {
        String s = getString(node, field);
        if (s == null) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return s;
    }

    /** Returns the boolean at {@code field} or {@code defaultValue} if missing/null. */
    public static boolean getBoolean(JsonNode node, String field, boolean defaultValue) {
        if (node == null) return defaultValue;
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return defaultValue;
        return val.asBoolean(defaultValue);
    }

    /** Returns the integer at {@code field}, or {@code null} if missing/null. */
    public static Integer getInt(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        return val.asInt();
    }
}
