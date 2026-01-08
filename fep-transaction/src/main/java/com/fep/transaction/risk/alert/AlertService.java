package com.fep.transaction.risk.alert;

import com.fep.transaction.risk.alert.handler.LogAlertHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Service for managing and dispatching alerts.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    // Alert handlers by channel
    private final Map<AlertChannel, AlertHandler> handlers = new EnumMap<>(AlertChannel.class);

    // Alert subscriptions
    private final Map<String, AlertSubscription> subscriptions = new ConcurrentHashMap<>();

    // Alert history (in-memory, should use database in production)
    private final ConcurrentLinkedDeque<Alert> alertHistory = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY_SIZE = 10000;

    // Statistics
    private final Map<AlertType, Long> alertCounts = new ConcurrentHashMap<>();
    private final Map<AlertSeverity, Long> severityCounts = new ConcurrentHashMap<>();

    // Async executor for non-blocking notifications
    private final ExecutorService notificationExecutor;

    public AlertService(List<AlertHandler> alertHandlers) {
        // Register all handlers
        for (AlertHandler handler : alertHandlers) {
            registerHandler(handler);
        }

        // Ensure log handler is always available
        if (!handlers.containsKey(AlertChannel.LOG_ONLY)) {
            registerHandler(new LogAlertHandler());
        }

        // Initialize executor
        this.notificationExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "alert-notifier");
            t.setDaemon(true);
            return t;
        });

        log.info("AlertService initialized with {} handlers", handlers.size());
    }

    /**
     * Registers an alert handler.
     */
    public void registerHandler(AlertHandler handler) {
        handlers.put(handler.getChannel(), handler);
        log.debug("Registered alert handler: {}", handler.getName());
    }

    /**
     * Adds an alert subscription.
     */
    public void addSubscription(AlertSubscription subscription) {
        subscriptions.put(subscription.getSubscriptionId(), subscription);
        log.info("Added alert subscription: {} for {} via {}",
                subscription.getSubscriptionId(),
                subscription.getSubscriberName(),
                subscription.getChannel());
    }

    /**
     * Removes an alert subscription.
     */
    public boolean removeSubscription(String subscriptionId) {
        AlertSubscription removed = subscriptions.remove(subscriptionId);
        if (removed != null) {
            log.info("Removed alert subscription: {}", subscriptionId);
            return true;
        }
        return false;
    }

    /**
     * Raises a new alert.
     */
    public Alert raiseAlert(Alert alert) {
        // Store in history
        addToHistory(alert);

        // Update statistics
        alertCounts.merge(alert.getType(), 1L, Long::sum);
        severityCounts.merge(alert.getSeverity(), 1L, Long::sum);

        // Always log the alert
        AlertHandler logHandler = handlers.get(AlertChannel.LOG_ONLY);
        if (logHandler != null) {
            logHandler.send(alert, null);
        }

        // Send to subscribers asynchronously
        notificationExecutor.submit(() -> notifySubscribers(alert));

        return alert;
    }

    /**
     * Raises a fraud alert.
     */
    public Alert raiseFraudAlert(String transactionId, String message, AlertSeverity severity) {
        Alert alert = Alert.fraudAlert(transactionId, message, severity);
        return raiseAlert(alert);
    }

    /**
     * Raises a blacklist hit alert.
     */
    public Alert raiseBlacklistAlert(String transactionId, String entity, String message) {
        Alert alert = Alert.blacklistAlert(transactionId, entity, message);
        return raiseAlert(alert);
    }

    /**
     * Raises a system alert.
     */
    public Alert raiseSystemAlert(String source, String message, AlertSeverity severity) {
        Alert alert = Alert.systemAlert(source, message, severity);
        return raiseAlert(alert);
    }

    /**
     * Acknowledges an alert.
     */
    public boolean acknowledgeAlert(String alertId, String userId) {
        Alert alert = findAlert(alertId);
        if (alert != null) {
            alert.acknowledge(userId);
            log.info("Alert {} acknowledged by {}", alertId, userId);
            return true;
        }
        return false;
    }

    /**
     * Resolves an alert.
     */
    public boolean resolveAlert(String alertId, String userId, String notes) {
        Alert alert = findAlert(alertId);
        if (alert != null) {
            alert.resolve(userId, notes);
            log.info("Alert {} resolved by {}: {}", alertId, userId, notes);
            return true;
        }
        return false;
    }

    /**
     * Escalates an alert.
     */
    public boolean escalateAlert(String alertId) {
        Alert alert = findAlert(alertId);
        if (alert != null) {
            alert.escalate();
            // Re-notify with escalation
            notificationExecutor.submit(() -> notifySubscribers(alert));
            log.warn("Alert {} escalated", alertId);
            return true;
        }
        return false;
    }

    /**
     * Gets alert by ID.
     */
    public Optional<Alert> getAlert(String alertId) {
        return Optional.ofNullable(findAlert(alertId));
    }

    /**
     * Gets recent alerts.
     */
    public List<Alert> getRecentAlerts(int limit) {
        return alertHistory.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Gets alerts by status.
     */
    public List<Alert> getAlertsByStatus(AlertStatus status) {
        return alertHistory.stream()
                .filter(a -> a.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * Gets alerts by severity.
     */
    public List<Alert> getAlertsBySeverity(AlertSeverity severity) {
        return alertHistory.stream()
                .filter(a -> a.getSeverity() == severity)
                .collect(Collectors.toList());
    }

    /**
     * Gets unresolved alerts.
     */
    public List<Alert> getUnresolvedAlerts() {
        return alertHistory.stream()
                .filter(a -> a.getStatus() != AlertStatus.RESOLVED &&
                        a.getStatus() != AlertStatus.DISMISSED)
                .collect(Collectors.toList());
    }

    /**
     * Gets alerts for a specific time range.
     */
    public List<Alert> getAlerts(LocalDateTime from, LocalDateTime to) {
        return alertHistory.stream()
                .filter(a -> !a.getCreatedAt().isBefore(from) && !a.getCreatedAt().isAfter(to))
                .collect(Collectors.toList());
    }

    /**
     * Gets all subscriptions.
     */
    public List<AlertSubscription> getSubscriptions() {
        return new ArrayList<>(subscriptions.values());
    }

    /**
     * Gets statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAlerts", alertHistory.size());
        stats.put("unresolvedAlerts", getUnresolvedAlerts().size());
        stats.put("alertsByType", new HashMap<>(alertCounts));
        stats.put("alertsBySeverity", new HashMap<>(severityCounts));
        stats.put("activeSubscriptions", subscriptions.values().stream()
                .filter(AlertSubscription::isActive).count());
        stats.put("availableHandlers", handlers.entrySet().stream()
                .filter(e -> e.getValue().isAvailable())
                .map(e -> e.getKey().name())
                .collect(Collectors.toList()));
        return stats;
    }

    /**
     * Clears alert history (use with caution).
     */
    public void clearHistory() {
        alertHistory.clear();
        alertCounts.clear();
        severityCounts.clear();
        log.warn("Alert history cleared");
    }

    // Private methods

    private void notifySubscribers(Alert alert) {
        for (AlertSubscription subscription : subscriptions.values()) {
            if (subscription.shouldReceive(alert)) {
                sendToSubscriber(alert, subscription);
            }
        }
    }

    private void sendToSubscriber(Alert alert, AlertSubscription subscription) {
        AlertHandler handler = handlers.get(subscription.getChannel());
        if (handler == null || !handler.isAvailable()) {
            log.warn("No handler available for channel: {}", subscription.getChannel());
            return;
        }

        try {
            boolean success = handler.send(alert, subscription.getRecipient());
            if (success) {
                alert.markSent();
                log.debug("Alert {} sent to {} via {}",
                        alert.getAlertId(),
                        subscription.getSubscriberName(),
                        subscription.getChannel());
            } else {
                alert.recordFailure("Send returned false");
            }
        } catch (Exception e) {
            alert.recordFailure(e.getMessage());
            log.error("Failed to send alert {} to {}: {}",
                    alert.getAlertId(),
                    subscription.getSubscriberName(),
                    e.getMessage());
        }
    }

    private void addToHistory(Alert alert) {
        alertHistory.addFirst(alert);
        while (alertHistory.size() > MAX_HISTORY_SIZE) {
            alertHistory.removeLast();
        }
    }

    private Alert findAlert(String alertId) {
        return alertHistory.stream()
                .filter(a -> alertId.equals(a.getAlertId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Shutdown the notification executor.
     */
    public void shutdown() {
        notificationExecutor.shutdown();
        try {
            if (!notificationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                notificationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            notificationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("AlertService shutdown complete");
    }
}
