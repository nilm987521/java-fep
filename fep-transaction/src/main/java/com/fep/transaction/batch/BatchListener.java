package com.fep.transaction.batch;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;

/**
 * Listener for batch processing events.
 */
public interface BatchListener {

    /**
     * Called when batch processing starts.
     *
     * @param request the batch request
     */
    default void onBatchStarted(BatchRequest request) {}

    /**
     * Called when batch processing completes.
     *
     * @param result the batch result
     */
    default void onBatchCompleted(BatchResult result) {}

    /**
     * Called when batch processing fails.
     *
     * @param batchId the batch ID
     * @param error the error that caused failure
     */
    default void onBatchFailed(String batchId, Throwable error) {}

    /**
     * Called when progress is made.
     *
     * @param batchId the batch ID
     * @param processed number of transactions processed
     * @param total total number of transactions
     */
    default void onProgress(String batchId, int processed, int total) {}

    /**
     * Called when a single transaction completes.
     *
     * @param request the transaction request
     * @param response the transaction response
     */
    default void onTransactionCompleted(TransactionRequest request, TransactionResponse response) {}
}
