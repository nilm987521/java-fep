package com.fep.jmeter.sampler;

/**
 * Format types for RAW message data encoding.
 *
 * <p>Defines how the raw message data is encoded in the sampler configuration.
 */
public enum RawMessageFormat {

    /**
     * Hexadecimal encoding.
     * Example: "0200603800000000000002000000000010004111111111111111"
     */
    HEX("Hexadecimal", "Message data in hex format (e.g., 0200603800...)"),

    /**
     * Base64 encoding.
     * Example: "AgBgOAAAAAAAAgAAAAAAAAEAQREREREREREQ"
     */
    BASE64("Base64", "Message data in Base64 format"),

    /**
     * Plain text / ASCII.
     * The text is converted to bytes using UTF-8 encoding.
     */
    TEXT("Plain Text", "Message data as plain text (UTF-8)");

    private final String displayName;
    private final String description;

    RawMessageFormat(String displayName, String description) {
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
        RawMessageFormat[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Parse type from String, returning HEX as default if invalid.
     */
    public static RawMessageFormat fromString(String value) {
        if (value == null || value.isEmpty()) {
            return HEX;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return HEX;
        }
    }
}
