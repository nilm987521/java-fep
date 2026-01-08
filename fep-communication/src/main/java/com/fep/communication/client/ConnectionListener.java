package com.fep.communication.client;

import com.fep.message.iso8583.Iso8583Message;

/**
 * Listener interface for connection events.
 */
public interface ConnectionListener {

    /**
     * Called when connection is established.
     *
     * @param connectionName the connection name
     */
    default void onConnected(String connectionName) {}

    /**
     * Called when connection is disconnected.
     *
     * @param connectionName the connection name
     * @param cause the cause of disconnection (may be null for clean disconnect)
     */
    default void onDisconnected(String connectionName, Throwable cause) {}

    /**
     * Called when sign-on is successful.
     *
     * @param connectionName the connection name
     */
    default void onSignedOn(String connectionName) {}

    /**
     * Called when sign-off is completed.
     *
     * @param connectionName the connection name
     */
    default void onSignedOff(String connectionName) {}

    /**
     * Called when reconnection attempt starts.
     *
     * @param connectionName the connection name
     * @param attempt the attempt number
     */
    default void onReconnecting(String connectionName, int attempt) {}

    /**
     * Called when a message is received.
     *
     * @param connectionName the connection name
     * @param message the received message
     */
    default void onMessageReceived(String connectionName, Iso8583Message message) {}

    /**
     * Called when connection state changes.
     *
     * @param connectionName the connection name
     * @param oldState the previous state
     * @param newState the new state
     */
    default void onStateChanged(String connectionName, ConnectionState oldState, ConnectionState newState) {}

    /**
     * Called when an error occurs.
     *
     * @param connectionName the connection name
     * @param error the error
     */
    default void onError(String connectionName, Throwable error) {}
}
