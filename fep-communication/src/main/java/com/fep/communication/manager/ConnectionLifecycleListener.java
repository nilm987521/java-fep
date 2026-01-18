package com.fep.communication.manager;

import com.fep.communication.client.FiscDualChannelClient;
import com.fep.communication.server.FiscDualChannelServer;

/**
 * Listener interface for connection lifecycle events.
 *
 * <p>Implementations receive notifications when connections are dynamically
 * added, removed, or recreated in the {@link DynamicConnectionManager}.
 *
 * <p>Typical use cases:
 * <ul>
 *   <li>Updating routing tables when connections change</li>
 *   <li>Refreshing connection pools or load balancers</li>
 *   <li>Triggering monitoring/alerting systems</li>
 *   <li>Logging connection lifecycle events for audit</li>
 * </ul>
 *
 * <p>Example implementation:
 * <pre>{@code
 * @Component
 * public class ConnectionMonitor implements ConnectionLifecycleListener {
 *     @Override
 *     public void onConnectionAdded(String channelId, FiscDualChannelClient client) {
 *         log.info("New connection available: {}", channelId);
 *         metricsService.registerConnection(channelId);
 *     }
 *
 *     @Override
 *     public void onConnectionRemoved(String channelId) {
 *         log.info("Connection removed: {}", channelId);
 *         metricsService.unregisterConnection(channelId);
 *     }
 * }
 * }</pre>
 */
public interface ConnectionLifecycleListener {

    /**
     * Called when a new connection is added.
     *
     * <p>This method is invoked after the connection has been successfully
     * established and the client is ready for use.
     *
     * @param channelId the channel ID of the new connection
     * @param client the connected FiscDualChannelClient instance
     */
    void onConnectionAdded(String channelId, FiscDualChannelClient client);

    /**
     * Called when a connection is removed.
     *
     * <p>This method is invoked after the connection has been gracefully
     * closed (sign-off completed and resources released).
     *
     * @param channelId the channel ID of the removed connection
     */
    void onConnectionRemoved(String channelId);

    /**
     * Called when a connection is recreated due to configuration change.
     *
     * <p>This occurs when the configuration for an existing channel changes,
     * requiring the connection to be closed and re-established with new settings.
     *
     * <p>Default implementation delegates to {@link #onConnectionAdded}.
     *
     * @param channelId the channel ID of the recreated connection
     * @param client the new FiscDualChannelClient instance
     */
    default void onConnectionRecreated(String channelId, FiscDualChannelClient client) {
        onConnectionAdded(channelId, client);
    }

    /**
     * Called when a connection fails to establish.
     *
     * <p>This method is invoked when an attempt to connect fails.
     * The DynamicConnectionManager may retry based on configuration.
     *
     * <p>Default implementation does nothing.
     *
     * @param channelId the channel ID that failed to connect
     * @param cause the cause of the failure
     */
    default void onConnectionFailed(String channelId, Throwable cause) {
        // Default: no-op
    }

    /**
     * Called when a connection state changes.
     *
     * <p>Default implementation does nothing.
     *
     * @param channelId the channel ID
     * @param oldState the previous state description
     * @param newState the new state description
     */
    default void onConnectionStateChanged(String channelId, String oldState, String newState) {
        // Default: no-op
    }

    /**
     * Called when a server is started and ready to accept connections.
     *
     * <p>This method is invoked after the server has successfully bound to its ports
     * and is ready to accept incoming client connections.
     *
     * <p>Default implementation does nothing.
     *
     * @param channelId the channel ID of the server
     * @param server the started FiscDualChannelServer instance
     */
    default void onServerStarted(String channelId, FiscDualChannelServer server) {
        // Default: no-op
    }

    /**
     * Called when a client connects to a server.
     *
     * <p>This method is invoked when an incoming client establishes a connection
     * to a server managed by this DynamicConnectionManager.
     *
     * <p>Default implementation does nothing.
     *
     * @param channelId the channel ID of the server
     * @param clientId the unique identifier of the connected client
     * @param remoteAddress the remote address of the client
     */
    default void onClientConnectedToServer(String channelId, String clientId, String remoteAddress) {
        // Default: no-op
    }

    /**
     * Called when a client disconnects from a server.
     *
     * <p>Default implementation does nothing.
     *
     * @param channelId the channel ID of the server
     * @param clientId the unique identifier of the disconnected client
     */
    default void onClientDisconnectedFromServer(String channelId, String clientId) {
        // Default: no-op
    }
}
