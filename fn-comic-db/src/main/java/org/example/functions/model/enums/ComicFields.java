package org.example.functions.model.enums;

/**
 * This enum represents common comic book fields shared across CSV import sources.
 */
public enum ComicFields {

    COMIC("Comic"),
    SERIES("Series"),
    ISSUE_NUMBER("Issue #");

    private final String columnName;

    ComicFields(String columnName) {
        this.columnName = columnName;
    }

    public String col() {
        return columnName;
    }
}