package com.fep.message.interfaces;

import com.fep.message.channel.Channel;

import java.util.Map;

/**
 * Interface for components that need to be notified of channel changes.
 * Follows the observer pattern similar to {@link SchemaSubscriber}.
 *
 * <p>Implementations can register with {@link com.fep.message.channel.ChannelSchemaRegistry}
 * to receive updates when channels are loaded, modified, or removed.
 */
public interface ChannelSubscriber {

    /**
     * Called when channels are updated.
     * This method is invoked:
     * <ul>
     *   <li>When the channel configuration is initially loaded</li>
     *   <li>When the configuration is reloaded (hot-reload)</li>
     *   <li>When channels are dynamically registered or unregistered</li>
     * </ul>
     *
     * @param channelMap the updated channel map (channelId -> Channel)
     */
    void onChannelsUpdated(Map<String, Channel> channelMap);
}
