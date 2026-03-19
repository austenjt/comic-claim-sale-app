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
    /** FLAT_RATE | GEMINI_MAILER | COMIC_BOX | FREE */
    private String packagingTier;
    /** Total estimated package weight in lbs. */
    private double packageWeightLbs;
    /** USPS zone used for the estimate (2–8), or 0 if flat/free. */
    private int shippingZone;
    /** Estimated shipping cost in USD. */
    private double estimatedCost;
    /** Human-readable note shown to the user. */
    private String notes;
    /** True when shipping is free (29+ books). */
    private boolean isFree;
}
