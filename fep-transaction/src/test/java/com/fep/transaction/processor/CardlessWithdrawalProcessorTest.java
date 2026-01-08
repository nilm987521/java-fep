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
 * Unit tests for CardlessWithdrawalProcessor.
 */
@DisplayName("CardlessWithdrawalProcessor Tests")
class CardlessWithdrawalProcessorTest {

    private CardlessWithdrawalProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CardlessWithdrawalProcessor();
    }

    @Test
    @DisplayName("Should support CARDLESS_WITHDRAWAL transaction type")
    void shouldSupportCardlessWithdrawalType() {
        assertEquals(TransactionType.CARDLESS_WITHDRAWAL, processor.getSupportedType());
    }

    @Nested
    @DisplayName("Successful Withdrawal Tests")
    class SuccessfulWithdrawalTests {

        @Test
        @DisplayName("Should process cardless withdrawal with code successfully")
        void shouldProcessWithCodeSuccessfully() {
            // Arrange
            TransactionRequest request = createCardlessRequest(
                    "CL001",
                    "0912345678",
                    "12345678",  // Cardless code
                    null,        // No OTP
                    new BigDecimal("5000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
            assertNotNull(response.getAuthorizationCode());
            assertNotNull(response.getCardlessReference());
        }

        @Test
        @DisplayName("Should process cardless withdrawal with OTP successfully")
        void shouldProcessWithOtpSuccessfully() {
            // Arrange
            TransactionRequest request = createCardlessRequest(
                    "CL002",
                    "0987654321",
                    null,        // No cardless code
                    "123456",    // OTP
                    new BigDecimal("10000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should process maximum amount successfully")
        void shouldProcessMaximumAmountSuccessfully() {
            // Arrange
            TransactionRequest request = createCardlessRequest(
                    "CL003",
                    "0912345678",
                    "12345678",
                    null,
                    new BigDecimal("30000") // Maximum allowed
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
        }

        @Test
        @DisplayName("Should process minimum amount successfully")
        void shouldProcessMinimumAmountSuccessfully() {
            // Arrange
            TransactionRequest request = createCardlessRequest(
                    "CL004",
                    "0912345678",
                    "12345678",
                    null,
                    new BigDecimal("100") // Minimum allowed
            );

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
                    .transactionId("CL-ERR001")
                    .transactionType(TransactionType.CARDLESS_WITHDRAWAL)
                    .mobilePhone("0912345678")
                    .cardlessCode("12345678")
                    .amount(new BigDecimal("5000"))
                    .terminalId("ATM00001")
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Source account is required"));
        }

        @Test
        @DisplayName("Should reject when mobile phone is missing")
        void shouldRejectMissingMobilePhone() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("CL-ERR002")
                    .transactionType(TransactionType.CARDLESS_WITHDRAWAL)
                    .sourceAccount("12345678901234")
                    .cardlessCode("12345678")
                    .amount(new BigDecimal("5000"))
                    .terminalId("ATM00001")
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Mobile phone number is required"));
        }

        @Test
        @DisplayName("Should reject invalid mobile phone format")
        void shouldRejectInvalidMobilePhone() {
            // Arrange
            TransactionRequest request = createCardlessRequest(
                    "CL-ERR003",
                    "1234567890", // Invalid format (not starting with 09)
                    "12345678",
                    null,
                    new BigDecimal("5000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid mobile phone number"));
        }

        @Test
        @DisplayName("Should reject when both code and OTP are missing")
        void shouldRejectMissingCredentials() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("CL-ERR004")
                    .transactionType(TransactionType.CARDLESS_WITHDRAWAL)
                    .sourceAccount("12345678901234")
                    .mobilePhone("0912345678")
                    .amount(new BigDecimal("5000"))
                    .terminalId("ATM00001")
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Cardless code or OTP is required"));
        }

        @Test
        @DisplayName("Should reject invalid cardless code format")
        void shouldRejectInvalidCardlessCode() {
            // Arrange
            TransactionRequest request = createCardlessRequest(
                    "CL-ERR005",
                    "0912345678",
                    "123", // Too short
                    null,
                    new BigDecimal("5000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid cardless code"));
        }

        @Test
        @DisplayName("Should reject invalid OTP format")
        void shouldRejectInvalidOtpFormat() {
            // Arrange
            TransactionRequest request = createCardlessRequest(
                    "CL-ERR006",
                    "0912345678",
                    null,
                    "12345", // Should be 6 digits
                    new BigDecimal("5000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid OTP format"));
        }

        @Test
        @DisplayName("Should reject non-numeric OTP")
        void shouldRejectNonNumericOtp() {
            // Arrange
            TransactionRequest request = createCardlessRequest(
                    "CL-ERR007",
                    "0912345678",
                    null,
                    "12345A", // Contains letter
                    new BigDecimal("5000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid OTP format"));
        }

        @Test
        @DisplayName("Should reject when terminal is missing")
        void shouldRejectMissingTerminal() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("CL-ERR008")
                    .transactionType(TransactionType.CARDLESS_WITHDRAWAL)
                    .sourceAccount("12345678901234")
                    .mobilePhone("0912345678")
                    .cardlessCode("12345678")
                    .amount(new BigDecimal("5000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Terminal ID is required"));
        }

        @Test
        @DisplayName("Should reject amount exceeding limit")
        void shouldRejectExceedingLimit() {
            // Arrange
            TransactionRequest request = createCardlessRequest(
                    "CL-ERR009",
                    "0912345678",
                    "12345678",
                    null,
                    new BigDecimal("40000") // Exceeds 30000 limit
            );

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
            TransactionRequest request = createCardlessRequest(
                    "CL-ERR010",
                    "0912345678",
                    "12345678",
                    null,
                    new BigDecimal("50") // Below 100 minimum
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Withdrawal amount must be at least 100"));
        }

        @Test
        @DisplayName("Should reject amount not multiple of 100")
        void shouldRejectNonMultipleOf100() {
            // Arrange
            TransactionRequest request = createCardlessRequest(
                    "CL-ERR011",
                    "0912345678",
                    "12345678",
                    null,
                    new BigDecimal("550") // Not a multiple of 100
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("multiple of 100"));
        }
    }

    @Nested
    @DisplayName("CardlessMethod Enum Tests")
    class CardlessMethodEnumTests {

        @Test
        @DisplayName("Should return correct method from code")
        void shouldReturnCorrectMethodFromCode() {
            assertEquals(CardlessWithdrawalProcessor.CardlessMethod.OTP,
                    CardlessWithdrawalProcessor.CardlessMethod.fromCode("01"));
            assertEquals(CardlessWithdrawalProcessor.CardlessMethod.MOBILE_APP,
                    CardlessWithdrawalProcessor.CardlessMethod.fromCode("02"));
            assertEquals(CardlessWithdrawalProcessor.CardlessMethod.QR_CODE,
                    CardlessWithdrawalProcessor.CardlessMethod.fromCode("03"));
        }

        @Test
        @DisplayName("Should return null for unknown code")
        void shouldReturnNullForUnknownCode() {
            assertNull(CardlessWithdrawalProcessor.CardlessMethod.fromCode("99"));
        }
    }

    // Helper method to create cardless withdrawal request
    private TransactionRequest createCardlessRequest(
            String txnId,
            String mobilePhone,
            String cardlessCode,
            String otpCode,
            BigDecimal amount) {

        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.CARDLESS_WITHDRAWAL)
                .processingCode("280000")
                .sourceAccount("12345678901234")
                .mobilePhone(mobilePhone)
                .cardlessCode(cardlessCode)
                .otpCode(otpCode)
                .amount(amount)
                .currencyCode("901")
                .terminalId("ATM00001")
                .acquiringBankCode("004")
                .stan("000001")
                .rrn("123456789012")
                .channel("ATM")
                .build();
    }
}
