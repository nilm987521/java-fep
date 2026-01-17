package com.fep.message.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ChannelSchemaConfiguration}.
 * Tests Spring Boot auto-configuration of ChannelSchemaRegistry.
 */
@DisplayName("ChannelSchemaConfiguration Spring Integration Tests")
class ChannelSchemaConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChannelSchemaConfiguration.class));

    @TempDir
    Path tempDir;

    private static final String VALID_CONFIG = """
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
                  "defaultRequestSchema": "Test Schema",
                  "defaultResponseSchema": "Test Schema"
                },
                {
                  "id": "POS_TEST_V1",
                  "name": "Test POS Channel",
                  "type": "POS",
                  "vendor": "TEST",
                  "version": "1.0",
                  "active": true,
                  "defaultRequestSchema": "POS Schema",
                  "defaultResponseSchema": "POS Schema"
                }
              ],
              "defaults": {
                "fallbackChannel": "ATM_TEST_V1",
                "unknownChannelBehavior": "USE_FALLBACK"
              }
            }
            """;

    @BeforeEach
    void setUp() {
        // Clear registry before each test to ensure isolation
        ChannelSchemaRegistry.getInstance().clear();
    }

    @Test
    @DisplayName("Should create ChannelSchemaRegistry bean")
    void shouldCreateChannelSchemaRegistryBean() {
        contextRunner
                .withPropertyValues(
                        "fep.channel.enabled=true",
                        "fep.channel.fail-on-missing-config=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ChannelSchemaRegistry.class);
                    assertThat(context).hasSingleBean(ChannelSchemaProperties.class);
                });
    }

    @Test
    @DisplayName("Should load configuration from file")
    void shouldLoadConfigurationFromFile() throws IOException {
        Path configFile = tempDir.resolve("channel-config.json");
        Files.writeString(configFile, VALID_CONFIG);

        contextRunner
                .withPropertyValues(
                        "fep.channel.enabled=true",
                        "fep.channel.config-file=" + configFile.toString(),
                        "fep.channel.fail-on-missing-config=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ChannelSchemaRegistry.class);

                    // The singleton registry should have been loaded by PostConstruct
                    ChannelSchemaRegistry registry = ChannelSchemaRegistry.getInstance();
                    assertThat(registry.getAllChannels()).hasSize(2);
                    assertThat(registry.getChannel("ATM_TEST_V1")).isPresent();
                    assertThat(registry.getChannel("POS_TEST_V1")).isPresent();
                });
    }

    @Test
    @DisplayName("Should not fail when config file missing and failOnMissingConfig is false")
    void shouldNotFailWhenConfigMissingAndNotRequired() {
        contextRunner
                .withPropertyValues(
                        "fep.channel.enabled=true",
                        "fep.channel.config-file=non-existent-file.json",
                        "fep.channel.fail-on-missing-config=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ChannelSchemaRegistry.class);
                    // Registry should be empty when config is missing
                    ChannelSchemaRegistry registry = ChannelSchemaRegistry.getInstance();
                    assertThat(registry.getAllChannels()).isEmpty();
                });
    }

    @Test
    @DisplayName("Should fail when config file missing and failOnMissingConfig is true")
    void shouldFailWhenConfigMissingAndRequired() {
        contextRunner
                .withPropertyValues(
                        "fep.channel.enabled=true",
                        "fep.channel.config-file=non-existent-file.json",
                        "fep.channel.fail-on-missing-config=true"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("Channel configuration file not found");
                });
    }

    @Test
    @DisplayName("Should disable auto-configuration when fep.channel.enabled is false")
    void shouldDisableWhenNotEnabled() {
        contextRunner
                .withPropertyValues(
                        "fep.channel.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ChannelSchemaConfiguration.class);
                });
    }

    @Test
    @DisplayName("Should use default values when properties not specified")
    void shouldUseDefaultValues() {
        contextRunner
                .withPropertyValues(
                        "fep.channel.fail-on-missing-config=false"
                )
                .run(context -> {
                    ChannelSchemaProperties properties = context.getBean(ChannelSchemaProperties.class);

                    assertThat(properties.getConfigFile()).isEqualTo("config/channel-schema-mapping.json");
                    assertThat(properties.isHotReload()).isFalse();
                    assertThat(properties.getHotReloadInterval()).isEqualTo(5000);
                    assertThat(properties.isValidateSchemaReferences()).isFalse();
                });
    }

    @Test
    @DisplayName("Should apply custom property values")
    void shouldApplyCustomPropertyValues() throws IOException {
        Path configFile = tempDir.resolve("custom-config.json");
        Files.writeString(configFile, VALID_CONFIG);

        contextRunner
                .withPropertyValues(
                        "fep.channel.config-file=" + configFile.toString(),
                        "fep.channel.hot-reload=true",
                        "fep.channel.hot-reload-interval=10000",
                        "fep.channel.fail-on-missing-config=true",
                        "fep.channel.validate-schema-references=true"
                )
                .run(context -> {
                    ChannelSchemaProperties properties = context.getBean(ChannelSchemaProperties.class);

                    assertThat(properties.getConfigFile()).isEqualTo(configFile.toString());
                    assertThat(properties.isHotReload()).isTrue();
                    assertThat(properties.getHotReloadInterval()).isEqualTo(10000);
                    assertThat(properties.isFailOnMissingConfig()).isTrue();
                    assertThat(properties.isValidateSchemaReferences()).isTrue();
                });
    }

    @Test
    @DisplayName("Should support runtime channel registration after context starts")
    void shouldSupportRuntimeChannelRegistration() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, VALID_CONFIG);

        contextRunner
                .withPropertyValues(
                        "fep.channel.config-file=" + configFile.toString()
                )
                .run(context -> {
                    ChannelSchemaRegistry registry = ChannelSchemaRegistry.getInstance();

                    // Verify initial channels
                    assertThat(registry.getAllChannels()).hasSize(2);

                    // Register new channel at runtime
                    Channel newChannel = Channel.builder()
                            .id("RUNTIME_V1")
                            .name("Runtime Channel")
                            .type("MOBILE")
                            .vendor("INTERNAL")
                            .version("1.0")
                            .active(true)
                            .defaultRequestSchema("Mobile Schema")
                            .defaultResponseSchema("Mobile Schema")
                            .build();

                    registry.registerChannel(newChannel);

                    // Verify channel was added
                    assertThat(registry.getAllChannels()).hasSize(3);
                    assertThat(registry.getChannel("RUNTIME_V1")).isPresent();
                    assertThat(registry.getChannel("RUNTIME_V1").get().getType()).isEqualTo("MOBILE");
                });
    }

    @Test
    @DisplayName("Should query channels by type and vendor")
    void shouldQueryChannelsByTypeAndVendor() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, VALID_CONFIG);

        contextRunner
                .withPropertyValues(
                        "fep.channel.config-file=" + configFile.toString()
                )
                .run(context -> {
                    ChannelSchemaRegistry registry = ChannelSchemaRegistry.getInstance();

                    // Query by type
                    assertThat(registry.getChannelsByType("ATM")).hasSize(1);
                    assertThat(registry.getChannelsByType("POS")).hasSize(1);
                    assertThat(registry.getChannelsByType("MOBILE")).isEmpty();

                    // Query by vendor
                    assertThat(registry.getChannelsByVendor("TEST")).hasSize(2);
                    assertThat(registry.getChannelsByVendor("UNKNOWN")).isEmpty();
                });
    }

    @Test
    @DisplayName("Should enable auto-configuration by default (matchIfMissing=true)")
    void shouldEnableByDefault() {
        contextRunner
                .withPropertyValues(
                        "fep.channel.fail-on-missing-config=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ChannelSchemaRegistry.class);
                });
    }

    @Test
    @DisplayName("ChannelSchemaProperties should have proper toString")
    void propertiesShouldHaveProperToString() {
        contextRunner
                .withPropertyValues(
                        "fep.channel.fail-on-missing-config=false"
                )
                .run(context -> {
                    ChannelSchemaProperties properties = context.getBean(ChannelSchemaProperties.class);
                    String toString = properties.toString();

                    assertThat(toString).contains("configFile");
                    assertThat(toString).contains("hotReload");
                    assertThat(toString).contains("hotReloadInterval");
                });
    }
}
