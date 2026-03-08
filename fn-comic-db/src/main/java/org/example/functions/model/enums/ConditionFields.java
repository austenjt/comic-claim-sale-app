package org.example.functions.model.enums;

/**
 * This enum is a map of condition/grading fields that appear in a GoCollect CSV data export file.
 */
public enum ConditionFields {

    CERTIFICATION_COMPANY("Certification Company"),
    CERTIFICATION_ID("Certification ID"),
    CGC_LABEL("CGC Label"),
    CGC_GRADE("CGC Grade"),
    CGC_PAGE_QUALITY("CGC Page Quality"),
    CGC_PEDIGREE("CGC Pedigree"),
    CGC_SIGNATURE("CGC Signature"),
    CGC_DEGREE_OF_RESTORATION("CGC Degree of Restoration"),
    CGC_GRADER_NOTES("CGC Grader Notes"),
    CBCS_LABEL("CBCS Label"),
    CBCS_GRADE("CBCS Grade"),
    CBCS_PAGE_QUALITY("CBCS Page Quality"),
    CBCS_PEDIGREE("CBCS Pedigree"),
    CBCS_SIGNATURE("CBCS Signature"),
    CBCS_DEGREE_OF_RESTORATION("CBCS Degree of Restoration"),
    NOT_CERTIFIED_LABEL("Not Certified Label"),
    NOT_CERTIFIED_GRADE("Not Certified Grade"),
    NOT_CERTIFIED_PAGE_QUALITY("Not Certified Page Quality"),
    NOT_CERTIFIED_PEDIGREE("Not Certified Pedigree"),
    NOT_CERTIFIED_DEGREE_OF_RESTORATION("Not Certified Degree of Restoration"),
    NOT_CERTIFIED_SIGNATURE("Not Certified Signature");

    private final String columnName;

    ConditionFields(String columnName) {
        this.columnName = columnName;
    }

    public String col() {
        return columnName;
    }
}
