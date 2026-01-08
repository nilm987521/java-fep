package com.fep.settlement.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Clearing record representing the net settlement amount between banks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClearingRecord {

    /** Unique clearing record ID */
    private String clearingId;

    /** Settlement date */
    private LocalDate settlementDate;

    /** Clearing batch number */
    private String batchNumber;

    /** Our bank code */
    private String ourBankCode;

    /** Counterparty bank code */
    private String counterpartyBankCode;

    /** Transaction category */
    private String transactionCategory;

    /** Total debit count (we pay) */
    private int debitCount;

    /** Total debit amount (we pay) */
    private BigDecimal debitAmount;

    /** Total credit count (we receive) */
    private int creditCount;

    /** Total credit amount (we receive) */
    private BigDecimal creditAmount;

    /** Net settlement amount (positive = we receive, negative = we pay) */
    private BigDecimal netAmount;

    /** Fee amount */
    private BigDecimal feeAmount;

    /** Currency code */
    @Builder.Default
    private String currencyCode = "TWD";

    /** Clearing status */
    @Builder.Default
    private ClearingStatus status = ClearingStatus.PENDING;

    /** Creation timestamp */
    private LocalDateTime createdAt;

    /** Confirmation timestamp */
    private LocalDateTime confirmedAt;

    /** Settlement timestamp (actual fund movement) */
    private LocalDateTime settledAt;

    /** Confirmed by */
    private String confirmedBy;

    /** Notes */
    private String notes;

    /** Reference to settlement file */
    private String settlementFileId;

    /**
     * Calculate net amount from debit and credit.
     */
    public BigDecimal calculateNetAmount() {
        BigDecimal credit = creditAmount != null ? creditAmount : BigDecimal.ZERO;
        BigDecimal debit = debitAmount != null ? debitAmount : BigDecimal.ZERO;
        return credit.subtract(debit);
    }

    /**
     * Check if we are net payer.
     */
    public boolean isNetPayer() {
        return netAmount != null && netAmount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Check if we are net receiver.
     */
    public boolean isNetReceiver() {
        return netAmount != null && netAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Get absolute net amount.
     */
    public BigDecimal getAbsoluteNetAmount() {
        return netAmount != null ? netAmount.abs() : BigDecimal.ZERO;
    }

    /**
     * Confirm the clearing record.
     */
    public void confirm(String userId) {
        this.status = ClearingStatus.CONFIRMED;
        this.confirmedBy = userId;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * Mark as settled.
     */
    public void settle() {
        this.status = ClearingStatus.SETTLED;
        this.settledAt = LocalDateTime.now();
    }
}
