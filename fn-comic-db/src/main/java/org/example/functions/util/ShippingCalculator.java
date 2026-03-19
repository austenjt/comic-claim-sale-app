package org.example.functions.util;

import org.example.functions.model.ShippingEstimate;

/**
 * Estimates shipping costs based on flat-rate packaging tiers.
 *
 *   1–10  books → Gemini Mailer       $8.00
 *   11–28 books → Comic Box           $15.00
 *   29–134 books → Larger Shipping Box $22.00  (~26 lbs)
 *   135+  books → Comic Short Box     $30.00
 */
public final class ShippingCalculator {

    private ShippingCalculator() {}

    private static final int GEMINI_MAX     = 10;
    private static final int COMIC_BOX_MAX  = 28;
    private static final int LARGE_BOX_MAX  = 134;

    private static final double GEMINI_RATE     =  8.00;
    private static final double COMIC_BOX_RATE  = 15.00;
    private static final double LARGE_BOX_RATE  = 22.00;
    private static final double SHORT_BOX_RATE  = 30.00;

    public static ShippingEstimate estimate(int bookCount) {
        if (bookCount <= 0) {
            return ShippingEstimate.builder()
                .bookCount(0).packagingTier("NONE")
                .estimatedCost(0).isFree(false)
                .notes("No items in cart.")
                .build();
        }

        if (bookCount <= GEMINI_MAX) {
            return tier(bookCount, "GEMINI_MAILER", GEMINI_RATE, "Gemini Mailer — flat rate.");
        }
        if (bookCount <= COMIC_BOX_MAX) {
            return tier(bookCount, "COMIC_BOX", COMIC_BOX_RATE, "Comic Box — flat rate.");
        }
        if (bookCount <= LARGE_BOX_MAX) {
            return tier(bookCount, "LARGE_BOX", LARGE_BOX_RATE, "Larger Shipping Box (~26 lbs) — flat rate.");
        }
        return tier(bookCount, "SHORT_BOX", SHORT_BOX_RATE, "Comic Short Box — flat rate.");
    }

    private static ShippingEstimate tier(int books, String tier, double cost, String notes) {
        return ShippingEstimate.builder()
            .bookCount(books)
            .packagingTier(tier)
            .estimatedCost(cost)
            .isFree(false)
            .notes(notes)
            .build();
    }
}
