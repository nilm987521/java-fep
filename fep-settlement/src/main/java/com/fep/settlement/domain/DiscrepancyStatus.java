package com.fep.settlement.domain;

/**
 * Status of a discrepancy case.
 */
public enum DiscrepancyStatus {

    /** Newly identified, not yet investigated */
    OPEN("待處理"),

    /** Under investigation */
    INVESTIGATING("調查中"),

    /** Pending approval for resolution */
    PENDING_APPROVAL("待核准"),

    /** Resolved */
    RESOLVED("已解決"),

    /** Written off */
    WRITTEN_OFF("已沖銷"),

    /** Escalated to management */
    ESCALATED("已上報"),

    /** Closed without resolution (timeout or other) */
    CLOSED("已結案");

    private final String chineseDescription;

    DiscrepancyStatus(String chineseDescription) {
        this.chineseDescription = chineseDescription;
    }

    public String getChineseDescription() {
        return chineseDescription;
    }

    /**
     * Check if this status indicates the case is still open.
     */
    public boolean isActive() {
        return this == OPEN ||
               this == INVESTIGATING ||
               this == PENDING_APPROVAL ||
               this == ESCALATED;
    }

    /**
     * Check if this is a terminal status.
     */
    public boolean isTerminal() {
        return this == RESOLVED ||
               this == WRITTEN_OFF ||
               this == CLOSED;
    }
}
