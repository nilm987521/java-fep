package com.fep.jmeter.sampler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * ATM transaction flow types.
 *
 * <p>Defines how the ATM simulator handles the transaction lifecycle:
 * <ul>
 *   <li>SINGLE_REQUEST: Send one request, wait for one response (default)</li>
 *   <li>FULL_TRANSACTION: Send request (0200), wait for response (0210),
 *       then send confirm (0220) without waiting for response</li>
 *   <li>CONFIRM_ONLY: Send only confirm message using stored variables</li>
 * </ul>
 */
public enum TransactionFlow {

    /**
     * Single request mode - send one message, wait for one response.
     * This is the default behavior for simple testing.
     */
    SINGLE_REQUEST("Single Request", "發送單一請求並等待回應"),

    /**
     * Full transaction mode - complete ATM transaction flow:
     * 1. Send 0200 request
     * 2. Wait for 0210 response
     * 3. If approved, send 0220 confirm (no response expected)
     */
    FULL_TRANSACTION("Full Transaction", "完整交易流程 (請求 → 回應 → 確認)"),

    /**
     * Confirm only mode - send only 0220 confirm message.
     * Uses JMeter variables from previous request/response.
     */
    CONFIRM_ONLY("Confirm Only", "僅發送確認訊息 (使用已存變數)");

    private final String displayName;
    private final String description;

    TransactionFlow(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get all display names for GUI combo box.
     */
    public static String[] names() {
        TransactionFlow[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Get all display names for GUI combo box.
     */
    public static String[] displayNames() {
        TransactionFlow[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }

    /**
     * Parse from string (name or display name).
     */
    @JsonCreator
    public static TransactionFlow fromString(String value) {
        if (value == null || value.isBlank()) {
            return SINGLE_REQUEST;
        }

        // Try enum name first
        for (TransactionFlow flow : values()) {
            if (flow.name().equalsIgnoreCase(value) ||
                flow.displayName.equalsIgnoreCase(value)) {
                return flow;
            }
        }

        return SINGLE_REQUEST;
    }
}
