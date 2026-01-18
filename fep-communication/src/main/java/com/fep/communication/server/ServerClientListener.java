package com.fep.communication.server;

import com.fep.message.iso8583.Iso8583Message;

/**
 * Listener for server-side client connection events.
 *
 * <p>Implement this interface to receive notifications when:
 * <ul>
 *   <li>A client connects to the server</li>
 *   <li>A client disconnects from the server</li>
 *   <li>A message is received from a client</li>
 * </ul>
 */
public interface ServerClientListener {

    /**
     * Called when a client establishes a connection.
     *
     * @param clientId      the unique identifier for the client
     * @param remoteAddress the remote address of the client
     */
    void onClientConnected(String clientId, String remoteAddress);

    /**
     * Called when a client disconnects.
     *
     * @param clientId the unique identifier for the client
     */
    void onClientDisconnected(String clientId);

    /**
     * Called when a message is received from a client.
     *
     * @param clientId the unique identifier for the client
     * @param message  the received message
     */
    default void onMessageReceived(String clientId, Iso8583Message message) {
        // Default: no-op
    }

    /**
     * Called when a message is sent to a client.
     *
     * @param clientId the unique identifier for the client
     * @param message  the sent message
     */
    default void onMessageSent(String clientId, Iso8583Message message) {
        // Default: no-op
    }

    /**
     * Called when an error occurs with a client connection.
     *
     * @param clientId the unique identifier for the client
     * @param cause    the error cause
     */
    default void onClientError(String clientId, Throwable cause) {
        // Default: no-op
    }
}
