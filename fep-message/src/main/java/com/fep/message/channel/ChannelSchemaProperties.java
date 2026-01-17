package com.fep.message.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Channel-Schema mapping.
 *
 * <p>Example configuration in application.yml:
 * <pre>
 * fep:
 *   channel:
 *     config-file: config/channel-schema-mapping.json
 *     hot-reload: true
 *     hot-reload-interval: 5000
 * </pre>
 */
@ConfigurationProperties(prefix = "fep.channel")
public class ChannelSchemaProperties {

    /**
     * Path to the channel-schema mapping configuration file.
     * Supports both absolute and relative paths.
     * Default: config/channel-schema-mapping.json
     */
    private String configFile = "config/channel-schema-mapping.json";

    /**
     * Enable hot-reload of channel configuration.
     * When enabled, the system will watch for file changes and reload automatically.
     * Default: false
     */
    private boolean hotReload = false;

    /**
     * Interval in milliseconds between hot-reload checks.
     * Only applicable when hot-reload is enabled.
     * Default: 5000 (5 seconds)
     */
    private long hotReloadInterval = 5000;

    /**
     * Whether to fail application startup if config file is not found.
     * If false, the registry will start empty and wait for dynamic registration.
     * Default: true
     */
    private boolean failOnMissingConfig = true;

    /**
     * Whether to validate schema references on startup.
     * When enabled, all referenced schemas must exist in JsonSchemaLoader.
     * Default: false (for flexibility during development)
     */
    private boolean validateSchemaReferences = false;

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public boolean isHotReload() {
        return hotReload;
    }

    public void setHotReload(boolean hotReload) {
        this.hotReload = hotReload;
    }

    public long getHotReloadInterval() {
        return hotReloadInterval;
    }

    public void setHotReloadInterval(long hotReloadInterval) {
        this.hotReloadInterval = hotReloadInterval;
    }

    public boolean isFailOnMissingConfig() {
        return failOnMissingConfig;
    }

    public void setFailOnMissingConfig(boolean failOnMissingConfig) {
        this.failOnMissingConfig = failOnMissingConfig;
    }

    public boolean isValidateSchemaReferences() {
        return validateSchemaReferences;
    }

    public void setValidateSchemaReferences(boolean validateSchemaReferences) {
        this.validateSchemaReferences = validateSchemaReferences;
    }

    @Override
    public String toString() {
        return "ChannelSchemaProperties{" +
                "configFile='" + configFile + '\'' +
                ", hotReload=" + hotReload +
                ", hotReloadInterval=" + hotReloadInterval +
                ", failOnMissingConfig=" + failOnMissingConfig +
                ", validateSchemaReferences=" + validateSchemaReferences +
                '}';
    }
}
