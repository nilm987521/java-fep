package com.fep.message.iso8583.field;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * ISO 8583 field length types.
 */
@Getter
@RequiredArgsConstructor
public enum LengthType {

    /**
     * Fixed length field.
     */
    FIXED(0, "Fixed length"),

    /**
     * Variable length with 1-digit length prefix (max 9).
     */
    LVAR(1, "Variable length (1 digit prefix, max 9)"),

    /**
     * Variable length with 2-digit length prefix (max 99).
     * Also known as LLVAR.
     */
    LLVAR(2, "Variable length (2 digit prefix, max 99)"),

    /**
     * Variable length with 3-digit length prefix (max 999).
     * Also known as LLLVAR.
     */
    LLLVAR(3, "Variable length (3 digit prefix, max 999)"),

    /**
     * Variable length with 4-digit length prefix (max 9999).
     * Also known as LLLLVAR.
     */
    LLLLVAR(4, "Variable length (4 digit prefix, max 9999)");

    private final int prefixLength;
    private final String description;

    /**
     * Checks if this is a variable length type.
     *
     * @return true if variable length
     */
    public boolean isVariable() {
        return prefixLength > 0;
    }

    /**
     * Gets the maximum data length for this type.
     *
     * @return maximum length, or -1 for FIXED type
     */
    public int getMaxLength() {
        return switch (this) {
            case FIXED -> -1;
            case LVAR -> 9;
            case LLVAR -> 99;
            case LLLVAR -> 999;
            case LLLLVAR -> 9999;
        };
    }
}
