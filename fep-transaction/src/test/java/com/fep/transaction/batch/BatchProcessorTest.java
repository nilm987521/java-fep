package com.fep.transaction.batch;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.service.TransactionService;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BatchProcessor.
 */
class BatchProcessorTest {

    private TransactionService mockService;
    private BatchProcessor processor;

    @BeforeEach
    void setUp() {
        mockService = mock(TransactionService.class);
        processor = new BatchProcessor(mockService, 4);
    }

    @AfterEach
    void tearDown() {
        processor.shutdown();
    }

    @Nested
    @DisplayName("Basic batch processing tests")
    class BasicProcessingTests {

        @Test
        @DisplayName("Should process empty batch with error")
        void shouldFailOnEmptyBatch() {
            BatchRequest request = BatchRequest.builder()
                    .batchId("BATCH001")
                    .batchType(BatchType.BULK_TRANSFER)
                    .transactions(new ArrayList<>())
                    .build();

            BatchResult result = processor.process(request);

            assertEquals(BatchStatus.FAILED, result.getStatus());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("Should process single transaction batch")
        void shouldProcessSingleTransaction() {
            // Arrange
            when(mockService.process(any())).thenReturn(createSuccessResponse("TXN001"));

            BatchRequest request = createBatchRequest("BATCH001", 1);

            // Act
            BatchResult result = processor.process(request);

            // Assert
            assertEquals(BatchStatus.COMPLETED, result.getStatus());
            assertEquals(1, result.getSuccessCount());
            assertEquals(0, result.getFailedCount());
            assertEquals(100.0, result.getSuccessRate());
        }

        @Test
        @DisplayName("Should process multiple transactions sequentially")
        void shouldProcessMultipleTransactionsSequentially() {
            // Arrange
            when(mockService.process(any())).thenReturn(createSuccessResponse("TXN"));

            BatchRequest request = createBatchRequest("BATCH001", 5);
            request.setMaxParallelism(1);

            // Act
            BatchResult result = processor.process(request);

            // Assert
            assertEquals(BatchStatus.COMPLETED, result.getStatus());
            assertEquals(5, result.getSuccessCount());
            assertEquals(0, result.getFailedCount());
            verify(mockService, times(5)).process(any());
        }

        @Test
        @DisplayName("Should process multiple transactions in parallel")
        void shouldProcessMultipleTransactionsInParallel() {
            // Arrange
            when(mockService.process(any())).thenReturn(createSuccessResponse("TXN"));

            BatchRequest request = createBatchRequest("BATCH001", 10);
            request.setMaxParallelism(4);

            // Act
            BatchResult result = processor.process(request);

            // Assert
            assertEquals(BatchStatus.COMPLETED, result.getStatus());
            assertEquals(10, result.getSuccessCount());
            verify(mockService, times(10)).process(any());
        }
    }

    @Nested
    @DisplayName("Error handling tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle mixed success and failure")
        void shouldHandleMixedResults() {
            // Arrange
            when(mockService.process(any()))
                    .thenReturn(createSuccessResponse("TXN"))
                    .thenReturn(createDeclinedResponse("TXN"))
                    .thenReturn(createSuccessResponse("TXN"))
                    .thenReturn(createDeclinedResponse("TXN"))
                    .thenReturn(createSuccessResponse("TXN"));

            BatchRequest request = createBatchRequest("BATCH001", 5);
            request.setMaxParallelism(1); // Sequential to control order

            // Act
            BatchResult result = processor.process(request);

            // Assert
            assertEquals(BatchStatus.COMPLETED_WITH_ERRORS, result.getStatus());
            assertEquals(3, result.getSuccessCount());
            assertEquals(2, result.getFailedCount());
            assertEquals(2, result.getErrors().size());
        }

        @Test
        @DisplayName("Should continue on error when configured")
        void shouldContinueOnError() {
            // Arrange
            when(mockService.process(any()))
                    .thenThrow(new RuntimeException("Processing error"))
                    .thenReturn(createSuccessResponse("TXN"));

            BatchRequest request = createBatchRequest("BATCH001", 2);
            request.setMaxParallelism(1);
            request.setContinueOnError(true);

            // Act
            BatchResult result = processor.process(request);

            // Assert
            assertEquals(1, result.getSuccessCount());
            assertEquals(1, result.getFailedCount());
            verify(mockService, times(2)).process(any());
        }

        @Test
        @DisplayName("Should fail all transactions on batch failure")
        void shouldFailAllOnBatchFailure() {
            // Arrange
            when(mockService.process(any())).thenReturn(createDeclinedResponse("TXN"));

            BatchRequest request = createBatchRequest("BATCH001", 3);
            request.setMaxParallelism(1);

            // Act
            BatchResult result = processor.process(request);

            // Assert
            assertEquals(BatchStatus.FAILED, result.getStatus());
            assertEquals(0, result.getSuccessCount());
            assertEquals(3, result.getFailedCount());
        }
    }

    @Nested
    @DisplayName("Listener tests")
    class ListenerTests {

        @Test
        @DisplayName("Should notify listeners on batch start")
        void shouldNotifyOnBatchStart() {
            // Arrange
            when(mockService.process(any())).thenReturn(createSuccessResponse("TXN"));
            AtomicInteger startCalled = new AtomicInteger(0);

            processor.addListener(new BatchListener() {
                @Override
                public void onBatchStarted(BatchRequest request) {
                    startCalled.incrementAndGet();
                }
            });

            BatchRequest request = createBatchRequest("BATCH001", 1);

            // Act
            processor.process(request);

            // Assert
            assertEquals(1, startCalled.get());
        }

        @Test
        @DisplayName("Should notify listeners on batch completion")
        void shouldNotifyOnBatchComplete() {
            // Arrange
            when(mockService.process(any())).thenReturn(createSuccessResponse("TXN"));
            AtomicInteger completeCalled = new AtomicInteger(0);

            processor.addListener(new BatchListener() {
                @Override
                public void onBatchCompleted(BatchResult result) {
                    completeCalled.incrementAndGet();
                }
            });

            BatchRequest request = createBatchRequest("BATCH001", 1);

            // Act
            processor.process(request);

            // Assert
            assertEquals(1, completeCalled.get());
        }

        @Test
        @DisplayName("Should notify progress updates")
        void shouldNotifyProgress() {
            // Arrange
            when(mockService.process(any())).thenReturn(createSuccessResponse("TXN"));
            List<Integer> progressUpdates = new ArrayList<>();

            processor.addListener(new BatchListener() {
                @Override
                public void onProgress(String batchId, int processed, int total) {
                    progressUpdates.add(processed);
                }
            });

            BatchRequest request = createBatchRequest("BATCH001", 3);
            request.setMaxParallelism(1);

            // Act
            processor.process(request);

            // Assert
            assertEquals(3, progressUpdates.size());
            assertTrue(progressUpdates.contains(1));
            assertTrue(progressUpdates.contains(2));
            assertTrue(progressUpdates.contains(3));
        }
    }

    @Nested
    @DisplayName("Async processing tests")
    class AsyncProcessingTests {

        @Test
        @DisplayName("Should process batch asynchronously")
        void shouldProcessAsync() throws Exception {
            // Arrange
            when(mockService.process(any())).thenReturn(createSuccessResponse("TXN"));

            BatchRequest request = createBatchRequest("BATCH001", 5);

            // Act
            CompletableFuture<BatchResult> future = processor.processAsync(request);
            BatchResult result = future.get();

            // Assert
            assertEquals(BatchStatus.COMPLETED, result.getStatus());
            assertEquals(5, result.getSuccessCount());
        }
    }

    @Nested
    @DisplayName("Statistics tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should calculate total amounts correctly")
        void shouldCalculateTotalAmounts() {
            // Arrange - Create responses with specific amounts
            when(mockService.process(any()))
                    .thenReturn(createSuccessResponseWithAmount("TXN1", new BigDecimal("1000")))
                    .thenReturn(createSuccessResponseWithAmount("TXN2", new BigDecimal("2000")))
                    .thenReturn(createDeclinedResponseWithAmount("TXN3", new BigDecimal("3000")));

            BatchRequest request = createBatchRequest("BATCH001", 3);
            request.setMaxParallelism(1);

            // Act
            BatchResult result = processor.process(request);

            // Assert
            assertEquals(new BigDecimal("3000"), result.getTotalSuccessAmount());
            assertEquals(new BigDecimal("3000"), result.getTotalFailedAmount());
        }

        @Test
        @DisplayName("Should track processing time")
        void shouldTrackProcessingTime() {
            // Arrange
            when(mockService.process(any())).thenAnswer(inv -> {
                Thread.sleep(10);
                return createSuccessResponse("TXN");
            });

            BatchRequest request = createBatchRequest("BATCH001", 2);
            request.setMaxParallelism(1);

            // Act
            BatchResult result = processor.process(request);

            // Assert
            assertNotNull(result.getStartTime());
            assertNotNull(result.getEndTime());
            assertNotNull(result.getProcessingTimeMs());
            assertTrue(result.getProcessingTimeMs() >= 20);
        }
    }

    // Helper methods

    private BatchRequest createBatchRequest(String batchId, int transactionCount) {
        List<TransactionRequest> transactions = new ArrayList<>();
        for (int i = 0; i < transactionCount; i++) {
            transactions.add(TransactionRequest.builder()
                    .transactionId("TXN" + String.format("%03d", i + 1))
                    .transactionType(TransactionType.TRANSFER)
                    .pan("4111111111111111")
                    .amount(new BigDecimal("1000"))
                    .currencyCode("901")
                    .terminalId("TERM001")
                    .sourceAccount("1234567890")
                    .destinationAccount("0987654321")
                    .pinBlock("1234567890ABCDEF")
                    .build());
        }

        return BatchRequest.builder()
                .batchId(batchId)
                .batchType(BatchType.BULK_TRANSFER)
                .transactions(transactions)
                .maxParallelism(4)
                .continueOnError(true)
                .build();
    }

    private TransactionResponse createSuccessResponse(String txnId) {
        return TransactionResponse.builder()
                .transactionId(txnId)
                .responseCode(ResponseCode.APPROVED.getCode())
                .responseCodeEnum(ResponseCode.APPROVED)
                .approved(true)
                .amount(new BigDecimal("1000"))
                .build();
    }

    private TransactionResponse createSuccessResponseWithAmount(String txnId, BigDecimal amount) {
        return TransactionResponse.builder()
                .transactionId(txnId)
                .responseCode(ResponseCode.APPROVED.getCode())
                .responseCodeEnum(ResponseCode.APPROVED)
                .approved(true)
                .amount(amount)
                .build();
    }

    private TransactionResponse createDeclinedResponse(String txnId) {
        return TransactionResponse.builder()
                .transactionId(txnId)
                .responseCode(ResponseCode.INSUFFICIENT_FUNDS.getCode())
                .responseCodeEnum(ResponseCode.INSUFFICIENT_FUNDS)
                .responseDescription("Insufficient funds")
                .approved(false)
                .amount(new BigDecimal("1000"))
                .build();
    }

    private TransactionResponse createDeclinedResponseWithAmount(String txnId, BigDecimal amount) {
        return TransactionResponse.builder()
                .transactionId(txnId)
                .responseCode(ResponseCode.INSUFFICIENT_FUNDS.getCode())
                .responseCodeEnum(ResponseCode.INSUFFICIENT_FUNDS)
                .responseDescription("Insufficient funds")
                .approved(false)
                .amount(amount)
                .build();
    }
}
