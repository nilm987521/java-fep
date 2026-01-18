package com.fep.message.channel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * Represents a reusable connection profile for channel connections.
 * Multiple channels can share the same connection profile.
 *
 * <p>This class encapsulates all TCP/IP connection settings including:
 * <ul>
 *   <li>Host and port configurations (dual-channel support)</li>
 *   <li>Timeout settings (connect, response)</li>
 *   <li>Heartbeat and retry configurations</li>
 *   <li>SSL/TLS settings</li>
 * </ul>
 *
 * <p>Example configuration:
 * <pre>
 * {
 *   "profileId": "FISC_PRIMARY",
 *   "host": "fisc.bank.com",
 *   "sendPort": 9001,
 *   "receivePort": 9002,
 *   "connectTimeout": 5000,
 *   "responseTimeout": 30000,
 *   "heartbeatInterval": 60000,
 *   "maxRetries": 3,
 *   "sslEnabled": false
 * }
 * </pre>
 */
@Data
@Builder
@EqualsAndHashCode(of = "profileId")
public class ConnectionProfile {

    /**
     * Unique profile identifier.
     * Convention: {PROVIDER}_{TYPE} (e.g., "FISC_PRIMARY", "FISC_BACKUP")
     */
    @JsonProperty(required = true)
    private String profileId;

    /**
     * Remote host address (hostname or IP).
     */
    @JsonProperty(required = true)
    private String host;

    /**
     * Port for sending messages (outbound channel).
     */
    private int sendPort;

    /**
     * Port for receiving messages (inbound channel).
     * If not specified, uses same as sendPort (single channel mode).
     */
    private int receivePort;

    /**
     * Connection timeout in milliseconds.
     * Default: 5000 (5 seconds)
     */
    @Builder.Default
    private int connectTimeout = 5000;

    /**
     * Response timeout in milliseconds.
     * Maximum time to wait for a response after sending a message.
     * Default: 30000 (30 seconds)
     */
    @Builder.Default
    private int responseTimeout = 30000;

    /**
     * Heartbeat interval in milliseconds.
     * Interval between network management (0800) messages.
     * Default: 60000 (60 seconds)
     */
    @Builder.Default
    private int heartbeatInterval = 60000;

    /**
     * Maximum number of retry attempts on connection failure.
     * Default: 3
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * Retry delay in milliseconds between connection attempts.
     * Default: 5000 (5 seconds)
     */
    @Builder.Default
    private int retryDelay = 5000;

    /**
     * Whether SSL/TLS is enabled for this connection.
     * Default: false
     */
    @Builder.Default
    private boolean sslEnabled = false;

    /**
     * SSL keystore path (if SSL is enabled).
     */
    private String sslKeystorePath;

    /**
     * SSL keystore password (if SSL is enabled).
     */
    private String sslKeystorePassword;

    /**
     * SSL truststore path (if SSL is enabled).
     */
    private String sslTruststorePath;

    /**
     * SSL truststore password (if SSL is enabled).
     */
    private String sslTruststorePassword;

    /**
     * Connection pool size (number of connections per channel).
     * Default: 1
     */
    @Builder.Default
    private int poolSize = 1;

    /**
     * Whether to auto-reconnect on connection failure.
     * Default: true
     */
    @Builder.Default
    private boolean autoReconnect = true;

    /**
     * Connection keep-alive interval in milliseconds.
     * Default: 30000 (30 seconds)
     */
    @Builder.Default
    private int keepAliveInterval = 30000;

    /**
     * Connection mode: CLIENT or SERVER.
     * <ul>
     *   <li>CLIENT: FEP connects to remote server (e.g., FEP → FISC)</li>
     *   <li>SERVER: FEP listens for incoming connections (e.g., ATM → FEP)</li>
     * </ul>
     * Default: CLIENT
     */
    @Builder.Default
    private String connectionMode = "CLIENT";

    /**
     * Additional connection properties.
     * (e.g., institutionId, encoding settings, etc.)
     */
    private Map<String, String> properties;

    /**
     * JSON creator for deserialization.
     */
    @JsonCreator
    public ConnectionProfile(
            @JsonProperty("profileId") String profileId,
            @JsonProperty("host") String host,
            @JsonProperty("sendPort") Integer sendPort,
            @JsonProperty("receivePort") Integer receivePort,
            @JsonProperty("connectTimeout") Integer connectTimeout,
            @JsonProperty("responseTimeout") Integer responseTimeout,
            @JsonProperty("heartbeatInterval") Integer heartbeatInterval,
            @JsonProperty("maxRetries") Integer maxRetries,
            @JsonProperty("retryDelay") Integer retryDelay,
            @JsonProperty("sslEnabled") Boolean sslEnabled,
            @JsonProperty("sslKeystorePath") String sslKeystorePath,
            @JsonProperty("sslKeystorePassword") String sslKeystorePassword,
            @JsonProperty("sslTruststorePath") String sslTruststorePath,
            @JsonProperty("sslTruststorePassword") String sslTruststorePassword,
            @JsonProperty("poolSize") Integer poolSize,
            @JsonProperty("autoReconnect") Boolean autoReconnect,
            @JsonProperty("keepAliveInterval") Integer keepAliveInterval,
            @JsonProperty("connectionMode") String connectionMode,
            @JsonProperty("properties") Map<String, String> properties) {
        this.profileId = profileId;
        this.host = host;
        this.sendPort = sendPort != null ? sendPort : 0;
        this.receivePort = receivePort != null ? receivePort : this.sendPort;
        this.connectTimeout = connectTimeout != null ? connectTimeout : 5000;
        this.responseTimeout = responseTimeout != null ? responseTimeout : 30000;
        this.heartbeatInterval = heartbeatInterval != null ? heartbeatInterval : 60000;
        this.maxRetries = maxRetries != null ? maxRetries : 3;
        this.retryDelay = retryDelay != null ? retryDelay : 5000;
        this.sslEnabled = sslEnabled != null && sslEnabled;
        this.sslKeystorePath = sslKeystorePath;
        this.sslKeystorePassword = sslKeystorePassword;
        this.sslTruststorePath = sslTruststorePath;
        this.sslTruststorePassword = sslTruststorePassword;
        this.poolSize = poolSize != null && poolSize > 0 ? poolSize : 1;
        this.autoReconnect = autoReconnect == null || autoReconnect;
        this.keepAliveInterval = keepAliveInterval != null ? keepAliveInterval : 30000;
        this.connectionMode = connectionMode != null ? connectionMode : "CLIENT";
        this.properties = properties;
    }

    /**
     * Gets a property value with a default fallback.
     *
     * @param key the property key
     * @param defaultValue the default value if key not found
     * @return the property value or default
     */
    public String getProperty(String key, String defaultValue) {
        if (properties == null) {
            return defaultValue;
        }
        return properties.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a property value, returning null if not found.
     *
     * @param key the property key
     * @return the property value or null
     */
    public String getProperty(String key) {
        return getProperty(key, null);
    }

    /**
     * Checks if this is a dual-channel configuration.
     * Dual-channel means send and receive ports are different.
     *
     * @return true if send and receive ports are different
     */
    public boolean isDualChannel() {
        return sendPort != receivePort && receivePort > 0;
    }

    /**
     * Checks if this profile is configured for server mode.
     * In server mode, FEP listens for incoming connections.
     *
     * @return true if server mode
     */
    public boolean isServerMode() {
        return "SERVER".equalsIgnoreCase(connectionMode);
    }

    /**
     * Checks if this profile is configured for client mode.
     * In client mode, FEP connects to remote servers.
     *
     * @return true if client mode
     */
    public boolean isClientMode() {
        return !"SERVER".equalsIgnoreCase(connectionMode);
    }

    /**
     * Gets the effective receive port.
     * Returns sendPort if receivePort is not configured.
     *
     * @return the receive port
     */
    public int getEffectiveReceivePort() {
        return receivePort > 0 ? receivePort : sendPort;
    }

    /**
     * Validates this connection profile.
     *
     * @throws ChannelConfigException if validation fails
     */
    public void validate() {
        if (profileId == null || profileId.isBlank()) {
            throw new ChannelConfigException("ConnectionProfile must have a profileId");
        }
        if (host == null || host.isBlank()) {
            throw new ChannelConfigException(
                    "ConnectionProfile '" + profileId + "' must have a host");
        }
        if (sendPort <= 0 || sendPort > 65535) {
            throw new ChannelConfigException(
                    "ConnectionProfile '" + profileId + "' has invalid sendPort: " + sendPort);
        }
        if (receivePort < 0 || receivePort > 65535) {
            throw new ChannelConfigException(
                    "ConnectionProfile '" + profileId + "' has invalid receivePort: " + receivePort);
        }
    }
}
