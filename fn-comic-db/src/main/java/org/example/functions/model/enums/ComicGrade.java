package org.example.functions.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ComicGrade {
    GEM_MINT(10.0, "Gem Mint"),
    MINT_PLUS(9.9, "Mint Plus"),
    MINT(9.8, "Mint"),
    NEAR_MINT_PLUS(9.6, "Near Mint+"),
    NEAR_MINT(9.4, "Near Mint"),
    NEAR_MINT_MINUS(9.2, "Near Mint-"),
    VERY_FINE_NEAR_MINT(9.0, "Very Fine/Near Mint"),
    VERY_FINE_PLUS(8.5, "Very Fine+"),
    VERY_FINE(8.0, "Very Fine"),
    VERY_FINE_MINUS(7.5, "Very Fine-"),
    FINE_VERY_FINE(7.0, "Fine/Very Fine"),
    FINE_PLUS(6.5, "Fine+"),
    FINE(6.0, "Fine"),
    FINE_MINUS(5.5, "Fine-"),
    VERY_GOOD_FINE(5.0, "Very Good/Fine"),
    VERY_GOOD_PLUS(4.5, "Very Good+"),
    VERY_GOOD(4.0, "Very Good"),
    VERY_GOOD_MINUS(3.5, "Very Good-"),
    GOOD_VERY_GOOD(3.0, "Good/Very Good"),
    GOOD_PLUS(2.5, "Good+"),
    GOOD(2.0, "Good"),
    GOOD_MINUS(1.8, "Good-"),
    FAIR_GOOD(1.5, "Fair/Good"),
    FAIR(1.0, "Fair"),
    POOR(0.5, "Poor");

    private final double numericGrade;
    private final String label;

    ComicGrade(double numericGrade, String label) {
        this.numericGrade = numericGrade;
        this.label = label;
    }

    @JsonValue
    public double getNumericGrade() {
        return numericGrade;
    }

    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static ComicGrade fromNumericGrade(double grade) {
        for (ComicGrade cg : values()) {
            if (cg.numericGrade == grade) {
                return cg;
            }
        }
        throw new IllegalArgumentException("Unknown comic grade: " + grade);
    }
}