package com.fep.message.generic.parser;

import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GenericMessageParserAssemblerTest {

    private final GenericMessageAssembler assembler = new GenericMessageAssembler();
    private final GenericMessageParser parser = new GenericMessageParser();

    @Test
    void shouldAssembleAndParseSimpleMessage() {
        String json = """
            {
              "name": "Simple Test",
              "fields": [
                { "id": "code", "length": 2, "encoding": "ASCII", "required": true },
                { "id": "terminalId", "length": 8, "encoding": "ASCII" },
                { "id": "amount", "length": 6, "encoding": "BCD" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        GenericMessage original = new GenericMessage(schema);
        original.setField("code", "11");
        original.setField("terminalId", "ATM12345");
        original.setField("amount", "100000");

        // Assemble
        byte[] bytes = assembler.assemble(original);

        // code (2 ASCII) + terminalId (8 ASCII) + amount (3 BCD) = 13 bytes
        assertEquals(13, bytes.length);

        // Parse
        GenericMessage parsed = parser.parse(bytes, schema);

        assertEquals("11", parsed.getFieldAsString("code"));
        assertEquals("ATM12345", parsed.getFieldAsString("terminalId"));
        assertEquals("100000", parsed.getFieldAsString("amount"));
    }

    @Test
    void shouldHandleVariableLengthFields() {
        String json = """
            {
              "name": "LLVAR Test",
              "fields": [
                { "id": "fixedField", "length": 4, "encoding": "ASCII" },
                { "id": "varField", "length": 99, "lengthType": "LLVAR", "encoding": "ASCII", "lengthEncoding": "ASCII" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        GenericMessage original = new GenericMessage(schema);
        original.setField("fixedField", "TEST");
        original.setField("varField", "Hello World");

        byte[] bytes = assembler.assemble(original);

        // fixedField (4) + length prefix (2 ASCII) + varField data (11) = 17 bytes
        assertEquals(17, bytes.length);

        GenericMessage parsed = parser.parse(bytes, schema);

        assertEquals("TEST", parsed.getFieldAsString("fixedField"));
        assertEquals("Hello World", parsed.getFieldAsString("varField"));
    }

    @Test
    void shouldHandleBcdLengthPrefix() {
        String json = """
            {
              "name": "BCD Length Test",
              "fields": [
                { "id": "pan", "length": 19, "lengthType": "LLVAR", "encoding": "BCD", "lengthEncoding": "BCD" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        GenericMessage original = new GenericMessage(schema);
        original.setField("pan", "4111111111111111");  // 16 digits

        byte[] bytes = assembler.assemble(original);

        // Length prefix (1 BCD byte for "16") + PAN (8 BCD bytes for 16 digits) = 9 bytes
        assertEquals(9, bytes.length);

        GenericMessage parsed = parser.parse(bytes, schema);

        assertEquals("4111111111111111", parsed.getFieldAsString("pan"));
    }

    @Test
    void shouldHandleHexFields() {
        String json = """
            {
              "name": "HEX Test",
              "fields": [
                { "id": "pinBlock", "length": 8, "encoding": "HEX" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        GenericMessage original = new GenericMessage(schema);
        original.setField("pinBlock", "DEADBEEF12345678");

        byte[] bytes = assembler.assemble(original);

        assertEquals(8, bytes.length);

        GenericMessage parsed = parser.parse(bytes, schema);

        assertEquals("DEADBEEF12345678", parsed.getFieldAsString("pinBlock"));
    }

    @Test
    void shouldHandleMessageWithHeader() {
        String json = """
            {
              "name": "Header Test",
              "header": {
                "includeLength": true,
                "lengthBytes": 2,
                "lengthEncoding": "BINARY",
                "lengthIncludesHeader": false,
                "fields": [
                  { "id": "protocolId", "length": 2, "encoding": "HEX", "defaultValue": "6000" }
                ]
              },
              "fields": [
                { "id": "command", "length": 2, "encoding": "ASCII" },
                { "id": "data", "length": 10, "encoding": "ASCII" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        GenericMessage original = new GenericMessage(schema);
        original.setField("command", "11");
        original.setField("data", "TESTDATA00");

        byte[] bytes = assembler.assemble(original);

        // Length header (2) + protocolId (2) + command (2) + data (10) = 16 bytes
        assertEquals(16, bytes.length);

        // First 2 bytes should be length (14 in big-endian)
        int length = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertEquals(14, length);  // 2 + 2 + 10 = 14 (excludes length header itself)
    }

    @Test
    void shouldApplyDefaultValues() {
        String json = """
            {
              "name": "Default Value Test",
              "fields": [
                { "id": "required", "length": 4, "encoding": "ASCII", "required": true },
                { "id": "withDefault", "length": 3, "encoding": "ASCII", "defaultValue": "ABC" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        GenericMessage original = new GenericMessage(schema);
        original.setField("required", "TEST");
        // Not setting withDefault - should use default

        byte[] bytes = assembler.assemble(original);

        // required (4) + withDefault (3) = 7 bytes
        assertEquals(7, bytes.length);

        GenericMessage parsed = parser.parse(bytes, schema);

        assertEquals("TEST", parsed.getFieldAsString("required"));
        assertEquals("ABC", parsed.getFieldAsString("withDefault"));
    }

    @Test
    void shouldHandleMixedEncodings() {
        String json = """
            {
              "name": "Mixed Encoding Test",
              "fields": [
                { "id": "asciiField", "length": 4, "encoding": "ASCII" },
                { "id": "bcdField", "length": 6, "encoding": "BCD" },
                { "id": "hexField", "length": 2, "encoding": "HEX" },
                { "id": "asciiField2", "length": 3, "encoding": "ASCII" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        GenericMessage original = new GenericMessage(schema);
        original.setField("asciiField", "TEST");
        original.setField("bcdField", "123456");
        original.setField("hexField", "ABCD");
        original.setField("asciiField2", "END");

        byte[] bytes = assembler.assemble(original);

        // ASCII (4) + BCD (3) + HEX (2) + ASCII (3) = 12 bytes
        assertEquals(12, bytes.length);

        GenericMessage parsed = parser.parse(bytes, schema);

        assertEquals("TEST", parsed.getFieldAsString("asciiField"));
        assertEquals("123456", parsed.getFieldAsString("bcdField"));
        assertEquals("ABCD", parsed.getFieldAsString("hexField"));
        assertEquals("END", parsed.getFieldAsString("asciiField2"));
    }
}
