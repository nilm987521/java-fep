package com.fep.transaction.batch;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processor for batch transactions.
 * Supports parallel processing, progress tracking, and error handling.
 */
public class BatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessor.class);

    private final TransactionService transactionService;
    private final ExecutorService executorService;
    private final int defaultParallelism;

    /** Listeners for batch processing events */
    private final List<BatchListener> listeners = new ArrayList<>();

    public BatchProcessor(TransactionService transactionService) {
        this(transactionService, 10);
    }

    public BatchProcessor(TransactionService transactionService, int defaultParallelism) {
        this.transactionService = transactionService;
        this.defaultParallelism = defaultParallelism;
        this.executorService = Executors.newFixedThreadPool(defaultParallelism * 2,
                new BatchThreadFactory());
    }

    /**
     * Processes a batch request synchronously.
     *
     * @param request the batch request
     * @return the batch result
     */
    public BatchResult process(BatchRequest request) {
        String batchId = request.getBatchId();
        log.info("[Batch-{}] Starting batch processing: {} transactions, type: {}",
                batchId, request.getTransactionCount(), request.getBatchType());

        BatchResult result = BatchResult.empty(batchId);
        result.setStartTime(LocalDateTime.now());
        result.setStatus(BatchStatus.PROCESSING);

        notifyListeners(listener -> listener.onBatchStarted(request));

        try {
            // Validate batch
            validateBatch(request);

            // Process transactions
            int parallelism = Math.min(request.getMaxParallelism(), defaultParallelism);
            if (parallelism <= 1) {
                processSequentially(request, result);
            } else {
                processInParallel(request, result, parallelism);
            }

            result.finalize(LocalDateTime.now());

            log.info("[Batch-{}] Batch completed: {} success, {} failed, {}% success rate",
                    batchId, result.getSuccessCount(), result.getFailedCount(),
                    String.format("%.2f", result.getSuccessRate()));

            notifyListeners(listener -> listener.onBatchCompleted(result));

        } catch (Exception e) {
            log.error("[Batch-{}] Batch processing failed: {}", batchId, e.getMessage(), e);
            result.setStatus(BatchStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());

            notifyListeners(listener -> listener.onBatchFailed(batchId, e));
        }

        return result;
    }

    /**
     * Processes a batch request asynchronously.
     *
     * @param request the batch request
     * @return a future containing the batch result
     */
    public CompletableFuture<BatchResult> processAsync(BatchRequest request) {
        return CompletableFuture.supplyAsync(() -> process(request), executorService);
    }

    /**
     * Validates the batch request.
     */
    private void validateBatch(BatchRequest request) {
        if (request.getBatchId() == null || request.getBatchId().isBlank()) {
            throw new IllegalArgumentException("Batch ID is required");
        }
        if (request.getTransactions() == null || request.getTransactions().isEmpty()) {
            throw new IllegalArgumentException("Batch must contain at least one transaction");
        }
        if (request.getMaxParallelism() < 1) {
            throw new IllegalArgumentException("Max parallelism must be at least 1");
        }
    }

    /**
     * Processes transactions sequentially.
     */
    private void processSequentially(BatchRequest request, BatchResult result) {
        List<TransactionRequest> transactions = request.getTransactions();
        int total = transactions.size();
        int processed = 0;

        for (TransactionRequest txnRequest : transactions) {
            processed++;
            processTransaction(txnRequest, result, request.isContinueOnError());

            // Notify progress
            int currentProcessed = processed;
            notifyListeners(listener -> listener.onProgress(request.getBatchId(),
                    currentProcessed, total));
        }
    }

    /**
     * Processes transactions in parallel.
     */
    private void processInParallel(BatchRequest request, BatchResult result, int parallelism) {
        List<TransactionRequest> transactions = request.getTransactions();
        int total = transactions.size();
        AtomicInteger processed = new AtomicInteger(0);

        // Create semaphore for parallelism control
        Semaphore semaphore = new Semaphore(parallelism);

        // Submit all tasks
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (TransactionRequest txnRequest : transactions) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    try {
                        processTransaction(txnRequest, result, request.isContinueOnError());
                    } finally {
                        semaphore.release();
                    }

                    // Notify progress
                    int current = processed.incrementAndGet();
                    notifyListeners(listener -> listener.onProgress(request.getBatchId(),
                            current, total));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Batch processing interrupted: {}", e.getMessage());
                }
            }, executorService);

            futures.add(future);
        }

        // Wait for all tasks to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Batch processing interrupted: {}", e.getMessage());
        } catch (ExecutionException | TimeoutException e) {
            log.error("Batch processing error: {}", e.getMessage());
        }
    }

    /**
     * Processes a single transaction.
     */
    private void processTransaction(TransactionRequest request, BatchResult result,
                                    boolean continueOnError) {
        String txnId = request.getTransactionId();

        try {
            TransactionResponse response = transactionService.process(request);

            synchronized (result) {
                if (response.isApproved()) {
                    result.addSuccess(response);
                    log.debug("[{}] Transaction processed successfully", txnId);
                } else {
                    result.addFailure(response, response.getResponseDescription());
                    log.debug("[{}] Transaction declined: {}", txnId, response.getResponseCode());
                }
            }

            // Notify transaction completion
            notifyListeners(listener -> listener.onTransactionCompleted(request, response));

        } catch (Exception e) {
            log.error("[{}] Transaction processing failed: {}", txnId, e.getMessage());

            TransactionResponse errorResponse = TransactionResponse.builder()
                    .transactionId(txnId)
                    .responseCode(ResponseCode.SYSTEM_MALFUNCTION.getCode())
                    .responseCodeEnum(ResponseCode.SYSTEM_MALFUNCTION)
                    .responseDescription(e.getMessage())
                    .approved(false)
                    .amount(request.getAmount())
                    .build();

            synchronized (result) {
                result.addFailure(errorResponse, e.getMessage());
            }

            if (!continueOnError) {
                throw new RuntimeException("Batch processing stopped due to error", e);
            }
        }
    }

    /**
     * Registers a batch listener.
     */
    public void addListener(BatchListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a batch listener.
     */
    public void removeListener(BatchListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifies all listeners.
     */
    private void notifyListeners(java.util.function.Consumer<BatchListener> action) {
        for (BatchListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.warn("Error notifying batch listener: {}", e.getMessage());
            }
        }
    }

    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Custom thread factory for batch processing threads.
     */
    private static class BatchThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "batch-processor-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
