package com.fep.message.iso8583;

import com.fep.message.iso8583.parser.FiscMessageAssembler;
import com.fep.message.iso8583.parser.FiscMessageParser;
import com.fep.message.iso8583.parser.MessageAssembler;
import com.fep.message.iso8583.parser.MessageParser;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory class for creating and managing ISO 8583 messages.
 *
 * <p>Provides convenient methods for:
 * <ul>
 *   <li>Creating new messages</li>
 *   <li>Parsing incoming messages</li>
 *   <li>Assembling outgoing messages</li>
 *   <li>Generating transaction identifiers (STAN, RRN)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * Iso8583MessageFactory factory = new Iso8583MessageFactory();
 *
 * // Create a withdrawal request
 * Iso8583Message request = factory.createMessage(MessageType.FINANCIAL_REQUEST);
 * request.setField(2, "4111111111111111");
 * request.setField(3, "010000");  // Withdrawal
 * request.setField(4, "000000010000");  // Amount: 100.00
 * factory.setTransactionFields(request);  // Auto-fill STAN, RRN, timestamps
 *
 * // Assemble to bytes
 * byte[] wireData = factory.assemble(request);
 *
 * // Parse response
 * Iso8583Message response = factory.parse(responseBytes);
 * }</pre>
 */
@Slf4j
public class Iso8583MessageFactory {

    private static final DateTimeFormatter TRANSMISSION_DATE_TIME_FORMAT =
        DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter LOCAL_TIME_FORMAT =
        DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter LOCAL_DATE_FORMAT =
        DateTimeFormatter.ofPattern("MMdd");

    /** STAN counter (rolls over at 999999) */
    private final AtomicInteger stanCounter = new AtomicInteger(0);

    /** RRN counter (rolls over at 999999999999) */
    private final AtomicInteger rrnCounter = new AtomicInteger(0);

    /** Parser for incoming messages */
    private final MessageParser parser;

    /** Assembler for outgoing messages */
    private final MessageAssembler assembler;

    /** Bank institution ID (used in field 32/33) */
    private String institutionId;

    /**
     * Creates a factory with default parser and assembler.
     */
    public Iso8583MessageFactory() {
        this.parser = new FiscMessageParser();
        this.assembler = new FiscMessageAssembler();
    }

    /**
     * Creates a factory with custom parser and assembler.
     *
     * @param parser the message parser
     * @param assembler the message assembler
     */
    public Iso8583MessageFactory(MessageParser parser, MessageAssembler assembler) {
        this.parser = parser;
        this.assembler = assembler;
    }

    /**
     * Sets the institution ID to be used in messages.
     *
     * @param institutionId the bank institution ID
     */
    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

    /**
     * Creates a new message with the specified type.
     *
     * @param messageType the message type
     * @return a new message
     */
    public Iso8583Message createMessage(MessageType messageType) {
        return new Iso8583Message(messageType);
    }

    /**
     * Creates a new message with the specified MTI.
     *
     * @param mti the message type indicator
     * @return a new message
     */
    public Iso8583Message createMessage(String mti) {
        return new Iso8583Message(mti);
    }

    /**
     * Parses a message from bytes.
     *
     * @param data the message bytes
     * @return the parsed message
     */
    public Iso8583Message parse(byte[] data) {
        return parser.parse(data);
    }

    /**
     * Assembles a message to bytes.
     *
     * @param message the message to assemble
     * @return the wire format bytes
     */
    public byte[] assemble(Iso8583Message message) {
        return assembler.assemble(message);
    }

    /**
     * Sets common transaction fields (STAN, RRN, timestamps).
     *
     * @param message the message to populate
     */
    public void setTransactionFields(Iso8583Message message) {
        LocalDateTime now = LocalDateTime.now();

        // Field 7: Transmission Date and Time (MMDDhhmmss)
        message.setField(7, now.format(TRANSMISSION_DATE_TIME_FORMAT));

        // Field 11: STAN
        message.setField(11, generateStan());

        // Field 12: Local Transaction Time (hhmmss)
        message.setField(12, now.format(LOCAL_TIME_FORMAT));

        // Field 13: Local Transaction Date (MMDD)
        message.setField(13, now.format(LOCAL_DATE_FORMAT));

        // Field 37: Retrieval Reference Number
        message.setField(37, generateRrn());

        // Field 32: Acquiring Institution ID (if set)
        if (institutionId != null && !message.hasField(32)) {
            message.setField(32, institutionId);
        }
    }

    /**
     * Generates a unique System Trace Audit Number (STAN).
     *
     * @return 6-digit STAN
     */
    public String generateStan() {
        int stan = stanCounter.updateAndGet(current -> {
            int next = current + 1;
            return next > 999999 ? 1 : next;
        });
        return String.format("%06d", stan);
    }

    /**
     * Generates a unique Retrieval Reference Number (RRN).
     *
     * @return 12-character RRN
     */
    public String generateRrn() {
        LocalDateTime now = LocalDateTime.now();
        int sequence = rrnCounter.updateAndGet(current -> {
            int next = current + 1;
            return next > 99 ? 1 : next;
        });

        // Format: YDDD + HHMMSS + 2-digit sequence = 12 chars
        // Y: last digit of year, DDD: day of year, HHMMSS: time
        String rrn = String.format("%d%03d%02d%02d%02d%02d",
            now.getYear() % 10,
            now.getDayOfYear(),
            now.getHour(),
            now.getMinute(),
            now.getSecond(),
            sequence);
        return rrn;
    }

    /**
     * Creates a network management message (0800).
     *
     * @param networkCode the network management code (field 70)
     * @return the network management message
     */
    public Iso8583Message createNetworkManagementMessage(String networkCode) {
        Iso8583Message message = createMessage(MessageType.NETWORK_MANAGEMENT_REQUEST);
        setTransactionFields(message);
        message.setField(70, networkCode);
        return message;
    }

    /**
     * Creates a sign-on message.
     *
     * @return the sign-on message
     */
    public Iso8583Message createSignOnMessage() {
        return createNetworkManagementMessage("001");
    }

    /**
     * Creates a sign-off message.
     *
     * @return the sign-off message
     */
    public Iso8583Message createSignOffMessage() {
        return createNetworkManagementMessage("002");
    }

    /**
     * Creates an echo test message.
     *
     * @return the echo test message
     */
    public Iso8583Message createEchoTestMessage() {
        return createNetworkManagementMessage("301");
    }

    /**
     * Creates a key exchange message.
     *
     * @return the key exchange message
     */
    public Iso8583Message createKeyExchangeMessage() {
        return createNetworkManagementMessage("101");
    }
}
