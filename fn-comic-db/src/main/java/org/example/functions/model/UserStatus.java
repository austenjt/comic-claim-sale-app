package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UserStatus {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    SUSPENDED("SUSPENDED");

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
