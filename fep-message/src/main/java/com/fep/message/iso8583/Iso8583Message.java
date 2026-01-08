package com.fep.message.iso8583;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents an ISO 8583 financial transaction message.
 * This is the core class for message handling in the FEP system.
 *
 * <p>An ISO 8583 message consists of:
 * <ul>
 *   <li>Message Type Indicator (MTI) - 4 digits identifying the message type</li>
 *   <li>Primary Bitmap - indicates which data elements (1-64) are present</li>
 *   <li>Secondary Bitmap - indicates which data elements (65-128) are present (optional)</li>
 *   <li>Data Elements - the actual transaction data fields</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
 * message.setField(2, "4111111111111111");  // PAN
 * message.setField(3, "000000");            // Processing Code
 * message.setField(4, "000000010000");      // Amount
 * }</pre>
 */
@Slf4j
@Getter
@Setter
public class Iso8583Message {

    /** Maximum number of fields in ISO 8583 (including secondary bitmap) */
    public static final int MAX_FIELDS = 128;

    /** Message Type Indicator */
    private String mti;

    /** Message Type enum (if available) */
    private MessageType messageType;

    /** Primary bitmap (fields 1-64) */
    private byte[] primaryBitmap;

    /** Secondary bitmap (fields 65-128), present if field 1 is set in primary bitmap */
    private byte[] secondaryBitmap;

    /** Data elements storage - using LinkedHashMap to maintain insertion order */
    private final Map<Integer, Object> fields = new LinkedHashMap<>();

    /** Raw message bytes for logging/debugging */
    private byte[] rawData;

    /** Message creation timestamp */
    private final long timestamp;

    /** Optional trace identifier for logging */
    private String traceId;

    /**
     * Creates an empty ISO 8583 message.
     */
    public Iso8583Message() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Creates an ISO 8583 message with the specified MTI.
     *
     * @param mti the Message Type Indicator (4 digits)
     */
    public Iso8583Message(String mti) {
        this();
        this.mti = mti;
        this.messageType = MessageType.fromCode(mti);
    }

    /**
     * Creates an ISO 8583 message with the specified message type.
     *
     * @param messageType the message type
     */
    public Iso8583Message(MessageType messageType) {
        this();
        this.messageType = messageType;
        this.mti = messageType.getCode();
    }

    /**
     * Sets a field value.
     *
     * @param fieldNumber the field number (1-128)
     * @param value the field value
     * @throws IllegalArgumentException if field number is out of range
     */
    public void setField(int fieldNumber, Object value) {
        validateFieldNumber(fieldNumber);
        if (value != null) {
            fields.put(fieldNumber, value);
            log.trace("Set field {}: {}", fieldNumber, maskSensitiveData(fieldNumber, value));
        } else {
            fields.remove(fieldNumber);
        }
    }

    /**
     * Gets a field value.
     *
     * @param fieldNumber the field number (1-128)
     * @return the field value, or null if not present
     */
    public Object getField(int fieldNumber) {
        validateFieldNumber(fieldNumber);
        return fields.get(fieldNumber);
    }

    /**
     * Gets a field value as String.
     *
     * @param fieldNumber the field number (1-128)
     * @return the field value as String, or null if not present
     */
    public String getFieldAsString(int fieldNumber) {
        Object value = getField(fieldNumber);
        return value != null ? value.toString() : null;
    }

    /**
     * Checks if a field is present.
     *
     * @param fieldNumber the field number (1-128)
     * @return true if the field is present
     */
    public boolean hasField(int fieldNumber) {
        return fields.containsKey(fieldNumber);
    }

    /**
     * Removes a field.
     *
     * @param fieldNumber the field number (1-128)
     * @return the previous value, or null if not present
     */
    public Object removeField(int fieldNumber) {
        validateFieldNumber(fieldNumber);
        return fields.remove(fieldNumber);
    }

    /**
     * Gets all present field numbers.
     *
     * @return set of field numbers
     */
    public Set<Integer> getFieldNumbers() {
        return fields.keySet();
    }

    /**
     * Checks if secondary bitmap is needed (any field 65-128 is present).
     *
     * @return true if secondary bitmap is needed
     */
    public boolean hasSecondaryBitmap() {
        return fields.keySet().stream().anyMatch(f -> f > 64);
    }

    /**
     * Clears all fields.
     */
    public void clear() {
        fields.clear();
        primaryBitmap = null;
        secondaryBitmap = null;
    }

    /**
     * Creates a response message for this request.
     *
     * @return a new response message with common fields copied
     */
    public Iso8583Message createResponse() {
        if (messageType == null || !messageType.isRequest()) {
            throw new IllegalStateException("Cannot create response for non-request message");
        }

        Iso8583Message response = new Iso8583Message(messageType.getResponseType());
        response.setTraceId(this.traceId);

        // Copy common fields to response
        // Field 2: Primary Account Number
        if (hasField(2)) response.setField(2, getField(2));
        // Field 3: Processing Code
        if (hasField(3)) response.setField(3, getField(3));
        // Field 4: Transaction Amount
        if (hasField(4)) response.setField(4, getField(4));
        // Field 7: Transmission Date and Time
        if (hasField(7)) response.setField(7, getField(7));
        // Field 11: System Trace Audit Number (STAN)
        if (hasField(11)) response.setField(11, getField(11));
        // Field 12: Local Transaction Time
        if (hasField(12)) response.setField(12, getField(12));
        // Field 13: Local Transaction Date
        if (hasField(13)) response.setField(13, getField(13));
        // Field 32: Acquiring Institution ID
        if (hasField(32)) response.setField(32, getField(32));
        // Field 37: Retrieval Reference Number
        if (hasField(37)) response.setField(37, getField(37));
        // Field 41: Card Acceptor Terminal ID
        if (hasField(41)) response.setField(41, getField(41));
        // Field 42: Card Acceptor ID Code
        if (hasField(42)) response.setField(42, getField(42));

        return response;
    }

    private void validateFieldNumber(int fieldNumber) {
        if (fieldNumber < 1 || fieldNumber > MAX_FIELDS) {
            throw new IllegalArgumentException(
                "Field number must be between 1 and " + MAX_FIELDS + ": " + fieldNumber);
        }
    }

    private String maskSensitiveData(int fieldNumber, Object value) {
        if (value == null) return "null";
        String strValue = value.toString();

        // Mask sensitive fields
        return switch (fieldNumber) {
            case 2 -> maskPan(strValue);           // PAN
            case 14 -> "****";                      // Expiration Date
            case 35 -> "****";                      // Track 2 Data
            case 36 -> "****";                      // Track 3 Data
            case 52 -> "****";                      // PIN Data
            case 55 -> "****";                      // EMV Data
            default -> strValue;
        };
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "****";
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }

    @Override
    public String toString() {
        return String.format("Iso8583Message{mti='%s', fields=%d, traceId='%s'}",
            mti, fields.size(), traceId);
    }

    /**
     * Returns a detailed string representation for debugging.
     *
     * @return detailed message content
     */
    public String toDetailString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ISO8583 Message:\n");
        sb.append("  MTI: ").append(mti).append("\n");
        sb.append("  Type: ").append(messageType != null ? messageType.getDescription() : "Unknown").append("\n");
        sb.append("  Fields:\n");
        fields.forEach((fieldNum, value) ->
            sb.append("    F").append(String.format("%03d", fieldNum))
              .append(": ").append(maskSensitiveData(fieldNum, value)).append("\n"));
        return sb.toString();
    }
}
