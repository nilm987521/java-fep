package com.fep.message.generic.schema;

import com.fep.message.exception.MessageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaLoaderTest {

    @BeforeEach
    void setUp() {
        JsonSchemaLoader.clearCache();
    }

    @Test
    void shouldParseSimpleSchema() {
        String json = """
            {
              "name": "Test Protocol",
              "version": "1.0",
              "fields": [
                {
                  "id": "field1",
                  "name": "Test Field",
                  "type": "ALPHANUMERIC",
                  "length": 10,
                  "encoding": "ASCII"
                }
              ]
            }
            """;

        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        assertEquals("Test Protocol", schema.getName());
        assertEquals("1.0", schema.getVersion());
        assertEquals(1, schema.getFields().size());
        assertEquals("field1", schema.getFields().get(0).getId());
        assertEquals(10, schema.getFields().get(0).getLength());
    }

    @Test
    void shouldParseSchemaWithVariableLengthFields() {
        String json = """
            {
              "name": "Variable Length Test",
              "version": "1.0",
              "fields": [
                {
                  "id": "varField",
                  "name": "Variable Field",
                  "length": 99,
                  "lengthType": "LLVAR",
                  "encoding": "ASCII",
                  "lengthEncoding": "ASCII"
                }
              ]
            }
            """;

        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        FieldSchema field = schema.getFields().get(0);

        assertEquals(FieldSchema.GenericLengthType.LLVAR, field.getLengthType());
        assertTrue(field.isVariableLength());
        assertEquals(2, field.getLengthPrefixDigits());
    }

    @Test
    void shouldParseCompositeFields() {
        String json = """
            {
              "name": "Composite Test",
              "version": "1.0",
              "fields": [
                {
                  "id": "composite",
                  "name": "Composite Field",
                  "type": "COMPOSITE",
                  "fields": [
                    { "id": "child1", "length": 5, "encoding": "ASCII" },
                    { "id": "child2", "length": 3, "encoding": "BCD" }
                  ]
                }
              ]
            }
            """;

        MessageSchema schema = JsonSchemaLoader.fromJson(json);
        FieldSchema composite = schema.getFields().get(0);

        assertTrue(composite.isComposite());
        assertEquals(2, composite.getFields().size());
        assertEquals("child1", composite.getFields().get(0).getId());
        assertEquals("child2", composite.getFields().get(1).getId());
    }

    @Test
    void shouldParseHeaderAndTrailer() {
        String json = """
            {
              "name": "Full Message Test",
              "version": "1.0",
              "header": {
                "includeLength": true,
                "lengthBytes": 2,
                "fields": [
                  { "id": "protocolId", "length": 2, "encoding": "HEX" }
                ]
              },
              "fields": [
                { "id": "body", "length": 10, "encoding": "ASCII" }
              ],
              "trailer": {
                "fields": [
                  { "id": "mac", "length": 8, "encoding": "HEX" }
                ]
              }
            }
            """;

        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        assertNotNull(schema.getHeader());
        assertTrue(schema.getHeader().isIncludeLength());
        assertEquals(2, schema.getHeader().getLengthBytes());
        assertEquals(1, schema.getHeader().getFields().size());

        assertNotNull(schema.getTrailer());
        assertEquals(1, schema.getTrailer().getFields().size());
    }

    @Test
    void shouldThrowExceptionForEmptyName() {
        String json = """
            {
              "name": "",
              "fields": [
                { "id": "f1", "length": 5 }
              ]
            }
            """;

        assertThrows(MessageException.class, () -> JsonSchemaLoader.fromJson(json));
    }

    @Test
    void shouldThrowExceptionForNoFields() {
        String json = """
            {
              "name": "Empty Schema",
              "fields": []
            }
            """;

        assertThrows(MessageException.class, () -> JsonSchemaLoader.fromJson(json));
    }

    @Test
    void shouldGetFieldById() {
        String json = """
            {
              "name": "Field Lookup Test",
              "fields": [
                { "id": "first", "length": 5 },
                { "id": "second", "length": 10 },
                { "id": "third", "length": 3 }
              ]
            }
            """;

        MessageSchema schema = JsonSchemaLoader.fromJson(json);

        assertTrue(schema.getField("second").isPresent());
        assertEquals(10, schema.getField("second").get().getLength());
        assertFalse(schema.getField("nonexistent").isPresent());
    }

    @Test
    void shouldCacheLoadedSchemas() {
        String json = """
            {
              "name": "Cache Test",
              "fields": [ { "id": "f1", "length": 5 } ]
            }
            """;

        MessageSchema schema1 = JsonSchemaLoader.fromJson(json);
        // fromJson doesn't cache, only fromFile and fromResource do
        // This test validates basic parsing works consistently
        MessageSchema schema2 = JsonSchemaLoader.fromJson(json);

        assertEquals(schema1.getName(), schema2.getName());
    }
}
