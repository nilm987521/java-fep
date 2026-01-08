package com.fep.transaction.service;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.query.ReversalEligibility;
import com.fep.transaction.query.TransactionQueryService;
import com.fep.transaction.repository.InMemoryTransactionRepository;
import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReversalService.
 */
class ReversalServiceTest {

    private InMemoryTransactionRepository repository;
    private TransactionQueryService queryService;
    private ReversalService reversalService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        queryService = new TransactionQueryService(repository);
        reversalService = new ReversalService(queryService, repository);
    }

    @Nested
    @DisplayName("Successful reversal tests")
    class SuccessfulReversalTests {

        @Test
        @DisplayName("Should reverse an approved transaction")
        void shouldReverseApprovedTransaction() {
            // Arrange - Create and save original transaction
            TransactionRecord original = createOriginalTransaction("ORIG001", TransactionStatus.APPROVED);
            repository.save(original);

            TransactionRequest reversalRequest = createReversalRequest("REV001", "ORIG001");

            // Act
            TransactionResponse response = reversalService.processReversal(reversalRequest);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
            assertNotNull(response.getAuthorizationCode());

            // Verify original is marked as reversed
            TransactionRecord updatedOriginal = repository.findById("ORIG001").orElse(null);
            assertNotNull(updatedOriginal);
            assertEquals(TransactionStatus.REVERSED, updatedOriginal.getStatus());
        }

        @Test
        @DisplayName("Should reverse a pending transaction")
        void shouldReversePendingTransaction() {
            // Arrange
            TransactionRecord original = createOriginalTransaction("ORIG002", TransactionStatus.PENDING);
            repository.save(original);

            TransactionRequest reversalRequest = createReversalRequest("REV002", "ORIG002");

            // Act
            TransactionResponse response = reversalService.processReversal(reversalRequest);

            // Assert
            assertTrue(response.isApproved());
        }

        @Test
        @DisplayName("Should create reversal record in repository")
        void shouldCreateReversalRecord() {
            // Arrange
            TransactionRecord original = createOriginalTransaction("ORIG003", TransactionStatus.APPROVED);
            repository.save(original);

            TransactionRequest reversalRequest = createReversalRequest("REV003", "ORIG003");

            // Act
            reversalService.processReversal(reversalRequest);

            // Assert
            TransactionRecord reversalRecord = repository.findById("REV003").orElse(null);
            assertNotNull(reversalRecord);
            assertEquals(TransactionType.REVERSAL, reversalRecord.getTransactionType());
            assertEquals("ORIG003", reversalRecord.getOriginalTransactionId());
        }
    }

    @Nested
    @DisplayName("Declined reversal tests")
    class DeclinedReversalTests {

        @Test
        @DisplayName("Should decline reversal for non-existent transaction")
        void shouldDeclineForNonExistent() {
            // Arrange
            TransactionRequest reversalRequest = createReversalRequest("REV001", "NON_EXISTENT");

            // Act
            TransactionResponse response = reversalService.processReversal(reversalRequest);

            // Assert
            assertFalse(response.isApproved());
            assertEquals(ResponseCode.INVALID_TRANSACTION.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should decline reversal for already reversed transaction")
        void shouldDeclineForAlreadyReversed() {
            // Arrange
            TransactionRecord original = createOriginalTransaction("ORIG001", TransactionStatus.REVERSED);
            repository.save(original);

            TransactionRequest reversalRequest = createReversalRequest("REV001", "ORIG001");

            // Act
            TransactionResponse response = reversalService.processReversal(reversalRequest);

            // Assert
            assertFalse(response.isApproved());
            assertEquals(ResponseCode.DUPLICATE_TRANSACTION.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should decline reversal for declined transaction")
        void shouldDeclineForDeclinedTransaction() {
            // Arrange
            TransactionRecord original = createOriginalTransaction("ORIG001", TransactionStatus.DECLINED);
            repository.save(original);

            TransactionRequest reversalRequest = createReversalRequest("REV001", "ORIG001");

            // Act
            TransactionResponse response = reversalService.processReversal(reversalRequest);

            // Assert
            assertFalse(response.isApproved());
            assertEquals(ResponseCode.TRANSACTION_NOT_PERMITTED.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should decline reversal when amounts don't match")
        void shouldDeclineForMismatchedAmounts() {
            // Arrange
            TransactionRecord original = createOriginalTransaction("ORIG001", TransactionStatus.APPROVED);
            original.setAmount(new BigDecimal("10000")); // Original is 10000
            repository.save(original);

            TransactionRequest reversalRequest = createReversalRequest("REV001", "ORIG001");
            reversalRequest.setAmount(new BigDecimal("5000")); // Trying to reverse 5000

            // Act
            TransactionResponse response = reversalService.processReversal(reversalRequest);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getResponseDescription().contains("does not match"));
        }
    }

    @Nested
    @DisplayName("Validation tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject reversal without original transaction ID")
        void shouldRejectWithoutOriginalId() {
            // Arrange
            TransactionRequest request = createReversalRequest("REV001", "ORIG001");
            request.setOriginalTransactionId(null);

            // Act
            TransactionResponse response = reversalService.processReversal(request);

            // Assert
            assertFalse(response.isApproved());
        }

        @Test
        @DisplayName("Should reject reversal without RRN")
        void shouldRejectWithoutRrn() {
            // Arrange
            TransactionRequest request = createReversalRequest("REV001", "ORIG001");
            request.setRrn(null);

            // Act
            TransactionResponse response = reversalService.processReversal(request);

            // Assert
            assertFalse(response.isApproved());
        }

        @Test
        @DisplayName("Should reject reversal without STAN")
        void shouldRejectWithoutStan() {
            // Arrange
            TransactionRequest request = createReversalRequest("REV001", "ORIG001");
            request.setStan(null);

            // Act
            TransactionResponse response = reversalService.processReversal(request);

            // Assert
            assertFalse(response.isApproved());
        }

        @Test
        @DisplayName("Should reject reversal without terminal ID")
        void shouldRejectWithoutTerminalId() {
            // Arrange
            TransactionRequest request = createReversalRequest("REV001", "ORIG001");
            request.setTerminalId(null);

            // Act
            TransactionResponse response = reversalService.processReversal(request);

            // Assert
            assertFalse(response.isApproved());
        }

        @Test
        @DisplayName("Should reject reversal without amount")
        void shouldRejectWithoutAmount() {
            // Arrange
            TransactionRequest request = createReversalRequest("REV001", "ORIG001");
            request.setAmount(null);

            // Act
            TransactionResponse response = reversalService.processReversal(request);

            // Assert
            assertFalse(response.isApproved());
        }
    }

    @Nested
    @DisplayName("Eligibility check tests")
    class EligibilityCheckTests {

        @Test
        @DisplayName("Should return eligible for approved transaction")
        void shouldReturnEligibleForApproved() {
            // Arrange
            TransactionRecord original = createOriginalTransaction("ORIG001", TransactionStatus.APPROVED);
            repository.save(original);

            // Act
            ReversalEligibility eligibility = reversalService.checkEligibility("ORIG001");

            // Assert
            assertTrue(eligibility.isEligible());
            assertNotNull(eligibility.getOriginalTransaction());
        }

        @Test
        @DisplayName("Should return not eligible for reversed transaction")
        void shouldReturnNotEligibleForReversed() {
            // Arrange
            TransactionRecord original = createOriginalTransaction("ORIG001", TransactionStatus.REVERSED);
            repository.save(original);

            // Act
            ReversalEligibility eligibility = reversalService.checkEligibility("ORIG001");

            // Assert
            assertFalse(eligibility.isEligible());
            assertEquals(ReversalEligibility.ReversalIneligibleReason.ALREADY_REVERSED,
                    eligibility.getReasonCode());
        }

        @Test
        @DisplayName("Should return not found for non-existent transaction")
        void shouldReturnNotFoundForNonExistent() {
            // Act
            ReversalEligibility eligibility = reversalService.checkEligibility("NON_EXISTENT");

            // Assert
            assertFalse(eligibility.isEligible());
            assertEquals(ReversalEligibility.ReversalIneligibleReason.NOT_FOUND,
                    eligibility.getReasonCode());
        }
    }

    // Helper methods

    private TransactionRecord createOriginalTransaction(String txnId, TransactionStatus status) {
        return TransactionRecord.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.WITHDRAWAL)
                .processingCode("010000")
                .maskedPan("411111******1111")
                .pan("4111111111111111")
                .amount(new BigDecimal("5000"))
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .terminalId("ATM00001")
                .acquiringBankCode("004")
                .stan("000001")
                .rrn("123456789012")
                .channel("ATM")
                .status(status)
                .requestTime(LocalDateTime.now().minusMinutes(5))
                .transactionTime(LocalDateTime.now().minusMinutes(5))
                .transactionDate(LocalDateTime.now().toLocalDate().toString())
                .build();
    }

    private TransactionRequest createReversalRequest(String txnId, String originalTxnId) {
        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.REVERSAL)
                .processingCode("201000")
                .pan("4111111111111111")
                .amount(new BigDecimal("5000"))
                .currencyCode("901")
                .terminalId("ATM00001")
                .acquiringBankCode("004")
                .stan("000002")
                .rrn("123456789012")
                .originalTransactionId(originalTxnId)
                .channel("ATM")
                .build();
    }
}
