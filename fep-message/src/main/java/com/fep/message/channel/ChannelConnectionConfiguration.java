package com.fep.message.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot auto-configuration for Channel Connection Registry.
 *
 * <p>This configuration class:
 * <ul>
 *   <li>Creates and configures the {@link ChannelConnectionRegistry} bean</li>
 *   <li>Loads V2 connection configuration from the specified file</li>
 *   <li>Applies YAML overrides for environment-specific settings</li>
 *   <li>Optionally enables hot-reload for configuration changes</li>
 *   <li>Integrates with {@link ChannelSchemaRegistry} for backward compatibility</li>
 * </ul>
 *
 * <p>Configuration example:
 * <pre>
 * fep:
 *   connection:
 *     enabled: true
 *     config-path: config/channel-config.json
 *     hot-reload-enabled: true
 *     profile-overrides:
 *       FISC_PRIMARY:
 *         host: ${FISC_HOST:localhost}
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(ChannelConnectionProperties.class)
@ConditionalOnProperty(prefix = "fep.connection", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(ChannelSchemaConfiguration.class)
public class ChannelConnectionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChannelConnectionConfiguration.class);

    private final ChannelConnectionProperties properties;

    public ChannelConnectionConfiguration(ChannelConnectionProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates the ChannelConnectionRegistry bean.
     *
     * <p>The registry is configured as a singleton and initialized with:
     * <ul>
     *   <li>V2 connection configuration from the specified file</li>
     *   <li>YAML property overrides applied</li>
     *   <li>Integration with ChannelSchemaRegistry</li>
     *   <li>Optional hot-reload capability</li>
     * </ul>
     *
     * @return the configured ChannelConnectionRegistry instance
     */
    @Bean(destroyMethod = "disableHotReload")
    @ConditionalOnMissingBean
    public ChannelConnectionRegistry channelConnectionRegistry() {
        log.info("Initializing ChannelConnectionRegistry with properties: {}", properties);

        // Get the singleton instance
        ChannelConnectionRegistry registry = ChannelConnectionRegistry.getInstance();

        // Set schema registry reference for V1 fallback
        if (properties.isIntegrateWithSchemaRegistry()) {
            registry.setSchemaRegistry(ChannelSchemaRegistry.getInstance());
        }

        // Initialize the registry
        initializeRegistry(registry);

        // Apply YAML overrides
        applyOverrides(registry);

        return registry;
    }

    /**
     * Initializes the registry with configuration.
     */
    private void initializeRegistry(ChannelConnectionRegistry registry) {
        String configPath = properties.getConfigPath();
        Path resolvedPath = resolveConfigPath(configPath);

        if (resolvedPath != null && Files.exists(resolvedPath)) {
            try {
                log.info("Loading channel connection configuration from: {}", resolvedPath);
                registry.loadFromFile(resolvedPath.toString());
                log.info("Successfully loaded {} connection profiles and {} channel connections",
                        registry.getAllProfileIds().size(),
                        registry.getAllChannelIds().size());

                // Enable hot-reload if configured
                if (properties.isHotReloadEnabled()) {
                    log.info("Enabling hot-reload for channel connection configuration");
                    registry.enableHotReload();
                }

                // Validate profile references if configured
                if (properties.isValidateProfileReferences()) {
                    validateProfileReferences(registry);
                }

            } catch (ChannelConfigException e) {
                handleConfigError(e);
            }
        } else {
            handleMissingConfig(configPath);
        }
    }

    /**
     * Resolves the configuration file path.
     * Supports classpath:, file:, and relative paths.
     */
    private Path resolveConfigPath(String configPath) {
        if (configPath == null || configPath.isEmpty()) {
            return null;
        }

        // Handle classpath: prefix
        if (configPath.startsWith("classpath:")) {
            String resource = configPath.substring("classpath:".length());
            try {
                var url = getClass().getClassLoader().getResource(resource);
                if (url != null) {
                    return Paths.get(url.toURI());
                }
            } catch (Exception e) {
                log.debug("Config file not found in classpath: {}", resource);
            }
            return null;
        }

        // Handle file: prefix
        if (configPath.startsWith("file:")) {
            return Paths.get(configPath.substring("file:".length()));
        }

        // Regular path resolution
        Path path = Paths.get(configPath);

        // If absolute path, use as-is
        if (path.isAbsolute()) {
            return path;
        }

        // Try relative to current working directory
        Path cwdPath = Paths.get(System.getProperty("user.dir"), configPath);
        if (Files.exists(cwdPath)) {
            return cwdPath;
        }

        // Try classpath resource
        try {
            var resource = getClass().getClassLoader().getResource(configPath);
            if (resource != null) {
                return Paths.get(resource.toURI());
            }
        } catch (Exception e) {
            log.debug("Config file not found in classpath: {}", configPath);
        }

        return path;
    }

    /**
     * Applies YAML property overrides to the loaded configuration.
     */
    private void applyOverrides(ChannelConnectionRegistry registry) {
        // Apply profile overrides
        Map<String, ChannelConnectionProperties.ProfileOverride> profileOverrides = properties.getProfileOverrides();
        if (profileOverrides != null && !profileOverrides.isEmpty()) {
            for (Map.Entry<String, ChannelConnectionProperties.ProfileOverride> entry : profileOverrides.entrySet()) {
                String profileId = entry.getKey();
                ChannelConnectionProperties.ProfileOverride override = entry.getValue();

                registry.getConnectionProfile(profileId).ifPresent(profile -> {
                    ConnectionProfile updated = applyProfileOverride(profile, override);
                    registry.registerProfile(updated);
                    log.debug("Applied override to profile: {}", profileId);
                });
            }
        }

        // Apply channel overrides
        Map<String, ChannelConnectionProperties.ChannelOverride> channelOverrides = properties.getChannelOverrides();
        if (channelOverrides != null && !channelOverrides.isEmpty()) {
            for (Map.Entry<String, ChannelConnectionProperties.ChannelOverride> entry : channelOverrides.entrySet()) {
                String channelId = entry.getKey();
                ChannelConnectionProperties.ChannelOverride override = entry.getValue();

                registry.getChannelConnection(channelId).ifPresent(connection -> {
                    ChannelConnection updated = applyChannelOverride(connection, override);
                    registry.registerConnection(updated);
                    log.debug("Applied override to channel: {}", channelId);
                });
            }
        }
    }

    /**
     * Applies a profile override to an existing profile.
     */
    private ConnectionProfile applyProfileOverride(ConnectionProfile original,
                                                    ChannelConnectionProperties.ProfileOverride override) {
        return ConnectionProfile.builder()
                .profileId(original.getProfileId())
                .host(override.getHost() != null ? override.getHost() : original.getHost())
                .sendPort(override.getSendPort() != null ? override.getSendPort() : original.getSendPort())
                .receivePort(override.getReceivePort() != null ? override.getReceivePort() : original.getReceivePort())
                .connectTimeout(override.getConnectTimeout() != null ? override.getConnectTimeout() : original.getConnectTimeout())
                .responseTimeout(override.getResponseTimeout() != null ? override.getResponseTimeout() : original.getResponseTimeout())
                .heartbeatInterval(override.getHeartbeatInterval() != null ? override.getHeartbeatInterval() : original.getHeartbeatInterval())
                .maxRetries(override.getMaxRetries() != null ? override.getMaxRetries() : original.getMaxRetries())
                .sslEnabled(override.getSslEnabled() != null ? override.getSslEnabled() : original.isSslEnabled())
                .retryDelay(original.getRetryDelay())
                .poolSize(original.getPoolSize())
                .autoReconnect(original.isAutoReconnect())
                .keepAliveInterval(original.getKeepAliveInterval())
                .properties(mergeProperties(original.getProperties(), override.getProperties()))
                .build();
    }

    /**
     * Applies a channel override to an existing connection.
     */
    private ChannelConnection applyChannelOverride(ChannelConnection original,
                                                    ChannelConnectionProperties.ChannelOverride override) {
        return ChannelConnection.builder()
                .channelId(original.getChannelId())
                .connectionProfileId(override.getConnectionProfile() != null ?
                        override.getConnectionProfile() : original.getConnectionProfileId())
                .active(override.getActive() != null ? override.getActive() : original.isActive())
                .priority(override.getPriority() != null ? override.getPriority() : original.getPriority())
                .schemas(mergeSchemas(original.getSchemas(), override.getSchemas()))
                .properties(mergeProperties(original.getProperties(), override.getProperties()))
                .description(original.getDescription())
                .build();
    }

    /**
     * Merges properties, with overrides taking precedence.
     */
    private Map<String, String> mergeProperties(Map<String, String> original, Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return original;
        }
        Map<String, String> merged = new HashMap<>();
        if (original != null) {
            merged.putAll(original);
        }
        merged.putAll(overrides);
        return merged;
    }

    /**
     * Merges schemas, with overrides taking precedence.
     */
    private Map<String, String> mergeSchemas(Map<String, String> original, Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return original;
        }
        Map<String, String> merged = new HashMap<>();
        if (original != null) {
            merged.putAll(original);
        }
        merged.putAll(overrides);
        return merged;
    }

    /**
     * Handles configuration loading errors.
     */
    private void handleConfigError(ChannelConfigException e) {
        if (properties.isFailOnMissingConfig()) {
            throw new IllegalStateException("Failed to load channel connection configuration", e);
        } else {
            log.warn("Failed to load channel connection configuration, registry will be empty: {}",
                    e.getMessage());
        }
    }

    /**
     * Handles missing configuration file.
     */
    private void handleMissingConfig(String configPath) {
        String message = String.format("Channel connection configuration file not found: %s", configPath);

        if (properties.isFailOnMissingConfig()) {
            throw new IllegalStateException(message);
        } else {
            log.warn("{} - registry will be empty until connections are registered dynamically", message);
        }
    }

    /**
     * Validates that all channel connections reference existing profiles.
     */
    private void validateProfileReferences(ChannelConnectionRegistry registry) {
        for (String channelId : registry.getAllChannelIds()) {
            registry.getChannelConnection(channelId).ifPresent(connection -> {
                String profileId = connection.getConnectionProfileId();
                if (profileId != null && !registry.hasProfile(profileId)) {
                    log.warn("Channel '{}' references unknown connection profile: {}",
                            channelId, profileId);
                }
            });
        }
    }
}
