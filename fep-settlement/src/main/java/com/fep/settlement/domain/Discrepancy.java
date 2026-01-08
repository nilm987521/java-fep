package com.fep.settlement.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a discrepancy found during reconciliation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Discrepancy {

    /** Unique discrepancy ID */
    private String discrepancyId;

    /** Type of discrepancy */
    private DiscrepancyType type;

    /** Settlement date */
    private LocalDate settlementDate;

    /** Reference to settlement file */
    private String settlementFileId;

    /** Reference to settlement record (if exists) */
    private String settlementRecordRef;

    /** Reference to internal transaction (if exists) */
    private String internalTransactionRef;

    /** Amount in settlement file */
    private BigDecimal settlementAmount;

    /** Amount in internal system */
    private BigDecimal internalAmount;

    /** Difference amount */
    private BigDecimal differenceAmount;

    /** Currency code */
    private String currencyCode;

    /** Card number (masked) */
    private String cardNumber;

    /** Transaction type */
    private String transactionType;

    /** Discrepancy status */
    @Builder.Default
    private DiscrepancyStatus status = DiscrepancyStatus.OPEN;

    /** Priority level */
    @Builder.Default
    private DiscrepancyPriority priority = DiscrepancyPriority.MEDIUM;

    /** Description of the discrepancy */
    private String description;

    /** Root cause after investigation */
    private String rootCause;

    /** Resolution notes */
    private String resolutionNotes;

    /** Resolution action taken */
    private ResolutionAction resolutionAction;

    /** Assigned investigator */
    private String assignedTo;

    /** Creation timestamp */
    private LocalDateTime createdAt;

    /** Last updated timestamp */
    private LocalDateTime updatedAt;

    /** Resolution timestamp */
    private LocalDateTime resolvedAt;

    /** Resolved by */
    private String resolvedBy;

    /** Investigation history */
    @Builder.Default
    private List<InvestigationNote> investigationNotes = new ArrayList<>();

    /** Related discrepancy IDs (for linked cases) */
    @Builder.Default
    private List<String> relatedDiscrepancies = new ArrayList<>();

    /**
     * Calculate the absolute difference amount.
     */
    public BigDecimal getAbsoluteDifference() {
        if (differenceAmount == null) {
            return BigDecimal.ZERO;
        }
        return differenceAmount.abs();
    }

    /**
     * Check if this discrepancy is still open.
     */
    public boolean isOpen() {
        return status == DiscrepancyStatus.OPEN ||
               status == DiscrepancyStatus.INVESTIGATING ||
               status == DiscrepancyStatus.PENDING_APPROVAL;
    }

    /**
     * Add investigation note.
     */
    public void addInvestigationNote(String userId, String note) {
        if (investigationNotes == null) {
            investigationNotes = new ArrayList<>();
        }
        investigationNotes.add(InvestigationNote.builder()
                .userId(userId)
                .note(note)
                .timestamp(LocalDateTime.now())
                .build());
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Resolve the discrepancy.
     */
    public void resolve(String userId, ResolutionAction action, String notes) {
        this.status = DiscrepancyStatus.RESOLVED;
        this.resolutionAction = action;
        this.resolutionNotes = notes;
        this.resolvedBy = userId;
        this.resolvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Investigation note entry.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestigationNote {
        private String userId;
        private String note;
        private LocalDateTime timestamp;
    }
}
