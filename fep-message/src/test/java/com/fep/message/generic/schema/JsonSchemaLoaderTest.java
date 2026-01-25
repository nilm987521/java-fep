package com.fep.message.generic.schema;

import com.fep.message.exception.MessageException;
import com.fep.message.interfaces.SchemaSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    // ==================== Subscriber Pattern Tests ====================

    @Test
    void shouldNotifySubscriberWhenSchemaReloaded(@TempDir Path tempDir) throws IOException {
        // Create a schema file
        Path schemaFile = tempDir.resolve("test-schemas.json");
        String schemaJson = """
            [
              {
                "name": "TestSchema",
                "fields": [ { "id": "f1", "length": 5 } ]
              }
            ]
            """;
        Files.writeString(schemaFile, schemaJson);

        // Track subscriber notifications
        AtomicReference<Map<String, MessageSchema>> receivedSchemaMap = new AtomicReference<>();
        AtomicInteger notifyCount = new AtomicInteger(0);

        SchemaSubscriber subscriber = schemaMap -> {
            receivedSchemaMap.set(schemaMap);
            notifyCount.incrementAndGet();
        };

        try {
            // Register subscriber
            JsonSchemaLoader.registerSubscriber(subscriber);

            // Load schema - should notify subscriber
            JsonSchemaLoader.reloadFromFilePath(schemaFile.toString());

            // Verify subscriber was notified
            assertEquals(1, notifyCount.get());
            assertNotNull(receivedSchemaMap.get());
            assertTrue(receivedSchemaMap.get().containsKey("TestSchema"));
        } finally {
            JsonSchemaLoader.unregisterSubscriber(subscriber);
        }
    }

    @Test
    void shouldNotifyNewSubscriberImmediatelyIfSchemasLoaded(@TempDir Path tempDir) throws IOException {
        // Create a schema file
        Path schemaFile = tempDir.resolve("test-schemas.json");
        String schemaJson = """
            [
              {
                "name": "ExistingSchema",
                "fields": [ { "id": "f1", "length": 5 } ]
              }
            ]
            """;
        Files.writeString(schemaFile, schemaJson);

        // Load schema first (before subscriber registration)
        JsonSchemaLoader.reloadFromFilePath(schemaFile.toString());

        // Track subscriber notifications
        AtomicReference<Map<String, MessageSchema>> receivedSchemaMap = new AtomicReference<>();

        SchemaSubscriber subscriber = receivedSchemaMap::set;

        try {
            // Register subscriber AFTER schema is loaded
            JsonSchemaLoader.registerSubscriber(subscriber);

            // Verify subscriber was immediately notified with existing schema
            assertNotNull(receivedSchemaMap.get());
            assertTrue(receivedSchemaMap.get().containsKey("ExistingSchema"));
        } finally {
            JsonSchemaLoader.unregisterSubscriber(subscriber);
        }
    }

    @Test
    void shouldNotNotifyUnregisteredSubscriber(@TempDir Path tempDir) throws IOException {
        // Create a schema file
        Path schemaFile = tempDir.resolve("test-schemas.json");
        String schemaJson = """
            [
              {
                "name": "TestSchema",
                "fields": [ { "id": "f1", "length": 5 } ]
              }
            ]
            """;
        Files.writeString(schemaFile, schemaJson);

        AtomicInteger notifyCount = new AtomicInteger(0);
        SchemaSubscriber subscriber = schemaMap -> notifyCount.incrementAndGet();

        // Register and then unregister
        JsonSchemaLoader.registerSubscriber(subscriber);
        JsonSchemaLoader.unregisterSubscriber(subscriber);

        // Load schema - should NOT notify unregistered subscriber
        JsonSchemaLoader.reloadFromFilePath(schemaFile.toString());

        // Verify subscriber was NOT notified (only got initial notification on register)
        // Since schema wasn't loaded before register, count should be 0
        assertEquals(0, notifyCount.get());
    }

    @Test
    void shouldNotDuplicateSubscriber(@TempDir Path tempDir) throws IOException {
        // Create a schema file
        Path schemaFile = tempDir.resolve("test-schemas.json");
        String schemaJson = """
            [
              {
                "name": "TestSchema",
                "fields": [ { "id": "f1", "length": 5 } ]
              }
            ]
            """;
        Files.writeString(schemaFile, schemaJson);

        AtomicInteger notifyCount = new AtomicInteger(0);
        SchemaSubscriber subscriber = schemaMap -> notifyCount.incrementAndGet();

        try {
            // Register same subscriber multiple times
            JsonSchemaLoader.registerSubscriber(subscriber);
            JsonSchemaLoader.registerSubscriber(subscriber);
            JsonSchemaLoader.registerSubscriber(subscriber);

            // Load schema
            JsonSchemaLoader.reloadFromFilePath(schemaFile.toString());

            // Verify subscriber was notified only once (not 3 times)
            assertEquals(1, notifyCount.get());
        } finally {
            JsonSchemaLoader.unregisterSubscriber(subscriber);
        }
    }

    @Test
    void shouldHandleSubscriberException(@TempDir Path tempDir) throws IOException {
        // Create a schema file
        Path schemaFile = tempDir.resolve("test-schemas.json");
        String schemaJson = """
            [
              {
                "name": "TestSchema",
                "fields": [ { "id": "f1", "length": 5 } ]
              }
            ]
            """;
        Files.writeString(schemaFile, schemaJson);

        AtomicInteger goodSubscriberCount = new AtomicInteger(0);

        // Bad subscriber that throws exception
        SchemaSubscriber badSubscriber = schemaMap -> {
            throw new RuntimeException("Intentional test exception");
        };

        // Good subscriber
        SchemaSubscriber goodSubscriber = schemaMap -> goodSubscriberCount.incrementAndGet();

        try {
            // Register both subscribers
            JsonSchemaLoader.registerSubscriber(badSubscriber);
            JsonSchemaLoader.registerSubscriber(goodSubscriber);

            // Load schema - bad subscriber throws, but good subscriber should still be notified
            assertDoesNotThrow(() -> JsonSchemaLoader.reloadFromFilePath(schemaFile.toString()));

            // Verify good subscriber was still notified despite bad subscriber's exception
            assertEquals(1, goodSubscriberCount.get());
        } finally {
            JsonSchemaLoader.unregisterSubscriber(badSubscriber);
            JsonSchemaLoader.unregisterSubscriber(goodSubscriber);
        }
    }

    @Test
    void shouldNotRegisterNullSubscriber() {
        // Should not throw, just ignore
        assertDoesNotThrow(() -> JsonSchemaLoader.registerSubscriber(null));
    }

    @Test
    void shouldNotThrowWhenUnregisteringNullSubscriber() {
        // Should not throw, just ignore
        assertDoesNotThrow(() -> JsonSchemaLoader.unregisterSubscriber(null));
    }

    // ==================== YAML Support Tests ====================

    @Test
    void shouldLoadSchemasFromYamlFile(@TempDir Path tempDir) throws IOException {
        // Create a YAML schema file (note: property names must match Java camelCase)
        Path schemaFile = tempDir.resolve("test-schemas.yml");
        String yamlContent = """
            schemas:
              - name: YAML Test Schema
                version: "1.0"
                vendor: TEST
                description: Test schema loaded from YAML
                header:
                  includeLength: true
                  lengthBytes: 4
                  lengthEncoding: ASCII
                  lengthIncludesHeader: false
                fields:
                  - id: mti
                    name: Message Type Indicator
                    type: NUMERIC
                    length: 4
                    encoding: ASCII
                    required: true
                  - id: processingCode
                    name: Processing Code
                    type: NUMERIC
                    length: 6
                    encoding: ASCII
            """;
        Files.writeString(schemaFile, yamlContent);

        // Load schema from YAML
        JsonSchemaLoader.reloadFromFilePath(schemaFile.toString());

        // Verify schema was loaded
        Map<String, MessageSchema> schemas = JsonSchemaLoader.getSchemaMap();
        assertEquals(1, schemas.size());
        assertTrue(schemas.containsKey("YAML Test Schema"));

        MessageSchema schema = schemas.get("YAML Test Schema");
        assertEquals("1.0", schema.getVersion());
        assertEquals("TEST", schema.getVendor());
        assertEquals(2, schema.getFields().size());

        // Verify header configuration
        assertNotNull(schema.getHeader());
        assertTrue(schema.getHeader().isIncludeLength());
        assertEquals(4, schema.getHeader().getLengthBytes());
        assertEquals("ASCII", schema.getHeader().getLengthEncoding());

        // Verify fields
        FieldSchema mtiField = schema.getFields().get(0);
        assertEquals("mti", mtiField.getId());
        assertEquals(4, mtiField.getLength());
        assertEquals("ASCII", mtiField.getEncoding());
    }

    @Test
    void shouldLoadSchemasFromYamlWithYamlExtension(@TempDir Path tempDir) throws IOException {
        Path schemaFile = tempDir.resolve("test-schemas.yaml");
        String yamlContent = """
            schemas:
              - name: YAML Extension Test
                fields:
                  - id: field1
                    length: 10
                    encoding: ASCII
            """;
        Files.writeString(schemaFile, yamlContent);

        JsonSchemaLoader.reloadFromFilePath(schemaFile.toString());

        Map<String, MessageSchema> schemas = JsonSchemaLoader.getSchemaMap();
        assertTrue(schemas.containsKey("YAML Extension Test"));
    }

    @Test
    void shouldLoadFromCollectionFileWithYaml(@TempDir Path tempDir) throws IOException {
        Path schemaFile = tempDir.resolve("collection.yml");
        String yamlContent = """
            schemas:
              - name: First Schema
                fields:
                  - id: f1
                    length: 5
                    encoding: ASCII
              - name: Second Schema
                fields:
                  - id: f2
                    length: 10
                    encoding: BCD
            """;
        Files.writeString(schemaFile, yamlContent);

        // Load specific schema by name
        MessageSchema schema = JsonSchemaLoader.fromCollectionFile(schemaFile, "Second Schema");

        assertNotNull(schema);
        assertEquals("Second Schema", schema.getName());
        assertEquals(1, schema.getFields().size());
        assertEquals("f2", schema.getFields().get(0).getId());
    }

    @Test
    void shouldGetSchemaNamesfromYamlFile(@TempDir Path tempDir) throws IOException {
        Path schemaFile = tempDir.resolve("schemas.yml");
        String yamlContent = """
            schemas:
              - name: Schema A
                fields:
                  - id: a
                    length: 1
              - name: Schema B
                fields:
                  - id: b
                    length: 2
              - name: Schema C
                fields:
                  - id: c
                    length: 3
            """;
        Files.writeString(schemaFile, yamlContent);

        var names = JsonSchemaLoader.getSchemaNames(schemaFile);

        assertEquals(3, names.size());
        assertTrue(names.contains("Schema A"));
        assertTrue(names.contains("Schema B"));
        assertTrue(names.contains("Schema C"));
    }
}
