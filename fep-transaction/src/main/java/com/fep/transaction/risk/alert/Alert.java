package com.fep.transaction.risk.alert;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a system alert.
 */
@Data
@Builder
public class Alert {

    /** Unique alert identifier */
    @Builder.Default
    private String alertId = UUID.randomUUID().toString();

    /** Alert type */
    private AlertType type;

    /** Alert severity */
    private AlertSeverity severity;

    /** Current status */
    @Builder.Default
    private AlertStatus status = AlertStatus.NEW;

    /** Alert title */
    private String title;

    /** Alert message */
    private String message;

    /** Source of the alert (service/component name) */
    private String source;

    /** Related transaction ID */
    private String transactionId;

    /** Related entity (card, account, merchant, etc.) */
    private String relatedEntity;

    /** Created timestamp */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Sent timestamp */
    private LocalDateTime sentAt;

    /** Acknowledged timestamp */
    private LocalDateTime acknowledgedAt;

    /** Resolved timestamp */
    private LocalDateTime resolvedAt;

    /** Who acknowledged/resolved the alert */
    private String handledBy;

    /** Resolution notes */
    private String resolutionNotes;

    /** Additional data */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /** Number of notification attempts */
    @Builder.Default
    private int notificationAttempts = 0;

    /** Last notification error */
    private String lastError;

    /**
     * Marks the alert as sent.
     */
    public void markSent() {
        this.status = AlertStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.notificationAttempts++;
    }

    /**
     * Marks the alert as acknowledged.
     */
    public void acknowledge(String userId) {
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedAt = LocalDateTime.now();
        this.handledBy = userId;
    }

    /**
     * Marks the alert as resolved.
     */
    public void resolve(String userId, String notes) {
        this.status = AlertStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.handledBy = userId;
        this.resolutionNotes = notes;
    }

    /**
     * Escalates the alert.
     */
    public void escalate() {
        this.status = AlertStatus.ESCALATED;
    }

    /**
     * Dismisses the alert as false positive.
     */
    public void dismiss(String userId, String reason) {
        this.status = AlertStatus.DISMISSED;
        this.resolvedAt = LocalDateTime.now();
        this.handledBy = userId;
        this.resolutionNotes = reason;
    }

    /**
     * Records a failed notification attempt.
     */
    public void recordFailure(String error) {
        this.notificationAttempts++;
        this.lastError = error;
        if (this.notificationAttempts >= 3) {
            this.status = AlertStatus.FAILED;
        }
    }

    /**
     * Adds metadata.
     */
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    /**
     * Creates a fraud alert.
     */
    public static Alert fraudAlert(String transactionId, String message, AlertSeverity severity) {
        return Alert.builder()
                .type(AlertType.FRAUD_SUSPECTED)
                .severity(severity)
                .title("Fraud Alert")
                .message(message)
                .source("FraudDetectionService")
                .transactionId(transactionId)
                .build();
    }

    /**
     * Creates a blacklist hit alert.
     */
    public static Alert blacklistAlert(String transactionId, String entity, String message) {
        return Alert.builder()
                .type(AlertType.BLACKLIST_HIT)
                .severity(AlertSeverity.HIGH)
                .title("Blacklist Match")
                .message(message)
                .source("BlacklistService")
                .transactionId(transactionId)
                .relatedEntity(entity)
                .build();
    }

    /**
     * Creates a system error alert.
     */
    public static Alert systemAlert(String source, String message, AlertSeverity severity) {
        return Alert.builder()
                .type(AlertType.SYSTEM_ERROR)
                .severity(severity)
                .title("System Alert")
                .message(message)
                .source(source)
                .build();
    }
}
