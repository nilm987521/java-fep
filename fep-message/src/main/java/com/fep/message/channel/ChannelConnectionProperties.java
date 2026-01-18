package com.fep.message.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration properties for unified Channel Connection configuration.
 * Supports V2 format with connection profiles and channel connections.
 *
 * <p>Example configuration in application.yml:
 * <pre>
 * fep:
 *   connection:
 *     config-path: classpath:channel-config.json
 *     hot-reload-enabled: true
 *     hot-reload-interval-seconds: 30
 *
 *     # Environment-specific overrides
 *     profile-overrides:
 *       FISC_PRIMARY:
 *         host: ${FISC_HOST:localhost}
 *         send-port: ${FISC_SEND_PORT:9001}
 *         receive-port: ${FISC_RECEIVE_PORT:9002}
 *
 *     channel-overrides:
 *       FISC_INTERBANK_V1:
 *         properties:
 *           macRequired: "false"
 * </pre>
 */
@ConfigurationProperties(prefix = "fep.connection")
public class ChannelConnectionProperties {

    /**
     * Path to the unified channel configuration file.
     * Supports classpath: and file: prefixes.
     * Default: config/channel-config.json
     */
    private String configPath = "config/channel-config.json";

    /**
     * Enable hot-reload of configuration.
     * When enabled, the system will watch for file changes and reload automatically.
     * Default: true
     */
    private boolean hotReloadEnabled = true;

    /**
     * Interval in seconds between hot-reload checks.
     * Only applicable when hot-reload is enabled.
     * Default: 30 seconds
     */
    private int hotReloadIntervalSeconds = 30;

    /**
     * Whether to fail application startup if config file is not found.
     * If false, the registry will start empty and wait for dynamic registration.
     * Default: false (graceful degradation)
     */
    private boolean failOnMissingConfig = false;

    /**
     * Whether to validate connection profile references on startup.
     * When enabled, all referenced profiles must exist.
     * Default: true
     */
    private boolean validateProfileReferences = true;

    /**
     * Whether to integrate with ChannelSchemaRegistry.
     * When enabled, channel schema information is synchronized.
     * Default: true
     */
    private boolean integrateWithSchemaRegistry = true;

    /**
     * Connection profile overrides from YAML.
     * Allows environment-specific configuration without modifying JSON file.
     * Key: profile ID, Value: override settings
     */
    private Map<String, ProfileOverride> profileOverrides;

    /**
     * Channel connection overrides from YAML.
     * Allows environment-specific configuration without modifying JSON file.
     * Key: channel ID, Value: override settings
     */
    private Map<String, ChannelOverride> channelOverrides;

    // ==================== Getters and Setters ====================

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public boolean isHotReloadEnabled() {
        return hotReloadEnabled;
    }

    public void setHotReloadEnabled(boolean hotReloadEnabled) {
        this.hotReloadEnabled = hotReloadEnabled;
    }

    public int getHotReloadIntervalSeconds() {
        return hotReloadIntervalSeconds;
    }

    public void setHotReloadIntervalSeconds(int hotReloadIntervalSeconds) {
        this.hotReloadIntervalSeconds = hotReloadIntervalSeconds;
    }

    public boolean isFailOnMissingConfig() {
        return failOnMissingConfig;
    }

    public void setFailOnMissingConfig(boolean failOnMissingConfig) {
        this.failOnMissingConfig = failOnMissingConfig;
    }

    public boolean isValidateProfileReferences() {
        return validateProfileReferences;
    }

    public void setValidateProfileReferences(boolean validateProfileReferences) {
        this.validateProfileReferences = validateProfileReferences;
    }

    public boolean isIntegrateWithSchemaRegistry() {
        return integrateWithSchemaRegistry;
    }

    public void setIntegrateWithSchemaRegistry(boolean integrateWithSchemaRegistry) {
        this.integrateWithSchemaRegistry = integrateWithSchemaRegistry;
    }

    public Map<String, ProfileOverride> getProfileOverrides() {
        return profileOverrides;
    }

    public void setProfileOverrides(Map<String, ProfileOverride> profileOverrides) {
        this.profileOverrides = profileOverrides;
    }

    public Map<String, ChannelOverride> getChannelOverrides() {
        return channelOverrides;
    }

    public void setChannelOverrides(Map<String, ChannelOverride> channelOverrides) {
        this.channelOverrides = channelOverrides;
    }

    @Override
    public String toString() {
        return "ChannelConnectionProperties{" +
                "configPath='" + configPath + '\'' +
                ", hotReloadEnabled=" + hotReloadEnabled +
                ", hotReloadIntervalSeconds=" + hotReloadIntervalSeconds +
                ", failOnMissingConfig=" + failOnMissingConfig +
                ", validateProfileReferences=" + validateProfileReferences +
                ", integrateWithSchemaRegistry=" + integrateWithSchemaRegistry +
                ", profileOverrides=" + (profileOverrides != null ? profileOverrides.size() : 0) +
                ", channelOverrides=" + (channelOverrides != null ? channelOverrides.size() : 0) +
                '}';
    }

    // ==================== Nested Override Classes ====================

    /**
     * Override settings for a connection profile.
     * Any non-null value will override the corresponding value from JSON config.
     */
    public static class ProfileOverride {
        private String host;
        private Integer sendPort;
        private Integer receivePort;
        private Integer connectTimeout;
        private Integer responseTimeout;
        private Integer heartbeatInterval;
        private Integer maxRetries;
        private Boolean sslEnabled;
        private Map<String, String> properties;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getSendPort() {
            return sendPort;
        }

        public void setSendPort(Integer sendPort) {
            this.sendPort = sendPort;
        }

        public Integer getReceivePort() {
            return receivePort;
        }

        public void setReceivePort(Integer receivePort) {
            this.receivePort = receivePort;
        }

        public Integer getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Integer connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Integer getResponseTimeout() {
            return responseTimeout;
        }

        public void setResponseTimeout(Integer responseTimeout) {
            this.responseTimeout = responseTimeout;
        }

        public Integer getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(Integer heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        public Integer getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Boolean getSslEnabled() {
            return sslEnabled;
        }

        public void setSslEnabled(Boolean sslEnabled) {
            this.sslEnabled = sslEnabled;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }
    }

    /**
     * Override settings for a channel connection.
     * Any non-null value will override the corresponding value from JSON config.
     */
    public static class ChannelOverride {
        private String connectionProfile;
        private Boolean active;
        private Integer priority;
        private Map<String, String> properties;
        private Map<String, String> schemas;

        public String getConnectionProfile() {
            return connectionProfile;
        }

        public void setConnectionProfile(String connectionProfile) {
            this.connectionProfile = connectionProfile;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        public Map<String, String> getSchemas() {
            return schemas;
        }

        public void setSchemas(Map<String, String> schemas) {
            this.schemas = schemas;
        }
    }
}
