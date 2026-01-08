package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BillPaymentProcessor.
 */
@DisplayName("BillPaymentProcessor Tests")
class BillPaymentProcessorTest {

    private BillPaymentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new BillPaymentProcessor();
    }

    @Test
    @DisplayName("Should support BILL_PAYMENT transaction type")
    void shouldSupportBillPaymentType() {
        assertEquals(TransactionType.BILL_PAYMENT, processor.getSupportedType());
    }

    @Nested
    @DisplayName("Successful Bill Payment Tests")
    class SuccessfulPaymentTests {

        @Test
        @DisplayName("Should process water bill payment successfully")
        void shouldProcessWaterBillSuccessfully() {
            // Arrange
            TransactionRequest request = createBillPaymentRequest(
                    "BILL001",
                    "01",  // Water
                    "12345678901234",
                    new BigDecimal("1500")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
            assertNotNull(response.getAuthorizationCode());
            assertEquals("12345678901234", response.getBillPaymentNumber());
            assertEquals("01", response.getBillTypeCode());
        }

        @Test
        @DisplayName("Should process electricity bill payment successfully")
        void shouldProcessElectricityBillSuccessfully() {
            // Arrange
            TransactionRequest request = createBillPaymentRequest(
                    "BILL002",
                    "02",  // Electricity
                    "98765432109876",
                    new BigDecimal("3200")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should process credit card bill payment successfully")
        void shouldProcessCreditCardBillSuccessfully() {
            // Arrange
            TransactionRequest request = createBillPaymentRequest(
                    "BILL003",
                    "05",  // Credit Card
                    "11112222333344445555",
                    new BigDecimal("50000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should process tax payment successfully")
        void shouldProcessTaxPaymentSuccessfully() {
            // Arrange - Vehicle License Tax
            TransactionRequest request = createBillPaymentRequest(
                    "TAX001",
                    "11",  // Vehicle Tax
                    "12345678901234",
                    new BigDecimal("7200")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should process tuition fee payment successfully")
        void shouldProcessTuitionPaymentSuccessfully() {
            // Arrange
            TransactionRequest request = createBillPaymentRequest(
                    "TUITION001",
                    "21",  // Tuition
                    "20241234567890",
                    new BigDecimal("25000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should process insurance premium payment successfully")
        void shouldProcessInsurancePaymentSuccessfully() {
            // Arrange
            TransactionRequest request = createBillPaymentRequest(
                    "INS001",
                    "07",  // Insurance
                    "98765432109876789012",
                    new BigDecimal("15000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
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
                    .transactionId("BILL-ERR001")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .billTypeCode("01")
                    .billPaymentNumber("12345678901234")
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Source account is required"));
        }

        @Test
        @DisplayName("Should reject when bill payment number is missing")
        void shouldRejectMissingPaymentNumber() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("BILL-ERR002")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billTypeCode("01")
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Bill payment number is required"));
        }

        @Test
        @DisplayName("Should reject when bill payment number is too short")
        void shouldRejectShortPaymentNumber() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("BILL-ERR003")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billTypeCode("01")
                    .billPaymentNumber("1234567890") // Too short (10 chars)
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid bill payment number length"));
        }

        @Test
        @DisplayName("Should reject when bill payment number is too long")
        void shouldRejectLongPaymentNumber() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("BILL-ERR004")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billTypeCode("01")
                    .billPaymentNumber("123456789012345678901") // Too long (21 chars)
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid bill payment number length"));
        }

        @Test
        @DisplayName("Should reject when bill payment number is non-numeric")
        void shouldRejectNonNumericPaymentNumber() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("BILL-ERR005")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billTypeCode("01")
                    .billPaymentNumber("1234567890ABCD") // Contains letters
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Bill payment number must be numeric"));
        }

        @Test
        @DisplayName("Should reject when bill type code is missing")
        void shouldRejectMissingBillTypeCode() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("BILL-ERR006")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billPaymentNumber("12345678901234")
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Bill type code is required"));
        }

        @Test
        @DisplayName("Should reject invalid bill type code")
        void shouldRejectInvalidBillTypeCode() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("BILL-ERR007")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billTypeCode("XX")  // Invalid code
                    .billPaymentNumber("12345678901234")
                    .amount(new BigDecimal("1000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid bill type code"));
        }

        @Test
        @DisplayName("Should reject when amount is missing")
        void shouldRejectMissingAmount() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("BILL-ERR008")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billTypeCode("01")
                    .billPaymentNumber("12345678901234")
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Payment amount is required"));
        }

        @Test
        @DisplayName("Should reject zero amount")
        void shouldRejectZeroAmount() {
            // Arrange
            TransactionRequest request = createBillPaymentRequest(
                    "BILL-ERR009",
                    "01",
                    "12345678901234",
                    BigDecimal.ZERO
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Payment amount must be at least"));
        }

        @Test
        @DisplayName("Should reject negative amount")
        void shouldRejectNegativeAmount() {
            // Arrange
            TransactionRequest request = createBillPaymentRequest(
                    "BILL-ERR010",
                    "01",
                    "12345678901234",
                    new BigDecimal("-100")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Payment amount must be at least"));
        }

        @Test
        @DisplayName("Should reject amount exceeding limit")
        void shouldRejectExceedingLimit() {
            // Arrange - Max is 10 million
            TransactionRequest request = createBillPaymentRequest(
                    "BILL-ERR011",
                    "01",
                    "12345678901234",
                    new BigDecimal("15000000")  // 15 million exceeds limit
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertEquals(ResponseCode.EXCEEDS_WITHDRAWAL_LIMIT.getCode(), response.getResponseCode());
        }
    }

    @Nested
    @DisplayName("Tax Payment Validation Tests")
    class TaxPaymentValidationTests {

        @Test
        @DisplayName("Should reject invalid individual tax ID (non-numeric)")
        void shouldRejectInvalidIndividualTaxId() {
            // Arrange - Tax ID with invalid format (wrong length/invalid characters)
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TAX-VAL001")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billTypeCode("11")  // Vehicle Tax
                    .billPaymentNumber("12345678901234")
                    .amount(new BigDecimal("7200"))
                    .taxId("INVALID")  // Invalid format - too short, wrong format
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert - Should fail because tax ID format is invalid
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid tax ID format"));
        }

        @Test
        @DisplayName("Should accept valid 10-digit individual tax ID")
        void shouldAcceptValidIndividualTaxId() {
            // Arrange - 10 digits for individual
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TAX-VAL001B")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billTypeCode("11")  // Vehicle Tax
                    .billPaymentNumber("12345678901234")
                    .amount(new BigDecimal("7200"))
                    .taxId("1234567890")  // Valid 10-digit tax ID
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
        }

        @Test
        @DisplayName("Should accept valid business tax ID")
        void shouldAcceptValidBusinessTaxId() {
            // Arrange - 8 digits for business
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TAX-VAL002")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billTypeCode("15")  // Business Tax
                    .billPaymentNumber("12345678901234")
                    .amount(new BigDecimal("50000"))
                    .taxId("12345678")  // 8-digit business tax ID
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
        }

        @Test
        @DisplayName("Should reject invalid tax ID format (wrong length)")
        void shouldRejectInvalidTaxIdFormat() {
            // Arrange - Invalid length (9 digits)
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TAX-VAL003")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billTypeCode("12")  // House Tax
                    .billPaymentNumber("12345678901234")
                    .amount(new BigDecimal("15000"))
                    .taxId("123456789")  // 9 digits - invalid
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid tax ID format"));
        }

        @Test
        @DisplayName("Should allow tax payment without tax ID")
        void shouldAllowTaxPaymentWithoutTaxId() {
            // Arrange - Tax ID is optional
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TAX-VAL004")
                    .transactionType(TransactionType.BILL_PAYMENT)
                    .sourceAccount("12345678901234")
                    .billTypeCode("13")  // Land Tax
                    .billPaymentNumber("12345678901234")
                    .amount(new BigDecimal("25000"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
        }
    }

    @Nested
    @DisplayName("BillType Enum Tests")
    class BillTypeEnumTests {

        @Test
        @DisplayName("Should return correct bill type from code")
        void shouldReturnCorrectBillTypeFromCode() {
            assertEquals(BillPaymentProcessor.BillType.WATER,
                    BillPaymentProcessor.BillType.fromCode("01"));
            assertEquals(BillPaymentProcessor.BillType.ELECTRICITY,
                    BillPaymentProcessor.BillType.fromCode("02"));
            assertEquals(BillPaymentProcessor.BillType.CREDIT_CARD,
                    BillPaymentProcessor.BillType.fromCode("05"));
            assertEquals(BillPaymentProcessor.BillType.VEHICLE_TAX,
                    BillPaymentProcessor.BillType.fromCode("11"));
        }

        @Test
        @DisplayName("Should return null for unknown code")
        void shouldReturnNullForUnknownCode() {
            assertNull(BillPaymentProcessor.BillType.fromCode("XX"));
            assertNull(BillPaymentProcessor.BillType.fromCode("00"));
        }

        @Test
        @DisplayName("Should have correct descriptions")
        void shouldHaveCorrectDescriptions() {
            BillPaymentProcessor.BillType water = BillPaymentProcessor.BillType.WATER;
            assertEquals("01", water.getCode());
            assertEquals("Water Bill", water.getDescription());
            assertEquals("水費", water.getChineseDescription());
        }
    }

    // Helper method to create bill payment request
    private TransactionRequest createBillPaymentRequest(
            String txnId,
            String billTypeCode,
            String billPaymentNumber,
            BigDecimal amount) {

        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.BILL_PAYMENT)
                .processingCode("500000")
                .sourceAccount("12345678901234")
                .billTypeCode(billTypeCode)
                .billPaymentNumber(billPaymentNumber)
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
