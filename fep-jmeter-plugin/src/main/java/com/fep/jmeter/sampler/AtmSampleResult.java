package com.fep.jmeter.sampler;

import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.parser.GenericMessageParser;
import com.fep.message.generic.schema.MessageSchema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.samplers.SampleResult;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * Custom SampleResult for ATM transactions with lazy request/response formatting.
 *
 * <p>Request formatting and response parsing are deferred until actually needed
 * (e.g., for display or assertion), improving TPS measurement accuracy.
 *
 * <p>Benefits:
 * <ul>
 *   <li>More accurate response time measurement (network round-trip only)</li>
 *   <li>Higher TPS (formatting/parsing doesn't block the sample loop)</li>
 *   <li>Lazy evaluation - processing only happens when needed</li>
 * </ul>
 */
@Slf4j
public class AtmSampleResult extends SampleResult {

    private static final long serialVersionUID = 1L;
    private static final HexFormat hexFormat = HexFormat.of();
    private static final GenericMessageParser parser = new GenericMessageParser();

    // ==================== Request (Lazy Formatting) ====================

    // Raw request data
    @Getter
    private byte[] rawRequestBytes;

    // Request message (already assembled, just need formatting)
    @Getter @Setter
    private transient GenericMessage requestMessage;

    // Formatted request (lazy initialized)
    private transient String formattedRequest;

    // ==================== Response (Lazy Parsing) ====================

    // Raw response data (not parsed yet)
    @Getter
    private byte[] rawResponseBytes;

    // Schema for parsing response
    @Getter @Setter
    private MessageSchema responseSchema;

    // Parsed response (lazy initialized)
    private transient GenericMessage parsedResponse;
    private transient boolean responseParsed = false;
    private transient String formattedResponse;

    // Response code (extracted after parsing)
    private String extractedResponseCode;

    public AtmSampleResult() {
        super();
    }

    // ==================== Request Methods ====================

    /**
     * Set the raw request bytes and message (without formatting).
     * Formatting is deferred until getSamplerData() is called.
     */
    public void setRawRequestBytes(byte[] bytes, GenericMessage message) {
        this.rawRequestBytes = bytes;
        this.requestMessage = message;
        this.formattedRequest = null;
    }

    /**
     * Override to provide lazy-formatted request data.
     */
    @Override
    public String getSamplerData() {
        if (formattedRequest != null) {
            return formattedRequest;
        }

        if (rawRequestBytes == null || requestMessage == null) {
            return super.getSamplerData();
        }

        // Format request lazily
        formattedRequest = formatRequest();
        return formattedRequest;
    }

    /**
     * Format the request for display.
     */
    private String formatRequest() {
        StringBuilder sb = new StringBuilder();
        MessageSchema schema = requestMessage.getSchema();

        sb.append("=== ATM Request (Generic Schema) ===\n");
        sb.append("Schema: ").append(schema.getName()).append("\n");
        sb.append("Length: ").append(rawRequestBytes.length).append(" bytes\n\n");

        // Display header configuration
        MessageSchema.HeaderSection header = schema.getHeader();
        if (header != null) {
            sb.append("Header Configuration:\n");
            sb.append("  Include Length: ").append(header.isIncludeLength()).append("\n");
            if (header.isIncludeLength()) {
                sb.append("  Length Bytes: ").append(header.getLengthBytes()).append("\n");
                sb.append("  Length Encoding: ").append(header.getLengthEncoding()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Fields:\n");
        sb.append(requestMessage.toString(true)).append("\n\n");

        sb.append("Hex:\n");
        sb.append(hexFormat.formatHex(rawRequestBytes)).append("\n");

        return sb.toString();
    }

    // ==================== Response Methods ====================

    /**
     * Set the raw response bytes (without parsing).
     * This is called immediately when response is received.
     */
    public void setRawResponseBytes(byte[] bytes) {
        this.rawResponseBytes = bytes;
        this.responseParsed = false;
        this.parsedResponse = null;
        this.formattedResponse = null;
        this.extractedResponseCode = null;
    }

    /**
     * Get the parsed response message (lazy parsing).
     * Parsing only happens on first call.
     */
    public GenericMessage getParsedResponse() {
        if (!responseParsed && rawResponseBytes != null && responseSchema != null) {
            parseResponse();
        }
        return parsedResponse;
    }

    /**
     * Parse the response (called lazily).
     */
    private synchronized void parseResponse() {
        if (responseParsed) {
            return;
        }

        try {
            // Note: skipLengthField=true because decoder already stripped it
            parsedResponse = parser.parse(rawResponseBytes, responseSchema, true);
            extractedResponseCode = extractResponseCode(parsedResponse);
            responseParsed = true;
        } catch (Exception e) {
            log.warn("Failed to parse response: {}", e.getMessage());
            responseParsed = true; // Mark as parsed to avoid retry
        }
    }

    /**
     * Get response code (triggers lazy parsing if needed).
     */
    public String getExtractedResponseCode() {
        if (extractedResponseCode == null && !responseParsed) {
            getParsedResponse(); // Trigger parsing
        }
        return extractedResponseCode;
    }

    /**
     * Override to provide lazy-formatted response data.
     */
    @Override
    public String getResponseDataAsString() {
        if (formattedResponse != null) {
            return formattedResponse;
        }

        if (rawResponseBytes == null) {
            return super.getResponseDataAsString();
        }

        // Format response (triggers parsing)
        formattedResponse = formatResponse();
        return formattedResponse;
    }

    /**
     * Format the response for display.
     */
    private String formatResponse() {
        StringBuilder sb = new StringBuilder();

        GenericMessage response = getParsedResponse();
        if (response != null) {
            sb.append("=== ATM Response (Generic Schema) ===\n");
            sb.append("Schema: ").append(responseSchema.getName()).append("\n");
            sb.append("Length: ").append(rawResponseBytes.length).append(" bytes\n\n");

            sb.append("Fields:\n");
            sb.append(response.toString(true)).append("\n\n");
        } else {
            sb.append("=== ATM Response (Raw) ===\n");
            sb.append("Length: ").append(rawResponseBytes.length).append(" bytes\n");
            sb.append("(Failed to parse response)\n\n");
        }

        sb.append("Hex:\n");
        sb.append(hexFormat.formatHex(rawResponseBytes)).append("\n");

        return sb.toString();
    }

    /**
     * Extract response code from parsed message.
     */
    private String extractResponseCode(GenericMessage message) {
        if (message == null) {
            return null;
        }

        // Try common response code field names
        String[] responseCodeFields = {"responseCode", "response_code", "respCode", "rc", "39"};
        for (String fieldId : responseCodeFields) {
            String value = message.getFieldAsString(fieldId);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Determine success based on response code.
     * Override to use lazy-extracted response code.
     */
    public boolean isResponseSuccess() {
        String rc = getExtractedResponseCode();
        return rc == null || "00".equals(rc) || "000".equals(rc);
    }

    /**
     * Get response message based on response code.
     */
    public String getResponseMessage() {
        String responseCode = getExtractedResponseCode();
        if (responseCode == null) return "OK";

        return switch (responseCode) {
            case "00" -> "Approved";
            case "01" -> "Refer to card issuer";
            case "05" -> "Do not honor";
            case "12" -> "Invalid transaction";
            case "13" -> "Invalid amount";
            case "14" -> "Invalid card number";
            case "51" -> "Insufficient funds";
            case "54" -> "Expired card";
            case "55" -> "Incorrect PIN";
            case "61" -> "Exceeds withdrawal amount limit";
            case "91" -> "Issuer or switch is inoperative";
            case "96" -> "System malfunction";
            default -> "Response: " + responseCode;
        };
    }

    /**
     * Store response variables in JMeter context.
     * Called when variables are actually needed.
     */
    public void storeResponseVariables(org.apache.jmeter.threads.JMeterVariables vars) {
        if (vars == null) return;

        GenericMessage response = getParsedResponse();
        if (response == null) return;

        for (Map.Entry<String, Object> entry : response.getAllFields().entrySet()) {
            String key = "RESPONSE_" + entry.getKey();
            vars.put(key, entry.getValue() != null ? entry.getValue().toString() : "");
        }

        // Store raw bytes for assertion
        vars.putObject("RESPONSE_RAW_BYTES", rawResponseBytes);
        if (responseSchema != null) {
            vars.put("RESPONSE_SCHEMA_NAME", responseSchema.getName());
        }
    }
}
