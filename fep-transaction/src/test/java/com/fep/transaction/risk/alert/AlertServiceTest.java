package com.fep.transaction.risk.alert;

import com.fep.transaction.risk.alert.handler.LogAlertHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertService Tests")
class AlertServiceTest {

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(List.of(new LogAlertHandler()));
    }

    @Nested
    @DisplayName("Raise Alerts")
    class RaiseAlertsTests {

        @Test
        @DisplayName("Should raise fraud alert")
        void shouldRaiseFraudAlert() {
            Alert alert = alertService.raiseFraudAlert("TXN123", "Suspected fraud detected", AlertSeverity.HIGH);

            assertNotNull(alert);
            assertNotNull(alert.getAlertId());
            assertEquals(AlertType.FRAUD_SUSPECTED, alert.getType());
            assertEquals(AlertSeverity.HIGH, alert.getSeverity());
            assertEquals("TXN123", alert.getTransactionId());
        }

        @Test
        @DisplayName("Should raise blacklist alert")
        void shouldRaiseBlacklistAlert() {
            Alert alert = alertService.raiseBlacklistAlert("TXN456", "4111****1111", "Card on blacklist");

            assertNotNull(alert);
            assertEquals(AlertType.BLACKLIST_HIT, alert.getType());
            assertEquals(AlertSeverity.HIGH, alert.getSeverity());
            assertEquals("4111****1111", alert.getRelatedEntity());
        }

        @Test
        @DisplayName("Should raise system alert")
        void shouldRaiseSystemAlert() {
            Alert alert = alertService.raiseSystemAlert("FISCConnection", "Connection timeout", AlertSeverity.CRITICAL);

            assertNotNull(alert);
            assertEquals(AlertType.SYSTEM_ERROR, alert.getType());
            assertEquals(AlertSeverity.CRITICAL, alert.getSeverity());
            assertEquals("FISCConnection", alert.getSource());
        }

        @Test
        @DisplayName("Should raise custom alert")
        void shouldRaiseCustomAlert() {
            Alert alert = Alert.builder()
                    .type(AlertType.HIGH_VALUE_TRANSACTION)
                    .severity(AlertSeverity.MEDIUM)
                    .title("High Value Transaction")
                    .message("Transaction exceeds threshold")
                    .source("TransactionMonitor")
                    .transactionId("TXN789")
                    .build();

            Alert raised = alertService.raiseAlert(alert);

            assertNotNull(raised);
            assertEquals(AlertType.HIGH_VALUE_TRANSACTION, raised.getType());
        }
    }

    @Nested
    @DisplayName("Alert Lifecycle")
    class AlertLifecycleTests {

        @Test
        @DisplayName("Should acknowledge alert")
        void shouldAcknowledgeAlert() {
            Alert alert = alertService.raiseFraudAlert("TXN123", "Test alert", AlertSeverity.HIGH);

            boolean acknowledged = alertService.acknowledgeAlert(alert.getAlertId(), "user123");

            assertTrue(acknowledged);
            Optional<Alert> updated = alertService.getAlert(alert.getAlertId());
            assertTrue(updated.isPresent());
            assertEquals(AlertStatus.ACKNOWLEDGED, updated.get().getStatus());
            assertEquals("user123", updated.get().getHandledBy());
        }

        @Test
        @DisplayName("Should resolve alert")
        void shouldResolveAlert() {
            Alert alert = alertService.raiseFraudAlert("TXN123", "Test alert", AlertSeverity.HIGH);

            boolean resolved = alertService.resolveAlert(alert.getAlertId(), "admin", "False positive");

            assertTrue(resolved);
            Optional<Alert> updated = alertService.getAlert(alert.getAlertId());
            assertTrue(updated.isPresent());
            assertEquals(AlertStatus.RESOLVED, updated.get().getStatus());
            assertEquals("False positive", updated.get().getResolutionNotes());
        }

        @Test
        @DisplayName("Should escalate alert")
        void shouldEscalateAlert() {
            Alert alert = alertService.raiseFraudAlert("TXN123", "Test alert", AlertSeverity.HIGH);

            boolean escalated = alertService.escalateAlert(alert.getAlertId());

            assertTrue(escalated);
            Optional<Alert> updated = alertService.getAlert(alert.getAlertId());
            assertTrue(updated.isPresent());
            assertEquals(AlertStatus.ESCALATED, updated.get().getStatus());
        }
    }

    @Nested
    @DisplayName("Alert Queries")
    class AlertQueriesTests {

        @BeforeEach
        void setupAlerts() {
            alertService.raiseFraudAlert("TXN1", "Alert 1", AlertSeverity.HIGH);
            alertService.raiseFraudAlert("TXN2", "Alert 2", AlertSeverity.MEDIUM);
            alertService.raiseSystemAlert("Service1", "System alert", AlertSeverity.CRITICAL);
        }

        @Test
        @DisplayName("Should get recent alerts")
        void shouldGetRecentAlerts() {
            List<Alert> alerts = alertService.getRecentAlerts(10);

            assertEquals(3, alerts.size());
        }

        @Test
        @DisplayName("Should get alerts by severity")
        void shouldGetAlertsBySeverity() {
            List<Alert> highAlerts = alertService.getAlertsBySeverity(AlertSeverity.HIGH);
            List<Alert> criticalAlerts = alertService.getAlertsBySeverity(AlertSeverity.CRITICAL);

            assertEquals(1, highAlerts.size());
            assertEquals(1, criticalAlerts.size());
        }

        @Test
        @DisplayName("Should get alerts by status")
        void shouldGetAlertsByStatus() {
            List<Alert> newAlerts = alertService.getAlertsByStatus(AlertStatus.NEW);

            assertEquals(3, newAlerts.size());
        }

        @Test
        @DisplayName("Should get unresolved alerts")
        void shouldGetUnresolvedAlerts() {
            List<Alert> alerts = alertService.getRecentAlerts(10);
            alertService.resolveAlert(alerts.get(0).getAlertId(), "admin", "Resolved");

            List<Alert> unresolved = alertService.getUnresolvedAlerts();

            assertEquals(2, unresolved.size());
        }

        @Test
        @DisplayName("Should get alerts by time range")
        void shouldGetAlertsByTimeRange() {
            LocalDateTime from = LocalDateTime.now().minusHours(1);
            LocalDateTime to = LocalDateTime.now().plusHours(1);

            List<Alert> alerts = alertService.getAlerts(from, to);

            assertEquals(3, alerts.size());
        }
    }

    @Nested
    @DisplayName("Subscriptions")
    class SubscriptionTests {

        @Test
        @DisplayName("Should add subscription")
        void shouldAddSubscription() {
            AlertSubscription subscription = AlertSubscription.builder()
                    .subscriptionId("SUB001")
                    .subscriberName("Admin")
                    .channel(AlertChannel.EMAIL)
                    .recipient("admin@example.com")
                    .minimumSeverity(AlertSeverity.HIGH)
                    .build();

            alertService.addSubscription(subscription);
            List<AlertSubscription> subscriptions = alertService.getSubscriptions();

            assertEquals(1, subscriptions.size());
            assertEquals("SUB001", subscriptions.get(0).getSubscriptionId());
        }

        @Test
        @DisplayName("Should remove subscription")
        void shouldRemoveSubscription() {
            AlertSubscription subscription = AlertSubscription.builder()
                    .subscriptionId("SUB002")
                    .subscriberName("User")
                    .channel(AlertChannel.SMS)
                    .recipient("0912345678")
                    .build();

            alertService.addSubscription(subscription);
            boolean removed = alertService.removeSubscription("SUB002");

            assertTrue(removed);
            assertTrue(alertService.getSubscriptions().isEmpty());
        }

        @Test
        @DisplayName("Should filter alerts by subscription criteria")
        void shouldFilterAlertsBySubscriptionCriteria() {
            AlertSubscription subscription = AlertSubscription.builder()
                    .subscriptionId("SUB003")
                    .subscriberName("Fraud Team")
                    .channel(AlertChannel.LOG_ONLY)
                    .recipient("log")
                    .minimumSeverity(AlertSeverity.HIGH)
                    .alertTypes(Set.of(AlertType.FRAUD_SUSPECTED))
                    .build();

            // Should receive fraud alert
            Alert fraudAlert = Alert.fraudAlert("TXN1", "Fraud", AlertSeverity.HIGH);
            assertTrue(subscription.shouldReceive(fraudAlert));

            // Should not receive system alert (wrong type)
            Alert systemAlert = Alert.systemAlert("Service", "Error", AlertSeverity.HIGH);
            assertFalse(subscription.shouldReceive(systemAlert));

            // Should not receive low severity fraud alert
            Alert lowAlert = Alert.fraudAlert("TXN2", "Minor", AlertSeverity.LOW);
            assertFalse(subscription.shouldReceive(lowAlert));
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track statistics")
        void shouldTrackStatistics() {
            alertService.raiseFraudAlert("TXN1", "Alert 1", AlertSeverity.HIGH);
            alertService.raiseFraudAlert("TXN2", "Alert 2", AlertSeverity.HIGH);
            alertService.raiseSystemAlert("Service", "System", AlertSeverity.CRITICAL);

            Map<String, Object> stats = alertService.getStatistics();

            assertEquals(3, stats.get("totalAlerts"));
            assertNotNull(stats.get("alertsByType"));
            assertNotNull(stats.get("alertsBySeverity"));
        }
    }

    @Nested
    @DisplayName("Alert Object")
    class AlertObjectTests {

        @Test
        @DisplayName("Should mark alert as sent")
        void shouldMarkAlertAsSent() {
            Alert alert = Alert.builder()
                    .type(AlertType.FRAUD_SUSPECTED)
                    .severity(AlertSeverity.HIGH)
                    .title("Test")
                    .message("Test message")
                    .build();

            alert.markSent();

            assertEquals(AlertStatus.SENT, alert.getStatus());
            assertNotNull(alert.getSentAt());
            assertEquals(1, alert.getNotificationAttempts());
        }

        @Test
        @DisplayName("Should record failure")
        void shouldRecordFailure() {
            Alert alert = Alert.builder()
                    .type(AlertType.SYSTEM_ERROR)
                    .severity(AlertSeverity.HIGH)
                    .title("Test")
                    .message("Test")
                    .build();

            alert.recordFailure("Connection refused");
            alert.recordFailure("Timeout");
            alert.recordFailure("Service unavailable");

            assertEquals(AlertStatus.FAILED, alert.getStatus());
            assertEquals(3, alert.getNotificationAttempts());
            assertEquals("Service unavailable", alert.getLastError());
        }

        @Test
        @DisplayName("Should add metadata")
        void shouldAddMetadata() {
            Alert alert = Alert.builder()
                    .type(AlertType.HIGH_VALUE_TRANSACTION)
                    .severity(AlertSeverity.MEDIUM)
                    .title("High Value")
                    .message("Large transaction")
                    .build();

            alert.addMetadata("amount", "500000");
            alert.addMetadata("currency", "TWD");

            assertEquals("500000", alert.getMetadata().get("amount"));
            assertEquals("TWD", alert.getMetadata().get("currency"));
        }

        @Test
        @DisplayName("Should dismiss alert")
        void shouldDismissAlert() {
            Alert alert = Alert.builder()
                    .type(AlertType.UNUSUAL_PATTERN)
                    .severity(AlertSeverity.LOW)
                    .title("Pattern")
                    .message("Unusual pattern")
                    .build();

            alert.dismiss("analyst", "False positive - known customer behavior");

            assertEquals(AlertStatus.DISMISSED, alert.getStatus());
            assertEquals("analyst", alert.getHandledBy());
            assertEquals("False positive - known customer behavior", alert.getResolutionNotes());
        }
    }
}
