package com.fep.message.iso8583;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * ISO 8583 Message Type Indicator (MTI) definitions.
 *
 * MTI format: VMCC
 * - V: Version (0=1987, 1=1993, 2=2003)
 * - M: Message class
 * - C1: Message function
 * - C2: Message origin
 */
@Getter
@RequiredArgsConstructor
public enum MessageType {

    // Authorization messages
    AUTH_REQUEST("0100", "Authorization Request"),
    AUTH_RESPONSE("0110", "Authorization Response"),
    AUTH_ADVICE("0120", "Authorization Advice"),
    AUTH_ADVICE_RESPONSE("0130", "Authorization Advice Response"),

    // Financial messages
    FINANCIAL_REQUEST("0200", "Financial Request"),
    FINANCIAL_RESPONSE("0210", "Financial Response"),
    FINANCIAL_ADVICE("0220", "Financial Advice"),
    FINANCIAL_ADVICE_RESPONSE("0230", "Financial Advice Response"),

    // File action messages
    FILE_ACTION_REQUEST("0300", "File Action Request"),
    FILE_ACTION_RESPONSE("0310", "File Action Response"),

    // Reversal messages
    REVERSAL_REQUEST("0400", "Reversal Request"),
    REVERSAL_RESPONSE("0410", "Reversal Response"),
    REVERSAL_ADVICE("0420", "Reversal Advice"),
    REVERSAL_ADVICE_RESPONSE("0430", "Reversal Advice Response"),

    // Reconciliation messages
    RECONCILIATION_REQUEST("0500", "Reconciliation Request"),
    RECONCILIATION_RESPONSE("0510", "Reconciliation Response"),

    // Administrative messages
    ADMIN_REQUEST("0600", "Administrative Request"),
    ADMIN_RESPONSE("0610", "Administrative Response"),

    // Fee collection messages
    FEE_COLLECTION_REQUEST("0700", "Fee Collection Request"),
    FEE_COLLECTION_RESPONSE("0710", "Fee Collection Response"),

    // Network management messages
    NETWORK_MANAGEMENT_REQUEST("0800", "Network Management Request"),
    NETWORK_MANAGEMENT_RESPONSE("0810", "Network Management Response");

    private final String code;
    private final String description;

    private static final Map<String, MessageType> CODE_MAP = new HashMap<>();

    static {
        for (MessageType type : values()) {
            CODE_MAP.put(type.code, type);
        }
    }

    /**
     * Gets the MessageType from MTI code.
     *
     * @param code the 4-digit MTI code
     * @return the MessageType, or null if not found
     */
    public static MessageType fromCode(String code) {
        return CODE_MAP.get(code);
    }

    /**
     * Checks if this message type is a request.
     *
     * @return true if request message
     */
    public boolean isRequest() {
        char functionCode = code.charAt(2);
        return functionCode == '0' || functionCode == '2';
    }

    /**
     * Checks if this message type is a response.
     *
     * @return true if response message
     */
    public boolean isResponse() {
        char functionCode = code.charAt(2);
        return functionCode == '1' || functionCode == '3';
    }

    /**
     * Gets the corresponding response type for a request.
     *
     * @return the response MessageType, or null if this is not a request
     */
    public MessageType getResponseType() {
        if (!isRequest()) {
            return null;
        }
        String responseCode = code.substring(0, 2) +
            (char) (code.charAt(2) + 1) + code.charAt(3);
        return fromCode(responseCode);
    }
}
