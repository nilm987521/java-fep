package com.fep.message.iso8583.field;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * ISO 8583 field data types.
 */
@Getter
@RequiredArgsConstructor
public enum FieldType {

    /**
     * Numeric data (digits 0-9).
     * Right-justified, zero-padded.
     */
    NUMERIC("n", "Numeric"),

    /**
     * Alphabetic data (A-Z, a-z).
     */
    ALPHA("a", "Alphabetic"),

    /**
     * Special characters only.
     */
    SPECIAL("s", "Special"),

    /**
     * Alpha-numeric (letters and digits).
     */
    ALPHA_NUMERIC("an", "Alpha-Numeric"),

    /**
     * Alpha-numeric with special characters.
     */
    ALPHA_NUMERIC_SPECIAL("ans", "Alpha-Numeric-Special"),

    /**
     * Numeric-special characters.
     */
    NUMERIC_SPECIAL("ns", "Numeric-Special"),

    /**
     * Binary data.
     */
    BINARY("b", "Binary"),

    /**
     * Track 2 code set.
     */
    TRACK2("z", "Track 2"),

    /**
     * Extended binary coded decimal (8421).
     */
    EXTENDED_BCD("x", "Extended BCD");

    private final String code;
    private final String description;
}
