package com.fep.settlement.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Settlement record representing a transaction in settlement file.
 * This corresponds to FISC settlement file record format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRecord {

    /** Record sequence number in the file */
    private long sequenceNumber;

    /** Settlement date (營業日) */
    private LocalDate settlementDate;

    /** Original transaction reference number (交易序號) */
    private String transactionRefNo;

    /** System trace audit number (STAN) */
    private String stan;

    /** Retrieval reference number (RRN) */
    private String rrn;

    /** Transaction type code */
    private String transactionType;

    /** Transaction code (交易代碼) */
    private String transactionCode;

    /** Acquiring bank code (收單行代號) */
    private String acquiringBankCode;

    /** Issuing bank code (發卡行代號) */
    private String issuingBankCode;

    /** Card number (卡號) - masked */
    private String cardNumber;

    /** Transaction amount (交易金額) */
    private BigDecimal amount;

    /** Currency code */
    private String currencyCode;

    /** Fee amount (手續費) */
    private BigDecimal feeAmount;

    /** Net settlement amount (清算淨額) */
    private BigDecimal netAmount;

    /** Transaction date time */
    private LocalDateTime transactionDateTime;

    /** Settlement status */
    @Builder.Default
    private SettlementStatus status = SettlementStatus.PENDING;

    /** Terminal ID */
    private String terminalId;

    /** Merchant ID */
    private String merchantId;

    /** Authorization code */
    private String authCode;

    /** Response code from original transaction */
    private String responseCode;

    /** Indicates if this is a reversal/refund */
    @Builder.Default
    private boolean reversal = false;

    /** Original transaction ref for reversals */
    private String originalTransactionRef;

    /** Channel (ATM, POS, EBANK, etc.) */
    private String channel;

    /** From account (for transfers) */
    private String fromAccount;

    /** To account (for transfers) */
    private String toAccount;

    /** Record hash for integrity check */
    private String recordHash;

    /** Raw record data */
    private String rawData;

    /** Processing notes */
    private String notes;

    /** Matched internal transaction ID */
    private String matchedTransactionId;

    /** Match timestamp */
    private LocalDateTime matchedAt;

    /**
     * Calculate net amount based on amount, fee, and whether it's a reversal.
     */
    public BigDecimal calculateNetAmount() {
        if (amount == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal fee = feeAmount != null ? feeAmount : BigDecimal.ZERO;
        BigDecimal net = amount.subtract(fee);

        return reversal ? net.negate() : net;
    }

    /**
     * Check if record is for a debit transaction (outgoing).
     */
    public boolean isDebit() {
        return transactionType != null && (
            transactionType.startsWith("01") || // Withdrawal
            transactionType.startsWith("02")    // Transfer out
        );
    }

    /**
     * Check if record is for a credit transaction (incoming).
     */
    public boolean isCredit() {
        return transactionType != null && (
            transactionType.startsWith("03") || // Deposit
            transactionType.startsWith("04")    // Transfer in
        );
    }

    /**
     * Get masked card number for display.
     */
    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() < 12) {
            return cardNumber;
        }
        return cardNumber.substring(0, 6) + "****" + cardNumber.substring(cardNumber.length() - 4);
    }
}
