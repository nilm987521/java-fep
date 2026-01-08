package com.fep.transaction.notification;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NotificationService.
 */
class NotificationServiceTest {

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
    }

    @AfterEach
    void tearDown() {
        notificationService.shutdown();
    }

    @Nested
    @DisplayName("SMS Notification Tests")
    class SmsNotificationTests {

        @Test
        @DisplayName("Should send SMS notification successfully")
        void shouldSendSmsSuccessfully() {
            NotificationRequest request = NotificationRequest.builder()
                    .notificationId("NOTIF-001")
                    .type(NotificationType.TRANSACTION_SUCCESS)
                    .channels(Set.of(NotificationChannel.SMS))
                    .phoneNumber("0912345678")
                    .templateVariables(createTemplateVariables())
                    .build();

            NotificationResult result = notificationService.send(request);

            assertTrue(result.isSuccess());
            assertEquals(1, result.getSuccessfulChannelCount());
            assertTrue(result.getChannelResults().containsKey(NotificationChannel.SMS));
            assertTrue(result.getChannelResults().get(NotificationChannel.SMS).isSuccess());
        }

        @Test
        @DisplayName("Should send OTP via SMS")
        void shouldSendOtpViaSms() {
            NotificationResult result = notificationService.sendOtp("0912345678", "123456", 5);

            assertTrue(result.isSuccess());
            assertTrue(result.getChannelResults().get(NotificationChannel.SMS).isSuccess());
        }

        @Test
        @DisplayName("Should send security alert")
        void shouldSendSecurityAlert() {
            NotificationResult result = notificationService.sendSecurityAlert(
                    "0912345678",
                    "異常登入嘗試",
                    "1234****5678"
            );

            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("Multi-Channel Notification Tests")
    class MultiChannelTests {

        @Test
        @DisplayName("Should send to multiple channels")
        void shouldSendToMultipleChannels() {
            NotificationRequest request = NotificationRequest.builder()
                    .notificationId("NOTIF-002")
                    .type(NotificationType.TRANSACTION_SUCCESS)
                    .channels(Set.of(NotificationChannel.SMS, NotificationChannel.EMAIL))
                    .phoneNumber("0912345678")
                    .emailAddress("test@example.com")
                    .templateVariables(createTemplateVariables())
                    .build();

            NotificationResult result = notificationService.send(request);

            assertTrue(result.isSuccess());
            assertEquals(2, result.getSuccessfulChannelCount());
            assertTrue(result.allChannelsSuccessful());
        }

        @Test
        @DisplayName("Should send push notification")
        void shouldSendPushNotification() {
            NotificationRequest request = NotificationRequest.builder()
                    .notificationId("NOTIF-003")
                    .type(NotificationType.TRANSACTION_SUCCESS)
                    .channels(Set.of(NotificationChannel.APP_PUSH))
                    .deviceToken("device-token-123")
                    .templateVariables(createTemplateVariables())
                    .build();

            NotificationResult result = notificationService.send(request);

            assertTrue(result.isSuccess());
            assertTrue(result.getChannelResults().get(NotificationChannel.APP_PUSH).isSuccess());
        }
    }

    @Nested
    @DisplayName("Async Notification Tests")
    class AsyncNotificationTests {

        @Test
        @DisplayName("Should send notification asynchronously")
        void shouldSendAsync() throws Exception {
            NotificationRequest request = NotificationRequest.builder()
                    .notificationId("NOTIF-ASYNC-001")
                    .type(NotificationType.TRANSACTION_SUCCESS)
                    .channels(Set.of(NotificationChannel.SMS))
                    .phoneNumber("0912345678")
                    .templateVariables(createTemplateVariables())
                    .build();

            CompletableFuture<NotificationResult> future = notificationService.sendAsync(request);
            NotificationResult result = future.get(5, TimeUnit.SECONDS);

            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("Transaction Notification Tests")
    class TransactionNotificationTests {

        @Test
        @DisplayName("Should create notification from successful transaction")
        void shouldCreateFromSuccessfulTransaction() {
            TransactionRequest txnRequest = TransactionRequest.builder()
                    .transactionId("TXN-001")
                    .transactionType(TransactionType.WITHDRAWAL)
                    .amount(new BigDecimal("10000"))
                    .currencyCode("TWD")
                    .sourceAccount("1234567890123456")
                    .build();

            TransactionResponse txnResponse = TransactionResponse.builder()
                    .transactionId("TXN-001")
                    .approved(true)
                    .availableBalance(new BigDecimal("50000"))
                    .build();

            NotificationRequest notification = notificationService.createFromTransaction(
                    txnRequest, txnResponse, "0912345678", "test@example.com"
            );

            assertNotNull(notification);
            assertEquals(NotificationType.TRANSACTION_SUCCESS, notification.getType());
            assertTrue(notification.getChannels().contains(NotificationChannel.SMS));
            assertTrue(notification.getChannels().contains(NotificationChannel.EMAIL));
            assertEquals("0912345678", notification.getPhoneNumber());
            assertEquals("test@example.com", notification.getEmailAddress());
        }

        @Test
        @DisplayName("Should create notification from failed transaction")
        void shouldCreateFromFailedTransaction() {
            TransactionRequest txnRequest = TransactionRequest.builder()
                    .transactionId("TXN-002")
                    .transactionType(TransactionType.WITHDRAWAL)
                    .amount(new BigDecimal("10000"))
                    .currencyCode("TWD")
                    .sourceAccount("1234567890123456")
                    .build();

            TransactionResponse txnResponse = TransactionResponse.builder()
                    .transactionId("TXN-002")
                    .approved(false)
                    .responseDescription("Insufficient funds")
                    .build();

            NotificationRequest notification = notificationService.createFromTransaction(
                    txnRequest, txnResponse, "0912345678", null
            );

            assertNotNull(notification);
            assertEquals(NotificationType.TRANSACTION_FAILED, notification.getType());
            assertEquals(1, notification.getChannels().size());
            assertTrue(notification.getChannels().contains(NotificationChannel.SMS));
        }

        @Test
        @DisplayName("Should send large amount alert when threshold exceeded")
        void shouldSendLargeAmountAlert() {
            TransactionRequest txnRequest = TransactionRequest.builder()
                    .transactionId("TXN-LARGE-001")
                    .transactionType(TransactionType.TRANSFER)
                    .amount(new BigDecimal("150000"))
                    .currencyCode("TWD")
                    .sourceAccount("1234567890123456")
                    .build();

            TransactionResponse txnResponse = TransactionResponse.builder()
                    .transactionId("TXN-LARGE-001")
                    .approved(true)
                    .build();

            NotificationResult result = notificationService.sendLargeAmountAlertIfNeeded(
                    txnRequest, txnResponse, "0912345678"
            );

            assertNotNull(result);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should not send large amount alert when below threshold")
        void shouldNotSendLargeAmountAlertBelowThreshold() {
            TransactionRequest txnRequest = TransactionRequest.builder()
                    .transactionId("TXN-SMALL-001")
                    .transactionType(TransactionType.TRANSFER)
                    .amount(new BigDecimal("5000"))
                    .currencyCode("TWD")
                    .sourceAccount("1234567890123456")
                    .build();

            TransactionResponse txnResponse = TransactionResponse.builder()
                    .transactionId("TXN-SMALL-001")
                    .approved(true)
                    .build();

            NotificationResult result = notificationService.sendLargeAmountAlertIfNeeded(
                    txnRequest, txnResponse, "0912345678"
            );

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Notification History Tests")
    class HistoryTests {

        @Test
        @DisplayName("Should track notification history")
        void shouldTrackHistory() {
            notificationService.clearHistory();

            NotificationRequest request = NotificationRequest.builder()
                    .notificationId("NOTIF-HIST-001")
                    .type(NotificationType.TRANSACTION_SUCCESS)
                    .channels(Set.of(NotificationChannel.SMS))
                    .phoneNumber("0912345678")
                    .templateVariables(createTemplateVariables())
                    .build();

            notificationService.send(request);
            notificationService.send(request);

            List<NotificationResult> history = notificationService.getHistory();
            assertEquals(2, history.size());
        }

        @Test
        @DisplayName("Should clear history")
        void shouldClearHistory() {
            NotificationRequest request = NotificationRequest.builder()
                    .notificationId("NOTIF-CLEAR-001")
                    .type(NotificationType.TRANSACTION_SUCCESS)
                    .channels(Set.of(NotificationChannel.SMS))
                    .phoneNumber("0912345678")
                    .templateVariables(createTemplateVariables())
                    .build();

            notificationService.send(request);
            assertFalse(notificationService.getHistory().isEmpty());

            notificationService.clearHistory();
            assertTrue(notificationService.getHistory().isEmpty());
        }
    }

    @Nested
    @DisplayName("Template Tests")
    class TemplateTests {

        @Test
        @DisplayName("Should render template with variables")
        void shouldRenderTemplateWithVariables() {
            NotificationTemplate template = NotificationTemplate.smsTransactionSuccessZh();

            Map<String, String> variables = Map.of(
                    "bankName", "測試銀行",
                    "maskedAccount", "1234****5678",
                    "transactionTime", "2026/01/06 12:00:00",
                    "transactionType", "提款",
                    "currency", "TWD",
                    "amount", "10,000.00",
                    "balance", "50,000.00",
                    "servicePhone", "0800-000-000"
            );

            String rendered = template.renderBody(variables);

            assertTrue(rendered.contains("測試銀行"));
            assertTrue(rendered.contains("1234****5678"));
            assertTrue(rendered.contains("提款"));
            assertTrue(rendered.contains("10,000.00"));
        }

        @Test
        @DisplayName("Should keep placeholder if variable not provided")
        void shouldKeepPlaceholderIfNotProvided() {
            NotificationTemplate template = NotificationTemplate.smsTransactionSuccessZh();

            String rendered = template.renderBody(Map.of("bankName", "測試銀行"));

            assertTrue(rendered.contains("測試銀行"));
            assertTrue(rendered.contains("${maskedAccount}"));
        }
    }

    private Map<String, String> createTemplateVariables() {
        return Map.of(
                "bankName", "FEP Bank",
                "maskedAccount", "1234****5678",
                "transactionTime", "2026/01/06 12:00:00",
                "transactionType", "提款",
                "currency", "TWD",
                "amount", "10,000.00",
                "balance", "50,000.00",
                "servicePhone", "0800-000-000"
        );
    }
}
