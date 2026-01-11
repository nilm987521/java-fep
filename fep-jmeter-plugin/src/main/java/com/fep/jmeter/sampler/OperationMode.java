package com.fep.jmeter.sampler;

/**
 * Operation modes for FISC Dual-Channel Server Simulator.
 *
 * <p>Defines the communication modes for the server simulator:
 * <ul>
 *   <li>{@link #PASSIVE} - Wait for incoming messages and respond</li>
 *   <li>{@link #ACTIVE} - Send proactive messages to clients</li>
 *   <li>{@link #BIDIRECTIONAL} - Simultaneously send and receive messages</li>
 * </ul>
 */
public enum OperationMode {

    /**
     * Wait for incoming messages and respond.
     *
     * <p>In this mode, the server waits for client requests and
     * generates appropriate responses based on configured rules.
     */
    PASSIVE,

    /**
     * Send proactive messages to clients.
     *
     * <p>In this mode, the server actively sends messages to connected
     * clients, such as sign-on, echo test, or key exchange notifications.
     */
    ACTIVE,

    /**
     * Simultaneously send and receive messages.
     *
     * <p>In this mode, the server handles both incoming requests and
     * proactively sends messages, simulating full bidirectional communication.
     */
    BIDIRECTIONAL;

    /**
     * Get all mode names as String array for JMeter GUI TAGS.
     *
     * @return array of mode names in enum declaration order
     */
    public static String[] names() {
        OperationMode[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Parse mode from String, returning PASSIVE as default if invalid.
     *
     * @param value the string value to parse
     * @return the corresponding OperationMode, or PASSIVE if null/empty/invalid
     */
    public static OperationMode fromString(String value) {
        if (value == null || value.isEmpty()) {
            return PASSIVE;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PASSIVE;
        }
    }
}
