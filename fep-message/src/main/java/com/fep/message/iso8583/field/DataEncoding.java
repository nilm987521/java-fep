package com.fep.message.iso8583.field;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Data encoding types for ISO 8583 fields.
 */
@Getter
@RequiredArgsConstructor
public enum DataEncoding {

    /**
     * ASCII encoding.
     * Each character takes 1 byte.
     */
    ASCII("ASCII"),

    /**
     * Binary Coded Decimal.
     * Each byte holds 2 digits (packed).
     * Used for numeric fields in FISC protocol.
     */
    BCD("BCD"),

    /**
     * EBCDIC encoding.
     * Legacy mainframe character set.
     */
    EBCDIC("EBCDIC"),

    /**
     * Raw binary data.
     * No encoding transformation.
     */
    BINARY("Binary");

    private final String description;

    /**
     * Calculates the byte length needed to store a given character length.
     *
     * @param charLength the character length
     * @return the byte length
     */
    public int calculateByteLength(int charLength) {
        return switch (this) {
            case BCD -> (charLength + 1) / 2;  // 2 digits per byte, rounded up
            case ASCII, EBCDIC -> charLength;   // 1 character per byte
            case BINARY -> charLength;          // Direct byte count
        };
    }

    /**
     * Calculates the character length from byte length.
     *
     * @param byteLength the byte length
     * @return the character length
     */
    public int calculateCharLength(int byteLength) {
        return switch (this) {
            case BCD -> byteLength * 2;        // 2 digits per byte
            case ASCII, EBCDIC -> byteLength;  // 1 character per byte
            case BINARY -> byteLength;         // Direct byte count
        };
    }
}
