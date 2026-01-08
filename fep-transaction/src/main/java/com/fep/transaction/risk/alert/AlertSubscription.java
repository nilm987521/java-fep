package com.fep.transaction.risk.alert;

import lombok.Builder;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents an alert subscription configuration.
 */
@Data
@Builder
public class AlertSubscription {

    /** Subscription ID */
    private String subscriptionId;

    /** Subscriber name */
    private String subscriberName;

    /** Notification channel */
    private AlertChannel channel;

    /** Recipient address (email, phone, webhook URL, etc.) */
    private String recipient;

    /** Alert types to subscribe to (empty = all) */
    @Builder.Default
    private Set<AlertType> alertTypes = new HashSet<>();

    /** Minimum severity to receive (null = all) */
    private AlertSeverity minimumSeverity;

    /** Whether the subscription is active */
    @Builder.Default
    private boolean active = true;

    /** Quiet hours start (0-23, null = no quiet hours) */
    private Integer quietHoursStart;

    /** Quiet hours end (0-23) */
    private Integer quietHoursEnd;

    /**
     * Checks if this subscription should receive the given alert.
     */
    public boolean shouldReceive(Alert alert) {
        if (!active) {
            return false;
        }

        // Check alert type filter
        if (!alertTypes.isEmpty() && !alertTypes.contains(alert.getType())) {
            return false;
        }

        // Check minimum severity
        if (minimumSeverity != null && !alert.getSeverity().isHigherThan(minimumSeverity)
                && alert.getSeverity() != minimumSeverity) {
            return false;
        }

        // Check quiet hours
        if (isInQuietHours()) {
            // Only allow CRITICAL alerts during quiet hours
            return alert.getSeverity() == AlertSeverity.CRITICAL;
        }

        return true;
    }

    private boolean isInQuietHours() {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }

        int currentHour = java.time.LocalTime.now().getHour();

        if (quietHoursStart <= quietHoursEnd) {
            // Normal range (e.g., 22-06 wraps around midnight)
            return currentHour >= quietHoursStart && currentHour < quietHoursEnd;
        } else {
            // Wraps around midnight (e.g., 22-06)
            return currentHour >= quietHoursStart || currentHour < quietHoursEnd;
        }
    }

    /**
     * Subscribes to all alert types.
     */
    public void subscribeToAll() {
        this.alertTypes.clear();
    }

    /**
     * Subscribes to specific alert types.
     */
    public void subscribeTo(AlertType... types) {
        for (AlertType type : types) {
            this.alertTypes.add(type);
        }
    }
}
