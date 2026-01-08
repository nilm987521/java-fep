package com.fep.transaction.risk.alert.handler;

import com.fep.transaction.risk.alert.Alert;
import com.fep.transaction.risk.alert.AlertChannel;
import com.fep.transaction.risk.alert.AlertHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Alert handler that sends SMS notifications.
 */
@Component
public class SmsAlertHandler implements AlertHandler {

    private static final Logger log = LoggerFactory.getLogger(SmsAlertHandler.class);
    private static final int MAX_SMS_LENGTH = 160;

    private boolean enabled = true;

    @Override
    public AlertChannel getChannel() {
        return AlertChannel.SMS;
    }

    @Override
    public boolean send(Alert alert, String recipient) {
        if (!isAvailable()) {
            log.warn("SMS alert handler is not available");
            return false;
        }

        if (!isValidPhoneNumber(recipient)) {
            log.error("Invalid phone number: {}", recipient);
            return false;
        }

        try {
            String message = formatSmsMessage(alert);
            log.info("Sending SMS alert to {}: {}", maskPhoneNumber(recipient), message);

            // TODO: Implement actual SMS sending via SMS gateway
            // smsGateway.send(recipient, message);

            return true;
        } catch (Exception e) {
            log.error("Failed to send SMS alert to {}: {}", maskPhoneNumber(recipient), e.getMessage());
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

    private String formatSmsMessage(Alert alert) {
        String message = String.format("[FEP-%s] %s: %s",
                alert.getSeverity().getCode(),
                alert.getType().getCode(),
                alert.getMessage());

        if (message.length() > MAX_SMS_LENGTH) {
            message = message.substring(0, MAX_SMS_LENGTH - 3) + "...";
        }
        return message;
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }
        // Taiwan phone number format: 09XXXXXXXX or +886XXXXXXXXX
        return phoneNumber.matches("^(09\\d{8}|\\+886\\d{9})$");
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) {
            return "****";
        }
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(phoneNumber.length() - 2);
    }
}
