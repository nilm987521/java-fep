package com.fep.security.hsm;

import lombok.Builder;
import lombok.Data;

/**
 * HSM connection configuration.
 */
@Data
@Builder
public class HsmConnectionConfig {

    /** HSM vendor */
    private HsmVendor vendor;

    /** Primary HSM host */
    private String primaryHost;

    /** Primary HSM port */
    private int primaryPort;

    /** Secondary HSM host (for failover) */
    private String secondaryHost;

    /** Secondary HSM port */
    private int secondaryPort;

    /** Connection timeout in milliseconds */
    @Builder.Default
    private int connectionTimeout = 5000;

    /** Read timeout in milliseconds */
    @Builder.Default
    private int readTimeout = 10000;

    /** Maximum connections in pool */
    @Builder.Default
    private int maxConnections = 10;

    /** Enable SSL/TLS */
    @Builder.Default
    private boolean sslEnabled = true;

    /** SSL certificate path */
    private String sslCertPath;

    /** SSL key path */
    private String sslKeyPath;

    /** Authentication header/password */
    private String authCredential;

    /** LMK (Local Master Key) ID */
    private String lmkId;

    /** Enable automatic reconnection */
    @Builder.Default
    private boolean autoReconnect = true;

    /** Reconnection interval in milliseconds */
    @Builder.Default
    private long reconnectIntervalMs = 5000;

    /** Health check interval in milliseconds */
    @Builder.Default
    private long healthCheckIntervalMs = 30000;
}
