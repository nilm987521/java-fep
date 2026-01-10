package com.fep.jmeter.validation;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a validation error for a specific field.
 */
@Getter
@Builder
@ToString
public class ValidationError {

    /**
     * Error types for categorization.
     */
    public enum ErrorType {
        MISSING,    // Required field is missing
        FORMAT,     // Field format is invalid
        VALUE,      // Field value is not in allowed set
        LENGTH,     // Field length is incorrect
        PATTERN     // Field doesn't match regex pattern
    }

    /** Field number that failed validation (null for message-level errors) */
    private final Integer fieldNumber;

    /** Type of validation error */
    private final ErrorType errorType;

    /** Expected value or description */
    private final String expected;

    /** Actual value found */
    private final String actual;

    /** Human-readable error message */
    private final String message;

    /**
     * Creates a MISSING error for a required field.
     */
    public static ValidationError missing(int fieldNumber) {
        return ValidationError.builder()
            .fieldNumber(fieldNumber)
            .errorType(ErrorType.MISSING)
            .expected("present")
            .actual("missing")
            .message("Required field " + fieldNumber + " is missing")
            .build();
    }

    /**
     * Creates a FORMAT error for an invalid field format.
     */
    public static ValidationError format(int fieldNumber, String expected, String actual) {
        return ValidationError.builder()
            .fieldNumber(fieldNumber)
            .errorType(ErrorType.FORMAT)
            .expected(expected)
            .actual(actual)
            .message("Field " + fieldNumber + " format invalid: expected " + expected + ", got " + actual)
            .build();
    }

    /**
     * Creates a VALUE error for an invalid field value.
     */
    public static ValidationError value(int fieldNumber, String allowedValues, String actual) {
        return ValidationError.builder()
            .fieldNumber(fieldNumber)
            .errorType(ErrorType.VALUE)
            .expected(allowedValues)
            .actual(actual)
            .message("Field " + fieldNumber + " value not allowed: " + actual + " not in [" + allowedValues + "]")
            .build();
    }

    /**
     * Creates a LENGTH error for incorrect field length.
     */
    public static ValidationError length(int fieldNumber, int expectedLength, int actualLength) {
        return ValidationError.builder()
            .fieldNumber(fieldNumber)
            .errorType(ErrorType.LENGTH)
            .expected(String.valueOf(expectedLength))
            .actual(String.valueOf(actualLength))
            .message("Field " + fieldNumber + " length invalid: expected " + expectedLength + ", got " + actualLength)
            .build();
    }

    /**
     * Creates a PATTERN error for regex mismatch.
     */
    public static ValidationError pattern(int fieldNumber, String pattern, String actual) {
        return ValidationError.builder()
            .fieldNumber(fieldNumber)
            .errorType(ErrorType.PATTERN)
            .expected(pattern)
            .actual(actual)
            .message("Field " + fieldNumber + " doesn't match pattern: " + pattern)
            .build();
    }
}
