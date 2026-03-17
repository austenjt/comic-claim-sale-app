package org.example.functions.model.enums;

/**
 * This enum is a map of fields that appear at the top of a GoCollect CSV data export file.
 */
public enum GoCollectFields {

    GCIN("GCIN"),
    COMIC_URL("Comic Url"),
    FMV("FMV 02-19-2026"),
    PERSONAL_ESTIMATE("Personal Estimate"),
    TARGET_PRICE("Target Price"),
    PRICE_PAID("Price Paid"),
    AMOUNT_EARNED("Amount Earned"),
    ASKING_PRICE("Asking Price"),
    MINIMUM_PRICE("Minimum Price"),
    MAXIMUM_PRICE("Maximum Price"),
    TRADE_VALUE_ESTIMATE("Trade Value Estimate"),
    TRADE_VALUE_PAID("Trade Value Paid"),
    DATE_ACQUIRED("Date Acquired"),
    DATE_SOLD("Date Sold"),
    PURCHASED_FROM("Purchased From"),
    PURCHASE_REFERENCE_URL("Purchase Reference URL"),
    PERSONAL_NOTES("Personal Notes"),
    PUBLIC_NOTES("Public Notes"),
    DATE_ADDED("Date Added");

    private final String columnName;

    GoCollectFields(String columnName) {
        this.columnName = columnName;
    }

    public String col() {
        return columnName;
    }
}
