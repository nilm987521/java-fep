package com.fep.jmeter.sampler;

/**
 * Message types for FISC Message Sender Sampler.
 *
 * <p>Defines the types of proactive messages that can be sent to FISC clients.
 */
public enum FiscMessageType {

    /** Network management echo test. */
    ECHO_TEST,

    /** Key change notification. */
    KEY_CHANGE_NOTIFY,

    /** System status notification. */
    SYSTEM_STATUS,

    /** Sign-on notification. */
    SIGN_ON_NOTIFY,

    /** Sign-off notification. */
    SIGN_OFF_NOTIFY,

    /** Custom message with user-defined MTI. */
    CUSTOM;

    /**
     * Get all type names as String array for JMeter GUI TAGS.
     */
    public static String[] names() {
        FiscMessageType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Parse type from String, returning ECHO_TEST as default if invalid.
     */
    public static FiscMessageType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return ECHO_TEST;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ECHO_TEST;
        }
    }
}
