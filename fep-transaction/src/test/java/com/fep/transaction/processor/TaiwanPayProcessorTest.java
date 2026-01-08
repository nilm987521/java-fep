package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaiwanPayProcessor.
 */
@DisplayName("TaiwanPayProcessor Tests")
class TaiwanPayProcessorTest {

    private TaiwanPayProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TaiwanPayProcessor();
    }

    @Test
    @DisplayName("Should support TAIWAN_PAY transaction type")
    void shouldSupportTaiwanPayType() {
        assertEquals(TransactionType.TAIWAN_PAY, processor.getSupportedType());
    }

    @Nested
    @DisplayName("Successful Payment Tests")
    class SuccessfulPaymentTests {

        @Test
        @DisplayName("Should process PUSH payment successfully")
        void shouldProcessPushPaymentSuccessfully() {
            // Arrange - Consumer presents QR to merchant
            TransactionRequest request = createTaiwanPayRequest(
                    "TWP001",
                    TaiwanPayProcessor.PAYMENT_TYPE_PUSH,
                    new BigDecimal("500")
            );
            request.setTaiwanPayToken("1234567890123456");

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
            assertNotNull(response.getAuthorizationCode());
            assertNotNull(response.getTaiwanPayReference());
        }

        @Test
        @DisplayName("Should process PULL payment successfully")
        void shouldProcessPullPaymentSuccessfully() {
            // Arrange - Consumer scans merchant QR
            TransactionRequest request = createTaiwanPayRequest(
                    "TWP002",
                    TaiwanPayProcessor.PAYMENT_TYPE_PULL,
                    new BigDecimal("1000")
            );
            request.setMerchantQrCode("12345678901234567890");

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
            assertNotNull(response.getTaiwanPayReference());
        }

        @Test
        @DisplayName("Should process maximum amount successfully")
        void shouldProcessMaximumAmountSuccessfully() {
            // Arrange
            TransactionRequest request = createTaiwanPayRequest(
                    "TWP003",
                    TaiwanPayProcessor.PAYMENT_TYPE_PUSH,
                    new BigDecimal("50000") // Maximum allowed
            );
            request.setTaiwanPayToken("1234567890123456");

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
        }
    }

    @Nested
    @DisplayName("Validation Error Tests")
    class ValidationErrorTests {

        @Test
        @DisplayName("Should reject when source account is missing")
        void shouldRejectMissingSourceAccount() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TWP-ERR001")
                    .transactionType(TransactionType.TAIWAN_PAY)
                    .paymentType(TaiwanPayProcessor.PAYMENT_TYPE_PUSH)
                    .taiwanPayToken("1234567890123456")
                    .amount(new BigDecimal("500"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Source account is required"));
        }

        @Test
        @DisplayName("Should reject when payment type is missing")
        void shouldRejectMissingPaymentType() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TWP-ERR002")
                    .transactionType(TransactionType.TAIWAN_PAY)
                    .sourceAccount("12345678901234")
                    .taiwanPayToken("1234567890123456")
                    .amount(new BigDecimal("500"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Payment type is required"));
        }

        @Test
        @DisplayName("Should reject invalid payment type")
        void shouldRejectInvalidPaymentType() {
            // Arrange
            TransactionRequest request = createTaiwanPayRequest(
                    "TWP-ERR003",
                    "INVALID",
                    new BigDecimal("500")
            );
            request.setTaiwanPayToken("1234567890123456");

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid payment type"));
        }

        @Test
        @DisplayName("Should reject PUSH without token")
        void shouldRejectPushWithoutToken() {
            // Arrange
            TransactionRequest request = createTaiwanPayRequest(
                    "TWP-ERR004",
                    TaiwanPayProcessor.PAYMENT_TYPE_PUSH,
                    new BigDecimal("500")
            );
            // No token set

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Taiwan Pay token is required"));
        }

        @Test
        @DisplayName("Should reject PULL without merchant QR")
        void shouldRejectPullWithoutMerchantQr() {
            // Arrange
            TransactionRequest request = createTaiwanPayRequest(
                    "TWP-ERR005",
                    TaiwanPayProcessor.PAYMENT_TYPE_PULL,
                    new BigDecimal("500")
            );
            // No merchant QR set

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Merchant QR code is required"));
        }

        @Test
        @DisplayName("Should reject short token")
        void shouldRejectShortToken() {
            // Arrange
            TransactionRequest request = createTaiwanPayRequest(
                    "TWP-ERR006",
                    TaiwanPayProcessor.PAYMENT_TYPE_PUSH,
                    new BigDecimal("500")
            );
            request.setTaiwanPayToken("123"); // Too short

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid Taiwan Pay token"));
        }

        @Test
        @DisplayName("Should reject when amount is missing")
        void shouldRejectMissingAmount() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TWP-ERR007")
                    .transactionType(TransactionType.TAIWAN_PAY)
                    .sourceAccount("12345678901234")
                    .paymentType(TaiwanPayProcessor.PAYMENT_TYPE_PUSH)
                    .taiwanPayToken("1234567890123456")
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Payment amount is required"));
        }

        @Test
        @DisplayName("Should reject amount exceeding limit")
        void shouldRejectExceedingLimit() {
            // Arrange
            TransactionRequest request = createTaiwanPayRequest(
                    "TWP-ERR008",
                    TaiwanPayProcessor.PAYMENT_TYPE_PUSH,
                    new BigDecimal("60000") // Exceeds 50000 limit
            );
            request.setTaiwanPayToken("1234567890123456");

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertEquals(ResponseCode.EXCEEDS_WITHDRAWAL_LIMIT.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should reject amount below minimum")
        void shouldRejectBelowMinimum() {
            // Arrange
            TransactionRequest request = createTaiwanPayRequest(
                    "TWP-ERR009",
                    TaiwanPayProcessor.PAYMENT_TYPE_PUSH,
                    new BigDecimal("0.5") // Below minimum
            );
            request.setTaiwanPayToken("1234567890123456");

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Payment amount must be at least"));
        }
    }

    // Helper method to create Taiwan Pay request
    private TransactionRequest createTaiwanPayRequest(
            String txnId,
            String paymentType,
            BigDecimal amount) {

        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.TAIWAN_PAY)
                .processingCode("290000")
                .sourceAccount("12345678901234")
                .paymentType(paymentType)
                .amount(amount)
                .currencyCode("901")
                .terminalId("POS00001")
                .acquiringBankCode("004")
                .stan("000001")
                .rrn("123456789012")
                .channel("MOBILE")
                .build();
    }
}
