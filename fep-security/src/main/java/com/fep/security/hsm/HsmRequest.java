package com.fep.security.hsm;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Request to be sent to HSM.
 */
@Data
@Builder
public class HsmRequest {

    /** HSM command */
    private HsmCommand command;

    /** Request parameters */
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    /** Request timeout in milliseconds */
    @Builder.Default
    private long timeoutMs = 5000;

    /** Request ID for tracking */
    private String requestId;

    /**
     * Adds a parameter.
     */
    public HsmRequest addParameter(String key, Object value) {
        parameters.put(key, value);
        return this;
    }

    /**
     * Gets a parameter value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key) {
        return (T) parameters.get(key);
    }

    /**
     * Gets a parameter with default value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        Object value = parameters.get(key);
        return value != null ? (T) value : defaultValue;
    }
}
