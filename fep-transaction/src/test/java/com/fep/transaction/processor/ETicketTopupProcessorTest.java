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
 * Unit tests for ETicketTopupProcessor.
 */
@DisplayName("ETicketTopupProcessor Tests")
class ETicketTopupProcessorTest {

    private ETicketTopupProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ETicketTopupProcessor();
    }

    @Test
    @DisplayName("Should support E_TICKET_TOPUP transaction type")
    void shouldSupportETicketTopupType() {
        assertEquals(TransactionType.E_TICKET_TOPUP, processor.getSupportedType());
    }

    @Nested
    @DisplayName("Successful Top-up Tests")
    class SuccessfulTopupTests {

        @Test
        @DisplayName("Should process EasyCard top-up successfully")
        void shouldProcessEasyCardTopupSuccessfully() {
            // Arrange
            TransactionRequest request = createTopupRequest(
                    "TOPUP001",
                    "01",  // EasyCard
                    "1234567890123456",
                    new BigDecimal("1000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
            assertNotNull(response.getAuthorizationCode());
            assertEquals("1234567890123456", response.getETicketCardNumber());
            assertNotNull(response.getETicketBalance());
            assertEquals(new BigDecimal("1500"), response.getETicketBalance()); // 500 + 1000
        }

        @Test
        @DisplayName("Should process iPASS top-up successfully")
        void shouldProcessIPassTopupSuccessfully() {
            // Arrange
            TransactionRequest request = createTopupRequest(
                    "TOPUP002",
                    "02",  // iPASS
                    "9876543210123456",
                    new BigDecimal("500")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should process icash top-up successfully")
        void shouldProcessIcashTopupSuccessfully() {
            // Arrange
            TransactionRequest request = createTopupRequest(
                    "TOPUP003",
                    "03",  // icash
                    "1111222233334444",
                    new BigDecimal("2000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should process minimum top-up amount successfully")
        void shouldProcessMinimumTopupSuccessfully() {
            // Arrange
            TransactionRequest request = createTopupRequest(
                    "TOPUP004",
                    "01",
                    "1234567890123456",
                    new BigDecimal("100")  // Minimum amount
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
        }

        @Test
        @DisplayName("Should process maximum top-up amount successfully")
        void shouldProcessMaximumTopupSuccessfully() {
            // Arrange
            TransactionRequest request = createTopupRequest(
                    "TOPUP005",
                    "01",
                    "1234567890123456",
                    new BigDecimal("10000")  // Maximum amount
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
                    .transactionId("TOPUP-ERR001")
                    .transactionType(TransactionType.E_TICKET_TOPUP)
                    .eTicketType("01")
                    .eTicketCardNumber("1234567890123456")
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Source account is required"));
        }

        @Test
        @DisplayName("Should reject when e-ticket card number is missing")
        void shouldRejectMissingCardNumber() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TOPUP-ERR002")
                    .transactionType(TransactionType.E_TICKET_TOPUP)
                    .sourceAccount("12345678901234")
                    .eTicketType("01")
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("E-ticket card number is required"));
        }

        @Test
        @DisplayName("Should reject when e-ticket card number is too short")
        void shouldRejectShortCardNumber() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TOPUP-ERR003")
                    .transactionType(TransactionType.E_TICKET_TOPUP)
                    .sourceAccount("12345678901234")
                    .eTicketType("01")
                    .eTicketCardNumber("123456789012") // Too short (12 chars)
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid e-ticket card number length"));
        }

        @Test
        @DisplayName("Should reject when e-ticket card number is non-numeric")
        void shouldRejectNonNumericCardNumber() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TOPUP-ERR004")
                    .transactionType(TransactionType.E_TICKET_TOPUP)
                    .sourceAccount("12345678901234")
                    .eTicketType("01")
                    .eTicketCardNumber("12345678ABCD1234") // Contains letters
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("E-ticket card number must be numeric"));
        }

        @Test
        @DisplayName("Should reject when e-ticket type is missing")
        void shouldRejectMissingETicketType() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TOPUP-ERR005")
                    .transactionType(TransactionType.E_TICKET_TOPUP)
                    .sourceAccount("12345678901234")
                    .eTicketCardNumber("1234567890123456")
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("E-ticket type is required"));
        }

        @Test
        @DisplayName("Should reject invalid e-ticket type")
        void shouldRejectInvalidETicketType() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TOPUP-ERR006")
                    .transactionType(TransactionType.E_TICKET_TOPUP)
                    .sourceAccount("12345678901234")
                    .eTicketType("99")  // Invalid type
                    .eTicketCardNumber("1234567890123456")
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid e-ticket type"));
        }

        @Test
        @DisplayName("Should reject when amount is missing")
        void shouldRejectMissingAmount() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TOPUP-ERR007")
                    .transactionType(TransactionType.E_TICKET_TOPUP)
                    .sourceAccount("12345678901234")
                    .eTicketType("01")
                    .eTicketCardNumber("1234567890123456")
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Top-up amount is required"));
        }

        @Test
        @DisplayName("Should reject amount below minimum")
        void shouldRejectBelowMinimumAmount() {
            // Arrange
            TransactionRequest request = createTopupRequest(
                    "TOPUP-ERR008",
                    "01",
                    "1234567890123456",
                    new BigDecimal("50")  // Below minimum 100
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Top-up amount must be at least 100"));
        }

        @Test
        @DisplayName("Should reject amount exceeding limit")
        void shouldRejectExceedingLimit() {
            // Arrange
            TransactionRequest request = createTopupRequest(
                    "TOPUP-ERR009",
                    "01",
                    "1234567890123456",
                    new BigDecimal("15000")  // Above maximum 10000
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertEquals(ResponseCode.EXCEEDS_WITHDRAWAL_LIMIT.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should reject invalid top-up amount")
        void shouldRejectInvalidTopupAmount() {
            // Arrange - 750 is not a valid denomination
            TransactionRequest request = createTopupRequest(
                    "TOPUP-ERR010",
                    "01",
                    "1234567890123456",
                    new BigDecimal("750")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Top-up amount must be one of"));
        }
    }

    @Nested
    @DisplayName("ETicketType Enum Tests")
    class ETicketTypeEnumTests {

        @Test
        @DisplayName("Should return correct e-ticket type from code")
        void shouldReturnCorrectETicketTypeFromCode() {
            assertEquals(ETicketTopupProcessor.ETicketType.EASYCARD,
                    ETicketTopupProcessor.ETicketType.fromCode("01"));
            assertEquals(ETicketTopupProcessor.ETicketType.IPASS,
                    ETicketTopupProcessor.ETicketType.fromCode("02"));
            assertEquals(ETicketTopupProcessor.ETicketType.ICASH,
                    ETicketTopupProcessor.ETicketType.fromCode("03"));
            assertEquals(ETicketTopupProcessor.ETicketType.HAPPYCASH,
                    ETicketTopupProcessor.ETicketType.fromCode("04"));
        }

        @Test
        @DisplayName("Should return null for unknown code")
        void shouldReturnNullForUnknownCode() {
            assertNull(ETicketTopupProcessor.ETicketType.fromCode("99"));
            assertNull(ETicketTopupProcessor.ETicketType.fromCode("00"));
        }

        @Test
        @DisplayName("Should have correct descriptions")
        void shouldHaveCorrectDescriptions() {
            ETicketTopupProcessor.ETicketType easyCard = ETicketTopupProcessor.ETicketType.EASYCARD;
            assertEquals("01", easyCard.getCode());
            assertEquals("EasyCard", easyCard.getDescription());
            assertEquals("悠遊卡", easyCard.getChineseDescription());
        }
    }

    @Nested
    @DisplayName("Valid Amount Tests")
    class ValidAmountTests {

        @Test
        @DisplayName("Should accept all valid denominations")
        void shouldAcceptAllValidDenominations() {
            BigDecimal[] validAmounts = {
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("300"),
                new BigDecimal("500"),
                new BigDecimal("1000"),
                new BigDecimal("2000"),
                new BigDecimal("3000"),
                new BigDecimal("5000"),
                new BigDecimal("10000")
            };

            for (BigDecimal amount : validAmounts) {
                TransactionRequest request = createTopupRequest(
                        "VALID-" + amount.intValue(),
                        "01",
                        "1234567890123456",
                        amount
                );

                TransactionResponse response = processor.process(request);
                assertTrue(response.isApproved(),
                        "Amount " + amount + " should be accepted");
            }
        }
    }

    // Helper method to create top-up request
    private TransactionRequest createTopupRequest(
            String txnId,
            String eTicketType,
            String eTicketCardNumber,
            BigDecimal amount) {

        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.E_TICKET_TOPUP)
                .processingCode("510000")
                .sourceAccount("12345678901234")
                .eTicketType(eTicketType)
                .eTicketCardNumber(eTicketCardNumber)
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
