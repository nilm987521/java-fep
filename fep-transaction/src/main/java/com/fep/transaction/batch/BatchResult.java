package com.fep.transaction.batch;

import com.fep.transaction.domain.TransactionResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of batch transaction processing.
 */
@Data
@Builder
public class BatchResult {

    /** Batch ID */
    private String batchId;

    /** Batch status */
    private BatchStatus status;

    /** Processing start time */
    private LocalDateTime startTime;

    /** Processing end time */
    private LocalDateTime endTime;

    /** Total number of transactions in batch */
    private int totalCount;

    /** Number of successful transactions */
    private int successCount;

    /** Number of failed transactions */
    private int failedCount;

    /** Total amount processed (successful) */
    private BigDecimal totalSuccessAmount;

    /** Total amount failed */
    private BigDecimal totalFailedAmount;

    /** Individual transaction results */
    @Builder.Default
    private List<TransactionResponse> results = new ArrayList<>();

    /** Failed transaction details */
    @Builder.Default
    private List<BatchItemError> errors = new ArrayList<>();

    /** Processing duration in milliseconds */
    private Long processingTimeMs;

    /** Error message if batch failed */
    private String errorMessage;

    /**
     * Adds a successful result.
     */
    public void addSuccess(TransactionResponse response) {
        if (results == null) {
            results = new ArrayList<>();
        }
        results.add(response);
        successCount++;
        if (response.getAmount() != null) {
            if (totalSuccessAmount == null) {
                totalSuccessAmount = BigDecimal.ZERO;
            }
            totalSuccessAmount = totalSuccessAmount.add(response.getAmount());
        }
    }

    /**
     * Adds a failed result.
     */
    public void addFailure(TransactionResponse response, String errorDetail) {
        if (results == null) {
            results = new ArrayList<>();
        }
        if (errors == null) {
            errors = new ArrayList<>();
        }
        results.add(response);
        errors.add(BatchItemError.builder()
                .transactionId(response.getTransactionId())
                .responseCode(response.getResponseCode())
                .errorDetail(errorDetail)
                .build());
        failedCount++;
        if (response.getAmount() != null) {
            if (totalFailedAmount == null) {
                totalFailedAmount = BigDecimal.ZERO;
            }
            totalFailedAmount = totalFailedAmount.add(response.getAmount());
        }
    }

    /**
     * Gets the success rate as percentage.
     */
    public double getSuccessRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) successCount / totalCount * 100.0;
    }

    /**
     * Gets the processing duration.
     */
    public Duration getProcessingDuration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }

    /**
     * Finalizes the batch result.
     */
    public void finalize(LocalDateTime end) {
        this.endTime = end;
        if (startTime != null) {
            this.processingTimeMs = Duration.between(startTime, end).toMillis();
        }
        this.totalCount = results != null ? results.size() : 0;

        // Determine final status
        if (failedCount == 0 && successCount > 0) {
            this.status = BatchStatus.COMPLETED;
        } else if (successCount == 0 && failedCount > 0) {
            this.status = BatchStatus.FAILED;
        } else if (successCount > 0 && failedCount > 0) {
            this.status = BatchStatus.COMPLETED_WITH_ERRORS;
        }
    }

    /**
     * Creates an empty result for a batch.
     */
    public static BatchResult empty(String batchId) {
        return BatchResult.builder()
                .batchId(batchId)
                .status(BatchStatus.PENDING)
                .totalCount(0)
                .successCount(0)
                .failedCount(0)
                .totalSuccessAmount(BigDecimal.ZERO)
                .totalFailedAmount(BigDecimal.ZERO)
                .results(new ArrayList<>())
                .errors(new ArrayList<>())
                .build();
    }

    /**
     * Creates a failed result.
     */
    public static BatchResult failed(String batchId, String errorMessage) {
        return BatchResult.builder()
                .batchId(batchId)
                .status(BatchStatus.FAILED)
                .errorMessage(errorMessage)
                .results(new ArrayList<>())
                .errors(new ArrayList<>())
                .build();
    }
}
