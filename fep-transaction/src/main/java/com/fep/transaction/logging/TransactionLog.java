package com.fep.transaction.logging;

import com.fep.transaction.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction log entry for audit and tracking purposes.
 */
@Data
@Builder
public class TransactionLog {

    /** Unique log ID */
    private String logId;

    /** Transaction ID */
    private String transactionId;

    /** Transaction type */
    private TransactionType transactionType;

    /** Processing code */
    private String processingCode;

    /** Masked PAN (first 6 + last 4) */
    private String maskedPan;

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

    /** Response code */
    private String responseCode;

    /** Response description */
    private String responseDescription;

    /** Whether transaction was approved */
    private boolean approved;

    /** Authorization code (if approved) */
    private String authorizationCode;

    /** Request timestamp */
    private LocalDateTime requestTime;

    /** Response timestamp */
    private LocalDateTime responseTime;

    /** Processing duration in milliseconds */
    private long processingTimeMs;

    /** Error details (if any) */
    private String errorDetails;

    /** Host reference number */
    private String hostReferenceNumber;

    /** Original transaction ID (for reversals) */
    private String originalTransactionId;

    /** Log creation timestamp */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Gets a summary string for logging.
     */
    public String toSummary() {
        return String.format("[%s] %s %s %s %s -> %s (%dms)",
                transactionId,
                transactionType != null ? transactionType.name() : "UNKNOWN",
                maskedPan,
                amount != null ? amount + " " + currencyCode : "-",
                responseCode,
                approved ? "APPROVED" : "DECLINED",
                processingTimeMs);
    }
}
