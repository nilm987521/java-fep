package com.fep.jmeter.engine;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes messages to the correct client based on Bank ID.
 *
 * <p>This router maintains mappings between:
 * <ul>
 *   <li>Bank ID → Send Channel (for response routing)</li>
 *   <li>Channel ID → Bank ID (for cleanup on disconnect)</li>
 *   <li>Receive Channels (where Sampler receives requests)</li>
 *   <li>Send Channels (where Sampler sends responses)</li>
 * </ul>
 *
 * <p>Port naming (from Sampler's perspective - what the Sampler DOES):
 * <ul>
 *   <li>Receive Channel: where Sampler RECEIVES requests from clients</li>
 *   <li>Send Channel: where Sampler SENDS responses to clients</li>
 * </ul>
 *
 * <p>Routing flow:
 * <pre>
 * 1. Client connects via Receive Channel → registered by channelId
 * 2. Client sends first request with Bank ID (F32)
 * 3. Router associates Bank ID with the channelId
 * 4. Client connects via Send Channel → registered by channelId
 * 5. When response is ready, router finds Send Channel by Bank ID
 * </pre>
 */
@Slf4j
public class ClientRouter {

    /** Bank ID → Channel ID mapping */
    private final Map<String, String> bankIdToChannelId = new ConcurrentHashMap<>();

    /** Channel ID → Bank ID reverse mapping (for cleanup) */
    private final Map<String, String> channelIdToBankId = new ConcurrentHashMap<>();

    /** Receive Channels by channel ID (where Sampler receives requests) */
    private final Map<String, Channel> receiveChannels = new ConcurrentHashMap<>();

    /** Send Channels by channel ID (where Sampler sends responses) */
    private final Map<String, Channel> sendChannels = new ConcurrentHashMap<>();

    /** Channel ID mapping between Receive and Send (based on source IP) */
    private final Map<String, String> receiveToSendMapping = new ConcurrentHashMap<>();

    /**
     * Registers a Receive channel (where Sampler receives requests).
     *
     * @param channelId the channel identifier (typically remote address)
     * @param channel the Netty channel
     */
    public void registerReceiveChannel(String channelId, Channel channel) {
        receiveChannels.put(channelId, channel);
        log.debug("[Router] Registered Receive channel: {}", channelId);
    }

    /**
     * Unregisters a Receive channel.
     *
     * @param channelId the channel identifier
     */
    public void unregisterReceiveChannel(String channelId) {
        receiveChannels.remove(channelId);

        // Clean up Bank ID association
        String bankId = channelIdToBankId.remove(channelId);
        if (bankId != null) {
            bankIdToChannelId.remove(bankId);
            log.debug("[Router] Removed Bank ID association on disconnect: {} -> {}", bankId, channelId);
        }

        log.debug("[Router] Unregistered Receive channel: {}", channelId);
    }

    /**
     * Registers a Send channel (where Sampler sends responses).
     *
     * @param channelId the channel identifier (typically remote address)
     * @param channel the Netty channel
     */
    public void registerSendChannel(String channelId, Channel channel) {
        sendChannels.put(channelId, channel);

        // Try to map this Send channel to existing Receive channel by IP
        String sourceIp = extractIp(channelId);
        for (String receiveChannelId : receiveChannels.keySet()) {
            if (extractIp(receiveChannelId).equals(sourceIp)) {
                receiveToSendMapping.put(receiveChannelId, channelId);
                log.debug("[Router] Mapped Receive channel {} to Send channel {} by IP",
                    receiveChannelId, channelId);
            }
        }

        log.debug("[Router] Registered Send channel: {}", channelId);
    }

    /**
     * Unregisters a Send channel.
     *
     * @param channelId the channel identifier
     */
    public void unregisterSendChannel(String channelId) {
        sendChannels.remove(channelId);

        // Clean up mappings
        receiveToSendMapping.values().removeIf(v -> v.equals(channelId));

        log.debug("[Router] Unregistered Send channel: {}", channelId);
    }

    /**
     * Associates a Bank ID with a channel ID.
     * Called when the first request from a client is received.
     *
     * @param bankId the Bank ID from F32
     * @param receiveChannelId the Receive channel identifier
     */
    public void associateBankIdWithChannel(String bankId, String receiveChannelId) {
        // Check if already associated
        String existingChannelId = bankIdToChannelId.get(bankId);
        if (existingChannelId != null && !existingChannelId.equals(receiveChannelId)) {
            log.warn("[Router] Bank ID {} reassigned from {} to {}",
                bankId, existingChannelId, receiveChannelId);
            channelIdToBankId.remove(existingChannelId);
        }

        bankIdToChannelId.put(bankId, receiveChannelId);
        channelIdToBankId.put(receiveChannelId, bankId);
        log.info("[Router] Associated Bank ID {} with channel {}", bankId, receiveChannelId);
    }

    /**
     * Gets the Send channel for a Bank ID.
     *
     * @param bankId the Bank ID
     * @return the Send channel, or null if not found
     */
    public Channel getSendChannel(String bankId) {
        String receiveChannelId = bankIdToChannelId.get(bankId);
        if (receiveChannelId == null) {
            log.debug("[Router] No channel mapping for Bank ID: {}", bankId);
            return null;
        }

        // Find corresponding Send channel
        String sendChannelId = receiveToSendMapping.get(receiveChannelId);
        if (sendChannelId != null) {
            Channel channel = sendChannels.get(sendChannelId);
            if (channel != null && channel.isActive()) {
                return channel;
            }
        }

        // Fallback: find Send channel from same IP
        String sourceIp = extractIp(receiveChannelId);
        for (Map.Entry<String, Channel> entry : sendChannels.entrySet()) {
            if (extractIp(entry.getKey()).equals(sourceIp) && entry.getValue().isActive()) {
                return entry.getValue();
            }
        }

        log.debug("[Router] No Send channel found for Bank ID: {}", bankId);
        return null;
    }

    /**
     * Gets the first available Send channel.
     *
     * @return an active Send channel, or null if none available
     */
    public Channel getFirstAvailableSendChannel() {
        for (Channel channel : sendChannels.values()) {
            if (channel.isActive()) {
                return channel;
            }
        }
        return null;
    }

    /**
     * Gets all active Send channels.
     *
     * @return collection of active Send channels
     */
    public Collection<Channel> getAllSendChannels() {
        return sendChannels.values();
    }

    /**
     * Gets all registered Bank IDs.
     *
     * @return set of Bank IDs
     */
    public Set<String> getRegisteredBankIds() {
        return bankIdToChannelId.keySet();
    }

    /**
     * Gets the number of connected Receive channels.
     */
    public int getReceiveChannelCount() {
        return receiveChannels.size();
    }

    /**
     * Gets the number of connected Send channels.
     */
    public int getSendChannelCount() {
        return sendChannels.size();
    }

    /**
     * Clears all mappings.
     */
    public void clear() {
        bankIdToChannelId.clear();
        channelIdToBankId.clear();
        receiveChannels.clear();
        sendChannels.clear();
        receiveToSendMapping.clear();
        log.info("[Router] Cleared all mappings");
    }

    /**
     * Extracts IP address from channel ID (remote address string).
     * Format is typically "/192.168.1.10:50001"
     */
    private String extractIp(String channelId) {
        if (channelId == null) {
            return "";
        }
        // Remove leading slash
        String address = channelId.startsWith("/") ? channelId.substring(1) : channelId;
        // Extract IP part before port
        int colonIndex = address.lastIndexOf(':');
        return colonIndex > 0 ? address.substring(0, colonIndex) : address;
    }
}
