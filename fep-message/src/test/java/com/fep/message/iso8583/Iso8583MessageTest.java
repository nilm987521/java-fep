package com.fep.message.iso8583;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Iso8583Message class.
 */
@DisplayName("ISO 8583 Message Tests")
class Iso8583MessageTest {

    @Test
    @DisplayName("Should create message with MTI string")
    void shouldCreateMessageWithMtiString() {
        Iso8583Message message = new Iso8583Message("0200");

        assertEquals("0200", message.getMti());
        assertEquals(MessageType.FINANCIAL_REQUEST, message.getMessageType());
    }

    @Test
    @DisplayName("Should create message with MessageType")
    void shouldCreateMessageWithMessageType() {
        Iso8583Message message = new Iso8583Message(MessageType.AUTH_REQUEST);

        assertEquals("0100", message.getMti());
        assertEquals(MessageType.AUTH_REQUEST, message.getMessageType());
    }

    @Test
    @DisplayName("Should set and get fields")
    void shouldSetAndGetFields() {
        Iso8583Message message = new Iso8583Message("0200");

        message.setField(2, "4111111111111111");
        message.setField(3, "000000");
        message.setField(4, "000000010000");

        assertEquals("4111111111111111", message.getField(2));
        assertEquals("000000", message.getField(3));
        assertEquals("000000010000", message.getField(4));
    }

    @Test
    @DisplayName("Should return field as string")
    void shouldReturnFieldAsString() {
        Iso8583Message message = new Iso8583Message("0200");

        message.setField(4, 12345);

        assertEquals("12345", message.getFieldAsString(4));
    }

    @Test
    @DisplayName("Should check if field exists")
    void shouldCheckIfFieldExists() {
        Iso8583Message message = new Iso8583Message("0200");

        message.setField(3, "000000");

        assertTrue(message.hasField(3));
        assertFalse(message.hasField(4));
    }

    @Test
    @DisplayName("Should remove field")
    void shouldRemoveField() {
        Iso8583Message message = new Iso8583Message("0200");

        message.setField(3, "000000");
        assertTrue(message.hasField(3));

        Object removed = message.removeField(3);

        assertEquals("000000", removed);
        assertFalse(message.hasField(3));
    }

    @Test
    @DisplayName("Should set field to null removes it")
    void shouldSetFieldToNullRemovesIt() {
        Iso8583Message message = new Iso8583Message("0200");

        message.setField(3, "000000");
        message.setField(3, null);

        assertFalse(message.hasField(3));
    }

    @Test
    @DisplayName("Should return all field numbers")
    void shouldReturnAllFieldNumbers() {
        Iso8583Message message = new Iso8583Message("0200");

        message.setField(2, "4111111111111111");
        message.setField(3, "000000");
        message.setField(4, "000000010000");
        message.setField(11, "000001");

        var fieldNumbers = message.getFieldNumbers();

        assertEquals(4, fieldNumbers.size());
        assertTrue(fieldNumbers.contains(2));
        assertTrue(fieldNumbers.contains(3));
        assertTrue(fieldNumbers.contains(4));
        assertTrue(fieldNumbers.contains(11));
    }

    @Test
    @DisplayName("Should detect need for secondary bitmap")
    void shouldDetectNeedForSecondaryBitmap() {
        Iso8583Message message = new Iso8583Message("0200");

        message.setField(3, "000000");
        assertFalse(message.hasSecondaryBitmap());

        message.setField(70, "301");
        assertTrue(message.hasSecondaryBitmap());
    }

    @Test
    @DisplayName("Should clear all fields")
    void shouldClearAllFields() {
        Iso8583Message message = new Iso8583Message("0200");

        message.setField(2, "4111111111111111");
        message.setField(3, "000000");
        message.setField(4, "000000010000");

        message.clear();

        assertTrue(message.getFieldNumbers().isEmpty());
    }

    @Test
    @DisplayName("Should reject invalid field numbers")
    void shouldRejectInvalidFieldNumbers() {
        Iso8583Message message = new Iso8583Message("0200");

        assertThrows(IllegalArgumentException.class, () -> message.setField(0, "value"));
        assertThrows(IllegalArgumentException.class, () -> message.setField(129, "value"));
        assertThrows(IllegalArgumentException.class, () -> message.getField(-1));
    }

    @Test
    @DisplayName("Should create response for request message")
    void shouldCreateResponseForRequestMessage() {
        Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        request.setField(2, "4111111111111111");
        request.setField(3, "000000");
        request.setField(4, "000000010000");
        request.setField(11, "000001");
        request.setField(37, "123456789012");
        request.setTraceId("TEST-001");

        Iso8583Message response = request.createResponse();

        assertEquals("0210", response.getMti());
        assertEquals(MessageType.FINANCIAL_RESPONSE, response.getMessageType());
        assertEquals("TEST-001", response.getTraceId());
        // Common fields should be copied
        assertEquals("4111111111111111", response.getField(2));
        assertEquals("000000", response.getField(3));
        assertEquals("000000010000", response.getField(4));
        assertEquals("000001", response.getField(11));
        assertEquals("123456789012", response.getField(37));
    }

    @Test
    @DisplayName("Should throw when creating response for non-request")
    void shouldThrowWhenCreatingResponseForNonRequest() {
        Iso8583Message response = new Iso8583Message(MessageType.FINANCIAL_RESPONSE);

        assertThrows(IllegalStateException.class, response::createResponse);
    }

    @Test
    @DisplayName("Should provide toString representation")
    void shouldProvideToStringRepresentation() {
        Iso8583Message message = new Iso8583Message("0200");
        message.setField(3, "000000");
        message.setTraceId("TEST-001");

        String str = message.toString();

        assertTrue(str.contains("0200"));
        assertTrue(str.contains("TEST-001"));
    }

    @Test
    @DisplayName("Should provide detailed string representation")
    void shouldProvideDetailedStringRepresentation() {
        Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        message.setField(2, "4111111111111111");
        message.setField(3, "000000");

        String detail = message.toDetailString();

        assertTrue(detail.contains("0200"));
        assertTrue(detail.contains("Financial Request"));
        assertTrue(detail.contains("F002"));
        assertTrue(detail.contains("F003"));
        // PAN should be masked
        assertTrue(detail.contains("411111******1111"));
    }

    @Test
    @DisplayName("Should have timestamp")
    void shouldHaveTimestamp() {
        long before = System.currentTimeMillis();
        Iso8583Message message = new Iso8583Message("0200");
        long after = System.currentTimeMillis();

        assertTrue(message.getTimestamp() >= before);
        assertTrue(message.getTimestamp() <= after);
    }
}
