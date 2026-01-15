package com.fep.jmeter.sampler;

/**
 * Defines the source of schema for parsing response messages.
 * Schema is loaded from external JSON file only.
 */
public enum ResponseSchemaSource {
    /**
     * Use the same schema as the request (default).
     */
    SAME_AS_REQUEST("Same as Request"),

    /**
     * Load schema from an external file.
     */
    FILE("From File");

    private final String displayName;

    ResponseSchemaSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parse from string value, defaulting to SAME_AS_REQUEST if not recognized.
     */
    public static ResponseSchemaSource fromString(String value) {
        if (value == null || value.isEmpty()) {
            return SAME_AS_REQUEST;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return SAME_AS_REQUEST;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
