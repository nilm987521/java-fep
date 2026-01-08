package com.fep.settlement.reconciliation;

/**
 * Status of reconciliation process.
 */
public enum ReconciliationStatus {

    /** Not started */
    PENDING("待處理"),

    /** In progress */
    IN_PROGRESS("對帳中"),

    /** Completed successfully with all records matched */
    COMPLETED("對帳完成"),

    /** Completed with some discrepancies */
    COMPLETED_WITH_DISCREPANCIES("對帳完成(有差異)"),

    /** Failed due to error */
    FAILED("對帳失敗"),

    /** Cancelled */
    CANCELLED("已取消");

    private final String chineseDescription;

    ReconciliationStatus(String chineseDescription) {
        this.chineseDescription = chineseDescription;
    }

    public String getChineseDescription() {
        return chineseDescription;
    }

    /**
     * Check if reconciliation is complete (success or failure).
     */
    public boolean isComplete() {
        return this == COMPLETED ||
               this == COMPLETED_WITH_DISCREPANCIES ||
               this == FAILED ||
               this == CANCELLED;
    }

    /**
     * Check if reconciliation was successful (possibly with discrepancies).
     */
    public boolean isSuccessful() {
        return this == COMPLETED ||
               this == COMPLETED_WITH_DISCREPANCIES;
    }
}
