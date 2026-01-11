package com.fep.jmeter.sampler;

/**
 * Protocol types for ATM Simulator.
 *
 * <p>Defines the communication protocol/format used by the ATM Simulator.
 * This allows flexibility to support various ATM protocols beyond ISO 8583.
 */
public enum AtmProtocolType {

    /**
     * ISO 8583 financial message format.
     * Uses FiscMessageAssembler/Parser for message handling.
     */
    ISO_8583("ISO 8583", "Standard ISO 8583 financial message format"),

    /**
     * Raw binary message mode.
     * Allows sending arbitrary bytes in HEX or Base64 format.
     * Full control over message content and structure.
     */
    RAW("Raw Binary", "Send raw bytes (HEX or Base64 encoded)"),

    /**
     * Generic schema-based message mode.
     * Uses user-defined JSON schema for message structure.
     * Supports any ATM protocol (NCR NDC, Diebold 91x, Wincor DDC, etc.).
     */
    GENERIC_SCHEMA("Generic Schema", "User-defined message format via JSON schema");

    private final String displayName;
    private final String description;

    AtmProtocolType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
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
     * Get all type names as String array for JMeter GUI TAGS.
     */
    public static String[] names() {
        AtmProtocolType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Parse type from String, returning ISO_8583 as default if invalid.
     */
    public static AtmProtocolType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return ISO_8583;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ISO_8583;
        }
    }
}
