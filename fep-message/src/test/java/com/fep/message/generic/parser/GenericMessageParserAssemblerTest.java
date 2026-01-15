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

    @Test
    void shouldEncodeBitmapBasedOnPresentFields() {
        String json = """
            {
              "name": "Bitmap Test",
              "fields": [
                { "id": "mti", "length": 4, "encoding": "ASCII" },
                { "id": "bitmap", "length": 8, "type": "BITMAP", "controls": ["field1", "field2", "field3", "field4", "field5", "field6", "field7", "field8"] },
                { "id": "field1", "length": 4, "encoding": "ASCII" },
                { "id": "field2", "length": 4, "encoding": "ASCII" },
                { "id": "field3", "length": 4, "encoding": "ASCII" },
                { "id": "field4", "length": 4, "encoding": "ASCII" },
                { "id": "field5", "length": 4, "encoding": "ASCII" },
                { "id": "field6", "length": 4, "encoding": "ASCII" },
                { "id": "field7", "length": 4, "encoding": "ASCII" },
                { "id": "field8", "length": 4, "encoding": "ASCII" }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        GenericMessage original = new GenericMessage(schema);
        original.setField("mti", "0200");
        // Only set field1 and field3 - bitmap should be 0b10100000 = 0xA0 for first byte
        original.setField("field1", "AAA1");
        original.setField("field3", "CCC3");

        // Display request message before assembly (bitmap auto-generated)
        System.out.println("Request message (before assembly):");
        System.out.println(original.toString(true));

        byte[] bytes = assembler.assemble(original);

        // mti (4) + bitmap (8) + field1 (4) + field3 (4) = 20 bytes
        assertEquals(20, bytes.length);

        // Check bitmap at position 4-11 (after mti)
        // First byte should be 0xA0 (bit 0 and bit 2 set = field1 and field3)
        assertEquals((byte) 0xA0, bytes[4]);
        // Rest of bitmap should be zeros
        for (int i = 5; i < 12; i++) {
            assertEquals((byte) 0x00, bytes[i]);
        }
    }

    @Test
    void shouldEncodeFiscAtmMessage() {
        String json = """
            {
              "name": "FISC ATM Format",
              "header": {
                "includeLength": true,
                "lengthBytes": 4,
                "lengthEncoding": "ASCII",
                "lengthIncludesHeader": false
              },
              "fields": [
                { "id": "mti", "length": 4, "encoding": "ASCII", "required": true },
                { "id": "bitmap", "length": 8, "type": "BITMAP", "controls": ["pan", "processingCode", "amount", "stan", "terminalId"] },
                { "id": "pan", "length": 19, "encoding": "BCD", "sensitive": true },
                { "id": "processingCode", "length": 6, "encoding": "ASCII", "required": true },
                { "id": "amount", "length": 12, "encoding": "ASCII" },
                { "id": "stan", "length": 6, "encoding": "ASCII", "required": true },
                { "id": "terminalId", "length": 8, "encoding": "ASCII", "required": true }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        GenericMessage original = new GenericMessage(schema);
        original.setField("mti", "0200");
        original.setField("processingCode", "010000");
        original.setField("amount", "000000000001");
        original.setField("stan", "123456");
        original.setField("terminalId", "00000000");

        byte[] bytes = assembler.assemble(original);

        // Length header (4) + mti (4) + bitmap (8) + processingCode (6) + amount (12) + stan (6) + terminalId (8) = 48 bytes
        assertEquals(48, bytes.length);

        // Verify length header "0044" (body length = 44)
        assertEquals("0044", new String(bytes, 0, 4));

        // Verify MTI "0200" at position 4
        assertEquals("0200", new String(bytes, 4, 4));

        // Verify bitmap at position 8-15
        // Controls: pan(0), processingCode(1), amount(2), stan(3), terminalId(4)
        // Present: processingCode(1), amount(2), stan(3), terminalId(4)
        // Bitmap byte 0: bit 6,5,4,3 set = 0x78
        assertEquals((byte) 0x78, bytes[8]);

        // Print hex for debugging
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b));
        }
        System.out.println("Assembled message: " + hex);
    }

    @Test
    void shouldParseBitmapControlledFieldsCorrectly() {
        // This test verifies that the parser respects the bitmap when parsing fields
        String json = """
            {
              "name": "Bitmap Parse Test",
              "fields": [
                { "id": "mti", "length": 4, "encoding": "ASCII", "required": true },
                { "id": "bitmap", "length": 8, "type": "BITMAP", "controls": ["pan", "processingCode", "amount", "stan", "terminalId"] },
                { "id": "pan", "length": 19, "encoding": "BCD", "sensitive": true },
                { "id": "processingCode", "length": 6, "encoding": "ASCII", "required": true },
                { "id": "amount", "length": 12, "encoding": "ASCII" },
                { "id": "stan", "length": 6, "encoding": "ASCII", "required": true },
                { "id": "terminalId", "length": 8, "encoding": "ASCII", "required": true }
              ]
            }
            """;
        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        // Build test data:
        // mti = "0200", bitmap = 0x78 (bits 1,2,3,4 = processingCode, amount, stan, terminalId)
        // NO pan (bit 0 not set)
        // processingCode = "010000", amount = "000000000001", stan = "123456", terminalId = "00000000"
        byte[] testData = hexToBytes(
            "30323030" +                         // mti = "0200"
            "7800000000000000" +                 // bitmap = 0x78 (bits 1-4 set, pan bit 0 not set)
            "303130303030" +                     // processingCode = "010000"
            "303030303030303030303031" +         // amount = "000000000001"
            "313233343536" +                     // stan = "123456"
            "3030303030303030"                   // terminalId = "00000000"
        );

        GenericMessage parsed = parser.parse(testData, schema);

        // Verify fields
        assertEquals("0200", parsed.getFieldAsString("mti"));
        assertNull(parsed.getField("pan"), "pan should not be parsed (bit 0 not set)");
        assertEquals("010000", parsed.getFieldAsString("processingCode"));
        assertEquals("000000000001", parsed.getFieldAsString("amount"));
        assertEquals("123456", parsed.getFieldAsString("stan"));
        assertEquals("00000000", parsed.getFieldAsString("terminalId"));

        System.out.println("Parsed message with bitmap control:");
        System.out.println(parsed.toString(true));
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
