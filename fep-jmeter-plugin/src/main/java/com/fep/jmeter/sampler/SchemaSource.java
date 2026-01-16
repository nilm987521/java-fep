package com.fep.jmeter.sampler;

/**
 * Source types for message schema.
 * Schema is loaded from an external JSON file only.
 * Default schema directory: ${user.dir}/schemas/atm-schemas.json
 */
public enum SchemaSource {

    /**
     * Load schema from an external JSON file.
     */
    FILE("File", "Load schema from JSON file");

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
     * Get all source names as a String array for JMeter GUI.
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

    /**
     * Get default schema file path using user.dir system property.
     */
    public static String getDefaultSchemaPath() {
        return System.getProperty("user.dir") + "/schemas/atm-schemas.json";
    }
}
