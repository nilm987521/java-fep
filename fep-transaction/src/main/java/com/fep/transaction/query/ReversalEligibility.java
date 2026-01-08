package com.fep.transaction.query;

import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionStatus;
import lombok.Builder;
import lombok.Data;

/**
 * Result of reversal eligibility check.
 */
@Data
@Builder
public class ReversalEligibility {

    /** Whether the transaction can be reversed */
    private boolean eligible;

    /** Reason if not eligible */
    private String reason;

    /** Reason code */
    private ReversalIneligibleReason reasonCode;

    /** Original transaction record (if found) */
    private TransactionRecord originalTransaction;

    /**
     * Reason codes for reversal ineligibility.
     */
    public enum ReversalIneligibleReason {
        NOT_FOUND,
        ALREADY_REVERSED,
        INVALID_STATUS,
        TIME_WINDOW_EXPIRED,
        ALREADY_SETTLED
    }

    /**
     * Creates an eligible result.
     */
    public static ReversalEligibility eligible(TransactionRecord record) {
        return ReversalEligibility.builder()
                .eligible(true)
                .originalTransaction(record)
                .build();
    }

    /**
     * Creates a not found result.
     */
    public static ReversalEligibility notFound(String transactionId) {
        return ReversalEligibility.builder()
                .eligible(false)
                .reason("Transaction not found: " + transactionId)
                .reasonCode(ReversalIneligibleReason.NOT_FOUND)
                .build();
    }

    /**
     * Creates an already reversed result.
     */
    public static ReversalEligibility alreadyReversed(String transactionId) {
        return ReversalEligibility.builder()
                .eligible(false)
                .reason("Transaction already reversed: " + transactionId)
                .reasonCode(ReversalIneligibleReason.ALREADY_REVERSED)
                .build();
    }

    /**
     * Creates a not reversible result due to status.
     */
    public static ReversalEligibility notReversible(String transactionId, TransactionStatus status) {
        return ReversalEligibility.builder()
                .eligible(false)
                .reason("Transaction cannot be reversed in status: " + status)
                .reasonCode(ReversalIneligibleReason.INVALID_STATUS)
                .build();
    }

    /**
     * Creates a time window expired result.
     */
    public static ReversalEligibility timeWindowExpired(String transactionId) {
        return ReversalEligibility.builder()
                .eligible(false)
                .reason("Reversal time window expired for: " + transactionId)
                .reasonCode(ReversalIneligibleReason.TIME_WINDOW_EXPIRED)
                .build();
    }
}
