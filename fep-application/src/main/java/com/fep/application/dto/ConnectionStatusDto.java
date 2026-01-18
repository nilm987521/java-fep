package com.fep.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing the status of a FISC connection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectionStatusDto {

    /**
     * Channel identifier (e.g., FISC_INTERBANK_V1)
     */
    private String channelId;

    /**
     * Connection state (DISCONNECTED, CONNECTING, CONNECTED, SIGNED_ON, etc.)
     */
    private String state;

    /**
     * Whether the send channel is connected
     */
    private boolean sendChannelConnected;

    /**
     * Whether the receive channel is connected
     */
    private boolean receiveChannelConnected;

    /**
     * Whether sign-on is complete
     */
    private boolean signedOn;

    /**
     * Remote host address
     */
    private String host;

    /**
     * Send channel port
     */
    private int sendPort;

    /**
     * Receive channel port
     */
    private int receivePort;

    /**
     * Connection profile ID
     */
    private String connectionProfileId;

    /**
     * Whether the channel is configured as active
     */
    private boolean active;

    /**
     * Priority for failover
     */
    private int priority;

    /**
     * Connection mode: CLIENT or SERVER
     */
    private String connectionMode;

    /**
     * Number of connected clients (for SERVER mode only)
     */
    private Integer connectedClients;

    /**
     * Timestamp of this status snapshot
     */
    private LocalDateTime timestamp;

    /**
     * Error message if in failed state
     */
    private String errorMessage;
}
