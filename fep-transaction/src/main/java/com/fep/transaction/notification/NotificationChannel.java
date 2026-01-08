package com.fep.transaction.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Notification delivery channels.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationChannel {

    /** SMS text message */
    SMS("SMS", "簡訊"),

    /** Email notification */
    EMAIL("Email", "電子郵件"),

    /** Mobile app push notification */
    APP_PUSH("App Push", "APP推播"),

    /** LINE messaging */
    LINE("LINE", "LINE訊息"),

    /** In-app notification */
    IN_APP("In-App", "站內訊息");

    private final String description;
    private final String chineseDescription;

    /**
     * Checks if this channel supports rich content (HTML, images, etc.).
     */
    public boolean supportsRichContent() {
        return this == EMAIL || this == APP_PUSH || this == IN_APP;
    }

    /**
     * Gets the maximum message length for this channel.
     */
    public int getMaxMessageLength() {
        return switch (this) {
            case SMS -> 160;  // Standard SMS length
            case EMAIL -> Integer.MAX_VALUE;
            case APP_PUSH -> 256;
            case LINE -> 2000;
            case IN_APP -> 4096;
        };
    }
}
