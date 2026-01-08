package com.fep.transaction.batch;

import com.fep.transaction.domain.TransactionRequest;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Request for batch transaction processing.
 */
@Data
@Builder
public class BatchRequest {

    /** Unique batch ID */
    private String batchId;

    /** Batch type (e.g., BILL_PAYMENT, SALARY, TRANSFER) */
    private BatchType batchType;

    /** List of transactions to process */
    @Builder.Default
    private List<TransactionRequest> transactions = new ArrayList<>();

    /** Submission time */
    @Builder.Default
    private LocalDateTime submissionTime = LocalDateTime.now();

    /** Requested processing time (for scheduled batches) */
    private LocalDateTime scheduledTime;

    /** Submitter identifier */
    private String submittedBy;

    /** Priority (higher = more urgent) */
    @Builder.Default
    private int priority = 5;

    /** Whether to continue on individual transaction failure */
    @Builder.Default
    private boolean continueOnError = true;

    /** Maximum parallel threads for processing */
    @Builder.Default
    private int maxParallelism = 10;

    /** Callback URL for completion notification */
    private String callbackUrl;

    /**
     * Adds a transaction to the batch.
     */
    public void addTransaction(TransactionRequest transaction) {
        if (transactions == null) {
            transactions = new ArrayList<>();
        }
        transactions.add(transaction);
    }

    /**
     * Gets the number of transactions in the batch.
     */
    public int getTransactionCount() {
        return transactions != null ? transactions.size() : 0;
    }
}
