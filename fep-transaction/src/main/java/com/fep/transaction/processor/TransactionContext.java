package com.fep.transaction.processor;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Transaction processing context.
 * Holds runtime information during transaction processing.
 */
@Data
@Builder
public class TransactionContext {

    /** Unique context ID */
    private String contextId;

    /** Processing start time */
    @Builder.Default
    private LocalDateTime startTime = LocalDateTime.now();

    /** Source channel (ATM, POS, MOBILE, etc.) */
    private String channel;

    /** Source IP address */
    private String sourceIp;

    /** Terminal ID */
    private String terminalId;

    /** Acquiring bank code */
    private String acquiringBankCode;

    /** Whether PIN was verified */
    @Builder.Default
    private boolean pinVerified = false;

    /** Whether MAC was verified */
    @Builder.Default
    private boolean macVerified = false;

    /** Original ISO 8583 message (for reference) */
    private Object originalMessage;

    /** Custom attributes for extensibility */
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * Sets a custom attribute.
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Gets a custom attribute.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Gets a custom attribute with default value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        Object value = attributes.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Calculates elapsed time in milliseconds since start.
     */
    public long getElapsedTimeMs() {
        return java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
    }
}
