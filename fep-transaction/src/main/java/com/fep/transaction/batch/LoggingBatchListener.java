package com.fep.transaction.batch;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Batch listener that logs processing events.
 */
public class LoggingBatchListener implements BatchListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingBatchListener.class);

    @Override
    public void onBatchStarted(BatchRequest request) {
        log.info("[Batch-{}] Started processing {} transactions, type: {}, priority: {}",
                request.getBatchId(),
                request.getTransactionCount(),
                request.getBatchType(),
                request.getPriority());
    }

    @Override
    public void onBatchCompleted(BatchResult result) {
        log.info("[Batch-{}] Completed: status={}, success={}, failed={}, rate={:.2f}%, time={}ms",
                result.getBatchId(),
                result.getStatus(),
                result.getSuccessCount(),
                result.getFailedCount(),
                result.getSuccessRate(),
                result.getProcessingTimeMs());

        if (result.getTotalSuccessAmount() != null) {
            log.info("[Batch-{}] Total amount: success={}, failed={}",
                    result.getBatchId(),
                    result.getTotalSuccessAmount(),
                    result.getTotalFailedAmount());
        }
    }

    @Override
    public void onBatchFailed(String batchId, Throwable error) {
        log.error("[Batch-{}] Failed: {}", batchId, error.getMessage(), error);
    }

    @Override
    public void onProgress(String batchId, int processed, int total) {
        int percentage = (int) ((double) processed / total * 100);
        if (percentage % 10 == 0 || processed == total) {
            log.info("[Batch-{}] Progress: {}/{} ({}%)", batchId, processed, total, percentage);
        }
    }

    @Override
    public void onTransactionCompleted(TransactionRequest request, TransactionResponse response) {
        if (log.isDebugEnabled()) {
            log.debug("[Batch TXN-{}] Completed: approved={}, code={}",
                    request.getTransactionId(),
                    response.isApproved(),
                    response.getResponseCode());
        }
    }
}
