package com.fep.jmeter.sampler;

/**
 * Source types for message schema in GENERIC_SCHEMA mode.
 */
public enum SchemaSource {

    /**
     * Load schema from an external JSON file.
     */
    FILE("File", "Load schema from JSON file"),

    /**
     * Use inline JSON schema content.
     */
    INLINE("Inline", "Inline JSON schema definition"),

    /**
     * Use a preset schema (NCR NDC, Diebold, etc.).
     */
    PRESET("Preset", "Built-in schema for common ATM protocols");

    private final String displayName;
    private final String description;

    SchemaSource(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get all source names as String array for JMeter GUI.
     */
    public static String[] names() {
        SchemaSource[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Parse from String, returning FILE as default.
     */
    public static SchemaSource fromString(String value) {
        if (value == null || value.isEmpty()) {
            return FILE;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FILE;
        }
    }
}
