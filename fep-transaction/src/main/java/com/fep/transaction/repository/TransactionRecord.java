package com.fep.transaction.repository;

import com.fep.transaction.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persistent transaction record.
 */
@Data
@Builder
public class TransactionRecord {

    /** Unique record ID (primary key) */
    private Long id;

    /** Transaction ID */
    private String transactionId;

    /** Transaction type */
    private TransactionType transactionType;

    /** Processing code */
    private String processingCode;

    /** Masked PAN */
    private String maskedPan;

    /** PAN (full or masked, for query matching) */
    private String pan;

    /** PAN hash (for lookups) */
    private String panHash;

    /** Transaction amount */
    private BigDecimal amount;

    /** Currency code */
    private String currencyCode;

    /** Source account (masked) */
    private String sourceAccount;

    /** Destination account (masked) */
    private String destinationAccount;

    /** Destination bank code */
    private String destinationBankCode;

    /** Terminal ID */
    private String terminalId;

    /** Merchant ID */
    private String merchantId;

    /** Acquiring bank code */
    private String acquiringBankCode;

    /** STAN */
    private String stan;

    /** RRN */
    private String rrn;

    /** Channel */
    private String channel;

    /** Transaction status */
    private TransactionStatus status;

    /** Response code */
    private String responseCode;

    /** Authorization code */
    private String authorizationCode;

    /** Host reference number */
    private String hostReferenceNumber;

    /** Original transaction ID (for reversals) */
    private String originalTransactionId;

    /** Request timestamp */
    private LocalDateTime requestTime;

    /** Transaction time (for query filtering) */
    private LocalDateTime transactionTime;

    /** Response timestamp */
    private LocalDateTime responseTime;

    /** Processing time in milliseconds */
    private Long processingTimeMs;

    /** Error details */
    private String errorDetails;

    /** Record creation time */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Record last update time */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Transaction date (for partitioning) */
    private String transactionDate;

    /**
     * Updates the status if transition is valid.
     *
     * @return true if status was updated
     */
    public boolean updateStatus(TransactionStatus newStatus) {
        if (status == null || status.canTransitionTo(newStatus)) {
            this.status = newStatus;
            this.updatedAt = LocalDateTime.now();
            return true;
        }
        return false;
    }
}
