package com.fep.transaction.notification;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Request object for sending transaction notifications.
 */
@Data
@Builder
public class NotificationRequest {

    /** Unique notification ID */
    private String notificationId;

    /** Type of notification */
    private NotificationType type;

    /** Delivery channels */
    private Set<NotificationChannel> channels;

    /** Recipient customer ID */
    private String customerId;

    /** Recipient phone number (for SMS) */
    private String phoneNumber;

    /** Recipient email address */
    private String emailAddress;

    /** Device token for push notification */
    private String deviceToken;

    /** LINE user ID */
    private String lineUserId;

    /** Related transaction ID */
    private String transactionId;

    /** Transaction amount */
    private BigDecimal amount;

    /** Currency code */
    private String currencyCode;

    /** Account number (masked) */
    private String maskedAccount;

    /** Merchant name (for purchases) */
    private String merchantName;

    /** Custom message content */
    private String customMessage;

    /** Message template ID */
    private String templateId;

    /** Template variables */
    @Builder.Default
    private Map<String, String> templateVariables = new HashMap<>();

    /** Preferred language (zh-TW, en-US) */
    @Builder.Default
    private String language = "zh-TW";

    /** Scheduled send time (null for immediate) */
    private LocalDateTime scheduledTime;

    /** Request timestamp */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Retry count */
    @Builder.Default
    private int retryCount = 0;

    /** Maximum retry attempts */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * Checks if this notification should be sent immediately.
     */
    public boolean isImmediate() {
        return scheduledTime == null || scheduledTime.isBefore(LocalDateTime.now());
    }

    /**
     * Checks if retry is allowed.
     */
    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    /**
     * Increments retry count.
     */
    public void incrementRetry() {
        retryCount++;
    }

    /**
     * Checks if Chinese language is preferred.
     */
    public boolean isChinese() {
        return language != null && language.startsWith("zh");
    }

    /**
     * Adds a template variable.
     */
    public NotificationRequest withVariable(String key, String value) {
        templateVariables.put(key, value);
        return this;
    }
}
