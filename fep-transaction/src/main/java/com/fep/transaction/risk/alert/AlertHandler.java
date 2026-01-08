package com.fep.transaction.risk.alert;

/**
 * Interface for alert notification handlers.
 */
public interface AlertHandler {

    /**
     * Gets the channel this handler supports.
     */
    AlertChannel getChannel();

    /**
     * Sends the alert notification.
     *
     * @param alert the alert to send
     * @param recipient the recipient (email, phone number, etc.)
     * @return true if sent successfully
     */
    boolean send(Alert alert, String recipient);

    /**
     * Checks if the handler is available.
     */
    boolean isAvailable();

    /**
     * Gets the handler name.
     */
    default String getName() {
        return getChannel().getDescription() + " Handler";
    }
}
