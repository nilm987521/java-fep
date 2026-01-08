package com.fep.transaction.risk.alert.handler;

import com.fep.transaction.risk.alert.Alert;
import com.fep.transaction.risk.alert.AlertChannel;
import com.fep.transaction.risk.alert.AlertHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Alert handler that logs alerts.
 */
@Component
public class LogAlertHandler implements AlertHandler {

    private static final Logger log = LoggerFactory.getLogger(LogAlertHandler.class);

    @Override
    public AlertChannel getChannel() {
        return AlertChannel.LOG_ONLY;
    }

    @Override
    public boolean send(Alert alert, String recipient) {
        String logMessage = formatAlert(alert);

        switch (alert.getSeverity()) {
            case CRITICAL -> log.error("[ALERT-CRITICAL] {}", logMessage);
            case HIGH -> log.error("[ALERT-HIGH] {}", logMessage);
            case MEDIUM -> log.warn("[ALERT-MEDIUM] {}", logMessage);
            case LOW -> log.info("[ALERT-LOW] {}", logMessage);
            default -> log.info("[ALERT-INFO] {}", logMessage);
        }

        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private String formatAlert(Alert alert) {
        return String.format("[%s] %s - %s | Type: %s | Source: %s | TxnId: %s",
                alert.getAlertId(),
                alert.getTitle(),
                alert.getMessage(),
                alert.getType().getCode(),
                alert.getSource(),
                alert.getTransactionId() != null ? alert.getTransactionId() : "N/A");
    }
}
