package com.fep.settlement.domain;

/**
 * Settlement record status.
 */
public enum SettlementStatus {

    /** Pending reconciliation */
    PENDING("待對帳"),

    /** Matched with internal transaction */
    MATCHED("已配對"),

    /** Discrepancy found - amount mismatch */
    AMOUNT_MISMATCH("金額不符"),

    /** Discrepancy found - not found in internal system */
    NOT_FOUND("查無交易"),

    /** Discrepancy found - duplicate record */
    DUPLICATE("重複記錄"),

    /** Discrepancy found - missing in settlement file */
    MISSING("遺漏記錄"),

    /** Under investigation */
    INVESTIGATING("調查中"),

    /** Resolved after investigation */
    RESOLVED("已解決"),

    /** Confirmed discrepancy */
    CONFIRMED_DISCREPANCY("確認差異"),

    /** Written off */
    WRITTEN_OFF("已沖銷"),

    /** Cleared/Settled */
    CLEARED("已清算");

    private final String chineseDescription;

    SettlementStatus(String chineseDescription) {
        this.chineseDescription = chineseDescription;
    }

    public String getChineseDescription() {
        return chineseDescription;
    }

    /**
     * Check if this status indicates a discrepancy.
     */
    public boolean isDiscrepancy() {
        return this == AMOUNT_MISMATCH ||
               this == NOT_FOUND ||
               this == DUPLICATE ||
               this == MISSING ||
               this == CONFIRMED_DISCREPANCY;
    }

    /**
     * Check if this status is a terminal state.
     */
    public boolean isTerminal() {
        return this == MATCHED ||
               this == RESOLVED ||
               this == WRITTEN_OFF ||
               this == CLEARED;
    }

    /**
     * Check if this status requires action.
     */
    public boolean requiresAction() {
        return isDiscrepancy() || this == INVESTIGATING;
    }
}
