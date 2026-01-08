package com.fep.message.iso8583;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Iso8583MessageFactory.
 */
@DisplayName("ISO 8583 Message Factory Tests")
class Iso8583MessageFactoryTest {

    private Iso8583MessageFactory factory;

    @BeforeEach
    void setUp() {
        factory = new Iso8583MessageFactory();
        factory.setInstitutionId("012");
    }

    @Test
    @DisplayName("Should create message with MessageType")
    void shouldCreateMessageWithMessageType() {
        Iso8583Message message = factory.createMessage(MessageType.FINANCIAL_REQUEST);

        assertNotNull(message);
        assertEquals("0200", message.getMti());
        assertEquals(MessageType.FINANCIAL_REQUEST, message.getMessageType());
    }

    @Test
    @DisplayName("Should create message with MTI string")
    void shouldCreateMessageWithMtiString() {
        Iso8583Message message = factory.createMessage("0100");

        assertNotNull(message);
        assertEquals("0100", message.getMti());
    }

    @Test
    @DisplayName("Should set transaction fields")
    void shouldSetTransactionFields() {
        Iso8583Message message = factory.createMessage(MessageType.FINANCIAL_REQUEST);

        factory.setTransactionFields(message);

        // Field 7: Transmission Date Time (MMDDhhmmss)
        assertTrue(message.hasField(7));
        assertEquals(10, message.getFieldAsString(7).length());

        // Field 11: STAN
        assertTrue(message.hasField(11));
        assertEquals(6, message.getFieldAsString(11).length());

        // Field 12: Local Time (hhmmss)
        assertTrue(message.hasField(12));
        assertEquals(6, message.getFieldAsString(12).length());

        // Field 13: Local Date (MMDD)
        assertTrue(message.hasField(13));
        assertEquals(4, message.getFieldAsString(13).length());

        // Field 37: RRN
        assertTrue(message.hasField(37));
        assertEquals(12, message.getFieldAsString(37).length());

        // Field 32: Institution ID
        assertTrue(message.hasField(32));
        assertEquals("012", message.getFieldAsString(32));
    }

    @Test
    @DisplayName("Should generate unique STAN")
    void shouldGenerateUniqueStan() {
        String stan1 = factory.generateStan();
        String stan2 = factory.generateStan();
        String stan3 = factory.generateStan();

        assertEquals(6, stan1.length());
        assertEquals(6, stan2.length());
        assertEquals(6, stan3.length());

        // STANs should be sequential
        assertNotEquals(stan1, stan2);
        assertNotEquals(stan2, stan3);
    }

    @Test
    @DisplayName("Should generate unique RRN")
    void shouldGenerateUniqueRrn() {
        String rrn1 = factory.generateRrn();
        String rrn2 = factory.generateRrn();

        assertEquals(12, rrn1.length());
        assertEquals(12, rrn2.length());

        // RRNs should be unique
        assertNotEquals(rrn1, rrn2);
    }

    @Test
    @DisplayName("Should create sign-on message")
    void shouldCreateSignOnMessage() {
        Iso8583Message message = factory.createSignOnMessage();

        assertEquals("0800", message.getMti());
        assertEquals(MessageType.NETWORK_MANAGEMENT_REQUEST, message.getMessageType());
        assertEquals("001", message.getFieldAsString(70));
    }

    @Test
    @DisplayName("Should create sign-off message")
    void shouldCreateSignOffMessage() {
        Iso8583Message message = factory.createSignOffMessage();

        assertEquals("0800", message.getMti());
        assertEquals("002", message.getFieldAsString(70));
    }

    @Test
    @DisplayName("Should create echo test message")
    void shouldCreateEchoTestMessage() {
        Iso8583Message message = factory.createEchoTestMessage();

        assertEquals("0800", message.getMti());
        assertEquals("301", message.getFieldAsString(70));
    }

    @Test
    @DisplayName("Should create key exchange message")
    void shouldCreateKeyExchangeMessage() {
        Iso8583Message message = factory.createKeyExchangeMessage();

        assertEquals("0800", message.getMti());
        assertEquals("101", message.getFieldAsString(70));
    }

    @Test
    @DisplayName("Should assemble and parse message")
    void shouldAssembleAndParseMessage() {
        // Create a message
        Iso8583Message original = factory.createMessage(MessageType.FINANCIAL_REQUEST);
        original.setField(3, "000000");
        original.setField(4, "000000010000");
        original.setField(11, "000001");
        original.setField(41, "TERM0001");
        original.setField(42, "MERCHANT001    ");

        // Assemble to bytes
        byte[] wireData = factory.assemble(original);
        assertNotNull(wireData);
        assertTrue(wireData.length > 0);

        // Parse back
        Iso8583Message parsed = factory.parse(wireData);

        assertEquals(original.getMti(), parsed.getMti());
        assertEquals(original.getFieldAsString(3), parsed.getFieldAsString(3));
        assertEquals(original.getFieldAsString(4), parsed.getFieldAsString(4));
        assertEquals(original.getFieldAsString(11), parsed.getFieldAsString(11));
        assertEquals(original.getFieldAsString(41), parsed.getFieldAsString(41));
        assertEquals(original.getFieldAsString(42), parsed.getFieldAsString(42));
    }

    @Test
    @DisplayName("Should not override existing institution ID")
    void shouldNotOverrideExistingInstitutionId() {
        Iso8583Message message = factory.createMessage(MessageType.FINANCIAL_REQUEST);
        message.setField(32, "999");

        factory.setTransactionFields(message);

        assertEquals("999", message.getFieldAsString(32));
    }
}
