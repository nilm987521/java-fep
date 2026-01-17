package com.fep.message.service;

import com.fep.message.channel.Channel;
import com.fep.message.channel.ChannelConfigException;
import com.fep.message.channel.ChannelSchemaRegistry;
import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChannelMessageService.
 */
@DisplayName("ChannelMessageService Tests")
class ChannelMessageServiceTest {

    private ChannelSchemaRegistry registry;
    private ChannelMessageService service;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        registry = ChannelSchemaRegistry.getInstance();
        registry.clear();
        JsonSchemaLoader.clearCache();

        // Create temp directory for test files
        tempDir = Files.createTempDirectory("channel-service-test");

        // Create and load test schemas
        Path schemaFile = tempDir.resolve("test-schemas.json");
        Files.writeString(schemaFile, createTestSchemaJson());
        JsonSchemaLoader.reloadFromFilePath(schemaFile.toString());

        // Load test channel config
        registry.loadFromJson(createTestConfigJson());

        // Create service
        service = new ChannelMessageService(registry);
    }

    @AfterEach
    void tearDown() throws IOException {
        registry.clear();
        JsonSchemaLoader.clearCache();

        // Clean up temp files
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    // ==================== Basic Query Tests ====================

    @Test
    @DisplayName("Should get channel by ID")
    void shouldGetChannelById() {
        // When
        Optional<Channel> channel = service.getChannel("ATM_TEST_V1");

        // Then
        assertTrue(channel.isPresent());
        assertEquals("Test ATM Channel", channel.get().getName());
        assertEquals("ATM", channel.get().getType());
    }

    @Test
    @DisplayName("Should return empty for non-existent channel")
    void shouldReturnEmptyForNonExistentChannel() {
        // When
        Optional<Channel> channel = service.getChannel("NONEXISTENT");

        // Then
        assertFalse(channel.isPresent());
    }

    @Test
    @DisplayName("Should get channel or throw")
    void shouldGetChannelOrThrow() {
        // When
        Channel channel = service.getChannelOrThrow("ATM_TEST_V1");

        // Then
        assertEquals("Test ATM Channel", channel.getName());
    }

    @Test
    @DisplayName("Should throw exception when channel not found")
    void shouldThrowExceptionWhenChannelNotFound() {
        // When & Then
        assertThrows(ChannelConfigException.class, () ->
                service.getChannelOrThrow("NONEXISTENT"));
    }

    @Test
    @DisplayName("Should check if channel exists")
    void shouldCheckIfChannelExists() {
        // Then
        assertTrue(service.hasChannel("ATM_TEST_V1"));
        assertFalse(service.hasChannel("NONEXISTENT"));
    }

    // ==================== Schema Resolution Tests ====================

    @Test
    @DisplayName("Should get request schema")
    void shouldGetRequestSchema() {
        // When
        MessageSchema schema = service.getRequestSchema("ATM_TEST_V1", "0200");

        // Then
        assertNotNull(schema);
        assertEquals("Test ATM Schema", schema.getName());
    }

    @Test
    @DisplayName("Should get response schema")
    void shouldGetResponseSchema() {
        // When
        MessageSchema schema = service.getResponseSchema("ATM_TEST_V1", "0210");

        // Then
        assertNotNull(schema);
        assertEquals("Test ATM Schema", schema.getName());
    }

    @Test
    @DisplayName("Should get default request schema")
    void shouldGetDefaultRequestSchema() {
        // When
        MessageSchema schema = service.getDefaultRequestSchema("POS_TEST_V1");

        // Then
        assertNotNull(schema);
        assertEquals("Test POS Schema", schema.getName());
    }

    @Test
    @DisplayName("Should get default response schema")
    void shouldGetDefaultResponseSchema() {
        // When
        MessageSchema schema = service.getDefaultResponseSchema("POS_TEST_V1");

        // Then
        assertNotNull(schema);
        assertEquals("Test POS Schema", schema.getName());
    }

    // ==================== Channel Properties Tests ====================

    @Test
    @DisplayName("Should get channel property")
    void shouldGetChannelProperty() {
        // When
        String encoding = service.getChannelProperty("ATM_TEST_V1", "encoding");

        // Then
        assertEquals("ASCII", encoding);
    }

    @Test
    @DisplayName("Should return null for non-existent property")
    void shouldReturnNullForNonExistentProperty() {
        // When
        String value = service.getChannelProperty("ATM_TEST_V1", "nonexistent");

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Should check if MAC is required")
    void shouldCheckIfMacIsRequired() {
        // Then
        assertTrue(service.isMacRequired("ATM_TEST_V1"));
        assertFalse(service.isMacRequired("POS_TEST_V1"));
    }

    @Test
    @DisplayName("Should get channel encoding")
    void shouldGetChannelEncoding() {
        // Then
        assertEquals("ASCII", service.getChannelEncoding("ATM_TEST_V1"));
        assertEquals("ASCII", service.getChannelEncoding("NONEXISTENT")); // Default
    }

    // ==================== Message Creation Tests ====================

    @Test
    @DisplayName("Should create request message")
    void shouldCreateRequestMessage() {
        // When
        GenericMessage message = service.createRequestMessage("ATM_TEST_V1", "0200");

        // Then
        assertNotNull(message);
        assertEquals("Test ATM Schema", message.getSchema().getName());
    }

    @Test
    @DisplayName("Should create response message")
    void shouldCreateResponseMessage() {
        // When
        GenericMessage message = service.createResponseMessage("ATM_TEST_V1", "0210");

        // Then
        assertNotNull(message);
        assertEquals("Test ATM Schema", message.getSchema().getName());
    }

    // ==================== Parsing Tests ====================

    @Test
    @DisplayName("Should parse message from byte array")
    void shouldParseMessageFromByteArray() {
        // Given
        byte[] data = "0200".getBytes(); // Just MTI for test schema

        // When
        GenericMessage message = service.parseMessage("ATM_TEST_V1", "0200", data);

        // Then
        assertNotNull(message);
        assertEquals("0200", message.getFieldAsString("mti"));
    }

    @Test
    @DisplayName("Should parse message from ByteBuf")
    void shouldParseMessageFromByteBuf() {
        // Given
        ByteBuf buffer = Unpooled.wrappedBuffer("0200".getBytes());

        try {
            // When
            GenericMessage message = service.parseMessage("ATM_TEST_V1", "0200", buffer);

            // Then
            assertNotNull(message);
            assertEquals("0200", message.getFieldAsString("mti"));
        } finally {
            buffer.release();
        }
    }

    @Test
    @DisplayName("Should parse request message")
    void shouldParseRequestMessage() {
        // Given
        byte[] data = "0200".getBytes();

        // When
        GenericMessage message = service.parseRequestMessage("ATM_TEST_V1", "0200", data);

        // Then
        assertNotNull(message);
        assertEquals("0200", message.getFieldAsString("mti"));
    }

    @Test
    @DisplayName("Should parse response message")
    void shouldParseResponseMessage() {
        // Given
        byte[] data = "0210".getBytes();

        // When
        GenericMessage message = service.parseResponseMessage("ATM_TEST_V1", "0210", data);

        // Then
        assertNotNull(message);
        assertEquals("0210", message.getFieldAsString("mti"));
    }

    @Test
    @DisplayName("Should throw exception when parsing with invalid channel")
    void shouldThrowExceptionWhenParsingWithInvalidChannel() {
        // Given
        byte[] data = "0200".getBytes();

        // When & Then
        assertThrows(ChannelConfigException.class, () ->
                service.parseMessage("NONEXISTENT", "0200", data));
    }

    // ==================== Assembly Tests ====================

    @Test
    @DisplayName("Should assemble message to byte array")
    void shouldAssembleMessageToByteArray() {
        // Given
        GenericMessage message = service.createRequestMessage("ATM_TEST_V1", "0200");
        message.setField("mti", "0200");

        // When
        byte[] data = service.assembleMessage("ATM_TEST_V1", "0200", message);

        // Then
        assertNotNull(data);
        assertEquals("0200", new String(data));
    }

    @Test
    @DisplayName("Should assemble message to ByteBuf")
    void shouldAssembleMessageToByteBuf() {
        // Given
        GenericMessage message = service.createRequestMessage("ATM_TEST_V1", "0200");
        message.setField("mti", "0200");

        // When
        ByteBuf buffer = service.assembleToBuffer("ATM_TEST_V1", "0200", message);

        try {
            // Then
            assertNotNull(buffer);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            assertEquals("0200", new String(data));
        } finally {
            buffer.release();
        }
    }

    // ==================== Round-trip Tests ====================

    @Test
    @DisplayName("Should support parse-modify-assemble round trip")
    void shouldSupportParseModifyAssembleRoundTrip() {
        // Given - original message
        byte[] originalData = "0200".getBytes();

        // When - parse
        GenericMessage message = service.parseMessage("ATM_TEST_V1", "0200", originalData);
        assertEquals("0200", message.getFieldAsString("mti"));

        // Modify
        message.setField("mti", "0210");

        // Assemble
        byte[] assembledData = service.assembleMessage("ATM_TEST_V1", "0210", message);

        // Then
        assertEquals("0210", new String(assembledData));
    }

    @Test
    @DisplayName("Should support different channels with different schemas")
    void shouldSupportDifferentChannelsWithDifferentSchemas() {
        // Given
        GenericMessage atmMessage = service.createRequestMessage("ATM_TEST_V1", "0200");
        atmMessage.setField("mti", "0200");

        GenericMessage posMessage = service.createRequestMessage("POS_TEST_V1", "0200");
        posMessage.setField("mti", "0200");

        // Then - different schemas
        assertEquals("Test ATM Schema", atmMessage.getSchema().getName());
        assertEquals("Test POS Schema", posMessage.getSchema().getName());

        // Both can be assembled
        byte[] atmData = service.assembleMessage("ATM_TEST_V1", "0200", atmMessage);
        byte[] posData = service.assembleMessage("POS_TEST_V1", "0200", posMessage);

        assertEquals("0200", new String(atmData));
        assertEquals("0200", new String(posData));
    }

    // ==================== Registry Access Tests ====================

    @Test
    @DisplayName("Should provide access to underlying registry")
    void shouldProvideAccessToUnderlyingRegistry() {
        // When
        ChannelSchemaRegistry reg = service.getRegistry();

        // Then
        assertNotNull(reg);
        assertSame(registry, reg);
    }

    // ==================== Default Constructor Tests ====================

    @Test
    @DisplayName("Should use singleton registry with default constructor")
    void shouldUseSingletonRegistryWithDefaultConstructor() {
        // Given
        ChannelMessageService defaultService = new ChannelMessageService();

        // Then
        assertNotNull(defaultService.getRegistry());
        assertSame(ChannelSchemaRegistry.getInstance(), defaultService.getRegistry());
    }

    // ==================== Helper Methods ====================

    private String createTestSchemaJson() {
        return """
            [
              {
                "name": "Test ATM Schema",
                "version": "1.0",
                "vendor": "TEST",
                "defaultEncoding": "ASCII",
                "fields": [
                  { "id": "mti", "name": "MTI", "type": "ALPHANUMERIC", "length": 4, "encoding": "ASCII", "required": true }
                ]
              },
              {
                "name": "Test POS Schema",
                "version": "1.0",
                "vendor": "TEST",
                "defaultEncoding": "ASCII",
                "fields": [
                  { "id": "mti", "name": "MTI", "type": "ALPHANUMERIC", "length": 4, "encoding": "ASCII", "required": true }
                ]
              }
            ]
            """;
    }

    private String createTestConfigJson() {
        return """
            {
              "$schema": "fep-channel-schema-mapping-v1",
              "version": "1.0.0",
              "channels": [
                {
                  "id": "ATM_TEST_V1",
                  "name": "Test ATM Channel",
                  "type": "ATM",
                  "vendor": "TEST",
                  "version": "1.0",
                  "active": true,
                  "defaultRequestSchema": "Test ATM Schema",
                  "defaultResponseSchema": "Test ATM Schema",
                  "properties": {
                    "encoding": "ASCII",
                    "macRequired": "true"
                  },
                  "tags": ["production"],
                  "priority": 10
                },
                {
                  "id": "POS_TEST_V1",
                  "name": "Test POS Channel",
                  "type": "POS",
                  "vendor": "TEST",
                  "version": "1.0",
                  "active": true,
                  "defaultRequestSchema": "Test POS Schema",
                  "defaultResponseSchema": "Test POS Schema",
                  "properties": {
                    "encoding": "ASCII"
                  },
                  "tags": ["production"],
                  "priority": 10
                },
                {
                  "id": "FISC_TEST_V1",
                  "name": "Test FISC Channel",
                  "type": "INTERBANK",
                  "vendor": "FISC",
                  "version": "1.0",
                  "active": true,
                  "defaultRequestSchema": "Test ATM Schema",
                  "defaultResponseSchema": "Test ATM Schema",
                  "properties": {
                    "encoding": "ASCII",
                    "macRequired": "true"
                  },
                  "tags": ["production", "interbank"],
                  "priority": 1
                }
              ],
              "defaults": {
                "fallbackChannel": "FISC_TEST_V1",
                "unknownChannelBehavior": "THROW_ERROR",
                "schemaNotFoundBehavior": "THROW_ERROR"
              }
            }
            """;
    }
}
