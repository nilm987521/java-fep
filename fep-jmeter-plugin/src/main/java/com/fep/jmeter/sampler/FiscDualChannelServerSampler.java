package com.fep.jmeter.sampler;

import com.fep.jmeter.engine.FiscDualChannelSimulatorEngine;
import com.fep.jmeter.validation.MessageValidationEngine;
import com.fep.message.iso8583.Iso8583Message;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * JMeter Sampler for simulating a FISC Dual-Channel Server.
 *
 * <p>This sampler starts a dual-channel TCP server that accurately models
 * FISC's production environment where requests and responses flow through
 * separate TCP connections.
 *
 * <p>Features:
 * <ul>
 *   <li>Dual-channel architecture (Send Port + Receive Port)</li>
 *   <li>Multi-client routing by Bank ID (F32)</li>
 *   <li>Message validation with configurable rules</li>
 *   <li>Response rules based on processing code</li>
 *   <li>Statistics tracking</li>
 * </ul>
 */
public class FiscDualChannelServerSampler extends AbstractSampler implements TestBean, TestStateListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(FiscDualChannelServerSampler.class);

    // Property names for TestBean (from Sampler's perspective)
    public static final String RECEIVE_PORT = "receivePort";  // Sampler RECEIVES requests here
    public static final String SEND_PORT = "sendPort";        // Sampler SENDS responses here
    public static final String SAMPLE_INTERVAL = "sampleInterval";
    public static final String DEFAULT_RESPONSE_CODE = "defaultResponseCode";
    public static final String RESPONSE_DELAY = "responseDelay";
    public static final String BALANCE_AMOUNT = "balanceAmount";
    public static final String ENABLE_VALIDATION = "enableValidation";
    public static final String VALIDATION_RULES = "validationRules";
    public static final String VALIDATION_ERROR_CODE = "validationErrorCode";
    public static final String ENABLE_BANK_ID_ROUTING = "enableBankIdRouting";
    public static final String BANK_ID_FIELD = "bankIdField";
    public static final String RESPONSE_RULES = "responseRules";
    public static final String CUSTOM_RESPONSE_FIELDS = "customResponseFields";

    // Operation mode properties
    public static final String OPERATION_MODE = "operationMode";
    public static final String ACTIVE_MESSAGE_TYPE = "activeMessageType";
    public static final String ACTIVE_TARGET_BANK_ID = "activeTargetBankId";
    public static final String ACTIVE_CUSTOM_MTI = "activeCustomMti";
    public static final String ACTIVE_CUSTOM_FIELDS = "activeCustomFields";

    // Operation mode constants
    public static final String MODE_PASSIVE = "PASSIVE";
    public static final String MODE_ACTIVE = "ACTIVE";
    public static final String MODE_BIDIRECTIONAL = "BIDIRECTIONAL";

    // Active message type constants
    public static final String ACTIVE_TYPE_SIGN_ON = "SIGN_ON";
    public static final String ACTIVE_TYPE_SIGN_OFF = "SIGN_OFF";
    public static final String ACTIVE_TYPE_ECHO_TEST = "ECHO_TEST";
    public static final String ACTIVE_TYPE_KEY_EXCHANGE = "KEY_EXCHANGE";
    public static final String ACTIVE_TYPE_CUSTOM = "CUSTOM";

    // Default values (from Sampler's perspective)
    private static final int DEFAULT_RECEIVE_PORT = 9000;  // Sampler RECEIVES requests here
    private static final int DEFAULT_SEND_PORT = 9001;     // Sampler SENDS responses here
    private static final int DEFAULT_SAMPLE_INTERVAL = 1000;
    private static final String DEFAULT_RESPONSE_CODE_VALUE = "00";
    private static final String DEFAULT_VALIDATION_ERROR_CODE = "30";

    // Server instance management (one server per port pair)
    private static final Map<String, ServerInstance> serverInstances = new ConcurrentHashMap<>();
    private static final Object serverLock = new Object();

    // STAN counter for active message sending
    private static final AtomicInteger stanCounter = new AtomicInteger(0);

    public FiscDualChannelServerSampler() {
        super();
        setName("FISC Dual-Channel Server Simulator");
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);

        int receivePort = getReceivePort();
        int sendPort = getSendPort();
        String instanceKey = receivePort + ":" + sendPort;

        try {
            // Get or create server instance
            ServerInstance instance = getOrCreateServer(instanceKey, receivePort, sendPort);

            // Dispatch based on operation mode
            String mode = getOperationMode();
            switch (mode) {
                case MODE_PASSIVE -> executePassiveMode(result, instance);
                case MODE_ACTIVE -> executeActiveMode(result, instance);
                case MODE_BIDIRECTIONAL -> executeBidirectionalMode(result, instance);
                default -> executePassiveMode(result, instance);
            }

            // Store statistics in JMeter variables
            storeVariables(instance.engine);

        } catch (Exception e) {
            if (!result.isStampedAtStart()) {
                result.sampleStart();
            }
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("ERROR");
            result.setResponseMessage(e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            log.error("FISC dual-channel server sampler error", e);
        }

        return result;
    }

    /**
     * Execute passive mode - wait for incoming messages and respond.
     */
    private void executePassiveMode(SampleResult result, ServerInstance instance) throws Exception {
        result.sampleStart();

        // Block and wait for message from request queue
        int waitTimeout = getSampleInterval();
        if (waitTimeout <= 0) {
            waitTimeout = 30000; // Default 30 seconds
        }

        FiscDualChannelSimulatorEngine.ReceivedRequest received =
            instance.engine.getRequestQueue().poll(waitTimeout, TimeUnit.MILLISECONDS);

        result.sampleEnd();

        if (received != null) {
            // Message received - report message content
            String validationResult = received.validationResult();
            boolean isValid = "PASS".equals(validationResult) || "SKIP".equals(validationResult);

            StringBuilder sb = new StringBuilder();
            sb.append("=== [PASSIVE] Received Message ===\n");
            sb.append("MTI: ").append(received.message().getMti()).append("\n");
            sb.append("Bank ID: ").append(received.bankId()).append("\n");
            sb.append("Timestamp: ").append(received.timestamp()).append("\n");
            sb.append("Validation: ").append(validationResult).append("\n");
            sb.append("\n=== Message Fields ===\n");

            // Output key fields
            String stan = received.message().getFieldAsString(11);
            String processingCode = received.message().getFieldAsString(3);
            String amount = received.message().getFieldAsString(4);
            String rrn = received.message().getFieldAsString(37);

            if (stan != null) sb.append("F11 (STAN): ").append(stan).append("\n");
            if (processingCode != null) sb.append("F3 (Processing Code): ").append(processingCode).append("\n");
            if (amount != null) sb.append("F4 (Amount): ").append(amount).append("\n");
            if (rrn != null) sb.append("F37 (RRN): ").append(rrn).append("\n");

            sb.append("\n=== Server Statistics ===\n");
            sb.append("Messages Received: ").append(instance.engine.getMessagesReceived().get()).append("\n");
            sb.append("Messages Sent: ").append(instance.engine.getMessagesSent().get()).append("\n");
            sb.append("Validation Errors: ").append(instance.engine.getValidationErrors().get()).append("\n");

            result.setResponseData(sb.toString(), StandardCharsets.UTF_8.name());
            result.setResponseCode("200");
            result.setResponseMessage("Message received: MTI=" + received.message().getMti());
            result.setSuccessful(isValid);

            // Store message details in JMeter variables
            storeReceivedMessageVariables(received);
        } else {
            // Timeout - report timeout error
            StringBuilder sb = new StringBuilder();
            sb.append("=== [PASSIVE] Timeout ===\n");
            sb.append("No message received within ").append(waitTimeout).append(" ms\n");
            sb.append("\n=== Server Status ===\n");
            sb.append("Receive Port: ").append(instance.engine.getReceivePort()).append("\n");
            sb.append("Send Port: ").append(instance.engine.getSendPort()).append("\n");
            sb.append("Receive Channel Clients: ").append(instance.engine.getReceiveChannelClientCount().get()).append("\n");
            sb.append("Send Channel Clients: ").append(instance.engine.getSendChannelClientCount().get()).append("\n");
            sb.append("Messages Received: ").append(instance.engine.getMessagesReceived().get()).append("\n");
            sb.append("Messages Sent: ").append(instance.engine.getMessagesSent().get()).append("\n");

            result.setResponseData(sb.toString(), StandardCharsets.UTF_8.name());
            result.setResponseCode("408");
            result.setResponseMessage("Timeout waiting for message");
            result.setSuccessful(false);
        }
    }

    /**
     * Execute active mode - send proactive messages to clients.
     */
    private void executeActiveMode(SampleResult result, ServerInstance instance) {
        result.sampleStart();

        // Build and send message
        Iso8583Message message = buildActiveMessage();
        String targetBankId = getActiveTargetBankId();

        int sentCount;
        if (targetBankId == null || targetBankId.isEmpty() || "ALL".equalsIgnoreCase(targetBankId)) {
            // Broadcast to all clients
            sentCount = instance.engine.broadcastMessage(message);
        } else {
            // Send to specific client
            boolean sent = instance.engine.sendProactiveMessage(targetBankId, message);
            sentCount = sent ? 1 : 0;
        }

        result.sampleEnd();

        // Build response
        StringBuilder sb = new StringBuilder();
        sb.append("=== [ACTIVE] Message Sent ===\n");
        sb.append("Type: ").append(getActiveMessageType()).append("\n");
        sb.append("MTI: ").append(message.getMti()).append("\n");
        sb.append("STAN: ").append(message.getFieldAsString(11)).append("\n");
        sb.append("Target: ").append(targetBankId == null || targetBankId.isEmpty() ? "ALL" : targetBankId).append("\n");
        sb.append("Sent To: ").append(sentCount).append(" client(s)\n");
        sb.append("\n=== Message Fields ===\n");
        sb.append(message.toDetailString());

        result.setResponseData(sb.toString(), StandardCharsets.UTF_8.name());

        if (sentCount > 0) {
            result.setResponseCode("200");
            result.setResponseMessage("Message sent to " + sentCount + " client(s)");
            result.setSuccessful(true);
        } else {
            result.setResponseCode("404");
            result.setResponseMessage("No clients available");
            result.setSuccessful(false);
        }

        // Store sent message variables
        storeSentMessageVariables(message, sentCount);
    }

    /**
     * Execute bidirectional mode - simultaneously send and receive messages.
     */
    private void executeBidirectionalMode(SampleResult result, ServerInstance instance) {
        result.sampleStart();

        // Execute passive and active in parallel
        CompletableFuture<SampleResult> passiveFuture = CompletableFuture.supplyAsync(() -> {
            SampleResult passiveResult = new SampleResult();
            passiveResult.setSampleLabel(getName() + " [Passive]");
            passiveResult.setDataType(SampleResult.TEXT);
            try {
                executePassiveMode(passiveResult, instance);
            } catch (Exception e) {
                passiveResult.setSuccessful(false);
                passiveResult.setResponseCode("ERROR");
                passiveResult.setResponseMessage(e.getMessage());
                log.error("Bidirectional passive mode error", e);
            }
            return passiveResult;
        });

        CompletableFuture<SampleResult> activeFuture = CompletableFuture.supplyAsync(() -> {
            SampleResult activeResult = new SampleResult();
            activeResult.setSampleLabel(getName() + " [Active]");
            activeResult.setDataType(SampleResult.TEXT);
            executeActiveMode(activeResult, instance);
            return activeResult;
        });

        // Wait for both to complete
        try {
            CompletableFuture.allOf(passiveFuture, activeFuture).get(
                getSampleInterval() + 5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Bidirectional mode wait interrupted", e);
        }

        result.sampleEnd();

        // Get results
        SampleResult passiveResult = passiveFuture.getNow(null);
        SampleResult activeResult = activeFuture.getNow(null);

        // Add sub-results
        if (passiveResult != null) {
            result.addSubResult(passiveResult);
        }
        if (activeResult != null) {
            result.addSubResult(activeResult);
        }

        // Build combined response
        StringBuilder sb = new StringBuilder();
        sb.append("=== [BIDIRECTIONAL] Combined Results ===\n\n");

        sb.append("--- Active Result ---\n");
        if (activeResult != null) {
            sb.append("Success: ").append(activeResult.isSuccessful()).append("\n");
            sb.append("Response: ").append(activeResult.getResponseMessage()).append("\n");
        } else {
            sb.append("Result not available\n");
        }

        sb.append("\n--- Passive Result ---\n");
        if (passiveResult != null) {
            sb.append("Success: ").append(passiveResult.isSuccessful()).append("\n");
            sb.append("Response: ").append(passiveResult.getResponseMessage()).append("\n");
        } else {
            sb.append("Result not available\n");
        }

        sb.append("\n=== Server Statistics ===\n");
        sb.append("Messages Received: ").append(instance.engine.getMessagesReceived().get()).append("\n");
        sb.append("Messages Sent: ").append(instance.engine.getMessagesSent().get()).append("\n");

        result.setResponseData(sb.toString(), StandardCharsets.UTF_8.name());

        // Determine overall success (both must succeed, or at least active succeeded)
        boolean activeSuccess = activeResult != null && activeResult.isSuccessful();
        boolean passiveSuccess = passiveResult != null && passiveResult.isSuccessful();

        // For bidirectional, consider success if active sent successfully
        // Passive timeout is acceptable in bidirectional mode
        result.setSuccessful(activeSuccess);
        result.setResponseCode(activeSuccess ? "200" : "500");
        result.setResponseMessage(String.format("Active: %s, Passive: %s",
            activeSuccess ? "OK" : "FAIL",
            passiveSuccess ? "OK" : (passiveResult != null ? "TIMEOUT" : "FAIL")));
    }

    /**
     * Build message for active sending based on configured message type.
     */
    private Iso8583Message buildActiveMessage() {
        String messageType = getActiveMessageType();
        Iso8583Message message;

        switch (messageType) {
            case ACTIVE_TYPE_SIGN_ON -> {
                message = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
                message.setField(70, "001"); // Sign-on
            }
            case ACTIVE_TYPE_SIGN_OFF -> {
                message = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
                message.setField(70, "002"); // Sign-off
            }
            case ACTIVE_TYPE_ECHO_TEST -> {
                message = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
                message.setField(70, "301"); // Echo test
            }
            case ACTIVE_TYPE_KEY_EXCHANGE -> {
                message = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
                message.setField(70, "161"); // Key exchange
                message.setField(48, "KEY_CHANGE_REQUIRED");
            }
            case ACTIVE_TYPE_CUSTOM -> {
                String customMti = getActiveCustomMti();
                if (customMti == null || customMti.isEmpty()) {
                    customMti = "0800";
                }
                message = new Iso8583Message(customMti);
            }
            default -> {
                message = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
                message.setField(70, "301"); // Default to echo test
            }
        }

        // Set common fields
        String stan = String.format("%06d", stanCounter.incrementAndGet() % 1000000);
        message.setField(11, stan);

        // Set transmission date/time
        LocalDateTime now = LocalDateTime.now();
        message.setField(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));

        // Parse and apply custom fields
        String customFields = getActiveCustomFields();
        if (customFields != null && !customFields.isEmpty()) {
            for (String field : customFields.split(";")) {
                String[] parts = field.split(":", 2);
                if (parts.length == 2) {
                    try {
                        int fieldNum = Integer.parseInt(parts[0].trim());
                        message.setField(fieldNum, parts[1].trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid field number: {}", parts[0]);
                    }
                }
            }
        }

        return message;
    }

    private void storeSentMessageVariables(Iso8583Message message, int sentCount) {
        JMeterContext context = JMeterContextService.getContext();
        if (context == null) return;

        JMeterVariables vars = context.getVariables();
        if (vars == null) return;

        vars.put("FISC_SENT_MTI", message.getMti());
        vars.put("FISC_SENT_STAN", message.getFieldAsString(11));
        vars.put("FISC_SENT_COUNT", String.valueOf(sentCount));
    }

    private ServerInstance getOrCreateServer(String instanceKey, int receivePort, int sendPort) throws Exception {
        synchronized (serverLock) {
            ServerInstance instance = serverInstances.get(instanceKey);

            if (instance == null || !instance.engine.isRunning()) {
                // Create new engine (receivePort=where Sampler receives, sendPort=where Sampler sends)
                FiscDualChannelSimulatorEngine engine = new FiscDualChannelSimulatorEngine(receivePort, sendPort);

                // Configure engine
                engine.setDefaultResponseCode(getDefaultResponseCode());
                engine.setResponseDelayMs(getResponseDelay());
                engine.setValidationErrorCode(getValidationErrorCode());
                engine.setEnableBankIdRouting(isEnableBankIdRouting());
                engine.setBankIdField(getBankIdField());

                // Configure validation
                MessageValidationEngine validationEngine = new MessageValidationEngine();
                if (isEnableValidation()) {
                    String rules = getValidationRules();
                    if (rules != null && !rules.isEmpty()) {
                        validationEngine.configure(rules);
                        engine.setValidationCallback(validationEngine.createValidationCallback());
                    }
                }

                // Configure response rules
                configureResponseRules(engine);

                // Start engine
                engine.start().get();

                instance = new ServerInstance(engine, validationEngine);
                serverInstances.put(instanceKey, instance);

                log.info("Started FISC dual-channel server: ReceivePort={}, SendPort={}",
                    engine.getReceivePort(), engine.getSendPort());
            } else {
                // Update configuration on existing server
                instance.engine.setDefaultResponseCode(getDefaultResponseCode());
                instance.engine.setResponseDelayMs(getResponseDelay());
                instance.engine.setValidationErrorCode(getValidationErrorCode());
                instance.engine.setEnableBankIdRouting(isEnableBankIdRouting());
                instance.engine.setBankIdField(getBankIdField());

                // Update validation rules
                if (isEnableValidation()) {
                    String rules = getValidationRules();
                    if (rules != null && !rules.isEmpty()) {
                        instance.validationEngine.configure(rules);
                        instance.engine.setValidationCallback(instance.validationEngine.createValidationCallback());
                    }
                } else {
                    instance.engine.setValidationCallback(null);
                }

                // Update response rules
                configureResponseRules(instance.engine);
            }

            return instance;
        }
    }

    private void configureResponseRules(FiscDualChannelSimulatorEngine engine) {
        String responseRules = getResponseRules();
        String customFields = getCustomResponseFields();
        String balanceAmount = getBalanceAmount();

        // Parse response rules: "processingCode:responseCode;..."
        Map<String, String> parsedRules = new ConcurrentHashMap<>();
        if (responseRules != null && !responseRules.isEmpty()) {
            for (String rule : responseRules.split(";")) {
                String[] parts = rule.split(":", 2);
                if (parts.length == 2) {
                    parsedRules.put(parts[0].trim(), parts[1].trim());
                }
            }
        }

        // Parse custom fields: "field:value;..."
        Map<Integer, String> parsedFields = new ConcurrentHashMap<>();
        if (customFields != null && !customFields.isEmpty()) {
            for (String field : customFields.split(";")) {
                String[] parts = field.split(":", 2);
                if (parts.length == 2) {
                    try {
                        int fieldNum = Integer.parseInt(parts[0].trim());
                        parsedFields.put(fieldNum, parts[1].trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid field number: {}", parts[0]);
                    }
                }
            }
        }

        // Override 0200 handler with rules
        engine.registerHandler("0200", request -> {
            String processingCode = request.getFieldAsString(3);

            Iso8583Message response = request.createResponse();

            // Determine response code
            String respCode = parsedRules.getOrDefault(processingCode, engine.getDefaultResponseCode());
            response.setField(39, respCode);

            // Set balance if configured
            if (balanceAmount != null && !balanceAmount.isEmpty()) {
                try {
                    String paddedBalance = String.format("%012d",
                        Long.parseLong(balanceAmount.replaceAll("[^0-9]", "")));
                    response.setField(54, paddedBalance);
                } catch (NumberFormatException e) {
                    log.warn("Invalid balance amount: {}", balanceAmount);
                }
            }

            // Apply custom fields
            parsedFields.forEach(response::setField);

            return response;
        });
    }

    private void storeVariables(FiscDualChannelSimulatorEngine engine) {
        JMeterContext context = JMeterContextService.getContext();
        if (context == null) return;

        JMeterVariables vars = context.getVariables();
        if (vars == null) return;

        vars.put("FISC_RECEIVE_PORT", String.valueOf(engine.getReceivePort()));
        vars.put("FISC_SEND_PORT", String.valueOf(engine.getSendPort()));
        vars.put("FISC_RECEIVE_CLIENTS", String.valueOf(engine.getReceiveChannelClientCount().get()));
        vars.put("FISC_SEND_CLIENTS", String.valueOf(engine.getSendChannelClientCount().get()));
        vars.put("FISC_MESSAGES_RECEIVED", String.valueOf(engine.getMessagesReceived().get()));
        vars.put("FISC_MESSAGES_SENT", String.valueOf(engine.getMessagesSent().get()));
        vars.put("FISC_VALIDATION_ERRORS", String.valueOf(engine.getValidationErrors().get()));
        vars.put("FISC_LAST_REQUEST_MTI", engine.getLastRequestMti() != null ? engine.getLastRequestMti() : "");
        vars.put("FISC_LAST_REQUEST_STAN", engine.getLastRequestStan() != null ? engine.getLastRequestStan() : "");
        vars.put("FISC_LAST_VALIDATION_RESULT", engine.getLastValidationResult());
    }

    private void storeReceivedMessageVariables(FiscDualChannelSimulatorEngine.ReceivedRequest received) {
        JMeterContext context = JMeterContextService.getContext();
        if (context == null) return;

        JMeterVariables vars = context.getVariables();
        if (vars == null) return;

        vars.put("FISC_RECEIVED_MTI", received.message().getMti());
        vars.put("FISC_RECEIVED_BANK_ID", received.bankId() != null ? received.bankId() : "");
        vars.put("FISC_RECEIVED_TIMESTAMP", String.valueOf(received.timestamp()));
        vars.put("FISC_RECEIVED_VALIDATION", received.validationResult());

        // Store key fields
        String stan = received.message().getFieldAsString(11);
        String processingCode = received.message().getFieldAsString(3);
        String amount = received.message().getFieldAsString(4);
        String rrn = received.message().getFieldAsString(37);
        String cardNumber = received.message().getFieldAsString(2);

        vars.put("FISC_RECEIVED_STAN", stan != null ? stan : "");
        vars.put("FISC_RECEIVED_PROCESSING_CODE", processingCode != null ? processingCode : "");
        vars.put("FISC_RECEIVED_AMOUNT", amount != null ? amount : "");
        vars.put("FISC_RECEIVED_RRN", rrn != null ? rrn : "");
        vars.put("FISC_RECEIVED_CARD_NUMBER", cardNumber != null ? cardNumber : "");
    }

    // TestStateListener implementation
    @Override
    public void testStarted() {
        testStarted("");
    }

    @Override
    public void testStarted(String host) {
        log.info("FISC dual-channel server test started on {}", host);
    }

    @Override
    public void testEnded() {
        testEnded("");
    }

    @Override
    public void testEnded(String host) {
        log.info("FISC dual-channel server test ended on {}, stopping all server instances", host);
        synchronized (serverLock) {
            serverInstances.values().forEach(instance -> {
                try {
                    instance.engine.close();
                } catch (Exception e) {
                    log.warn("Error stopping FISC dual-channel server", e);
                }
            });
            serverInstances.clear();
        }
    }

    // Getters and Setters for TestBean properties
    public int getReceivePort() {
        return getPropertyAsInt(RECEIVE_PORT, DEFAULT_RECEIVE_PORT);
    }

    public void setReceivePort(int port) {
        setProperty(RECEIVE_PORT, port);
    }

    public int getSendPort() {
        return getPropertyAsInt(SEND_PORT, DEFAULT_SEND_PORT);
    }

    public void setSendPort(int port) {
        setProperty(SEND_PORT, port);
    }

    public int getSampleInterval() {
        return getPropertyAsInt(SAMPLE_INTERVAL, DEFAULT_SAMPLE_INTERVAL);
    }

    public void setSampleInterval(int interval) {
        setProperty(SAMPLE_INTERVAL, interval);
    }

    public String getDefaultResponseCode() {
        return getPropertyAsString(DEFAULT_RESPONSE_CODE, DEFAULT_RESPONSE_CODE_VALUE);
    }

    public void setDefaultResponseCode(String code) {
        setProperty(DEFAULT_RESPONSE_CODE, code);
    }

    public int getResponseDelay() {
        return getPropertyAsInt(RESPONSE_DELAY, 0);
    }

    public void setResponseDelay(int delay) {
        setProperty(RESPONSE_DELAY, delay);
    }

    public String getBalanceAmount() {
        return getPropertyAsString(BALANCE_AMOUNT, "");
    }

    public void setBalanceAmount(String amount) {
        setProperty(BALANCE_AMOUNT, amount);
    }

    public boolean isEnableValidation() {
        return getPropertyAsBoolean(ENABLE_VALIDATION, true);
    }

    public void setEnableValidation(boolean enable) {
        setProperty(ENABLE_VALIDATION, enable);
    }

    public String getValidationRules() {
        return getPropertyAsString(VALIDATION_RULES, "");
    }

    public void setValidationRules(String rules) {
        setProperty(VALIDATION_RULES, rules);
    }

    public String getValidationErrorCode() {
        return getPropertyAsString(VALIDATION_ERROR_CODE, DEFAULT_VALIDATION_ERROR_CODE);
    }

    public void setValidationErrorCode(String code) {
        setProperty(VALIDATION_ERROR_CODE, code);
    }

    public boolean isEnableBankIdRouting() {
        return getPropertyAsBoolean(ENABLE_BANK_ID_ROUTING, true);
    }

    public void setEnableBankIdRouting(boolean enable) {
        setProperty(ENABLE_BANK_ID_ROUTING, enable);
    }

    public int getBankIdField() {
        return getPropertyAsInt(BANK_ID_FIELD, 32);
    }

    public void setBankIdField(int field) {
        setProperty(BANK_ID_FIELD, field);
    }

    public String getResponseRules() {
        return getPropertyAsString(RESPONSE_RULES, "");
    }

    public void setResponseRules(String rules) {
        setProperty(RESPONSE_RULES, rules);
    }

    public String getCustomResponseFields() {
        return getPropertyAsString(CUSTOM_RESPONSE_FIELDS, "");
    }

    public void setCustomResponseFields(String fields) {
        setProperty(CUSTOM_RESPONSE_FIELDS, fields);
    }

    // Operation mode getters and setters
    public String getOperationMode() {
        return getPropertyAsString(OPERATION_MODE, MODE_PASSIVE);
    }

    public void setOperationMode(String mode) {
        setProperty(OPERATION_MODE, mode);
    }

    public String getActiveMessageType() {
        return getPropertyAsString(ACTIVE_MESSAGE_TYPE, ACTIVE_TYPE_ECHO_TEST);
    }

    public void setActiveMessageType(String type) {
        setProperty(ACTIVE_MESSAGE_TYPE, type);
    }

    public String getActiveTargetBankId() {
        return getPropertyAsString(ACTIVE_TARGET_BANK_ID, "");
    }

    public void setActiveTargetBankId(String bankId) {
        setProperty(ACTIVE_TARGET_BANK_ID, bankId);
    }

    public String getActiveCustomMti() {
        return getPropertyAsString(ACTIVE_CUSTOM_MTI, "0800");
    }

    public void setActiveCustomMti(String mti) {
        setProperty(ACTIVE_CUSTOM_MTI, mti);
    }

    public String getActiveCustomFields() {
        return getPropertyAsString(ACTIVE_CUSTOM_FIELDS, "");
    }

    public void setActiveCustomFields(String fields) {
        setProperty(ACTIVE_CUSTOM_FIELDS, fields);
    }

    /**
     * Gets the engine instance for a specific port pair.
     * Used by FiscMessageSenderSampler to send proactive messages.
     *
     * @param receivePort the Receive port (where Sampler receives requests)
     * @param sendPort the Send port (where Sampler sends responses)
     */
    public static FiscDualChannelSimulatorEngine getEngine(int receivePort, int sendPort) {
        String instanceKey = receivePort + ":" + sendPort;
        ServerInstance instance = serverInstances.get(instanceKey);
        return instance != null ? instance.engine : null;
    }

    /**
     * Gets the first available engine instance.
     */
    public static FiscDualChannelSimulatorEngine getFirstEngine() {
        return serverInstances.values().stream()
            .map(i -> i.engine)
            .filter(FiscDualChannelSimulatorEngine::isRunning)
            .findFirst()
            .orElse(null);
    }

    /**
     * Server instance wrapper.
     */
    private record ServerInstance(
        FiscDualChannelSimulatorEngine engine,
        MessageValidationEngine validationEngine
    ) {}
}
