package com.fep.jmeter.sampler;

import com.fep.jmeter.engine.BankCoreSimulatorEngine;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.MessageType;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMeter Sampler for sending proactive messages from Bank Core System Simulator.
 *
 * <p>This sampler sends messages to connected FEP clients, simulating Bank Core
 * initiated communications like reconciliation notifications, account updates, or system status.
 *
 * <p>Message Types:
 * <ul>
 *   <li>RECONCILIATION_NOTIFY - Settlement/Reconciliation notification</li>
 *   <li>ACCOUNT_UPDATE - Account balance/status update notification</li>
 *   <li>SYSTEM_STATUS - Core system status notification</li>
 *   <li>ECHO_TEST - Network management echo test (0800)</li>
 *   <li>SIGN_ON_NOTIFY - Sign-on notification (0800)</li>
 *   <li>SIGN_OFF_NOTIFY - Sign-off notification (0800)</li>
 *   <li>CUSTOM - Custom message with user-defined MTI</li>
 * </ul>
 */
public class BankCoreMessageSenderSampler extends AbstractSampler implements TestBean {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(BankCoreMessageSenderSampler.class);

    // Property names
    public static final String MESSAGE_TYPE = "messageType";
    public static final String TARGET_FEP_ID = "targetFepId";
    public static final String CUSTOM_MTI = "customMti";
    public static final String MESSAGE_FIELDS = "messageFields";
    public static final String SEND_PORT = "sendPort";
    public static final String RECEIVE_PORT = "receivePort";
    public static final String SETTLEMENT_DATE = "settlementDate";
    public static final String ACCOUNT_NUMBER = "accountNumber";

    // Message type options (deprecated, use BankCoreMessageType enum instead)
    /** @deprecated Use {@link BankCoreMessageType#RECONCILIATION_NOTIFY} instead */
    @Deprecated
    public static final String TYPE_RECONCILIATION = BankCoreMessageType.RECONCILIATION_NOTIFY.name();
    /** @deprecated Use {@link BankCoreMessageType#ACCOUNT_UPDATE} instead */
    @Deprecated
    public static final String TYPE_ACCOUNT_UPDATE = BankCoreMessageType.ACCOUNT_UPDATE.name();
    /** @deprecated Use {@link BankCoreMessageType#SYSTEM_STATUS} instead */
    @Deprecated
    public static final String TYPE_SYSTEM_STATUS = BankCoreMessageType.SYSTEM_STATUS.name();
    /** @deprecated Use {@link BankCoreMessageType#ECHO_TEST} instead */
    @Deprecated
    public static final String TYPE_ECHO_TEST = BankCoreMessageType.ECHO_TEST.name();
    /** @deprecated Use {@link BankCoreMessageType#SIGN_ON_NOTIFY} instead */
    @Deprecated
    public static final String TYPE_SIGN_ON = BankCoreMessageType.SIGN_ON_NOTIFY.name();
    /** @deprecated Use {@link BankCoreMessageType#SIGN_OFF_NOTIFY} instead */
    @Deprecated
    public static final String TYPE_SIGN_OFF = BankCoreMessageType.SIGN_OFF_NOTIFY.name();
    /** @deprecated Use {@link BankCoreMessageType#CUSTOM} instead */
    @Deprecated
    public static final String TYPE_CUSTOM = BankCoreMessageType.CUSTOM.name();

    // STAN counter for unique message identification
    private static final AtomicInteger stanCounter = new AtomicInteger(0);

    public BankCoreMessageSenderSampler() {
        super();
        setName("Bank Core Message Sender");
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);

        try {
            result.sampleStart();

            // Get the engine
            BankCoreSimulatorEngine engine = getEngine();
            if (engine == null) {
                throw new IllegalStateException(
                    "No Bank Core Server is running. " +
                    "Please add BankCoreServerSampler before this sampler.");
            }

            // Build message
            Iso8583Message message = buildMessage();

            // Send message
            String targetFepId = getTargetFepId();
            int sentCount;
            if (targetFepId == null || targetFepId.isEmpty() || "ALL".equalsIgnoreCase(targetFepId)) {
                // Broadcast to all clients
                sentCount = engine.broadcastMessage(message);
            } else {
                // Send to specific client
                boolean sent = engine.sendProactiveMessage(targetFepId, message);
                sentCount = sent ? 1 : 0;
            }

            result.sampleEnd();

            // Build response
            StringBuilder sb = new StringBuilder();
            sb.append("=== Bank Core Message Sent ===\n");
            sb.append("Type: ").append(getMessageType()).append("\n");
            sb.append("MTI: ").append(message.getMti()).append("\n");
            sb.append("STAN: ").append(message.getFieldAsString(11)).append("\n");
            sb.append("Target FEP: ").append(targetFepId == null || targetFepId.isEmpty() ? "ALL" : targetFepId).append("\n");
            sb.append("Sent To: ").append(sentCount).append(" FEP client(s)\n");
            sb.append("\n=== Message Fields ===\n");
            sb.append(message.toDetailString());

            result.setResponseData(sb.toString(), StandardCharsets.UTF_8.name());

            if (sentCount > 0) {
                result.setResponseCode("200");
                result.setResponseMessage("Message sent to " + sentCount + " FEP client(s)");
                result.setSuccessful(true);
            } else {
                result.setResponseCode("404");
                result.setResponseMessage("No FEP clients available");
                result.setSuccessful(false);
            }

            // Store in JMeter variables
            JMeterContext context = JMeterContextService.getContext();
            if (context != null) {
                JMeterVariables vars = context.getVariables();
                if (vars != null) {
                    vars.put("BANKCORE_SENT_MTI", message.getMti());
                    vars.put("BANKCORE_SENT_STAN", message.getFieldAsString(11));
                    vars.put("BANKCORE_SENT_COUNT", String.valueOf(sentCount));
                }
            }

        } catch (Exception e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("ERROR");
            result.setResponseMessage(e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            log.error("Bank Core message sender error", e);
        }

        return result;
    }

    private BankCoreSimulatorEngine getEngine() {
        int sendPort = getSendPort();
        int receivePort = getReceivePort();

        if (sendPort > 0 && receivePort > 0) {
            return BankCoreServerSampler.getEngine(sendPort, receivePort);
        }

        return BankCoreServerSampler.getFirstEngine();
    }

    private Iso8583Message buildMessage() {
        BankCoreMessageType messageType = BankCoreMessageType.fromString(getMessageType());

        Iso8583Message message = switch (messageType) {
            case RECONCILIATION_NOTIFY -> {
                // Settlement/Reconciliation notification
                Iso8583Message msg = new Iso8583Message("0820"); // Network Management Advice
                msg.setField(70, "201"); // Reconciliation notification

                // Add settlement date
                String settlementDate = getSettlementDate();
                if (settlementDate != null && !settlementDate.isEmpty()) {
                    msg.setField(15, settlementDate); // Settlement date
                } else {
                    msg.setField(15, LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd")));
                }
                msg.setField(48, "RECONCILIATION_READY");
                yield msg;
            }
            case ACCOUNT_UPDATE -> {
                // Account update notification
                Iso8583Message msg = new Iso8583Message("0620"); // Financial Advice
                msg.setField(70, "301"); // Account update

                // Add account number if provided
                String accountNumber = getAccountNumber();
                if (accountNumber != null && !accountNumber.isEmpty()) {
                    msg.setField(102, accountNumber); // Account identification
                }
                msg.setField(48, "ACCOUNT_BALANCE_UPDATED");
                yield msg;
            }
            case SYSTEM_STATUS -> {
                Iso8583Message msg = new Iso8583Message("0820"); // Network Management Advice
                msg.setField(70, "001"); // System status
                msg.setField(48, "CORE_SYSTEM_STATUS");
                yield msg;
            }
            case ECHO_TEST -> {
                Iso8583Message msg = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
                msg.setField(70, "301"); // Echo test
                yield msg;
            }
            case SIGN_ON_NOTIFY -> {
                Iso8583Message msg = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
                msg.setField(70, "001"); // Sign-on
                yield msg;
            }
            case SIGN_OFF_NOTIFY -> {
                Iso8583Message msg = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
                msg.setField(70, "002"); // Sign-off
                yield msg;
            }
            case CUSTOM -> {
                String customMti = getCustomMti();
                if (customMti == null || customMti.isEmpty()) {
                    customMti = "0800";
                }
                yield new Iso8583Message(customMti);
            }
        };

        // Set common fields
        String stan = String.format("%06d", stanCounter.incrementAndGet() % 1000000);
        message.setField(11, stan);

        // Set transmission date/time
        LocalDateTime now = LocalDateTime.now();
        message.setField(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));

        // Parse and apply custom fields
        String customFields = getMessageFields();
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

    // Getters and Setters
    public String getMessageType() {
        return getPropertyAsString(MESSAGE_TYPE, BankCoreMessageType.ECHO_TEST.name());
    }

    public void setMessageType(String type) {
        setProperty(MESSAGE_TYPE, type);
    }

    public String getTargetFepId() {
        return getPropertyAsString(TARGET_FEP_ID, "");
    }

    public void setTargetFepId(String fepId) {
        setProperty(TARGET_FEP_ID, fepId);
    }

    public String getCustomMti() {
        return getPropertyAsString(CUSTOM_MTI, "0800");
    }

    public void setCustomMti(String mti) {
        setProperty(CUSTOM_MTI, mti);
    }

    public String getMessageFields() {
        return getPropertyAsString(MESSAGE_FIELDS, "");
    }

    public void setMessageFields(String fields) {
        setProperty(MESSAGE_FIELDS, fields);
    }

    public int getSendPort() {
        return getPropertyAsInt(SEND_PORT, 0);
    }

    public void setSendPort(int port) {
        setProperty(SEND_PORT, port);
    }

    public int getReceivePort() {
        return getPropertyAsInt(RECEIVE_PORT, 0);
    }

    public void setReceivePort(int port) {
        setProperty(RECEIVE_PORT, port);
    }

    public String getSettlementDate() {
        return getPropertyAsString(SETTLEMENT_DATE, "");
    }

    public void setSettlementDate(String date) {
        setProperty(SETTLEMENT_DATE, date);
    }

    public String getAccountNumber() {
        return getPropertyAsString(ACCOUNT_NUMBER, "");
    }

    public void setAccountNumber(String accountNumber) {
        setProperty(ACCOUNT_NUMBER, accountNumber);
    }
}
