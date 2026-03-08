package org.example.functions.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PageQuality {
    WHITE("WHITE"),
    OFF_WHITE_TO_WHITE("OFF-WHITE TO WHITE"),
    OFF_WHITE("OFF-WHITE"),
    CREAM_TO_OFF_WHITE("CREAM TO OFF-WHITE"),
    CREAM("CREAM"),
    TAN_TO_CREAM("TAN TO CREAM"),
    TAN("TAN"),
    BROWN_TO_TAN("BROWN TO TAN"),
    BROWN("BROWN"),
    BRITTLE("BRITTLE");

    private final String value;

    PageQuality(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}