package com.fep.transaction.notification;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Result of a notification delivery attempt.
 */
@Data
@Builder
public class NotificationResult {

    /** Notification ID */
    private String notificationId;

    /** Overall success status */
    private boolean success;

    /** Per-channel delivery results */
    @Builder.Default
    private Map<NotificationChannel, ChannelResult> channelResults = new HashMap<>();

    /** Error message (if failed) */
    private String errorMessage;

    /** Processing time in milliseconds */
    private long processingTimeMs;

    /** Result timestamp */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Result for a specific channel.
     */
    @Data
    @Builder
    public static class ChannelResult {
        /** Channel */
        private NotificationChannel channel;

        /** Success status */
        private boolean success;

        /** Provider reference ID */
        private String providerRefId;

        /** Error code */
        private String errorCode;

        /** Error message */
        private String errorMessage;

        /** Delivery time */
        private LocalDateTime deliveredAt;
    }

    /**
     * Creates a successful result.
     */
    public static NotificationResult success(String notificationId) {
        return NotificationResult.builder()
                .notificationId(notificationId)
                .success(true)
                .build();
    }

    /**
     * Creates a failed result.
     */
    public static NotificationResult failure(String notificationId, String errorMessage) {
        return NotificationResult.builder()
                .notificationId(notificationId)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * Adds a channel result.
     */
    public NotificationResult withChannelResult(ChannelResult result) {
        channelResults.put(result.getChannel(), result);
        return this;
    }

    /**
     * Checks if all channels were successful.
     */
    public boolean allChannelsSuccessful() {
        return channelResults.values().stream().allMatch(ChannelResult::isSuccess);
    }

    /**
     * Gets the count of successful channels.
     */
    public long getSuccessfulChannelCount() {
        return channelResults.values().stream().filter(ChannelResult::isSuccess).count();
    }

    /**
     * Gets the count of failed channels.
     */
    public long getFailedChannelCount() {
        return channelResults.values().stream().filter(r -> !r.isSuccess()).count();
    }
}
