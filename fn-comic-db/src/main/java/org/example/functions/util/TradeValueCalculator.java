package org.example.functions.util;

import org.example.functions.model.enums.ComicGrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/**
 * Calculates the trade-in credit value for a comic based on its NM estimated value
 * and the condition the user reports for their trade-in copy.
 *
 * <p>Credit = nmEstimatedValue × gradeMultiplier(grade), rounded to 2 decimal places.
 */
public final class TradeValueCalculator {

    private TradeValueCalculator() {}

    /**
     * Multiplier table: what percentage of NM value we credit for each grade.
     * Higher grades get a premium; lower grades get a steep discount.
     */
    private static final Map<ComicGrade, BigDecimal> MULTIPLIERS = new EnumMap<>(ComicGrade.class);

    // These values need to sync with the values in trade-board.compoonent.ts in the front-end
    // Maybe we could return these in the /config endpoint so the front end can calculate dynamically?
    static {
        MULTIPLIERS.put(ComicGrade.GEM_MINT,          new BigDecimal("5.50"));
        MULTIPLIERS.put(ComicGrade.MINT,               new BigDecimal("3.40"));
        MULTIPLIERS.put(ComicGrade.NEAR_MINT_MINT,     new BigDecimal("2.25"));
        MULTIPLIERS.put(ComicGrade.NEAR_MINT_PLUS,     new BigDecimal("1.66"));
        MULTIPLIERS.put(ComicGrade.NEAR_MINT,         new BigDecimal("1.00"));
        MULTIPLIERS.put(ComicGrade.NEAR_MINT_MINUS,   new BigDecimal("0.75"));
        MULTIPLIERS.put(ComicGrade.VERY_FINE_NEAR_MINT, new BigDecimal("0.55"));
        MULTIPLIERS.put(ComicGrade.VERY_FINE_PLUS,    new BigDecimal("0.40"));
        MULTIPLIERS.put(ComicGrade.VERY_FINE,         new BigDecimal("0.22"));
        MULTIPLIERS.put(ComicGrade.VERY_FINE_MINUS,   new BigDecimal("0.14"));
        MULTIPLIERS.put(ComicGrade.FINE_VERY_FINE,    new BigDecimal("0.13"));
        MULTIPLIERS.put(ComicGrade.FINE_PLUS,         new BigDecimal("0.12"));
        MULTIPLIERS.put(ComicGrade.FINE,              new BigDecimal("0.10"));
        MULTIPLIERS.put(ComicGrade.FINE_MINUS,        new BigDecimal("0.08"));
        MULTIPLIERS.put(ComicGrade.VERY_GOOD_FINE,    new BigDecimal("0.06"));
        MULTIPLIERS.put(ComicGrade.VERY_GOOD_PLUS,    new BigDecimal("0.05"));
        MULTIPLIERS.put(ComicGrade.VERY_GOOD,         new BigDecimal("0.04"));
        MULTIPLIERS.put(ComicGrade.VERY_GOOD_MINUS,   new BigDecimal("0.04"));
        MULTIPLIERS.put(ComicGrade.GOOD_VERY_GOOD,    new BigDecimal("0.02"));
        MULTIPLIERS.put(ComicGrade.GOOD_PLUS,         new BigDecimal("0.02"));
        MULTIPLIERS.put(ComicGrade.GOOD,              new BigDecimal("0.00"));
        MULTIPLIERS.put(ComicGrade.GOOD_MINUS,        new BigDecimal("0.00"));
        MULTIPLIERS.put(ComicGrade.FAIR_GOOD,         new BigDecimal("0.00"));
        MULTIPLIERS.put(ComicGrade.FAIR,              new BigDecimal("0.00"));
        MULTIPLIERS.put(ComicGrade.POOR,              new BigDecimal("0.00"));
    }

    /**
     * @param nmEstimatedValue the NM reference price for the wanted comic (must be non-null and positive)
     * @param grade            the condition of the user's trade-in copy
     * @return trade-in credit amount, rounded to 2 decimal places
     */
    public static BigDecimal calculate(BigDecimal nmEstimatedValue, ComicGrade grade) {
        if (nmEstimatedValue == null) throw new IllegalArgumentException("nmEstimatedValue must not be null");
        if (grade == null) throw new IllegalArgumentException("grade must not be null");
        BigDecimal multiplier = MULTIPLIERS.get(grade);
        return nmEstimatedValue.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    /** Returns the multiplier for a given grade (useful for previewing in API responses). */
    public static BigDecimal multiplierFor(ComicGrade grade) {
        return MULTIPLIERS.getOrDefault(grade, BigDecimal.ZERO);
    }
}
