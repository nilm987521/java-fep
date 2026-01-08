package com.fep.security.hsm;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Response from HSM.
 */
@Data
@Builder
public class HsmResponse {

    /** Whether the operation was successful */
    private boolean success;

    /** Response code from HSM */
    private String responseCode;

    /** Error message if failed */
    private String errorMessage;

    /** Response data */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /** Request ID for correlation */
    private String requestId;

    /** Processing time in milliseconds */
    private long processingTimeMs;

    /**
     * Creates a successful response.
     */
    public static HsmResponse success(String requestId) {
        return HsmResponse.builder()
                .success(true)
                .responseCode("00")
                .requestId(requestId)
                .build();
    }

    /**
     * Creates a failed response.
     */
    public static HsmResponse failure(String requestId, String errorCode, String errorMessage) {
        return HsmResponse.builder()
                .success(false)
                .responseCode(errorCode)
                .errorMessage(errorMessage)
                .requestId(requestId)
                .build();
    }

    /**
     * Adds response data.
     */
    public HsmResponse addData(String key, Object value) {
        data.put(key, value);
        return this;
    }

    /**
     * Gets response data.
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) data.get(key);
    }

    /**
     * Gets response data with default value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, T defaultValue) {
        Object value = data.get(key);
        return value != null ? (T) value : defaultValue;
    }
}
