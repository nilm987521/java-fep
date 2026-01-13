package com.fep.jmeter.sampler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Preset schemas for common ATM protocols.
 */
public enum PresetSchema {

    /**
     * NCR NDC protocol.
     */
    NCR_NDC("NCR NDC", "schemas/ncr-ndc-v1.json", "NCR NDC ATM protocol"),

    /**
     * Diebold 91x protocol.
     */
    DIEBOLD_91X("Diebold 91x", "schemas/diebold-91x-v1.json", "Diebold 91x ATM protocol"),

    /**
     * Wincor Nixdorf DDC protocol.
     */
    WINCOR_DDC("Wincor DDC", "schemas/wincor-ddc-v1.json", "Wincor Nixdorf DDC protocol"),

    /**
     * Taiwan FISC ATM format.
     */
    FISC_ATM("FISC ATM", "schemas/fisc-atm-v1.json", "Taiwan FISC ATM message format"),

    /**
     * Generic ISO 8583 (schema-based, not hardcoded).
     */
    ISO8583_GENERIC("ISO 8583 Generic", "schemas/iso8583-generic.json", "Generic ISO 8583 format via schema");

    private final String displayName;
    private final String resourcePath;
    private final String description;

    PresetSchema(String displayName, String resourcePath, String description) {
        this.displayName = displayName;
        this.resourcePath = resourcePath;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Load schema JSON content from resource.
     *
     * @return the schema JSON content, or error message if loading fails
     */
    public String loadSchemaContent() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return "// Schema not found: " + resourcePath;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "// Error loading schema: " + e.getMessage();
        }
    }

    /**
     * Get all preset names as String array for JMeter GUI.
     */
    public static String[] names() {
        PresetSchema[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Get display names for JMeter GUI.
     */
    public static String[] displayNames() {
        PresetSchema[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }

    /**
     * Parse from String, returning NCR_NDC as default.
     */
    public static PresetSchema fromString(String value) {
        if (value == null || value.isEmpty()) {
            return NCR_NDC;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NCR_NDC;
        }
    }
}
