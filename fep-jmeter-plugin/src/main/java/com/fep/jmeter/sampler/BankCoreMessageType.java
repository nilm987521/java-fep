package com.fep.jmeter.sampler;

/**
 * Message types for Bank Core Message Sender Sampler.
 *
 * <p>Defines the types of proactive messages that can be sent from bank core system.
 */
public enum BankCoreMessageType {

    /** Network management echo test. */
    ECHO_TEST,

    /** Settlement/Reconciliation notification. */
    RECONCILIATION_NOTIFY,

    /** Account balance/status update. */
    ACCOUNT_UPDATE,

    /** Core system status notification. */
    SYSTEM_STATUS,

    /** Sign-on notification. */
    SIGN_ON_NOTIFY,

    /** Sign-off notification. */
    SIGN_OFF_NOTIFY,

    /** Custom message with user-defined format. */
    CUSTOM;

    /**
     * Get all type names as String array for JMeter GUI TAGS.
     */
    public static String[] names() {
        BankCoreMessageType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Parse type from String, returning ECHO_TEST as default if invalid.
     */
    public static BankCoreMessageType fromString(String value) {
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
