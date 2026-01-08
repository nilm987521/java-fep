package com.fep.transaction.risk.alert.handler;

import com.fep.transaction.risk.alert.Alert;
import com.fep.transaction.risk.alert.AlertChannel;
import com.fep.transaction.risk.alert.AlertHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Alert handler that sends webhook notifications.
 */
@Component
public class WebhookAlertHandler implements AlertHandler {

    private static final Logger log = LoggerFactory.getLogger(WebhookAlertHandler.class);

    private boolean enabled = true;
    private int timeoutMs = 5000;

    @Override
    public AlertChannel getChannel() {
        return AlertChannel.WEBHOOK;
    }

    @Override
    public boolean send(Alert alert, String webhookUrl) {
        if (!isAvailable()) {
            log.warn("Webhook alert handler is not available");
            return false;
        }

        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.error("Invalid webhook URL");
            return false;
        }

        try {
            Map<String, Object> payload = buildPayload(alert);
            log.info("Sending webhook alert to {}: {}", webhookUrl, alert.getAlertId());

            // TODO: Implement actual HTTP POST using WebClient or RestTemplate
            // webClient.post()
            //     .uri(webhookUrl)
            //     .bodyValue(payload)
            //     .retrieve()
            //     .toBodilessEntity()
            //     .block(Duration.ofMillis(timeoutMs));

            log.debug("Webhook payload: {}", payload);
            return true;
        } catch (Exception e) {
            log.error("Failed to send webhook alert to {}: {}", webhookUrl, e.getMessage());
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

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    private Map<String, Object> buildPayload(Alert alert) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("alertId", alert.getAlertId());
        payload.put("type", alert.getType().getCode());
        payload.put("severity", alert.getSeverity().getCode());
        payload.put("title", alert.getTitle());
        payload.put("message", alert.getMessage());
        payload.put("source", alert.getSource());
        payload.put("timestamp", alert.getCreatedAt().toString());

        if (alert.getTransactionId() != null) {
            payload.put("transactionId", alert.getTransactionId());
        }
        if (alert.getRelatedEntity() != null) {
            payload.put("relatedEntity", alert.getRelatedEntity());
        }
        if (!alert.getMetadata().isEmpty()) {
            payload.put("metadata", alert.getMetadata());
        }

        return payload;
    }
}
