package com.fep.communication.config;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for FISC TCP/IP connection.
 */
@Getter
@Setter
@Builder
public class FiscConnectionConfig {

    /** Primary host address */
    private String primaryHost;

    /** Primary port */
    private int primaryPort;

    /** Backup host address (optional) */
    private String backupHost;

    /** Backup port (optional) */
    private int backupPort;

    /** Connection timeout in milliseconds */
    @Builder.Default
    private int connectTimeoutMs = 10000;

    /** Read timeout (response timeout) in milliseconds */
    @Builder.Default
    private int readTimeoutMs = 30000;

    /** Write timeout in milliseconds */
    @Builder.Default
    private int writeTimeoutMs = 10000;

    /** Idle timeout before sending heartbeat in milliseconds */
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

    /** Whether to use backup host on primary failure */
    @Builder.Default
    private boolean useBackupOnFailure = true;

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

    /** Connection pool minimum size */
    @Builder.Default
    private int poolMinSize = 2;

    /** Connection pool maximum size */
    @Builder.Default
    private int poolMaxSize = 10;

    /** Maximum wait time to get connection from pool in milliseconds */
    @Builder.Default
    private int poolMaxWaitMs = 5000;

    /** Institution ID for this connection */
    private String institutionId;

    /** Connection name for logging */
    private String connectionName;

    /**
     * Checks if backup host is configured.
     */
    public boolean hasBackupHost() {
        return backupHost != null && !backupHost.isEmpty() && backupPort > 0;
    }

    /**
     * Creates a default configuration for testing.
     */
    public static FiscConnectionConfig defaultConfig() {
        return FiscConnectionConfig.builder()
            .primaryHost("localhost")
            .primaryPort(9000)
            .connectionName("default")
            .build();
    }
}
