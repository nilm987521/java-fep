package com.fep.jmeter.sampler;

/**
 * Active message types for FISC Dual-Channel Server Simulator.
 *
 * <p>Defines the types of proactive messages that can be sent in ACTIVE or BIDIRECTIONAL mode:
 * <ul>
 *   <li>{@link #SIGN_ON} - Sign-on request (MTI 0800, F70=001)</li>
 *   <li>{@link #SIGN_OFF} - Sign-off request (MTI 0800, F70=002)</li>
 *   <li>{@link #ECHO_TEST} - Echo test (MTI 0800, F70=301)</li>
 *   <li>{@link #KEY_EXCHANGE} - Key exchange notification (MTI 0800, F70=161)</li>
 *   <li>{@link #CUSTOM} - Custom message with user-defined MTI and fields</li>
 * </ul>
 */
public enum ActiveMessageType {

    /**
     * Sign-on request.
     *
     * <p>Sends MTI 0800 with F70=001 to initiate a session.
     */
    SIGN_ON,

    /**
     * Sign-off request.
     *
     * <p>Sends MTI 0800 with F70=002 to terminate a session.
     */
    SIGN_OFF,

    /**
     * Echo test.
     *
     * <p>Sends MTI 0800 with F70=301 for connectivity testing.
     */
    ECHO_TEST,

    /**
     * Key exchange notification.
     *
     * <p>Sends MTI 0800 with F70=161 to notify key change.
     */
    KEY_EXCHANGE,

    /**
     * Custom message.
     *
     * <p>Sends a user-defined message with custom MTI and fields.
     */
    CUSTOM;

    /**
     * Get all type names as String array for JMeter GUI TAGS.
     *
     * @return array of type names in enum declaration order
     */
    public static String[] names() {
        ActiveMessageType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Parse type from String, returning ECHO_TEST as default if invalid.
     *
     * @param value the string value to parse
     * @return the corresponding ActiveMessageType, or ECHO_TEST if null/empty/invalid
     */
    public static ActiveMessageType fromString(String value) {
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
