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
 * Unit tests for CrossBorderPaymentProcessor.
 */
@DisplayName("CrossBorderPaymentProcessor Tests")
class CrossBorderPaymentProcessorTest {

    private CrossBorderPaymentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CrossBorderPaymentProcessor();
    }

    @Test
    @DisplayName("Should support CROSS_BORDER_PAYMENT transaction type")
    void shouldSupportCrossBorderPaymentType() {
        assertEquals(TransactionType.CROSS_BORDER_PAYMENT, processor.getSupportedType());
    }

    @Nested
    @DisplayName("Successful Payment Tests")
    class SuccessfulPaymentTests {

        @Test
        @DisplayName("Should process cross-border payment to US successfully")
        void shouldProcessPaymentToUsSuccessfully() {
            // Arrange
            TransactionRequest request = createCrossBorderRequest(
                    "CBP001",
                    "US",
                    "BOFAUS3N",
                    "John Smith",
                    new BigDecimal("100000"),
                    "01"
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
            assertNotNull(response.getAuthorizationCode());
            assertNotNull(response.getCrossBorderReference());
            assertNotNull(response.getSwiftReference());
            assertNotNull(response.getCrossBorderFee());
            assertNotNull(response.getEstimatedArrival());
        }

        @Test
        @DisplayName("Should process cross-border payment to Japan successfully")
        void shouldProcessPaymentToJapanSuccessfully() {
            // Arrange
            TransactionRequest request = createCrossBorderRequest(
                    "CBP002",
                    "JP",
                    "MABORJPJ",
                    "Tanaka Taro",
                    new BigDecimal("50000"),
                    "02"
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should calculate correct fee")
        void shouldCalculateCorrectFee() {
            // Arrange - Amount 100,000 TWD, fee rate 0.5% = 500 TWD
            TransactionRequest request = createCrossBorderRequest(
                    "CBP003",
                    "US",
                    "BOFAUS3N",
                    "John Smith",
                    new BigDecimal("100000"),
                    "01"
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertNotNull(response.getCrossBorderFee());
            // Fee = 100,000 * 0.5% = 500 TWD
            assertTrue(response.getCrossBorderFee().compareTo(new BigDecimal("300")) >= 0);
        }

        @Test
        @DisplayName("Should apply minimum fee for small amounts")
        void shouldApplyMinimumFee() {
            // Arrange - Amount 10,000 TWD, calculated fee = 50 TWD, but minimum is 300
            TransactionRequest request = createCrossBorderRequest(
                    "CBP004",
                    "HK",
                    "HSBCHKHH", // Fixed: SWIFT code without space
                    "Wong Ming",
                    new BigDecimal("10000"),
                    "01"
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(new BigDecimal("300"), response.getCrossBorderFee());
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
                    .transactionId("CBP-ERR001")
                    .transactionType(TransactionType.CROSS_BORDER_PAYMENT)
                    .destinationCountryCode("US")
                    .destinationAccount("123456789")
                    .beneficiaryBankSwift("BOFAUS3N")
                    .beneficiaryName("John Smith")
                    .amount(new BigDecimal("50000"))
                    .remittancePurposeCode("01")
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Source account is required"));
        }

        @Test
        @DisplayName("Should reject unsupported country")
        void shouldRejectUnsupportedCountry() {
            // Arrange
            TransactionRequest request = createCrossBorderRequest(
                    "CBP-ERR002",
                    "ZZ", // Invalid country
                    "TESTZZXX",
                    "Test User",
                    new BigDecimal("50000"),
                    "01"
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Unsupported destination country"));
        }

        @Test
        @DisplayName("Should reject missing country code")
        void shouldRejectMissingCountryCode() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("CBP-ERR003")
                    .transactionType(TransactionType.CROSS_BORDER_PAYMENT)
                    .sourceAccount("12345678901234")
                    .destinationAccount("123456789")
                    .beneficiaryBankSwift("BOFAUS3N")
                    .beneficiaryName("John Smith")
                    .amount(new BigDecimal("50000"))
                    .remittancePurposeCode("01")
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Destination country code is required"));
        }

        @Test
        @DisplayName("Should reject invalid SWIFT code format")
        void shouldRejectInvalidSwiftCode() {
            // Arrange
            TransactionRequest request = createCrossBorderRequest(
                    "CBP-ERR004",
                    "US",
                    "INVALID", // Invalid SWIFT (should be 8 or 11 chars with specific format)
                    "John Smith",
                    new BigDecimal("50000"),
                    "01"
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid SWIFT code"));
        }

        @Test
        @DisplayName("Should reject missing beneficiary name")
        void shouldRejectMissingBeneficiaryName() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("CBP-ERR005")
                    .transactionType(TransactionType.CROSS_BORDER_PAYMENT)
                    .sourceAccount("12345678901234")
                    .destinationCountryCode("US")
                    .destinationAccount("123456789")
                    .beneficiaryBankSwift("BOFAUS3N")
                    .amount(new BigDecimal("50000"))
                    .remittancePurposeCode("01")
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Beneficiary name is required"));
        }

        @Test
        @DisplayName("Should reject beneficiary name with invalid characters")
        void shouldRejectInvalidBeneficiaryName() {
            // Arrange
            TransactionRequest request = createCrossBorderRequest(
                    "CBP-ERR006",
                    "US",
                    "BOFAUS3N",
                    "John@Smith#123", // Invalid characters
                    new BigDecimal("50000"),
                    "01"
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("invalid characters"));
        }

        @Test
        @DisplayName("Should reject amount exceeding limit")
        void shouldRejectExceedingLimit() {
            // Arrange
            TransactionRequest request = createCrossBorderRequest(
                    "CBP-ERR007",
                    "US",
                    "BOFAUS3N",
                    "John Smith",
                    new BigDecimal("600000"), // Exceeds 500,000 limit
                    "01"
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
            TransactionRequest request = createCrossBorderRequest(
                    "CBP-ERR008",
                    "US",
                    "BOFAUS3N",
                    "John Smith",
                    new BigDecimal("50"), // Below 100 minimum
                    "01"
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Payment amount must be at least"));
        }

        @Test
        @DisplayName("Should reject invalid remittance purpose code")
        void shouldRejectInvalidPurposeCode() {
            // Arrange
            TransactionRequest request = createCrossBorderRequest(
                    "CBP-ERR009",
                    "US",
                    "BOFAUS3N",
                    "John Smith",
                    new BigDecimal("50000"),
                    "XX" // Invalid purpose code
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid remittance purpose code"));
        }
    }

    @Nested
    @DisplayName("RemittancePurpose Enum Tests")
    class RemittancePurposeEnumTests {

        @Test
        @DisplayName("Should return correct purpose from code")
        void shouldReturnCorrectPurposeFromCode() {
            assertEquals(CrossBorderPaymentProcessor.RemittancePurpose.FAMILY_SUPPORT,
                    CrossBorderPaymentProcessor.RemittancePurpose.fromCode("01"));
            assertEquals(CrossBorderPaymentProcessor.RemittancePurpose.EDUCATION,
                    CrossBorderPaymentProcessor.RemittancePurpose.fromCode("02"));
            assertEquals(CrossBorderPaymentProcessor.RemittancePurpose.TRADE_PAYMENT,
                    CrossBorderPaymentProcessor.RemittancePurpose.fromCode("04"));
        }

        @Test
        @DisplayName("Should return null for unknown code")
        void shouldReturnNullForUnknownCode() {
            assertNull(CrossBorderPaymentProcessor.RemittancePurpose.fromCode("XX"));
        }
    }

    // Helper method to create cross-border payment request
    private TransactionRequest createCrossBorderRequest(
            String txnId,
            String countryCode,
            String swiftCode,
            String beneficiaryName,
            BigDecimal amount,
            String purposeCode) {

        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.CROSS_BORDER_PAYMENT)
                .processingCode("520000")
                .sourceAccount("12345678901234")
                .destinationCountryCode(countryCode)
                .destinationAccount("123456789012")
                .beneficiaryBankSwift(swiftCode)
                .beneficiaryName(beneficiaryName)
                .amount(amount)
                .currencyCode("901")
                .remittancePurposeCode(purposeCode)
                .terminalId("BRANCH01")
                .acquiringBankCode("004")
                .stan("000001")
                .rrn("123456789012")
                .channel("INTERNET")
                .build();
    }
}
