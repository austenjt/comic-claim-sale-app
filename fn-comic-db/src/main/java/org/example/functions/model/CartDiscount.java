package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents one discount rule's contribution to a submitted cart.
 * Stored as an array in Cart.discountBreakdown so the frontend can compute
 * exact per-item discounted prices for each rule independently.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartDiscount {
    private double amount;
    private String description;
    @JsonProperty("excludesSets")
    private boolean excludesSets;
    @JsonProperty("excludesAuctions")
    private boolean excludesAuctions;
    @JsonProperty("excludesGraded")
    private boolean excludesGraded;

    public CartDiscount() {}

    /** Convenience constructor — defaults the two newer flags to false for back-compat. */
    public CartDiscount(double amount, String description, boolean excludesSets) {
        this(amount, description, excludesSets, false, false);
    }

    public CartDiscount(double amount, String description,
                        boolean excludesSets, boolean excludesAuctions, boolean excludesGraded) {
        this.amount = amount;
        this.description = description;
        this.excludesSets = excludesSets;
        this.excludesAuctions = excludesAuctions;
        this.excludesGraded = excludesGraded;
    }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isExcludesSets() { return excludesSets; }
    public void setExcludesSets(boolean excludesSets) { this.excludesSets = excludesSets; }

    public boolean isExcludesAuctions() { return excludesAuctions; }
    public void setExcludesAuctions(boolean excludesAuctions) { this.excludesAuctions = excludesAuctions; }

    public boolean isExcludesGraded() { return excludesGraded; }
    public void setExcludesGraded(boolean excludesGraded) { this.excludesGraded = excludesGraded; }
}
