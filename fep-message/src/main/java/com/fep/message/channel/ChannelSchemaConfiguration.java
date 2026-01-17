package com.fep.message.channel;

import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Spring Boot auto-configuration for Channel-Schema Registry.
 *
 * <p>This configuration class:
 * <ul>
 *   <li>Creates and configures the {@link ChannelSchemaRegistry} bean</li>
 *   <li>Loads channel configuration from the specified file</li>
 *   <li>Optionally enables hot-reload for configuration changes</li>
 *   <li>Integrates with {@link JsonSchemaLoader} for schema resolution</li>
 * </ul>
 *
 * <p>Configuration example:
 * <pre>
 * fep:
 *   channel:
 *     enabled: true
 *     config-file: config/channel-schema-mapping.json
 *     hot-reload: true
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(ChannelSchemaProperties.class)
@ConditionalOnProperty(prefix = "fep.channel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChannelSchemaConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChannelSchemaConfiguration.class);

    private final ChannelSchemaProperties properties;

    public ChannelSchemaConfiguration(ChannelSchemaProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates the ChannelSchemaRegistry bean.
     *
     * <p>The registry is configured as a singleton and initialized with:
     * <ul>
     *   <li>Channel configuration from the specified file</li>
     *   <li>Integration with JsonSchemaLoader (via static methods)</li>
     *   <li>Optional hot-reload capability</li>
     * </ul>
     *
     * @return the configured ChannelSchemaRegistry instance
     */
    @Bean(destroyMethod = "disableHotReload")
    @ConditionalOnMissingBean
    public ChannelSchemaRegistry channelSchemaRegistry() {
        log.info("Initializing ChannelSchemaRegistry with properties: {}", properties);

        // Get the singleton instance
        ChannelSchemaRegistry registry = ChannelSchemaRegistry.getInstance();

        // Initialize the registry
        initializeRegistry(registry);

        return registry;
    }

    /**
     * Initializes the registry with configuration.
     * Loads configuration and enables hot-reload if configured.
     */
    private void initializeRegistry(ChannelSchemaRegistry registry) {
        String configFile = properties.getConfigFile();
        Path configPath = resolveConfigPath(configFile);

        if (configPath != null && Files.exists(configPath)) {
            try {
                log.info("Loading channel configuration from: {}", configPath);
                registry.loadFromFile(configPath.toString());
                log.info("Successfully loaded {} channels", registry.getAllChannels().size());

                // Enable hot-reload if configured
                if (properties.isHotReload()) {
                    log.info("Enabling hot-reload for channel configuration");
                    registry.enableHotReload();
                }

                // Validate schema references if configured
                if (properties.isValidateSchemaReferences()) {
                    validateSchemaReferences(registry);
                }

            } catch (ChannelConfigException e) {
                handleConfigError(e);
            }
        } else {
            handleMissingConfig(configFile);
        }
    }

    /**
     * Resolves the configuration file path.
     * Supports both absolute and relative paths.
     */
    private Path resolveConfigPath(String configFile) {
        if (configFile == null || configFile.isEmpty()) {
            return null;
        }

        Path path = Paths.get(configFile);

        // If absolute path, use as-is
        if (path.isAbsolute()) {
            return path;
        }

        // Try relative to current working directory
        Path cwdPath = Paths.get(System.getProperty("user.dir"), configFile);
        if (Files.exists(cwdPath)) {
            return cwdPath;
        }

        // Try classpath resource
        try {
            var resource = getClass().getClassLoader().getResource(configFile);
            if (resource != null) {
                return Paths.get(resource.toURI());
            }
        } catch (Exception e) {
            log.debug("Config file not found in classpath: {}", configFile);
        }

        // Return the original path for error reporting
        return path;
    }

    /**
     * Handles configuration loading errors.
     */
    private void handleConfigError(ChannelConfigException e) {
        if (properties.isFailOnMissingConfig()) {
            throw new IllegalStateException("Failed to load channel configuration", e);
        } else {
            log.warn("Failed to load channel configuration, registry will be empty: {}", e.getMessage());
        }
    }

    /**
     * Handles missing configuration file.
     */
    private void handleMissingConfig(String configFile) {
        String message = String.format("Channel configuration file not found: %s", configFile);

        if (properties.isFailOnMissingConfig()) {
            throw new IllegalStateException(message);
        } else {
            log.warn("{} - registry will be empty until channels are registered dynamically", message);
        }
    }

    /**
     * Validates that all referenced schemas exist in JsonSchemaLoader.
     */
    private void validateSchemaReferences(ChannelSchemaRegistry registry) {
        Map<String, MessageSchema> allSchemas = JsonSchemaLoader.getSchemaMap();

        for (Channel channel : registry.getAllChannels()) {
            String defaultRequestSchema = channel.getDefaultRequestSchema();
            String defaultResponseSchema = channel.getDefaultResponseSchema();

            if (defaultRequestSchema != null && !allSchemas.containsKey(defaultRequestSchema)) {
                log.warn("Channel '{}' references unknown request schema: {}",
                        channel.getId(), defaultRequestSchema);
            }

            if (defaultResponseSchema != null && !allSchemas.containsKey(defaultResponseSchema)) {
                log.warn("Channel '{}' references unknown response schema: {}",
                        channel.getId(), defaultResponseSchema);
            }
        }
    }
}
