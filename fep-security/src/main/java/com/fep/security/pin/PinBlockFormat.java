package com.fep.security.pin;

/**
 * PIN Block formats as defined by ISO 9564-1.
 */
public enum PinBlockFormat {

    /**
     * Format 0 (ISO 9564-1 Format 0)
     *
     * PIN Block = XOR of PIN Field and PAN Field
     *
     * PIN Field: 0 || PIN Length || PIN || F padding
     * PAN Field: 0000 || rightmost 12 digits of PAN (excluding check digit)
     */
    FORMAT_0("ISO 9564-1 Format 0", 0),

    /**
     * Format 1 (ISO 9564-1 Format 1)
     *
     * PIN Block = PIN Field (no XOR with PAN)
     *
     * PIN Field: 1 || PIN Length || PIN || Random padding
     */
    FORMAT_1("ISO 9564-1 Format 1", 1),

    /**
     * Format 2 (ISO 9564-1 Format 2)
     *
     * Used for IC card transactions
     *
     * PIN Field: 2 || PIN Length || PIN || F padding
     */
    FORMAT_2("ISO 9564-1 Format 2", 2),

    /**
     * Format 3 (ISO 9564-1 Format 3)
     *
     * Similar to Format 0, but with enhanced security
     *
     * PIN Block = XOR of PIN Field and PAN Field
     * PIN Field: 3 || PIN Length || PIN || Random padding
     * PAN Field: 0000 || rightmost 12 digits of PAN (excluding check digit)
     */
    FORMAT_3("ISO 9564-1 Format 3", 3),

    /**
     * Format 4 (ISO 9564-1 Format 4)
     *
     * AES-based PIN block format
     */
    FORMAT_4("ISO 9564-1 Format 4", 4);

    private final String description;
    private final int formatCode;

    PinBlockFormat(String description, int formatCode) {
        this.description = description;
        this.formatCode = formatCode;
    }

    public String getDescription() {
        return description;
    }

    public int getFormatCode() {
        return formatCode;
    }

    public static PinBlockFormat fromCode(int code) {
        for (PinBlockFormat format : values()) {
            if (format.formatCode == code) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown PIN block format code: " + code);
    }
}
