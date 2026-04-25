package org.example.functions.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Payment status for an active or archived order.
 *
 * <p>Wire format: enum names are serialized verbatim (via {@link #toJson()}) so the JSON
 * contract with the Angular frontend is identical to the previous string-based representation.</p>
 *
 * <p>{@link #PARTIAL} is reserved for future split-payment workflows; it is accepted by the
 * deserializer but no current code path produces it.</p>
 */
public enum PaymentStatus {
    UNPAID,
    PAID,
    PARTIAL;

    @JsonValue
    public String toJson() {
        return name();
    }

    /**
     * Case-insensitive lookup used by Jackson to deserialize incoming JSON and existing
     * Cosmos documents. Returns {@code null} for {@code null}/blank input.
     */
    @JsonCreator
    public static PaymentStatus fromJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return PaymentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown payment status: " + value);
        }
    }
}
