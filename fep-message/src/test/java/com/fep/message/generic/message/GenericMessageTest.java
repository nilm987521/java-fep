package com.fep.message.generic.message;

import com.fep.message.generic.schema.FieldSchema;
import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GenericMessageTest {

    private MessageSchema schema;

    @BeforeEach
    void setUp() {
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                { "id": "terminalId", "length": 8, "encoding": "ASCII", "required": true },
                { "id": "amount", "length": 12, "encoding": "BCD", "type": "NUMERIC" },
                { "id": "cardNumber", "length": 19, "encoding": "ASCII", "sensitive": true },
                { "id": "track2", "length": 37, "lengthType": "LLVAR", "encoding": "ASCII" }
              ]
            }
            """;
        schema = JsonSchemaLoader.fromJson(json);
    }

    @Test
    void shouldSetAndGetFields() {
        GenericMessage message = new GenericMessage(schema);

        message.setField("terminalId", "ATM12345");
        message.setField("amount", "000000100000");

        assertEquals("ATM12345", message.getFieldAsString("terminalId"));
        assertEquals("000000100000", message.getFieldAsString("amount"));
    }

    @Test
    void shouldCheckIfFieldExists() {
        GenericMessage message = new GenericMessage(schema);

        message.setField("terminalId", "ATM12345");

        assertTrue(message.hasField("terminalId"));
        assertFalse(message.hasField("amount"));
    }

    @Test
    void shouldHandleNestedFields() {
        String json = """
            {
              "name": "Composite Test",
              "fields": [
                {
                  "id": "deviceStatus",
                  "type": "COMPOSITE",
                  "fields": [
                    { "id": "cardReader", "length": 1 },
                    { "id": "dispenser", "length": 1 }
                  ]
                }
              ]
            }
            """;
        MessageSchema compositeSchema = JsonSchemaLoader.fromJson(json);
        GenericMessage message = new GenericMessage(compositeSchema);

        message.setNestedField("deviceStatus", "cardReader", "0");
        message.setNestedField("deviceStatus", "dispenser", "1");

        assertEquals("0", message.getNestedField("deviceStatus", "cardReader"));
        assertEquals("1", message.getNestedField("deviceStatus", "dispenser"));
    }

    @Test
    void shouldApplyVariables() {
        GenericMessage message = new GenericMessage(schema);

        message.setField("terminalId", "${atmId}");
        message.setField("amount", "${amt}");

        Map<String, String> variables = new HashMap<>();
        variables.put("atmId", "ATM99999");
        variables.put("amt", "000000050000");

        message.applyVariables(variables);

        assertEquals("ATM99999", message.getFieldAsString("terminalId"));
        assertEquals("000000050000", message.getFieldAsString("amount"));
    }

    @Test
    void shouldValidateRequiredFields() {
        GenericMessage message = new GenericMessage(schema);
        // terminalId is required but not set

        GenericMessage.ValidationResult result = message.validate();

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("terminalId")));
    }

    @Test
    void shouldValidateFieldLength() {
        GenericMessage message = new GenericMessage(schema);

        message.setField("terminalId", "ATM123456789012345"); // Too long

        GenericMessage.ValidationResult result = message.validate();

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("exceeds max length")));
    }

    @Test
    void shouldValidateNumericFields() {
        GenericMessage message = new GenericMessage(schema);

        message.setField("terminalId", "ATM12345");
        message.setField("amount", "ABC"); // Not numeric

        GenericMessage.ValidationResult result = message.validate();

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("numeric")));
    }

    @Test
    void shouldPassValidationWhenAllFieldsValid() {
        GenericMessage message = new GenericMessage(schema);

        message.setField("terminalId", "ATM12345");
        message.setField("amount", "000000100000");

        GenericMessage.ValidationResult result = message.validate();

        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void shouldCopyMessage() {
        GenericMessage original = new GenericMessage(schema);
        original.setField("terminalId", "ATM12345");
        original.setField("amount", "000000100000");

        GenericMessage copy = original.copy();

        assertEquals(original.getFieldAsString("terminalId"), copy.getFieldAsString("terminalId"));
        assertEquals(original.getFieldAsString("amount"), copy.getFieldAsString("amount"));

        // Modify copy should not affect original
        copy.setField("terminalId", "ATM99999");
        assertEquals("ATM12345", original.getFieldAsString("terminalId"));
    }

    @Test
    void shouldMaskSensitiveFieldsInToString() {
        GenericMessage message = new GenericMessage(schema);

        message.setField("terminalId", "ATM12345");
        message.setField("cardNumber", "4111111111111111");

        String str = message.toString();

        assertTrue(str.contains("ATM12345"));
        assertTrue(str.contains("****"));  // cardNumber should be masked
        assertFalse(str.contains("4111111111111111"));
    }
}
