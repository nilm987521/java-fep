package com.fep.transaction.risk.alert.handler;

import com.fep.transaction.risk.alert.Alert;
import com.fep.transaction.risk.alert.AlertChannel;
import com.fep.transaction.risk.alert.AlertHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Alert handler that sends email notifications.
 * Note: Actual email sending implementation should be configured with Spring Mail.
 */
@Component
public class EmailAlertHandler implements AlertHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertHandler.class);

    private boolean enabled = true;

    @Override
    public AlertChannel getChannel() {
        return AlertChannel.EMAIL;
    }

    @Override
    public boolean send(Alert alert, String recipient) {
        if (!isAvailable()) {
            log.warn("Email alert handler is not available");
            return false;
        }

        try {
            // In production, this would use JavaMailSender
            String subject = formatSubject(alert);
            String body = formatBody(alert);

            log.info("Sending email alert to {}: {}", recipient, subject);
            // TODO: Implement actual email sending with Spring Mail
            // mailSender.send(message);

            log.debug("Email body: {}", body);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email alert to {}: {}", recipient, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private String formatSubject(Alert alert) {
        return String.format("[%s] %s - %s",
                alert.getSeverity().getCode(),
                alert.getType().getDescription(),
                alert.getTitle());
    }

    private String formatBody(Alert alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== FEP System Alert ===\n\n");
        sb.append("Alert ID: ").append(alert.getAlertId()).append("\n");
        sb.append("Severity: ").append(alert.getSeverity().getDescription()).append("\n");
        sb.append("Type: ").append(alert.getType().getDescription()).append("\n");
        sb.append("Time: ").append(alert.getCreatedAt()).append("\n");
        sb.append("\n");
        sb.append("Title: ").append(alert.getTitle()).append("\n");
        sb.append("Message: ").append(alert.getMessage()).append("\n");
        sb.append("\n");
        if (alert.getTransactionId() != null) {
            sb.append("Transaction ID: ").append(alert.getTransactionId()).append("\n");
        }
        if (alert.getRelatedEntity() != null) {
            sb.append("Related Entity: ").append(alert.getRelatedEntity()).append("\n");
        }
        sb.append("Source: ").append(alert.getSource()).append("\n");
        sb.append("\n");
        sb.append("--- This is an automated message from FEP System ---");
        return sb.toString();
    }
}
