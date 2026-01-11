package com.fep.jmeter.sampler;

/**
 * Length header types for message framing.
 *
 * <p>Defines how messages are framed with length prefixes for TCP communication.
 */
public enum LengthHeaderType {

    /**
     * No length header. Message is sent as-is.
     * Useful for protocols that use delimiters or fixed-length messages.
     */
    NONE("None", "No length header", 0),

    /**
     * 2-byte big-endian length header.
     * Most common format for financial protocols (FISC, ISO 8583).
     */
    TWO_BYTES("2-byte", "2-byte big-endian length prefix", 2),

    /**
     * 4-byte big-endian length header.
     * Used by some enterprise messaging systems.
     */
    FOUR_BYTES("4-byte", "4-byte big-endian length prefix", 4),

    /**
     * 2-byte BCD (Binary Coded Decimal) length header.
     * Each byte represents two decimal digits.
     */
    TWO_BYTES_BCD("2-byte BCD", "2-byte BCD length prefix", 2),

    /**
     * ASCII length header (variable digits).
     * Length is encoded as ASCII decimal digits.
     */
    ASCII_4("4-char ASCII", "4-character ASCII decimal length", 4);

    private final String displayName;
    private final String description;
    private final int headerSize;

    LengthHeaderType(String displayName, String description, int headerSize) {
        this.displayName = displayName;
        this.description = description;
        this.headerSize = headerSize;
    }

    /**
     * Get the human-readable display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the header size in bytes.
     */
    public int getHeaderSize() {
        return headerSize;
    }

    /**
     * Get all type names as String array for JMeter GUI TAGS.
     */
    public static String[] names() {
        LengthHeaderType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Parse type from String, returning TWO_BYTES as default if invalid.
     */
    public static LengthHeaderType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return TWO_BYTES;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TWO_BYTES;
        }
    }
}
