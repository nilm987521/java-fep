package com.fep.jmeter.config;

/**
 * Template source types for Transaction Template Config.
 *
 * <p>Defines where transaction templates are loaded from.
 */
public enum TemplateSource {

    /** Use predefined common templates. */
    COMMON,

    /** Load templates from JSON file. */
    FILE,

    /** Define templates inline as JSON. */
    INLINE;

    /**
     * Get all source names as String array for JMeter GUI TAGS.
     */
    public static String[] names() {
        TemplateSource[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Parse source from String, returning COMMON as default if invalid.
     */
    public static TemplateSource fromString(String value) {
        if (value == null || value.isEmpty()) {
            return COMMON;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
}
