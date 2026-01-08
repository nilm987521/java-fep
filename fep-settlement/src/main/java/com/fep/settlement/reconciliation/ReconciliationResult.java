package com.fep.settlement.reconciliation;

import com.fep.settlement.domain.Discrepancy;
import com.fep.settlement.domain.SettlementRecord;
import com.fep.settlement.domain.SettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of reconciliation process.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResult {

    /** Unique result ID */
    private String resultId;

    /** Settlement date */
    private LocalDate settlementDate;

    /** Settlement file ID */
    private String settlementFileId;

    /** Reconciliation start time */
    private LocalDateTime startTime;

    /** Reconciliation end time */
    private LocalDateTime endTime;

    /** Overall status */
    @Builder.Default
    private ReconciliationStatus status = ReconciliationStatus.IN_PROGRESS;

    /** Total records in settlement file */
    private int totalSettlementRecords;

    /** Total internal transactions compared */
    private int totalInternalTransactions;

    /** Matched record count */
    private int matchedCount;

    /** Discrepancy count */
    private int discrepancyCount;

    /** Total matched amount */
    private BigDecimal matchedAmount;

    /** Total discrepancy amount */
    private BigDecimal discrepancyAmount;

    /** List of matched records */
    @Builder.Default
    private List<MatchedPair> matchedRecords = new ArrayList<>();

    /** List of discrepancies found */
    @Builder.Default
    private List<Discrepancy> discrepancies = new ArrayList<>();

    /** Unmatched settlement records (in FISC but not in our system) */
    @Builder.Default
    private List<SettlementRecord> unmatchedSettlementRecords = new ArrayList<>();

    /** Unmatched internal transactions (in our system but not in FISC) */
    @Builder.Default
    private List<String> unmatchedInternalTransactions = new ArrayList<>();

    /** Status summary */
    @Builder.Default
    private Map<SettlementStatus, Integer> statusSummary = new HashMap<>();

    /** Error message if reconciliation failed */
    private String errorMessage;

    /**
     * Get reconciliation duration.
     */
    public Duration getDuration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }

    /**
     * Get match rate as percentage.
     */
    public double getMatchRate() {
        if (totalSettlementRecords == 0) {
            return 0.0;
        }
        return (matchedCount * 100.0) / totalSettlementRecords;
    }

    /**
     * Check if reconciliation is successful (100% match or within tolerance).
     */
    public boolean isSuccessful() {
        return status == ReconciliationStatus.COMPLETED &&
               discrepancyCount == 0;
    }

    /**
     * Check if there are discrepancies.
     */
    public boolean hasDiscrepancies() {
        return discrepancyCount > 0 || !discrepancies.isEmpty();
    }

    /**
     * Add a matched pair.
     */
    public void addMatchedPair(SettlementRecord settlementRecord, String internalTransactionId) {
        if (matchedRecords == null) {
            matchedRecords = new ArrayList<>();
        }
        matchedRecords.add(MatchedPair.builder()
                .settlementRecord(settlementRecord)
                .internalTransactionId(internalTransactionId)
                .matchedAt(LocalDateTime.now())
                .build());
        matchedCount++;
        if (settlementRecord.getAmount() != null) {
            matchedAmount = (matchedAmount != null ? matchedAmount : BigDecimal.ZERO)
                    .add(settlementRecord.getAmount());
        }
    }

    /**
     * Add a discrepancy.
     */
    public void addDiscrepancy(Discrepancy discrepancy) {
        if (discrepancies == null) {
            discrepancies = new ArrayList<>();
        }
        discrepancies.add(discrepancy);
        discrepancyCount++;
    }

    /**
     * Add an unmatched settlement record.
     */
    public void addUnmatchedSettlementRecord(SettlementRecord record) {
        if (unmatchedSettlementRecords == null) {
            unmatchedSettlementRecords = new ArrayList<>();
        }
        unmatchedSettlementRecords.add(record);
    }

    /**
     * Add an unmatched internal transaction.
     */
    public void addUnmatchedInternalTransaction(String transactionId) {
        if (unmatchedInternalTransactions == null) {
            unmatchedInternalTransactions = new ArrayList<>();
        }
        unmatchedInternalTransactions.add(transactionId);
    }

    /**
     * Complete the reconciliation.
     */
    public void complete() {
        this.endTime = LocalDateTime.now();
        this.status = hasDiscrepancies() ?
                ReconciliationStatus.COMPLETED_WITH_DISCREPANCIES :
                ReconciliationStatus.COMPLETED;
    }

    /**
     * Fail the reconciliation.
     */
    public void fail(String errorMessage) {
        this.endTime = LocalDateTime.now();
        this.status = ReconciliationStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * Get summary as map.
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("settlementDate", settlementDate);
        summary.put("totalSettlementRecords", totalSettlementRecords);
        summary.put("totalInternalTransactions", totalInternalTransactions);
        summary.put("matchedCount", matchedCount);
        summary.put("discrepancyCount", discrepancyCount);
        summary.put("matchRate", String.format("%.2f%%", getMatchRate()));
        summary.put("matchedAmount", matchedAmount);
        summary.put("discrepancyAmount", discrepancyAmount);
        summary.put("unmatchedSettlementCount", unmatchedSettlementRecords != null ?
                unmatchedSettlementRecords.size() : 0);
        summary.put("unmatchedInternalCount", unmatchedInternalTransactions != null ?
                unmatchedInternalTransactions.size() : 0);
        summary.put("status", status);
        summary.put("duration", getDuration().toMillis() + "ms");
        return summary;
    }

    /**
     * Matched pair of settlement record and internal transaction.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchedPair {
        private SettlementRecord settlementRecord;
        private String internalTransactionId;
        private LocalDateTime matchedAt;
        private BigDecimal amountDifference;
        private String matchKey;
    }
}
