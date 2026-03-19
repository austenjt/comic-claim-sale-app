package org.example.functions.util;

import org.example.functions.model.ShippingEstimate;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estimates USPS Ground Advantage shipping costs based on book count and delivery address.
 *
 * Packaging tiers:
 *   1–8 books   → Gemini Mailer, flat $8.00
 *   9–10 books  → Gemini Mailer, zone-based USPS GA rate
 *   11–28 books → Comic Box, zone-based USPS GA rate
 *   29+ books   → Free (Short Box)
 *
 * Zone is derived from the buyer's state (extracted from free-text address) vs. the
 * seller's origin state (configured via ORIGIN_STATE env var, default "TN").
 */
public final class ShippingCalculator {

    private ShippingCalculator() {}

    // ─── Weight constants ────────────────────────────────────────────────────

    /** Average weight of a single bagged/boarded comic book (lbs). */
    private static final double COMIC_LBS = 0.165;
    /** Packaging overhead for a Gemini Mailer (lbs). */
    private static final double GEMINI_PACKAGING_LBS = 0.50;
    /** Packaging overhead for a standard comic box (lbs). */
    private static final double COMIC_BOX_PACKAGING_LBS = 1.50;

    // ─── Tier thresholds ─────────────────────────────────────────────────────

    private static final int FLAT_RATE_MAX   = 8;
    private static final int GEMINI_MAX      = 10;
    private static final int COMIC_BOX_MAX   = 28;   // 29+ is free
    private static final double FLAT_RATE    = 8.00;

    // ─── USPS Ground Advantage rate table ────────────────────────────────────
    //
    // Weight breakpoints in lbs; rates indexed [zoneIdx][breakIdx].
    // zoneIdx 0 = Zone 2, zoneIdx 6 = Zone 8.
    // Zone 1 is treated same as Zone 2 (local/same district).

    private static final double[] WEIGHT_BREAKS = {
        0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 26
    };

    private static final double[][] RATE_TABLE = {
        // Zone 2
        { 5.25, 5.25,  6.10,  6.65,  7.15,  7.60,  8.00,  8.40,  8.75,  9.10,  9.45, 10.15, 11.25, 13.50, 16.10 },
        // Zone 3
        { 5.40, 5.40,  6.50,  7.20,  7.85,  8.45,  9.00,  9.55, 10.05, 10.55, 11.00, 12.05, 13.60, 16.60, 20.10 },
        // Zone 4
        { 5.55, 5.55,  7.10,  8.00,  8.85,  9.65, 10.40, 11.10, 11.80, 12.45, 13.10, 14.55, 16.75, 20.90, 25.80 },
        // Zone 5
        { 5.75, 5.75,  7.80,  9.05, 10.20, 11.25, 12.20, 13.15, 14.05, 14.95, 15.80, 17.75, 20.75, 26.40, 33.00 },
        // Zone 6
        { 6.00, 6.00,  8.55, 10.15, 11.55, 12.85, 14.05, 15.25, 16.40, 17.50, 18.60, 21.10, 24.95, 32.10, 40.50 },
        // Zone 7
        { 6.30, 6.30,  9.40, 11.30, 13.00, 14.60, 16.05, 17.55, 18.95, 20.35, 21.70, 24.80, 29.50, 38.35, 48.60 },
        // Zone 8
        { 6.70, 6.70, 11.25, 13.85, 16.20, 18.40, 20.40, 22.45, 24.35, 26.25, 28.10, 32.50, 39.20, 51.50, 66.10 },
    };

    // ─── Geographic regions ───────────────────────────────────────────────────

    private enum Region { NORTHEAST, SOUTHEAST, MIDWEST, SOUTHWEST, MOUNTAIN, PACIFIC, REMOTE }

    private static final Map<String, Region> STATE_REGIONS = new HashMap<>();
    static {
        for (String s : new String[]{"ME","NH","VT","MA","RI","CT","NY","NJ","PA","DE","MD","DC"})
            STATE_REGIONS.put(s, Region.NORTHEAST);
        for (String s : new String[]{"VA","WV","NC","SC","GA","FL","TN","KY","AL","MS","AR","LA"})
            STATE_REGIONS.put(s, Region.SOUTHEAST);
        for (String s : new String[]{"OH","IN","MI","WI","MN","IA","MO","IL","ND","SD","NE","KS"})
            STATE_REGIONS.put(s, Region.MIDWEST);
        for (String s : new String[]{"TX","OK","NM","AZ"})
            STATE_REGIONS.put(s, Region.SOUTHWEST);
        for (String s : new String[]{"CO","UT","NV","ID","WY","MT"})
            STATE_REGIONS.put(s, Region.MOUNTAIN);
        for (String s : new String[]{"CA","WA","OR"})
            STATE_REGIONS.put(s, Region.PACIFIC);
        for (String s : new String[]{"AK","HI","PR","VI","GU","MP","AS"})
            STATE_REGIONS.put(s, Region.REMOTE);
    }

    /**
     * Zone matrix [fromRegion.ordinal()][toRegion.ordinal()].
     * Rows: NE, SE, MW, SW, MT, PC, RM
     */
    private static final int[][] ZONE_MATRIX = {
        //  NE  SE  MW  SW  MT  PC  RM
        {    2,  3,  4,  5,  6,  7,  8 }, // from NORTHEAST
        {    3,  2,  4,  4,  6,  7,  8 }, // from SOUTHEAST
        {    4,  4,  2,  3,  4,  6,  8 }, // from MIDWEST
        {    5,  4,  3,  2,  3,  4,  8 }, // from SOUTHWEST
        {    6,  6,  4,  3,  2,  3,  7 }, // from MOUNTAIN
        {    7,  7,  6,  4,  3,  2,  6 }, // from PACIFIC
        {    8,  8,  8,  8,  7,  6,  2 }, // from REMOTE
    };

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * @param bookCount    number of non-set-container items in the cart
     * @param originState  seller's 2-letter state abbreviation (from env ORIGIN_STATE)
     * @param destAddress  buyer's free-text address (state extracted via regex)
     */
    public static ShippingEstimate estimate(int bookCount, String originState, String destAddress) {
        if (bookCount <= 0) {
            return ShippingEstimate.builder()
                .bookCount(0).packagingTier("NONE").estimatedCost(0).isFree(true)
                .notes("No items in cart.")
                .build();
        }

        // Free shipping threshold
        if (bookCount >= 29) {
            double weight = bookCount * COMIC_LBS + 5.0; // Short Box packaging ~5 lbs
            return ShippingEstimate.builder()
                .bookCount(bookCount)
                .packagingTier("FREE")
                .packageWeightLbs(Math.round(weight * 100.0) / 100.0)
                .shippingZone(0)
                .estimatedCost(0.00)
                .isFree(true)
                .notes("Free shipping on orders of 29+ books (Short Box).")
                .build();
        }

        // Flat rate for 1–8 books
        if (bookCount <= FLAT_RATE_MAX) {
            double weight = bookCount * COMIC_LBS + GEMINI_PACKAGING_LBS;
            return ShippingEstimate.builder()
                .bookCount(bookCount)
                .packagingTier("FLAT_RATE")
                .packageWeightLbs(Math.round(weight * 100.0) / 100.0)
                .shippingZone(0)
                .estimatedCost(FLAT_RATE)
                .isFree(false)
                .notes("Flat rate — Gemini Mailer.")
                .build();
        }

        // Zone-based estimate
        int zone = estimateZone(originState, destAddress);
        String tier;
        double packageWeight;

        if (bookCount <= GEMINI_MAX) {
            tier = "GEMINI_MAILER";
            packageWeight = bookCount * COMIC_LBS + GEMINI_PACKAGING_LBS;
        } else {
            tier = "COMIC_BOX";
            packageWeight = bookCount * COMIC_LBS + COMIC_BOX_PACKAGING_LBS;
        }

        packageWeight = Math.round(packageWeight * 100.0) / 100.0;
        double cost = Math.round(getRate(zone, packageWeight) * 100.0) / 100.0;

        String packagingLabel = "GEMINI_MAILER".equals(tier) ? "Gemini Mailer" : "Comic Box";
        String notes = String.format("Est. Zone %d, %s (%.1f lbs) — USPS Ground Advantage.", zone, packagingLabel, packageWeight);

        return ShippingEstimate.builder()
            .bookCount(bookCount)
            .packagingTier(tier)
            .packageWeightLbs(packageWeight)
            .shippingZone(zone)
            .estimatedCost(cost)
            .isFree(false)
            .notes(notes)
            .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Looks up the USPS GA rate for a given zone and package weight (ceiling bracket). */
    private static double getRate(int zone, double weightLbs) {
        int zoneIdx = Math.max(0, Math.min(6, zone - 2)); // zones 2–8 → indices 0–6
        for (int i = 0; i < WEIGHT_BREAKS.length; i++) {
            if (weightLbs <= WEIGHT_BREAKS[i]) {
                return RATE_TABLE[zoneIdx][i];
            }
        }
        // Beyond 26 lbs: extrapolate at roughly $1.50/lb over the 26 lb rate
        double excess = weightLbs - WEIGHT_BREAKS[WEIGHT_BREAKS.length - 1];
        return RATE_TABLE[zoneIdx][WEIGHT_BREAKS.length - 1] + excess * 1.50;
    }

    private static final Pattern STATE_PATTERN =
        Pattern.compile("(?:,\\s*|\\s)([A-Z]{2})(?:\\s+\\d{5})?(?:[\\s,]|$)");

    /** Extracts a 2-letter US state abbreviation from a free-text address. */
    static String extractState(String address) {
        if (address == null || address.isBlank()) return null;
        // Uppercase the address for matching
        String upper = address.toUpperCase();
        Matcher m = STATE_PATTERN.matcher(upper);
        while (m.find()) {
            String candidate = m.group(1);
            if (STATE_REGIONS.containsKey(candidate)) return candidate;
        }
        return null;
    }

    /** Returns the estimated USPS zone (2–8) between origin and destination states. */
    static int estimateZone(String originState, String destAddress) {
        String destState = extractState(destAddress);

        Region origin = originState != null ? STATE_REGIONS.get(originState.toUpperCase()) : null;
        Region dest   = destState   != null ? STATE_REGIONS.get(destState.toUpperCase())   : null;

        if (origin == null) origin = Region.SOUTHEAST; // default seller region
        if (dest   == null) return 5;                  // unknown destination → Zone 5 (mid-range estimate)

        return ZONE_MATRIX[origin.ordinal()][dest.ordinal()];
    }
}
