package com.fep.transaction.risk.alert;

/**
 * Channels for alert notifications.
 */
public enum AlertChannel {

    /** Email notification */
    EMAIL("Email", true),

    /** SMS notification */
    SMS("SMS", true),

    /** LINE messaging */
    LINE("LINE", true),

    /** Slack notification */
    SLACK("Slack", true),

    /** Webhook callback */
    WEBHOOK("Webhook", true),

    /** In-app notification */
    IN_APP("In-App", true),

    /** Log only (no external notification) */
    LOG_ONLY("Log Only", false);

    private final String description;
    private final boolean external;

    AlertChannel(String description, boolean external) {
        this.description = description;
        this.external = external;
    }

    public String getDescription() {
        return description;
    }

    public boolean isExternal() {
        return external;
    }
}
