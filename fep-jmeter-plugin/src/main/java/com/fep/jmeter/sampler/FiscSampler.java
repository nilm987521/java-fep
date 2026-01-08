package com.fep.jmeter.sampler;

import com.fep.communication.client.FiscClient;
import com.fep.communication.config.FiscConnectionConfig;
import com.fep.jmeter.config.FiscConfigElement;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.iso8583.MessageType;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * JMeter Sampler for sending FISC ISO 8583 messages.
 *
 * <p>This sampler allows JMeter to send financial transactions to FISC
 * using the FEP communication module. It supports various transaction types:
 * <ul>
 *   <li>Withdrawal (提款)</li>
 *   <li>Transfer (轉帳)</li>
 *   <li>Balance Inquiry (餘額查詢)</li>
 *   <li>Bill Payment (繳費)</li>
 *   <li>Network Management (Sign-on/Sign-off/Echo)</li>
 * </ul>
 *
 * <p>The sampler uses connection pooling to efficiently manage FISC connections
 * across multiple threads.
 */
public class FiscSampler extends AbstractSampler implements TestBean, TestStateListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(FiscSampler.class);

    // Property names for TestBean
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String CONNECTION_TIMEOUT = "connectionTimeout";
    public static final String READ_TIMEOUT = "readTimeout";
    public static final String TRANSACTION_TYPE = "transactionType";
    public static final String PAN = "pan";
    public static final String PROCESSING_CODE = "processingCode";
    public static final String AMOUNT = "amount";
    public static final String INSTITUTION_ID = "institutionId";
    public static final String TERMINAL_ID = "terminalId";
    public static final String CUSTOM_FIELDS = "customFields";

    // Transaction type constants
    public static final String TXN_WITHDRAWAL = "WITHDRAWAL";
    public static final String TXN_TRANSFER = "TRANSFER";
    public static final String TXN_BALANCE_INQUIRY = "BALANCE_INQUIRY";
    public static final String TXN_BILL_PAYMENT = "BILL_PAYMENT";
    public static final String TXN_SIGN_ON = "SIGN_ON";
    public static final String TXN_SIGN_OFF = "SIGN_OFF";
    public static final String TXN_ECHO_TEST = "ECHO_TEST";

    // Thread-local client management
    private static final Map<String, FiscClient> clientPool = new ConcurrentHashMap<>();
    private static final Iso8583MessageFactory messageFactory = new Iso8583MessageFactory();

    public FiscSampler() {
        super();
        setName("FISC Sampler");
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);

        String host = getHost();
        int port = getPort();
        String transactionType = getTransactionType();

        try {
            // Get or create client for this thread
            FiscClient client = getOrCreateClient(host, port);

            // Build the ISO 8583 message
            Iso8583Message request = buildMessage(transactionType);
            String requestStr = formatMessageForDisplay(request);
            result.setSamplerData(requestStr);

            // Start timing
            result.sampleStart();

            // Send and receive
            Iso8583Message response = client.sendAndReceive(request, getReadTimeout())
                .get(getReadTimeout() + 5000, TimeUnit.MILLISECONDS);

            // Stop timing
            result.sampleEnd();

            // Process response
            String responseCode = response.getFieldAsString(39);
            String responseStr = formatMessageForDisplay(response);
            result.setResponseData(responseStr, StandardCharsets.UTF_8.name());
            result.setResponseCode(responseCode != null ? responseCode : "N/A");
            result.setResponseMessage(getResponseMessage(responseCode));

            // Check if successful (response code 00 = approved)
            boolean success = "00".equals(responseCode);
            result.setSuccessful(success);

            // Store response fields in JMeter variables for later use
            storeResponseVariables(response);

            if (success) {
                log.debug("FISC transaction successful: {} -> {}", transactionType, responseCode);
            } else {
                log.warn("FISC transaction failed: {} -> {}", transactionType, responseCode);
            }

        } catch (Exception e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("ERROR");
            result.setResponseMessage(e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            log.error("FISC sampler error", e);
        }

        return result;
    }

    /**
     * Gets or creates a FiscClient for the current thread.
     */
    private FiscClient getOrCreateClient(String host, int port) throws Exception {
        String key = Thread.currentThread().getName() + "_" + host + "_" + port;

        return clientPool.computeIfAbsent(key, k -> {
            FiscConnectionConfig config = FiscConnectionConfig.builder()
                .primaryHost(host)
                .primaryPort(port)
                .connectTimeoutMs(getConnectionTimeout())
                .readTimeoutMs(getReadTimeout())
                .connectionName("JMeter-" + Thread.currentThread().getName())
                .institutionId(getInstitutionId())
                .autoReconnect(true)
                .build();

            FiscClient client = new FiscClient(config);
            try {
                client.connect().get(config.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
                log.info("Connected to FISC at {}:{}", host, port);
            } catch (Exception e) {
                throw new RuntimeException("Failed to connect to FISC: " + e.getMessage(), e);
            }
            return client;
        });
    }

    /**
     * Builds an ISO 8583 message based on transaction type.
     */
    private Iso8583Message buildMessage(String transactionType) {
        Iso8583Message message;

        switch (transactionType) {
            case TXN_SIGN_ON:
                return messageFactory.createSignOnMessage();
            case TXN_SIGN_OFF:
                return messageFactory.createSignOffMessage();
            case TXN_ECHO_TEST:
                return messageFactory.createEchoTestMessage();
            default:
                message = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
                break;
        }

        // Set common fields
        messageFactory.setTransactionFields(message);

        // Set PAN (Field 2)
        String pan = getPan();
        if (pan != null && !pan.isEmpty()) {
            message.setField(2, pan);
        }

        // Set Processing Code (Field 3) based on transaction type
        String processingCode = getProcessingCodeForType(transactionType);
        message.setField(3, processingCode);

        // Set Amount (Field 4)
        String amount = getAmount();
        if (amount != null && !amount.isEmpty()) {
            // Ensure 12-digit format with leading zeros
            String paddedAmount = String.format("%012d", Long.parseLong(amount.replaceAll("[^0-9]", "")));
            message.setField(4, paddedAmount);
        }

        // Set Terminal ID (Field 41)
        String terminalId = getTerminalId();
        if (terminalId != null && !terminalId.isEmpty()) {
            message.setField(41, terminalId);
        }

        // Set Institution ID (Field 32)
        String institutionId = getInstitutionId();
        if (institutionId != null && !institutionId.isEmpty()) {
            message.setField(32, institutionId);
        }

        // Apply custom fields
        applyCustomFields(message);

        return message;
    }

    /**
     * Gets the processing code for the transaction type.
     */
    private String getProcessingCodeForType(String transactionType) {
        // Check if user specified a custom processing code
        String customCode = getProcessingCode();
        if (customCode != null && !customCode.isEmpty()) {
            return customCode;
        }

        // Default processing codes
        return switch (transactionType) {
            case TXN_WITHDRAWAL -> "010000";        // Cash withdrawal
            case TXN_TRANSFER -> "400000";          // Transfer
            case TXN_BALANCE_INQUIRY -> "310000";   // Balance inquiry
            case TXN_BILL_PAYMENT -> "500000";      // Bill payment
            default -> "000000";
        };
    }

    /**
     * Applies custom fields from the configuration.
     */
    private void applyCustomFields(Iso8583Message message) {
        String customFieldsStr = getCustomFields();
        if (customFieldsStr == null || customFieldsStr.isEmpty()) {
            return;
        }

        // Parse format: "field:value;field:value"
        String[] pairs = customFieldsStr.split(";");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                try {
                    int fieldNum = Integer.parseInt(kv[0].trim());
                    String value = kv[1].trim();
                    // Support JMeter variable substitution
                    value = substituteVariables(value);
                    message.setField(fieldNum, value);
                } catch (NumberFormatException e) {
                    log.warn("Invalid field number in custom fields: {}", kv[0]);
                }
            }
        }
    }

    /**
     * Substitutes JMeter variables in the value.
     */
    private String substituteVariables(String value) {
        JMeterContext context = JMeterContextService.getContext();
        if (context != null) {
            JMeterVariables vars = context.getVariables();
            if (vars != null) {
                // Simple variable substitution: ${varName}
                while (value.contains("${")) {
                    int start = value.indexOf("${");
                    int end = value.indexOf("}", start);
                    if (end > start) {
                        String varName = value.substring(start + 2, end);
                        String varValue = vars.get(varName);
                        if (varValue != null) {
                            value = value.substring(0, start) + varValue + value.substring(end + 1);
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }
        return value;
    }

    /**
     * Formats an ISO 8583 message for display.
     */
    private String formatMessageForDisplay(Iso8583Message message) {
        StringBuilder sb = new StringBuilder();
        sb.append("MTI: ").append(message.getMti()).append("\n");
        sb.append("Fields:\n");
        for (Integer fieldNum : message.getFieldNumbers()) {
            Object value = message.getField(fieldNum);
            // Mask sensitive fields
            String displayValue = maskSensitiveField(fieldNum, value);
            sb.append(String.format("  F%03d: %s\n", fieldNum, displayValue));
        }
        return sb.toString();
    }

    /**
     * Masks sensitive field values for display.
     */
    private String maskSensitiveField(int fieldNum, Object value) {
        if (value == null) return "null";
        String strValue = value.toString();
        return switch (fieldNum) {
            case 2 -> maskPan(strValue);     // PAN
            case 14 -> "****";               // Expiration
            case 35, 36 -> "****";           // Track data
            case 52 -> "****";               // PIN
            default -> strValue;
        };
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "****";
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }

    /**
     * Stores response fields in JMeter variables.
     */
    private void storeResponseVariables(Iso8583Message response) {
        JMeterContext context = JMeterContextService.getContext();
        if (context != null) {
            JMeterVariables vars = context.getVariables();
            if (vars != null) {
                vars.put("FISC_MTI", response.getMti());
                vars.put("FISC_RESPONSE_CODE", response.getFieldAsString(39));
                vars.put("FISC_STAN", response.getFieldAsString(11));
                vars.put("FISC_RRN", response.getFieldAsString(37));

                // Store balance if present (field 54)
                String balance = response.getFieldAsString(54);
                if (balance != null) {
                    vars.put("FISC_BALANCE", balance);
                }
            }
        }
    }

    /**
     * Gets human-readable message for response code.
     */
    private String getResponseMessage(String responseCode) {
        if (responseCode == null) return "Unknown";
        return switch (responseCode) {
            case "00" -> "Approved";
            case "01" -> "Refer to card issuer";
            case "03" -> "Invalid merchant";
            case "04" -> "Pick up card";
            case "05" -> "Do not honor";
            case "12" -> "Invalid transaction";
            case "13" -> "Invalid amount";
            case "14" -> "Invalid card number";
            case "30" -> "Format error";
            case "41" -> "Lost card";
            case "43" -> "Stolen card";
            case "51" -> "Insufficient funds";
            case "54" -> "Expired card";
            case "55" -> "Incorrect PIN";
            case "57" -> "Transaction not permitted";
            case "58" -> "Transaction not permitted to terminal";
            case "61" -> "Exceeds withdrawal limit";
            case "65" -> "Exceeds withdrawal frequency";
            case "75" -> "PIN tries exceeded";
            case "91" -> "Issuer unavailable";
            case "96" -> "System malfunction";
            default -> "Unknown response: " + responseCode;
        };
    }

    // TestStateListener implementation
    @Override
    public void testStarted() {
        testStarted("");
    }

    @Override
    public void testStarted(String host) {
        log.info("FISC test started on {}", host);
    }

    @Override
    public void testEnded() {
        testEnded("");
    }

    @Override
    public void testEnded(String host) {
        log.info("FISC test ended on {}, closing connections", host);
        // Close all clients
        clientPool.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing FISC client", e);
            }
        });
        clientPool.clear();
    }

    // Getters and Setters for TestBean properties
    public String getHost() {
        return getPropertyAsString(HOST, "localhost");
    }

    public void setHost(String host) {
        setProperty(HOST, host);
    }

    public int getPort() {
        return getPropertyAsInt(PORT, 9000);
    }

    public void setPort(int port) {
        setProperty(PORT, port);
    }

    public int getConnectionTimeout() {
        return getPropertyAsInt(CONNECTION_TIMEOUT, 10000);
    }

    public void setConnectionTimeout(int timeout) {
        setProperty(CONNECTION_TIMEOUT, timeout);
    }

    public int getReadTimeout() {
        return getPropertyAsInt(READ_TIMEOUT, 30000);
    }

    public void setReadTimeout(int timeout) {
        setProperty(READ_TIMEOUT, timeout);
    }

    public String getTransactionType() {
        return getPropertyAsString(TRANSACTION_TYPE, TXN_ECHO_TEST);
    }

    public void setTransactionType(String type) {
        setProperty(TRANSACTION_TYPE, type);
    }

    public String getPan() {
        return getPropertyAsString(PAN, "");
    }

    public void setPan(String pan) {
        setProperty(PAN, pan);
    }

    public String getProcessingCode() {
        return getPropertyAsString(PROCESSING_CODE, "");
    }

    public void setProcessingCode(String code) {
        setProperty(PROCESSING_CODE, code);
    }

    public String getAmount() {
        return getPropertyAsString(AMOUNT, "");
    }

    public void setAmount(String amount) {
        setProperty(AMOUNT, amount);
    }

    public String getInstitutionId() {
        return getPropertyAsString(INSTITUTION_ID, "");
    }

    public void setInstitutionId(String id) {
        setProperty(INSTITUTION_ID, id);
    }

    public String getTerminalId() {
        return getPropertyAsString(TERMINAL_ID, "");
    }

    public void setTerminalId(String id) {
        setProperty(TERMINAL_ID, id);
    }

    public String getCustomFields() {
        return getPropertyAsString(CUSTOM_FIELDS, "");
    }

    public void setCustomFields(String fields) {
        setProperty(CUSTOM_FIELDS, fields);
    }
}
