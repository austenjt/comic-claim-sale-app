package org.example.functions.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum GradingCompany {
    CGC("CGC"),
    CBCS("CBCS"),
    PGX("PGX"),
    NOT_CERTIFIED("NOT CERTIFIED");

    private final String value;

    GradingCompany(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}