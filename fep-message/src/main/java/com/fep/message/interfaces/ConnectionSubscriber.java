package com.fep.message.interfaces;

import com.fep.message.channel.ChannelConnection;
import com.fep.message.channel.ConnectionProfile;

import java.util.Map;

/**
 * Interface for components that need to be notified of channel connection changes.
 * Follows the observer pattern similar to {@link ChannelSubscriber}.
 *
 * <p>Implementations can register with {@link com.fep.message.channel.ChannelConnectionRegistry}
 * to receive updates when channel connections or profiles are loaded, modified, or removed.
 *
 * <p>Typical subscribers include:
 * <ul>
 *   <li>Connection managers that need to establish/re-establish connections</li>
 *   <li>Message handlers that need current connection settings</li>
 *   <li>Monitoring components that track connection status</li>
 * </ul>
 */
public interface ConnectionSubscriber {

    /**
     * Called when channel connections are updated.
     * This method is invoked:
     * <ul>
     *   <li>When the configuration is initially loaded</li>
     *   <li>When the configuration is reloaded (hot-reload)</li>
     *   <li>When connections are dynamically registered or unregistered</li>
     * </ul>
     *
     * @param connectionMap the updated connection map (channelId -> ChannelConnection)
     * @param profileMap the updated profile map (profileId -> ConnectionProfile)
     */
    void onConnectionsUpdated(Map<String, ChannelConnection> connectionMap,
                               Map<String, ConnectionProfile> profileMap);

    /**
     * Called when a specific channel connection is added or updated.
     * Optional method - default implementation does nothing.
     *
     * @param channelId the channel ID
     * @param connection the added or updated connection
     */
    default void onConnectionChanged(String channelId, ChannelConnection connection) {
        // Default: no-op
    }

    /**
     * Called when a specific channel connection is removed.
     * Optional method - default implementation does nothing.
     *
     * @param channelId the removed channel ID
     */
    default void onConnectionRemoved(String channelId) {
        // Default: no-op
    }
}
