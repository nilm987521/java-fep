package com.fep.transaction.retry;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Context for tracking retry attempts.
 */
@Data
@Builder
public class RetryContext {

    /** Original transaction request */
    private TransactionRequest request;

    /** Retry policy being used */
    private RetryPolicy policy;

    /** Current attempt number (0 = initial, 1+ = retries) */
    @Builder.Default
    private int currentAttempt = 0;

    /** Start time of first attempt */
    @Builder.Default
    private LocalDateTime startTime = LocalDateTime.now();

    /** History of all attempts */
    @Builder.Default
    private List<AttemptRecord> attemptHistory = new ArrayList<>();

    /** Last response received */
    private TransactionResponse lastResponse;

    /** Last exception encountered */
    private Exception lastException;

    /** Final status */
    private RetryStatus status;

    /** Total elapsed time */
    private Duration totalElapsed;

    /**
     * Record of a single attempt.
     */
    @Data
    @Builder
    public static class AttemptRecord {
        private int attemptNumber;
        private LocalDateTime timestamp;
        private Duration duration;
        private TransactionResponse response;
        private Exception exception;
        private String responseCode;
        private boolean success;
    }

    /**
     * Retry status.
     */
    public enum RetryStatus {
        /** Initial state */
        PENDING,
        /** Currently retrying */
        RETRYING,
        /** Succeeded */
        SUCCESS,
        /** All retries exhausted */
        EXHAUSTED,
        /** Not retryable error */
        FAILED,
        /** Cancelled by user/system */
        CANCELLED
    }

    /**
     * Creates a new retry context.
     */
    public static RetryContext create(TransactionRequest request, RetryPolicy policy) {
        return RetryContext.builder()
                .request(request)
                .policy(policy)
                .status(RetryStatus.PENDING)
                .build();
    }

    /**
     * Records a successful attempt.
     */
    public void recordSuccess(TransactionResponse response, Duration duration) {
        AttemptRecord record = AttemptRecord.builder()
                .attemptNumber(currentAttempt)
                .timestamp(LocalDateTime.now())
                .duration(duration)
                .response(response)
                .responseCode(response.getResponseCode())
                .success(true)
                .build();
        attemptHistory.add(record);

        this.lastResponse = response;
        this.lastException = null;
        this.status = RetryStatus.SUCCESS;
        calculateTotalElapsed();
    }

    /**
     * Records a failed attempt.
     */
    public void recordFailure(TransactionResponse response, Duration duration) {
        AttemptRecord record = AttemptRecord.builder()
                .attemptNumber(currentAttempt)
                .timestamp(LocalDateTime.now())
                .duration(duration)
                .response(response)
                .responseCode(response != null ? response.getResponseCode() : null)
                .success(false)
                .build();
        attemptHistory.add(record);

        this.lastResponse = response;
        this.lastException = null;
        calculateTotalElapsed();
    }

    /**
     * Records an exception.
     */
    public void recordException(Exception exception, Duration duration) {
        AttemptRecord record = AttemptRecord.builder()
                .attemptNumber(currentAttempt)
                .timestamp(LocalDateTime.now())
                .duration(duration)
                .exception(exception)
                .success(false)
                .build();
        attemptHistory.add(record);

        this.lastResponse = null;
        this.lastException = exception;
        calculateTotalElapsed();
    }

    /**
     * Increments attempt counter and checks if retry is allowed.
     */
    public boolean prepareNextAttempt() {
        currentAttempt++;

        if (!policy.shouldRetry(currentAttempt)) {
            status = RetryStatus.EXHAUSTED;
            return false;
        }

        status = RetryStatus.RETRYING;
        return true;
    }

    /**
     * Checks if the last result is retryable.
     */
    public boolean isLastResultRetryable() {
        if (lastException != null) {
            return policy.isRetryableException(lastException);
        }
        if (lastResponse != null) {
            return policy.isRetryableResponseCode(lastResponse.getResponseCode());
        }
        return false;
    }

    /**
     * Gets the delay before next retry.
     */
    public Duration getNextRetryDelay() {
        return policy.getDelayForAttempt(currentAttempt + 1);
    }

    /**
     * Marks as failed (not retryable).
     */
    public void markFailed() {
        status = RetryStatus.FAILED;
        calculateTotalElapsed();
    }

    /**
     * Marks as cancelled.
     */
    public void markCancelled() {
        status = RetryStatus.CANCELLED;
        calculateTotalElapsed();
    }

    /**
     * Checks if retry process is complete.
     */
    public boolean isComplete() {
        return status == RetryStatus.SUCCESS ||
               status == RetryStatus.EXHAUSTED ||
               status == RetryStatus.FAILED ||
               status == RetryStatus.CANCELLED;
    }

    /**
     * Gets the total number of attempts made.
     */
    public int getTotalAttempts() {
        return attemptHistory.size();
    }

    /**
     * Gets the number of retry attempts (excluding initial).
     */
    public int getRetryCount() {
        return Math.max(0, attemptHistory.size() - 1);
    }

    private void calculateTotalElapsed() {
        this.totalElapsed = Duration.between(startTime, LocalDateTime.now());
    }

    /**
     * Gets a summary string.
     */
    public String getSummary() {
        return String.format("RetryContext[txnId=%s, attempts=%d, status=%s, elapsed=%dms]",
                request != null ? request.getTransactionId() : "null",
                getTotalAttempts(),
                status,
                totalElapsed != null ? totalElapsed.toMillis() : 0);
    }
}
