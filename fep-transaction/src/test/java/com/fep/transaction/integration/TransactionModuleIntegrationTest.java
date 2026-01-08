package com.fep.transaction.integration;

import com.fep.transaction.batch.BatchProcessor;
import com.fep.transaction.batch.BatchRequest;
import com.fep.transaction.batch.BatchResult;
import com.fep.transaction.batch.BatchStatus;
import com.fep.transaction.batch.BatchType;
import com.fep.transaction.config.TransactionModule;
import com.fep.transaction.config.TransactionModuleConfig;
import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.query.ReversalEligibility;
import com.fep.transaction.query.TransactionQueryService;
import com.fep.transaction.query.TransactionStatistics;
import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.repository.TransactionStatus;
import com.fep.transaction.service.ReversalService;
import com.fep.transaction.service.TransactionService;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the complete transaction module.
 * Tests the end-to-end flow from request to response.
 */
@DisplayName("Transaction Module Integration Tests")
class TransactionModuleIntegrationTest {

    private TransactionModule module;
    private TransactionService transactionService;
    private BatchProcessor batchProcessor;
    private ReversalService reversalService;
    private TransactionQueryService queryService;
    private TransactionRepository repository;

    @BeforeEach
    void setUp() {
        // Create a fully configured module
        module = TransactionModuleConfig.moduleBuilder().build();
        transactionService = module.getTransactionService();
        batchProcessor = module.getBatchProcessor();
        reversalService = module.getReversalService();
        queryService = module.getQueryService();
        repository = module.getRepository();
    }

    @AfterEach
    void tearDown() {
        module.shutdown();
    }

    @Nested
    @DisplayName("Single Transaction Processing Tests")
    class SingleTransactionTests {

        @Test
        @DisplayName("Should process withdrawal successfully")
        void shouldProcessWithdrawalSuccessfully() {
            // Arrange
            TransactionRequest request = createWithdrawalRequest("TXN001", new BigDecimal("5000"));

            // Act
            TransactionResponse response = transactionService.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
            assertEquals("TXN001", response.getTransactionId());
        }

        @Test
        @DisplayName("Should process transfer successfully")
        void shouldProcessTransferSuccessfully() {
            // Arrange
            TransactionRequest request = createTransferRequest("TXN002", new BigDecimal("10000"));

            // Act
            TransactionResponse response = transactionService.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should process balance inquiry successfully")
        void shouldProcessBalanceInquirySuccessfully() {
            // Arrange
            TransactionRequest request = createBalanceInquiryRequest("TXN003");

            // Act
            TransactionResponse response = transactionService.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertNotNull(response.getAvailableBalance());
        }

        @Test
        @DisplayName("Should reject transaction exceeding withdrawal limit")
        void shouldRejectExceedingLimit() {
            // Arrange - Try to withdraw more than limit allows
            TransactionRequest request = createWithdrawalRequest("TXN004", new BigDecimal("999999999"));

            // Act
            TransactionResponse response = transactionService.process(request);

            // Assert
            assertFalse(response.isApproved());
            // The processor rejects with EXCEEDS_WITHDRAWAL_LIMIT (61) for amounts exceeding single limit
            assertEquals(ResponseCode.EXCEEDS_WITHDRAWAL_LIMIT.getCode(), response.getResponseCode());
        }
    }

    @Nested
    @DisplayName("Batch Processing Tests")
    class BatchProcessingTests {

        @Test
        @DisplayName("Should process batch of transfers")
        void shouldProcessBatch() {
            // Arrange
            List<TransactionRequest> transactions = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                transactions.add(createTransferRequest("BATCH-TXN" + String.format("%03d", i),
                        new BigDecimal("1000")));
            }

            BatchRequest batchRequest = BatchRequest.builder()
                    .batchId("BATCH001")
                    .batchType(BatchType.BULK_TRANSFER)
                    .transactions(transactions)
                    .maxParallelism(2)
                    .build();

            // Act
            BatchResult result = batchProcessor.process(batchRequest);

            // Assert - batch should complete (may have some validation errors due to pipeline)
            assertTrue(result.getStatus() == BatchStatus.COMPLETED ||
                       result.getStatus() == BatchStatus.COMPLETED_WITH_ERRORS);
            assertTrue(result.getTotalCount() > 0);
            assertNotNull(result.getProcessingTimeMs());
        }

        @Test
        @DisplayName("Should handle batch with mixed results")
        void shouldHandleMixedBatchResults() {
            // Arrange - Mix valid and invalid transactions
            List<TransactionRequest> transactions = new ArrayList<>();
            transactions.add(createTransferRequest("BATCH-TXN001", new BigDecimal("1000")));
            transactions.add(createWithdrawalRequest("BATCH-TXN002", new BigDecimal("999999999"))); // Too high
            transactions.add(createTransferRequest("BATCH-TXN003", new BigDecimal("2000")));

            BatchRequest batchRequest = BatchRequest.builder()
                    .batchId("BATCH002")
                    .batchType(BatchType.BULK_TRANSFER)
                    .transactions(transactions)
                    .maxParallelism(1)
                    .continueOnError(true)
                    .build();

            // Act
            BatchResult result = batchProcessor.process(batchRequest);

            // Assert - Batch should process all transactions
            assertEquals(3, result.getTotalCount());
            // At least one should fail (the one exceeding limit)
            assertTrue(result.getFailedCount() >= 1);
            // Status should reflect mixed results
            assertTrue(result.getStatus() == BatchStatus.COMPLETED_WITH_ERRORS ||
                       result.getStatus() == BatchStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("Reversal Processing Tests")
    class ReversalProcessingTests {

        @Test
        @DisplayName("Should reverse approved transaction successfully")
        void shouldReverseApprovedTransaction() {
            // Arrange - First create and save an original transaction
            TransactionRecord originalRecord = TransactionRecord.builder()
                    .transactionId("ORIG001")
                    .transactionType(TransactionType.WITHDRAWAL)
                    .amount(new BigDecimal("5000"))
                    .status(TransactionStatus.APPROVED)
                    .terminalId("ATM00001")
                    .rrn("123456789012")
                    .stan("000001")
                    .build();
            repository.save(originalRecord);

            // Create reversal request
            TransactionRequest reversalRequest = TransactionRequest.builder()
                    .transactionId("REV001")
                    .transactionType(TransactionType.REVERSAL)
                    .amount(new BigDecimal("5000"))
                    .terminalId("ATM00001")
                    .rrn("123456789012")
                    .stan("000002")
                    .originalTransactionId("ORIG001")
                    .build();

            // Act
            TransactionResponse response = reversalService.processReversal(reversalRequest);

            // Assert
            assertTrue(response.isApproved());

            // Verify original transaction is marked as reversed
            Optional<TransactionRecord> updatedOriginal = repository.findById("ORIG001");
            assertTrue(updatedOriginal.isPresent());
            assertEquals(TransactionStatus.REVERSED, updatedOriginal.get().getStatus());
        }

        @Test
        @DisplayName("Should reject reversal of non-existent transaction")
        void shouldRejectReversalOfNonExistent() {
            // Arrange
            TransactionRequest reversalRequest = TransactionRequest.builder()
                    .transactionId("REV002")
                    .transactionType(TransactionType.REVERSAL)
                    .amount(new BigDecimal("5000"))
                    .terminalId("ATM00001")
                    .rrn("123456789012")
                    .stan("000003")
                    .originalTransactionId("NON_EXISTENT")
                    .build();

            // Act
            TransactionResponse response = reversalService.processReversal(reversalRequest);

            // Assert
            assertFalse(response.isApproved());
            assertEquals(ResponseCode.INVALID_TRANSACTION.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should check reversal eligibility correctly")
        void shouldCheckReversalEligibility() {
            // Arrange - Save an approved transaction
            TransactionRecord approvedRecord = TransactionRecord.builder()
                    .transactionId("ELIG001")
                    .transactionType(TransactionType.TRANSFER)
                    .amount(new BigDecimal("10000"))
                    .status(TransactionStatus.APPROVED)
                    .build();
            repository.save(approvedRecord);

            // Save a declined transaction
            TransactionRecord declinedRecord = TransactionRecord.builder()
                    .transactionId("ELIG002")
                    .transactionType(TransactionType.TRANSFER)
                    .amount(new BigDecimal("10000"))
                    .status(TransactionStatus.DECLINED)
                    .build();
            repository.save(declinedRecord);

            // Act & Assert - Approved transaction should be eligible
            ReversalEligibility approvedEligibility = reversalService.checkEligibility("ELIG001");
            assertTrue(approvedEligibility.isEligible());

            // Declined transaction should NOT be eligible
            ReversalEligibility declinedEligibility = reversalService.checkEligibility("ELIG002");
            assertFalse(declinedEligibility.isEligible());
            assertEquals(ReversalEligibility.ReversalIneligibleReason.INVALID_STATUS,
                    declinedEligibility.getReasonCode());
        }
    }

    @Nested
    @DisplayName("Query Service Tests")
    class QueryServiceTests {

        @BeforeEach
        void setUpTestData() {
            // Save some test transactions
            repository.save(TransactionRecord.builder()
                    .transactionId("QUERY001")
                    .transactionType(TransactionType.WITHDRAWAL)
                    .amount(new BigDecimal("1000"))
                    .status(TransactionStatus.APPROVED)
                    .rrn("RRN001")
                    .stan("STAN001")
                    .terminalId("TERM001")
                    .build());

            repository.save(TransactionRecord.builder()
                    .transactionId("QUERY002")
                    .transactionType(TransactionType.TRANSFER)
                    .amount(new BigDecimal("5000"))
                    .status(TransactionStatus.APPROVED)
                    .rrn("RRN002")
                    .stan("STAN002")
                    .terminalId("TERM001")
                    .build());

            repository.save(TransactionRecord.builder()
                    .transactionId("QUERY003")
                    .transactionType(TransactionType.WITHDRAWAL)
                    .amount(new BigDecimal("2000"))
                    .status(TransactionStatus.DECLINED)
                    .rrn("RRN003")
                    .stan("STAN003")
                    .terminalId("TERM002")
                    .build());
        }

        @Test
        @DisplayName("Should find transaction by ID")
        void shouldFindByTransactionId() {
            // Act
            Optional<TransactionRecord> result = queryService.findById("QUERY001");

            // Assert
            assertTrue(result.isPresent());
            assertEquals("QUERY001", result.get().getTransactionId());
            assertEquals(TransactionType.WITHDRAWAL, result.get().getTransactionType());
        }

        @Test
        @DisplayName("Should find transaction by RRN")
        void shouldFindByRrn() {
            // Act
            Optional<TransactionRecord> result = queryService.findByRrn("RRN002");

            // Assert
            assertTrue(result.isPresent());
            assertEquals("QUERY002", result.get().getTransactionId());
        }

        @Test
        @DisplayName("Should calculate transaction statistics")
        void shouldCalculateStatistics() {
            // Act
            TransactionStatistics stats = queryService.getStatistics(
                    com.fep.transaction.query.TransactionQuery.builder().build());

            // Assert
            assertEquals(3, stats.getTotalCount());
            assertEquals(2, stats.getApprovedCount());
            assertEquals(1, stats.getDeclinedCount());
        }
    }

    @Nested
    @DisplayName("End-to-End Flow Tests")
    class EndToEndTests {

        @Test
        @DisplayName("Should complete full transaction lifecycle")
        void shouldCompleteFullTransactionLifecycle() {
            // Step 1: Process a withdrawal
            TransactionRequest withdrawalRequest = createWithdrawalRequest("E2E001", new BigDecimal("3000"));
            TransactionResponse withdrawalResponse = transactionService.process(withdrawalRequest);

            assertTrue(withdrawalResponse.isApproved());

            // Step 2: Query the transaction
            Optional<TransactionRecord> foundTxn = queryService.findById("E2E001");
            assertTrue(foundTxn.isPresent());

            // Step 3: Check reversal eligibility
            // First save the record for reversal check
            repository.save(TransactionRecord.builder()
                    .transactionId("E2E001")
                    .transactionType(TransactionType.WITHDRAWAL)
                    .amount(new BigDecimal("3000"))
                    .status(TransactionStatus.APPROVED)
                    .terminalId("ATM00001")
                    .rrn("123456789012")
                    .stan("000001")
                    .build());

            ReversalEligibility eligibility = reversalService.checkEligibility("E2E001");
            assertTrue(eligibility.isEligible());

            // Step 4: Perform reversal
            TransactionRequest reversalRequest = TransactionRequest.builder()
                    .transactionId("E2E001-REV")
                    .transactionType(TransactionType.REVERSAL)
                    .amount(new BigDecimal("3000"))
                    .terminalId("ATM00001")
                    .rrn("123456789012")
                    .stan("000002")
                    .originalTransactionId("E2E001")
                    .build();

            TransactionResponse reversalResponse = reversalService.processReversal(reversalRequest);
            assertTrue(reversalResponse.isApproved());

            // Step 5: Verify original is reversed
            Optional<TransactionRecord> reversedTxn = repository.findById("E2E001");
            assertTrue(reversedTxn.isPresent());
            assertEquals(TransactionStatus.REVERSED, reversedTxn.get().getStatus());

            // Step 6: Verify cannot reverse again
            eligibility = reversalService.checkEligibility("E2E001");
            assertFalse(eligibility.isEligible());
            assertEquals(ReversalEligibility.ReversalIneligibleReason.ALREADY_REVERSED,
                    eligibility.getReasonCode());
        }
    }

    // Helper methods

    private TransactionRequest createWithdrawalRequest(String txnId, BigDecimal amount) {
        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.WITHDRAWAL)
                .processingCode("010000")
                .pan("4111111111111111")
                .amount(amount)
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .terminalId("ATM00001")
                .acquiringBankCode("004")
                .stan("000001")
                .rrn("123456789012")
                .pinBlock("1234567890ABCDEF")
                .channel("ATM")
                .build();
    }

    private TransactionRequest createTransferRequest(String txnId, BigDecimal amount) {
        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.TRANSFER)
                .processingCode("400000")
                .pan("4111111111111111")
                .amount(amount)
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .destinationAccount("09876543210987")
                .destinationBankCode("012")
                .terminalId("ATM00001")
                .acquiringBankCode("004")
                .stan("000002")
                .rrn("123456789013")
                .pinBlock("1234567890ABCDEF")
                .channel("ATM")
                .build();
    }

    private TransactionRequest createBalanceInquiryRequest(String txnId) {
        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.BALANCE_INQUIRY)
                .processingCode("310000")
                .pan("4111111111111111")
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .terminalId("ATM00001")
                .acquiringBankCode("004")
                .stan("000003")
                .rrn("123456789014")
                .pinBlock("1234567890ABCDEF")
                .channel("ATM")
                .build();
    }
}
