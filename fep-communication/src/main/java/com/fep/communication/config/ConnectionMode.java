package com.fep.communication.config;

/**
 * Defines the connection mode for a channel.
 *
 * <p>In FISC architecture:
 * <ul>
 *   <li>{@link #CLIENT} - FEP initiates connection to remote server (e.g., FEP connects to FISC)</li>
 *   <li>{@link #SERVER} - FEP accepts incoming connections (e.g., ATM connects to FEP)</li>
 * </ul>
 */
public enum ConnectionMode {

    /**
     * Client mode: FEP initiates outbound TCP connection to a remote server.
     * <p>Used for: FEP → FISC connections
     */
    CLIENT,

    /**
     * Server mode: FEP listens on a port and accepts inbound TCP connections.
     * <p>Used for: ATM → FEP, POS → FEP connections
     */
    SERVER
}
