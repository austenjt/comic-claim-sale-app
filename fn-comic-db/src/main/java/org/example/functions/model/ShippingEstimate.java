package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShippingEstimate {
    /** Number of books in the cart (set containers excluded). */
    private int bookCount;
    /** GEMINI_MAILER | COMIC_BOX | LARGE_BOX | SHORT_BOX | NONE */
    private String packagingTier;
    /** Flat shipping cost in USD. */
    private double estimatedCost;
    /** Human-readable note shown to the user. */
    private String notes;
    /** Always false — reserved for future free-shipping promotions. */
    private boolean isFree;
}
