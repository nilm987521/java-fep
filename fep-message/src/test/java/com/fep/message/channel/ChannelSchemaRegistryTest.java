package com.fep.message.channel;

import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import com.fep.message.interfaces.ChannelSubscriber;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChannelSchemaRegistry.
 */
class ChannelSchemaRegistryTest {

    private ChannelSchemaRegistry registry;
    private Path tempDir;
    private Path configFile;
    private Path schemaFile;

    @BeforeEach
    void setUp() throws IOException {
        registry = ChannelSchemaRegistry.getInstance();
        registry.clear();
        JsonSchemaLoader.clearCache();

        // Create temp directory for test files
        tempDir = Files.createTempDirectory("channel-test");

        // Create test schema file
        schemaFile = tempDir.resolve("test-schemas.json");
        Files.writeString(schemaFile, createTestSchemaJson());

        // Load schemas first
        JsonSchemaLoader.reloadFromFilePath(schemaFile.toString());
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

    // ==================== Channel Loading Tests ====================

    @Test
    @DisplayName("Should load channels from JSON file")
    void shouldLoadChannelsFromJsonFile() throws IOException {
        // Given
        configFile = tempDir.resolve("channel-config.json");
        Files.writeString(configFile, createTestConfigJson());

        // When
        registry.loadFromFile(configFile.toString());

        // Then
        assertEquals(3, registry.getAllChannelIds().size());
        assertTrue(registry.hasChannel("ATM_TEST_V1"));
        assertTrue(registry.hasChannel("POS_TEST_V1"));
        assertTrue(registry.hasChannel("FISC_TEST_V1"));
    }

    @Test
    @DisplayName("Should load channels from JSON string")
    void shouldLoadChannelsFromJsonString() {
        // Given
        String json = createTestConfigJson();

        // When
        registry.loadFromJson(json);

        // Then
        assertEquals(3, registry.getAllChannelIds().size());
    }

    @Test
    @DisplayName("Should throw exception for missing file")
    void shouldThrowExceptionForMissingFile() {
        // When & Then
        assertThrows(ChannelConfigException.class, () ->
                registry.loadFromFile("/nonexistent/path/config.json"));
    }

    @Test
    @DisplayName("Should throw exception for invalid channel without id")
    void shouldThrowExceptionForChannelWithoutId() {
        // Given
        String json = """
            {
              "channels": [
                { "type": "ATM", "vendor": "TEST" }
              ]
            }
            """;

        // When & Then
        assertThrows(ChannelConfigException.class, () ->
                registry.loadFromJson(json));
    }

    @Test
    @DisplayName("Should throw exception for invalid channel without type")
    void shouldThrowExceptionForChannelWithoutType() {
        // Given
        String json = """
            {
              "channels": [
                { "id": "TEST_V1", "vendor": "TEST" }
              ]
            }
            """;

        // When & Then
        assertThrows(ChannelConfigException.class, () ->
                registry.loadFromJson(json));
    }

    // ==================== Channel Query Tests ====================

    @Test
    @DisplayName("Should get channel by ID")
    void shouldGetChannelById() {
        // Given
        registry.loadFromJson(createTestConfigJson());

        // When
        Optional<Channel> channel = registry.getChannel("ATM_TEST_V1");

        // Then
        assertTrue(channel.isPresent());
        assertEquals("ATM_TEST_V1", channel.get().getId());
        assertEquals("ATM", channel.get().getType());
        assertEquals("TEST", channel.get().getVendor());
    }

    @Test
    @DisplayName("Should return empty for non-existent channel")
    void shouldReturnEmptyForNonExistentChannel() {
        // Given
        registry.loadFromJson(createTestConfigJson());

        // When
        Optional<Channel> channel = registry.getChannel("NONEXISTENT");

        // Then
        assertTrue(channel.isEmpty());
    }

    @Test
    @DisplayName("Should get active channels sorted by priority")
    void shouldGetActiveChannelsSortedByPriority() {
        // Given
        registry.loadFromJson(createTestConfigJson());

        // When
        List<Channel> activeChannels = registry.getActiveChannels();

        // Then
        assertEquals(3, activeChannels.size());
        // FISC has priority 1, ATM and POS have priority 10
        assertEquals("FISC_TEST_V1", activeChannels.get(0).getId());
    }

    @Test
    @DisplayName("Should get channels by type")
    void shouldGetChannelsByType() {
        // Given
        registry.loadFromJson(createTestConfigJson());

        // When
        List<Channel> atmChannels = registry.getChannelsByType("ATM");

        // Then
        assertEquals(1, atmChannels.size());
        assertEquals("ATM_TEST_V1", atmChannels.get(0).getId());
    }

    @Test
    @DisplayName("Should get channels by vendor")
    void shouldGetChannelsByVendor() {
        // Given
        registry.loadFromJson(createTestConfigJson());

        // When
        List<Channel> testVendorChannels = registry.getChannelsByVendor("TEST");

        // Then
        assertEquals(2, testVendorChannels.size());
    }

    @Test
    @DisplayName("Should get channels by tag")
    void shouldGetChannelsByTag() {
        // Given
        registry.loadFromJson(createTestConfigJson());

        // When
        List<Channel> productionChannels = registry.getChannelsByTag("production");

        // Then
        assertEquals(3, productionChannels.size());
    }

    // ==================== Schema Resolution Tests ====================

    @Test
    @DisplayName("Should get default request schema")
    void shouldGetDefaultRequestSchema() {
        // Given
        registry.loadFromJson(createTestConfigJson());

        // When
        MessageSchema schema = registry.getDefaultRequestSchema("ATM_TEST_V1");

        // Then
        assertNotNull(schema);
        assertEquals("Test ATM Schema", schema.getName());
    }

    @Test
    @DisplayName("Should get default response schema")
    void shouldGetDefaultResponseSchema() {
        // Given
        registry.loadFromJson(createTestConfigJson());

        // When
        MessageSchema schema = registry.getDefaultResponseSchema("ATM_TEST_V1");

        // Then
        assertNotNull(schema);
        assertEquals("Test ATM Schema", schema.getName());
    }

    @Test
    @DisplayName("Should get request schema for message type")
    void shouldGetRequestSchemaForMessageType() {
        // Given
        registry.loadFromJson(createTestConfigJson());

        // When
        MessageSchema schema = registry.getRequestSchema("ATM_TEST_V1", "0200");

        // Then
        assertNotNull(schema);
        assertEquals("Test ATM Schema", schema.getName());
    }

    @Test
    @DisplayName("Should get schema override for specific message type")
    void shouldGetSchemaOverrideForSpecificMessageType() {
        // Given
        String json = """
            {
              "channels": [
                {
                  "id": "TEST_V1",
                  "type": "ATM",
                  "active": true,
                  "defaultRequestSchema": "Test ATM Schema",
                  "defaultResponseSchema": "Test ATM Schema"
                }
              ],
              "schemaOverrides": {
                "TEST_V1": {
                  "0400": {
                    "request": "Test POS Schema",
                    "response": "Test POS Schema"
                  }
                }
              }
            }
            """;
        registry.loadFromJson(json);

        // When
        MessageSchema defaultSchema = registry.getRequestSchema("TEST_V1", "0200");
        MessageSchema overrideSchema = registry.getRequestSchema("TEST_V1", "0400");

        // Then
        assertEquals("Test ATM Schema", defaultSchema.getName());
        assertEquals("Test POS Schema", overrideSchema.getName());
    }

    @Test
    @DisplayName("Should throw exception for unknown channel")
    void shouldThrowExceptionForUnknownChannel() {
        // Given
        String json = """
            {
              "channels": [
                { "id": "TEST_V1", "type": "ATM", "active": true, "defaultRequestSchema": "Test ATM Schema" }
              ],
              "defaults": {
                "unknownChannelBehavior": "THROW_ERROR"
              }
            }
            """;
        registry.loadFromJson(json);

        // When & Then
        assertThrows(ChannelConfigException.class, () ->
                registry.getRequestSchema("NONEXISTENT", "0200"));
    }

    @Test
    @DisplayName("Should use fallback for unknown channel when configured")
    void shouldUseFallbackForUnknownChannel() {
        // Given
        String json = """
            {
              "channels": [
                { "id": "FALLBACK_V1", "type": "ATM", "active": true, "defaultRequestSchema": "Test ATM Schema" }
              ],
              "defaults": {
                "fallbackChannel": "FALLBACK_V1",
                "unknownChannelBehavior": "USE_FALLBACK"
              }
            }
            """;
        registry.loadFromJson(json);

        // When
        MessageSchema schema = registry.getRequestSchema("NONEXISTENT", "0200");

        // Then
        assertNotNull(schema);
        assertEquals("Test ATM Schema", schema.getName());
    }

    // ==================== Runtime Registration Tests ====================

    @Test
    @DisplayName("Should register channel at runtime")
    void shouldRegisterChannelAtRuntime() {
        // Given
        Channel newChannel = Channel.builder()
                .id("RUNTIME_V1")
                .name("Runtime Channel")
                .type("TEST")
                .vendor("RUNTIME")
                .active(true)
                .defaultRequestSchema("Test ATM Schema")
                .build();

        // When
        registry.registerChannel(newChannel);

        // Then
        assertTrue(registry.hasChannel("RUNTIME_V1"));
        assertEquals("Runtime Channel", registry.getChannel("RUNTIME_V1").get().getName());
    }

    @Test
    @DisplayName("Should unregister channel at runtime")
    void shouldUnregisterChannelAtRuntime() {
        // Given
        registry.loadFromJson(createTestConfigJson());
        assertTrue(registry.hasChannel("ATM_TEST_V1"));

        // When
        Channel removed = registry.unregisterChannel("ATM_TEST_V1");

        // Then
        assertNotNull(removed);
        assertFalse(registry.hasChannel("ATM_TEST_V1"));
    }

    @Test
    @DisplayName("Should update existing channel")
    void shouldUpdateExistingChannel() {
        // Given
        registry.loadFromJson(createTestConfigJson());
        Channel updated = Channel.builder()
                .id("ATM_TEST_V1")
                .name("Updated Name")
                .type("ATM")
                .vendor("UPDATED")
                .active(true)
                .build();

        // When
        registry.updateChannel(updated);

        // Then
        Channel channel = registry.getChannel("ATM_TEST_V1").orElseThrow();
        assertEquals("Updated Name", channel.getName());
        assertEquals("UPDATED", channel.getVendor());
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent channel")
    void shouldThrowExceptionWhenUpdatingNonExistentChannel() {
        // Given
        Channel channel = Channel.builder()
                .id("NONEXISTENT")
                .type("TEST")
                .build();

        // When & Then
        assertThrows(ChannelConfigException.class, () ->
                registry.updateChannel(channel));
    }

    // ==================== Subscriber Tests ====================

    @Test
    @DisplayName("Should notify subscribers when channels loaded")
    void shouldNotifySubscribersWhenChannelsLoaded() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger receivedCount = new AtomicInteger(0);

        ChannelSubscriber subscriber = channelMap -> {
            receivedCount.set(channelMap.size());
            latch.countDown();
        };
        registry.registerSubscriber(subscriber);

        // When
        registry.loadFromJson(createTestConfigJson());

        // Then
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(3, receivedCount.get());
    }

    @Test
    @DisplayName("Should notify subscribers when channel registered at runtime")
    void shouldNotifySubscribersWhenChannelRegisteredAtRuntime() throws InterruptedException {
        // Given
        registry.loadFromJson(createTestConfigJson());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger receivedCount = new AtomicInteger(0);

        ChannelSubscriber subscriber = channelMap -> {
            receivedCount.set(channelMap.size());
            latch.countDown();
        };
        registry.registerSubscriber(subscriber);

        // When
        Channel newChannel = Channel.builder()
                .id("NEW_V1")
                .type("TEST")
                .active(true)
                .build();
        registry.registerChannel(newChannel);

        // Then
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(4, receivedCount.get()); // 3 original + 1 new
    }

    @Test
    @DisplayName("Should unregister subscriber")
    void shouldUnregisterSubscriber() throws InterruptedException {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        ChannelSubscriber subscriber = channelMap -> callCount.incrementAndGet();

        registry.registerSubscriber(subscriber);
        registry.loadFromJson(createTestConfigJson());
        assertEquals(1, callCount.get());

        // When
        registry.unregisterSubscriber(subscriber);
        registry.loadFromJson(createTestConfigJson());

        // Then
        assertEquals(1, callCount.get()); // Should not be called again
    }

    @Test
    @DisplayName("Should immediately notify new subscriber if channels already loaded")
    void shouldImmediatelyNotifyNewSubscriberIfChannelsLoaded() throws InterruptedException {
        // Given
        registry.loadFromJson(createTestConfigJson());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger receivedCount = new AtomicInteger(0);

        // When
        ChannelSubscriber subscriber = channelMap -> {
            receivedCount.set(channelMap.size());
            latch.countDown();
        };
        registry.registerSubscriber(subscriber);

        // Then
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(3, receivedCount.get());
    }

    // ==================== Schema Override Tests ====================

    @Test
    @DisplayName("Should add schema override at runtime")
    void shouldAddSchemaOverrideAtRuntime() {
        // Given
        registry.loadFromJson(createTestConfigJson());
        SchemaOverride override = new SchemaOverride("Test POS Schema", "Test POS Schema");

        // When
        registry.addSchemaOverride("ATM_TEST_V1", "0400", override);
        MessageSchema schema = registry.getRequestSchema("ATM_TEST_V1", "0400");

        // Then
        assertEquals("Test POS Schema", schema.getName());
    }

    // ==================== Channel Properties Tests ====================

    @Test
    @DisplayName("Should get channel property with default")
    void shouldGetChannelPropertyWithDefault() {
        // Given
        registry.loadFromJson(createTestConfigJson());

        // When
        Channel channel = registry.getChannel("ATM_TEST_V1").orElseThrow();
        String encoding = channel.getProperty("encoding", "UTF-8");
        String nonExistent = channel.getProperty("nonexistent", "default");

        // Then
        assertEquals("ASCII", encoding);
        assertEquals("default", nonExistent);
    }

    @Test
    @DisplayName("Should check channel has tag")
    void shouldCheckChannelHasTag() {
        // Given
        registry.loadFromJson(createTestConfigJson());
        Channel channel = registry.getChannel("ATM_TEST_V1").orElseThrow();

        // Then
        assertTrue(channel.hasTag("production"));
        assertFalse(channel.hasTag("nonexistent"));
    }

    // ==================== Clear and Reload Tests ====================

    @Test
    @DisplayName("Should clear all channels")
    void shouldClearAllChannels() {
        // Given
        registry.loadFromJson(createTestConfigJson());
        assertEquals(3, registry.getAllChannelIds().size());

        // When
        registry.clear();

        // Then
        assertEquals(0, registry.getAllChannelIds().size());
        assertNull(registry.getConfig());
    }

    @Test
    @DisplayName("Should reload configuration")
    void shouldReloadConfiguration() throws IOException {
        // Given
        configFile = tempDir.resolve("reload-test.json");
        Files.writeString(configFile, createTestConfigJson());
        registry.loadFromFile(configFile.toString());
        assertEquals(3, registry.getAllChannelIds().size());

        // Modify config file
        String newConfig = """
            {
              "channels": [
                { "id": "NEW_V1", "type": "NEW", "active": true }
              ]
            }
            """;
        Files.writeString(configFile, newConfig);

        // When
        registry.reload();

        // Then
        assertEquals(1, registry.getAllChannelIds().size());
        assertTrue(registry.hasChannel("NEW_V1"));
    }

    // ==================== Helper Methods ====================

    private String createTestSchemaJson() {
        return """
            [
              {
                "name": "Test ATM Schema",
                "version": "1.0",
                "vendor": "TEST",
                "fields": [
                  { "id": "mti", "name": "MTI", "type": "NUMERIC", "length": 4, "encoding": "ASCII", "required": true }
                ]
              },
              {
                "name": "Test POS Schema",
                "version": "1.0",
                "vendor": "TEST",
                "fields": [
                  { "id": "mti", "name": "MTI", "type": "NUMERIC", "length": 4, "encoding": "ASCII", "required": true }
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
                    "encoding": "ASCII"
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
                  "tags": ["production", "interbank"],
                  "priority": 1
                }
              ],
              "defaults": {
                "fallbackChannel": "FISC_TEST_V1",
                "unknownChannelBehavior": "THROW_ERROR"
              }
            }
            """;
    }
}
