package org.example.functions.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Controls where a comic appears in the storefront.
 *
 * <ul>
 *   <li>{@link #NOT_LISTED} — not visible in any public listing.</li>
 *   <li>{@link #FOR_SALE}   — appears on the main for-sale dashboard.</li>
 *   <li>{@link #WANTED}     — appears on the Trade Board; users can offer a trade-in credit.</li>
 * </ul>
 *
 * <p>Replaces the legacy {@code isForSale} boolean. Legacy documents that still carry
 * {@code isForSale} but lack {@code listingType} are handled by
 * {@link org.example.functions.model.ComicBook#getEffectiveListingType()}.</p>
 */
public enum ListingType {
    NOT_LISTED,
    FOR_SALE,
    WANTED;

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static ListingType fromJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ListingType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown listing type: " + value);
        }
    }
}
