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
 *   <li>{@link #FINALIZING} — user submitted the order; admin is processing payment.</li>
 *   <li>{@link #FINALIZED} — order has been locked but not yet shipped/delivered.</li>
 *   <li>{@link #FULFILLED} — order has been shipped/delivered and archived.</li>
 *   <li>{@link #DELETED} — cart was abandoned, expired, or fully released by admin/user.</li>
 * </ul>
 */
public enum CartStatus {
    OPEN,
    FINALIZING,
    FINALIZED,
    FULFILLED,
    DELETED;

    @JsonValue
    public String toJson() {
        return name();
    }

    /**
     * Case-insensitive lookup used by Jackson to deserialize incoming JSON and existing
     * Cosmos documents. Returns {@code null} for {@code null}/blank input so that a missing
     * status field doesn't cause the whole document parse to fail.
     */
    @JsonCreator
    public static CartStatus fromJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return CartStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown cart status: " + value);
        }
    }
}
