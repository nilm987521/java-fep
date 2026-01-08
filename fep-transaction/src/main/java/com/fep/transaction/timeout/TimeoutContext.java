package com.fep.transaction.timeout;

import com.fep.transaction.enums.TransactionType;
import lombok.Data;

import java.time.Instant;

/**
 * Tracks timeout information for a single transaction.
 */
@Data
public class TimeoutContext {

    /** Transaction identifier */
    private final String transactionId;

    /** Transaction type for timeout lookup */
    private final TransactionType transactionType;

    /** When the transaction started */
    private final Instant startTime;

    /** Configured timeout in milliseconds */
    private final long timeoutMs;

    /** Warning threshold (percentage of timeout) */
    private static final double WARNING_THRESHOLD = 0.8;

    /** Current status */
    private TimeoutStatus status;

    public TimeoutContext(String transactionId, TransactionType transactionType, long timeoutMs) {
        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.startTime = Instant.now();
        this.timeoutMs = timeoutMs;
        this.status = TimeoutStatus.ACTIVE;
    }

    /**
     * Gets elapsed time in milliseconds since transaction started.
     */
    public long getElapsedMs() {
        return Instant.now().toEpochMilli() - startTime.toEpochMilli();
    }

    /**
     * Gets remaining time before timeout in milliseconds.
     */
    public long getRemainingMs() {
        long remaining = timeoutMs - getElapsedMs();
        return Math.max(0, remaining);
    }

    /**
     * Checks if the transaction has timed out.
     */
    public boolean isTimedOut() {
        return getElapsedMs() >= timeoutMs;
    }

    /**
     * Checks if the transaction is in warning zone.
     */
    public boolean isInWarningZone() {
        return getElapsedMs() >= (timeoutMs * WARNING_THRESHOLD) && !isTimedOut();
    }

    /**
     * Updates and returns the current timeout status.
     */
    public TimeoutStatus checkStatus() {
        if (status == TimeoutStatus.COMPLETED) {
            return status;
        }

        if (isTimedOut()) {
            status = TimeoutStatus.EXPIRED;
        } else if (isInWarningZone()) {
            status = TimeoutStatus.WARNING;
        } else {
            status = TimeoutStatus.ACTIVE;
        }

        return status;
    }

    /**
     * Marks the transaction as completed (before timeout).
     */
    public void markCompleted() {
        if (status != TimeoutStatus.EXPIRED) {
            status = TimeoutStatus.COMPLETED;
        }
    }

    /**
     * Gets the percentage of timeout elapsed.
     */
    public double getElapsedPercentage() {
        return Math.min(100.0, (getElapsedMs() * 100.0) / timeoutMs);
    }
}
