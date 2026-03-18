package org.example.functions.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * This represents any non-numeric values a comic might have.
 *   Back-end must have knowledge of the same list.
 */
public enum NumberSentinel {
    NEGATIVE_ONE("-1"),
    NN("NN"),
    SET("SET");

    private final String value;

    NumberSentinel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}