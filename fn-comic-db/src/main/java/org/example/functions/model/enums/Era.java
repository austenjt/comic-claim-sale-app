package org.example.functions.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Era {
    GOLDEN_AGE("Golden Age"),
    SILVER_AGE("Silver Age"),
    BRONZE_AGE("Bronze Age"),
    COPPER_AGE("Copper Age"),
    MODERN_AGE("Modern Age");

    private final String label;

    Era(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}