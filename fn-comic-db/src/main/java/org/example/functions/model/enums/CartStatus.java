package org.example.functions.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle states for a {@link org.example.functions.model.Cart}.
 *
 * <p>Wire format: enum names are serialized verbatim (via {@link #toJson()}) so the JSON
 * contract with the Angular frontend is identical to the previous string-based representation.</p>
 *
 * <ul>
 *   <li>{@link #OPEN} — user is still building the cart, items can be added/removed.</li>
 *   <li>{@link #SUBMITTED} — user submitted the order; admin is processing payment/shipping.</li>
 *   <li>{@link #FULFILLED} — order has been shipped/delivered and archived.</li>
 *   <li>{@link #DELETED} — cart was abandoned, expired, or fully released by admin/user.</li>
 * </ul>
 *
 * <p>Legacy migration: Cosmos documents with status {@code "FINALIZING"} or {@code "FINALIZED"}
 * (both pre-collapse values) are transparently read as {@link #SUBMITTED} and will be updated
 * to {@code "SUBMITTED"} on their next write.</p>
 */
public enum CartStatus {
    OPEN,
    SUBMITTED,
    FULFILLED,
    DELETED;

    @JsonValue
    public String toJson() {
        return name();
    }

    /**
     * Case-insensitive lookup used by Jackson to deserialize incoming JSON and existing
     * Cosmos documents. Maps legacy {@code FINALIZING} and {@code FINALIZED} values to
     * {@link #SUBMITTED}. Returns {@code null} for {@code null}/blank input.
     */
    @JsonCreator
    public static CartStatus fromJson(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().toUpperCase();
        if ("FINALIZING".equals(v) || "FINALIZED".equals(v)) return SUBMITTED;
        try {
            return CartStatus.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown cart status: " + value);
        }
    }
}
