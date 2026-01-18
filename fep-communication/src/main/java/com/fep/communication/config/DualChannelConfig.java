package com.fep.communication.config;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for FISC dual-channel (Send/Receive separated) connection.
 *
 * <p>In FISC dual-channel architecture:
 * <ul>
 *   <li>Send Channel: dedicated TCP connection for sending all requests</li>
 *   <li>Receive Channel: dedicated TCP connection for receiving all responses</li>
 * </ul>
 *
 * <p>Each channel can have its own primary and backup host configuration.
 */
@Getter
@Setter
@Builder
public class DualChannelConfig {

    // ==================== Send Channel Configuration ====================

    /** Send channel primary host address */
    private String sendHost;

    /** Send channel primary port */
    private int sendPort;

    /** Send channel backup host address (optional) */
    private String sendBackupHost;

    /** Send channel backup port (optional) */
    private int sendBackupPort;

    // ==================== Receive Channel Configuration ====================

    /** Receive channel primary host address */
    private String receiveHost;

    /** Receive channel primary port */
    private int receivePort;

    /** Receive channel backup host address (optional) */
    private String receiveBackupHost;

    /** Receive channel backup port (optional) */
    private int receiveBackupPort;

    // ==================== Common Configuration ====================

    /** Connection timeout in milliseconds */
    @Builder.Default
    private int connectTimeoutMs = 10000;

    /** Read timeout (response timeout) in milliseconds */
    @Builder.Default
    private int readTimeoutMs = 30000;

    /** Write timeout in milliseconds */
    @Builder.Default
    private int writeTimeoutMs = 10000;

    /** Idle timeout before considering connection stale in milliseconds */
    @Builder.Default
    private int idleTimeoutMs = 30000;

    /** Heartbeat interval in milliseconds */
    @Builder.Default
    private int heartbeatIntervalMs = 30000;

    /** Maximum retry attempts for reconnection */
    @Builder.Default
    private int maxRetryAttempts = 3;

    /** Delay between retry attempts in milliseconds */
    @Builder.Default
    private int retryDelayMs = 5000;

    /** Whether to enable auto-reconnect */
    @Builder.Default
    private boolean autoReconnect = true;

    /** TCP keep-alive enabled */
    @Builder.Default
    private boolean tcpKeepAlive = true;

    /** TCP no-delay (disable Nagle's algorithm) */
    @Builder.Default
    private boolean tcpNoDelay = true;

    /** Socket receive buffer size */
    @Builder.Default
    private int receiveBufferSize = 65536;

    /** Socket send buffer size */
    @Builder.Default
    private int sendBufferSize = 65536;

    /** Institution ID for this connection */
    private String institutionId;

    /** Connection name for logging */
    private String connectionName;

    /**
     * Channel ID for schema resolution.
     * Used by ChannelMessageService to select appropriate message schema.
     * Examples: ATM_FISC_V1, POS_GENERIC_V1, FISC_INTERBANK_V1
     */
    private String channelId;

    /**
     * Whether to enable GenericMessage transformation.
     * When enabled, messages will be transformed using ChannelMessageService.
     */
    @Builder.Default
    private boolean enableGenericMessageTransform = false;

    // ==================== Dual-Channel Specific Configuration ====================

    /** Strategy for handling single channel failure */
    @Builder.Default
    private ChannelFailureStrategy failureStrategy = ChannelFailureStrategy.FAIL_WHEN_BOTH_DOWN;

    /** Health check interval in milliseconds */
    @Builder.Default
    private int healthCheckIntervalMs = 10000;

    /** Whether to enable dual-channel mode (for backward compatibility) */
    @Builder.Default
    private boolean dualChannelMode = true;

    // ==================== Helper Methods ====================

    /**
     * Checks if Send channel backup host is configured.
     *
     * @return true if backup is configured
     */
    public boolean hasSendBackupHost() {
        return sendBackupHost != null && !sendBackupHost.isEmpty() && sendBackupPort > 0;
    }

    /**
     * Checks if Receive channel backup host is configured.
     *
     * @return true if backup is configured
     */
    public boolean hasReceiveBackupHost() {
        return receiveBackupHost != null && !receiveBackupHost.isEmpty() && receiveBackupPort > 0;
    }

    /**
     * Gets the Send channel name for logging.
     *
     * @return send channel name
     */
    public String getSendChannelName() {
        return (connectionName != null ? connectionName : "FISC") + "-SEND";
    }

    /**
     * Gets the Receive channel name for logging.
     *
     * @return receive channel name
     */
    public String getReceiveChannelName() {
        return (connectionName != null ? connectionName : "FISC") + "-RECV";
    }

    /**
     * Validates the configuration.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (sendHost == null || sendHost.isEmpty()) {
            throw new IllegalArgumentException("Send host is required");
        }
        if (sendPort <= 0 || sendPort > 65535) {
            throw new IllegalArgumentException("Invalid send port: " + sendPort);
        }
        if (receiveHost == null || receiveHost.isEmpty()) {
            throw new IllegalArgumentException("Receive host is required");
        }
        if (receivePort <= 0 || receivePort > 65535) {
            throw new IllegalArgumentException("Invalid receive port: " + receivePort);
        }
        if (connectTimeoutMs <= 0) {
            throw new IllegalArgumentException("Connect timeout must be positive");
        }
        if (readTimeoutMs <= 0) {
            throw new IllegalArgumentException("Read timeout must be positive");
        }
    }

    /**
     * Creates a DualChannelConfig from a legacy FiscConnectionConfig.
     *
     * <p>For backward compatibility, this maps the single-channel config to dual-channel
     * by using the same host/port for both Send and Receive channels.
     *
     * @param singleConfig the legacy single-channel configuration
     * @return a new DualChannelConfig
     */
    public static DualChannelConfig fromSingleConfig(FiscConnectionConfig singleConfig) {
        return DualChannelConfig.builder()
            .sendHost(singleConfig.getPrimaryHost())
            .sendPort(singleConfig.getPrimaryPort())
            .sendBackupHost(singleConfig.getBackupHost())
            .sendBackupPort(singleConfig.getBackupPort())
            .receiveHost(singleConfig.getPrimaryHost())
            .receivePort(singleConfig.getPrimaryPort())
            .receiveBackupHost(singleConfig.getBackupHost())
            .receiveBackupPort(singleConfig.getBackupPort())
            .connectTimeoutMs(singleConfig.getConnectTimeoutMs())
            .readTimeoutMs(singleConfig.getReadTimeoutMs())
            .writeTimeoutMs(singleConfig.getWriteTimeoutMs())
            .idleTimeoutMs(singleConfig.getIdleTimeoutMs())
            .heartbeatIntervalMs(singleConfig.getHeartbeatIntervalMs())
            .maxRetryAttempts(singleConfig.getMaxRetryAttempts())
            .retryDelayMs(singleConfig.getRetryDelayMs())
            .autoReconnect(singleConfig.isAutoReconnect())
            .tcpKeepAlive(singleConfig.isTcpKeepAlive())
            .tcpNoDelay(singleConfig.isTcpNoDelay())
            .receiveBufferSize(singleConfig.getReceiveBufferSize())
            .sendBufferSize(singleConfig.getSendBufferSize())
            .institutionId(singleConfig.getInstitutionId())
            .connectionName(singleConfig.getConnectionName())
            .dualChannelMode(false) // Not true dual-channel mode
            .build();
    }

    /**
     * Creates a default configuration for testing.
     *
     * @return default configuration
     */
    public static DualChannelConfig defaultConfig() {
        return DualChannelConfig.builder()
            .sendHost("localhost")
            .sendPort(9001)
            .receiveHost("localhost")
            .receivePort(9002)
            .connectionName("default")
            .build();
    }

    /**
     * Creates a FiscConnectionConfig for the Send channel.
     *
     * <p>Useful when creating individual channel connections.
     *
     * @return FiscConnectionConfig for Send channel
     */
    public FiscConnectionConfig toSendChannelConfig() {
        return FiscConnectionConfig.builder()
            .primaryHost(sendHost)
            .primaryPort(sendPort)
            .backupHost(sendBackupHost)
            .backupPort(sendBackupPort)
            .connectTimeoutMs(connectTimeoutMs)
            .readTimeoutMs(readTimeoutMs)
            .writeTimeoutMs(writeTimeoutMs)
            .idleTimeoutMs(idleTimeoutMs)
            .heartbeatIntervalMs(heartbeatIntervalMs)
            .maxRetryAttempts(maxRetryAttempts)
            .retryDelayMs(retryDelayMs)
            .autoReconnect(autoReconnect)
            .tcpKeepAlive(tcpKeepAlive)
            .tcpNoDelay(tcpNoDelay)
            .receiveBufferSize(receiveBufferSize)
            .sendBufferSize(sendBufferSize)
            .institutionId(institutionId)
            .connectionName(getSendChannelName())
            .build();
    }

    /**
     * Creates a FiscConnectionConfig for the Receive channel.
     *
     * <p>Useful when creating individual channel connections.
     *
     * @return FiscConnectionConfig for Receive channel
     */
    public FiscConnectionConfig toReceiveChannelConfig() {
        return FiscConnectionConfig.builder()
            .primaryHost(receiveHost)
            .primaryPort(receivePort)
            .backupHost(receiveBackupHost)
            .backupPort(receiveBackupPort)
            .connectTimeoutMs(connectTimeoutMs)
            .readTimeoutMs(readTimeoutMs)
            .writeTimeoutMs(writeTimeoutMs)
            .idleTimeoutMs(idleTimeoutMs)
            .heartbeatIntervalMs(heartbeatIntervalMs)
            .maxRetryAttempts(maxRetryAttempts)
            .retryDelayMs(retryDelayMs)
            .autoReconnect(autoReconnect)
            .tcpKeepAlive(tcpKeepAlive)
            .tcpNoDelay(tcpNoDelay)
            .receiveBufferSize(receiveBufferSize)
            .sendBufferSize(sendBufferSize)
            .institutionId(institutionId)
            .connectionName(getReceiveChannelName())
            .build();
    }
}
