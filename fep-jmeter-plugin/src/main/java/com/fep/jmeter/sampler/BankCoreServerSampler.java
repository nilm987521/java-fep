package com.fep.jmeter.sampler;

import com.fep.jmeter.engine.BankCoreSimulatorEngine;
import com.fep.jmeter.validation.MessageValidationEngine;
import com.fep.message.iso8583.Iso8583Message;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JMeter Sampler for simulating a Bank Core System with Dual-Channel architecture.
 *
 * <p>This sampler starts a dual-channel TCP server that models
 * bank core system behavior where FEP connects to send transactions
 * and receive responses through separate TCP connections.
 *
 * <p>Features:
 * <ul>
 *   <li>Dual-channel architecture (Send Port + Receive Port)</li>
 *   <li>Multi-FEP routing by FEP ID (F32)</li>
 *   <li>Message validation with configurable rules</li>
 *   <li>Response rules based on processing code</li>
 *   <li>Account balance simulation</li>
 *   <li>Proactive message support (via BankCoreMessageSenderSampler)</li>
 *   <li>Statistics tracking</li>
 * </ul>
 *
 * <p>Supported transaction types:
 * <ul>
 *   <li>0100 - Authorization Request</li>
 *   <li>0200 - Financial Request (Withdrawal, Transfer, Inquiry)</li>
 *   <li>0400 - Reversal Request</li>
 *   <li>0800 - Network Management (Sign On/Off, Echo)</li>
 * </ul>
 */
public class BankCoreServerSampler extends AbstractSampler implements TestBean, TestStateListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(BankCoreServerSampler.class);

    // Property names for TestBean
    public static final String SEND_PORT = "sendPort";
    public static final String RECEIVE_PORT = "receivePort";
    public static final String SAMPLE_INTERVAL = "sampleInterval";
    public static final String DEFAULT_RESPONSE_CODE = "defaultResponseCode";
    public static final String RESPONSE_DELAY = "responseDelay";
    public static final String AVAILABLE_BALANCE = "availableBalance";
    public static final String LEDGER_BALANCE = "ledgerBalance";
    public static final String ENABLE_VALIDATION = "enableValidation";
    public static final String VALIDATION_RULES = "validationRules";
    public static final String VALIDATION_ERROR_CODE = "validationErrorCode";
    public static final String ENABLE_FEP_ID_ROUTING = "enableFepIdRouting";
    public static final String FEP_ID_FIELD = "fepIdField";
    public static final String RESPONSE_RULES = "responseRules";
    public static final String CUSTOM_RESPONSE_FIELDS = "customResponseFields";

    // Default values
    private static final int DEFAULT_SEND_PORT = 9100;
    private static final int DEFAULT_RECEIVE_PORT = 9101;
    private static final int DEFAULT_SAMPLE_INTERVAL = 1000;
    private static final String DEFAULT_RESPONSE_CODE_VALUE = "00";
    private static final String DEFAULT_VALIDATION_ERROR_CODE = "30";
    private static final String DEFAULT_AVAILABLE_BALANCE = "100000";  // 1,000.00
    private static final String DEFAULT_LEDGER_BALANCE = "150000";     // 1,500.00

    // Server instance management (one server per port pair)
    private static final Map<String, ServerInstance> serverInstances = new ConcurrentHashMap<>();
    private static final Object serverLock = new Object();

    public BankCoreServerSampler() {
        super();
        setName("Bank Core Server Simulator");
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);

        int sendPort = getSendPort();
        int receivePort = getReceivePort();
        String instanceKey = sendPort + ":" + receivePort;

        try {
            result.sampleStart();

            // Get or create server instance
            ServerInstance instance = getOrCreateServer(instanceKey, sendPort, receivePort);

            // Wait for sample interval
            int sampleInterval = getSampleInterval();
            if (sampleInterval > 0) {
                Thread.sleep(sampleInterval);
            }

            result.sampleEnd();

            // Build response data
            StringBuilder sb = new StringBuilder();
            sb.append("=== Bank Core Server Simulator Status ===\n");
            sb.append("Send Port: ").append(instance.engine.getSendPort()).append("\n");
            sb.append("Receive Port: ").append(instance.engine.getReceivePort()).append("\n");
            sb.append("Status: Running\n");
            sb.append("\n=== Connection Statistics ===\n");
            sb.append("Send Channel FEPs: ").append(instance.engine.getSendChannelClientCount().get()).append("\n");
            sb.append("Receive Channel FEPs: ").append(instance.engine.getReceiveChannelClientCount().get()).append("\n");
            sb.append("Registered FEP IDs: ").append(instance.engine.getClientRouter().getRegisteredBankIds()).append("\n");
            sb.append("\n=== Message Statistics ===\n");
            sb.append("Messages Received: ").append(instance.engine.getMessagesReceived().get()).append("\n");
            sb.append("Messages Sent: ").append(instance.engine.getMessagesSent().get()).append("\n");
            sb.append("Validation Errors: ").append(instance.engine.getValidationErrors().get()).append("\n");
            sb.append("\n=== Last Request ===\n");
            sb.append("MTI: ").append(instance.engine.getLastRequestMti()).append("\n");
            sb.append("STAN: ").append(instance.engine.getLastRequestStan()).append("\n");
            sb.append("Validation: ").append(instance.engine.getLastValidationResult()).append("\n");
            sb.append("\n=== Configuration ===\n");
            sb.append("Default Response Code: ").append(getDefaultResponseCode()).append("\n");
            sb.append("Response Delay: ").append(getResponseDelay()).append(" ms\n");
            sb.append("Validation Enabled: ").append(isEnableValidation()).append("\n");
            sb.append("FEP ID Routing: ").append(isEnableFepIdRouting()).append("\n");
            sb.append("Available Balance: ").append(getAvailableBalance()).append("\n");
            sb.append("Ledger Balance: ").append(getLedgerBalance()).append("\n");

            result.setResponseData(sb.toString(), StandardCharsets.UTF_8.name());
            result.setResponseCode("200");
            result.setResponseMessage("Server Running");
            result.setSuccessful(true);

            // Store statistics in JMeter variables
            storeVariables(instance.engine);

        } catch (Exception e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("ERROR");
            result.setResponseMessage(e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            log.error("Bank Core server sampler error", e);
        }

        return result;
    }

    private ServerInstance getOrCreateServer(String instanceKey, int sendPort, int receivePort) throws Exception {
        synchronized (serverLock) {
            ServerInstance instance = serverInstances.get(instanceKey);

            if (instance == null || !instance.engine.isRunning()) {
                // Create new engine
                BankCoreSimulatorEngine engine = new BankCoreSimulatorEngine(sendPort, receivePort);

                // Configure engine
                engine.setDefaultResponseCode(getDefaultResponseCode());
                engine.setResponseDelayMs(getResponseDelay());
                engine.setValidationErrorCode(getValidationErrorCode());
                engine.setEnableFepIdRouting(isEnableFepIdRouting());
                engine.setFepIdField(getFepIdField());
                engine.setDefaultAvailableBalance(formatBalance(getAvailableBalance()));
                engine.setDefaultLedgerBalance(formatBalance(getLedgerBalance()));

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

                log.info("Started Bank Core server: Send={}, Receive={}",
                    engine.getSendPort(), engine.getReceivePort());
            } else {
                // Update configuration on existing server
                instance.engine.setDefaultResponseCode(getDefaultResponseCode());
                instance.engine.setResponseDelayMs(getResponseDelay());
                instance.engine.setValidationErrorCode(getValidationErrorCode());
                instance.engine.setEnableFepIdRouting(isEnableFepIdRouting());
                instance.engine.setFepIdField(getFepIdField());
                instance.engine.setDefaultAvailableBalance(formatBalance(getAvailableBalance()));
                instance.engine.setDefaultLedgerBalance(formatBalance(getLedgerBalance()));

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

    private String formatBalance(String balance) {
        if (balance == null || balance.isEmpty()) {
            return "000000000000";
        }
        try {
            long amount = Long.parseLong(balance.replaceAll("[^0-9]", ""));
            return String.format("%012d", amount);
        } catch (NumberFormatException e) {
            return "000000000000";
        }
    }

    private void configureResponseRules(BankCoreSimulatorEngine engine) {
        String responseRules = getResponseRules();
        String customFields = getCustomResponseFields();
        String availableBalance = getAvailableBalance();
        String ledgerBalance = getLedgerBalance();

        // Parse response rules from JSON: {"processingCode": "responseCode", ...}
        Map<String, String> parsedRules = parseJsonResponseRules(responseRules);

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

            // Determine response code based on rules or default
            String respCode = parsedRules.getOrDefault(processingCode, engine.getDefaultResponseCode());
            response.setField(39, respCode);

            // Set balance for inquiry (31xxxx) if success
            if ("00".equals(respCode) && processingCode != null && processingCode.startsWith("31")) {
                String balanceData = formatBalance(availableBalance) + formatBalance(ledgerBalance);
                response.setField(54, balanceData);
            }

            // Apply custom fields
            parsedFields.forEach(response::setField);

            return response;
        });

        // Override 0100 handler with rules
        engine.registerHandler("0100", request -> {
            String processingCode = request.getFieldAsString(3);

            Iso8583Message response = request.createResponse();

            // Determine response code
            String respCode = parsedRules.getOrDefault(processingCode, engine.getDefaultResponseCode());
            response.setField(39, respCode);

            // Generate auth code for successful authorization
            if ("00".equals(respCode)) {
                response.setField(38, String.format("%06d",
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(1000000)));
            }

            // Apply custom fields
            parsedFields.forEach(response::setField);

            return response;
        });
    }

    private Map<String, String> parseJsonResponseRules(String jsonRules) {
        if (jsonRules == null || jsonRules.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(jsonRules, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse response rules JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void storeVariables(BankCoreSimulatorEngine engine) {
        JMeterContext context = JMeterContextService.getContext();
        if (context == null) return;

        JMeterVariables vars = context.getVariables();
        if (vars == null) return;

        vars.put("BANKCORE_SEND_PORT", String.valueOf(engine.getSendPort()));
        vars.put("BANKCORE_RECEIVE_PORT", String.valueOf(engine.getReceivePort()));
        vars.put("BANKCORE_SEND_CLIENTS", String.valueOf(engine.getSendChannelClientCount().get()));
        vars.put("BANKCORE_RECEIVE_CLIENTS", String.valueOf(engine.getReceiveChannelClientCount().get()));
        vars.put("BANKCORE_MESSAGES_RECEIVED", String.valueOf(engine.getMessagesReceived().get()));
        vars.put("BANKCORE_MESSAGES_SENT", String.valueOf(engine.getMessagesSent().get()));
        vars.put("BANKCORE_VALIDATION_ERRORS", String.valueOf(engine.getValidationErrors().get()));
        vars.put("BANKCORE_LAST_REQUEST_MTI", engine.getLastRequestMti() != null ? engine.getLastRequestMti() : "");
        vars.put("BANKCORE_LAST_REQUEST_STAN", engine.getLastRequestStan() != null ? engine.getLastRequestStan() : "");
        vars.put("BANKCORE_LAST_VALIDATION_RESULT", engine.getLastValidationResult());
    }

    // TestStateListener implementation
    @Override
    public void testStarted() {
        testStarted("");
    }

    @Override
    public void testStarted(String host) {
        log.info("Bank Core server test started on {}", host);
    }

    @Override
    public void testEnded() {
        testEnded("");
    }

    @Override
    public void testEnded(String host) {
        log.info("Bank Core server test ended on {}, stopping all server instances", host);
        synchronized (serverLock) {
            serverInstances.values().forEach(instance -> {
                try {
                    instance.engine.close();
                } catch (Exception e) {
                    log.warn("Error stopping Bank Core server", e);
                }
            });
            serverInstances.clear();
        }
    }

    // Getters and Setters for TestBean properties
    public int getSendPort() {
        return getPropertyAsInt(SEND_PORT, DEFAULT_SEND_PORT);
    }

    public void setSendPort(int port) {
        setProperty(SEND_PORT, port);
    }

    public int getReceivePort() {
        return getPropertyAsInt(RECEIVE_PORT, DEFAULT_RECEIVE_PORT);
    }

    public void setReceivePort(int port) {
        setProperty(RECEIVE_PORT, port);
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

    public String getAvailableBalance() {
        return getPropertyAsString(AVAILABLE_BALANCE, DEFAULT_AVAILABLE_BALANCE);
    }

    public void setAvailableBalance(String balance) {
        setProperty(AVAILABLE_BALANCE, balance);
    }

    public String getLedgerBalance() {
        return getPropertyAsString(LEDGER_BALANCE, DEFAULT_LEDGER_BALANCE);
    }

    public void setLedgerBalance(String balance) {
        setProperty(LEDGER_BALANCE, balance);
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

    public boolean isEnableFepIdRouting() {
        return getPropertyAsBoolean(ENABLE_FEP_ID_ROUTING, true);
    }

    public void setEnableFepIdRouting(boolean enable) {
        setProperty(ENABLE_FEP_ID_ROUTING, enable);
    }

    public int getFepIdField() {
        return getPropertyAsInt(FEP_ID_FIELD, 32);
    }

    public void setFepIdField(int field) {
        setProperty(FEP_ID_FIELD, field);
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

    /**
     * Gets the engine instance for a specific port pair.
     * Used by BankCoreMessageSenderSampler to send proactive messages.
     */
    public static BankCoreSimulatorEngine getEngine(int sendPort, int receivePort) {
        String instanceKey = sendPort + ":" + receivePort;
        ServerInstance instance = serverInstances.get(instanceKey);
        return instance != null ? instance.engine : null;
    }

    /**
     * Gets the first available engine instance.
     */
    public static BankCoreSimulatorEngine getFirstEngine() {
        return serverInstances.values().stream()
            .map(i -> i.engine)
            .filter(BankCoreSimulatorEngine::isRunning)
            .findFirst()
            .orElse(null);
    }

    /**
     * Server instance wrapper.
     */
    private record ServerInstance(
        BankCoreSimulatorEngine engine,
        MessageValidationEngine validationEngine
    ) {}
}
