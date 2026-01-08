package com.fep.settlement.domain;

/**
 * Actions taken to resolve a discrepancy.
 */
public enum ResolutionAction {

    /** Adjust internal record to match settlement */
    ADJUST_INTERNAL("調整本行記錄", "Adjusted internal record to match settlement file"),

    /** Dispute with counterparty - request correction */
    DISPUTE_REQUEST("爭議請求", "Raised dispute with counterparty, requested correction"),

    /** Dispute accepted - counterparty will adjust */
    DISPUTE_ACCEPTED("爭議接受", "Counterparty accepted dispute and will adjust"),

    /** Dispute rejected - we will adjust */
    DISPUTE_REJECTED("爭議駁回", "Dispute rejected, adjusted our records"),

    /** Manual adjustment after investigation */
    MANUAL_ADJUSTMENT("人工調整", "Manual adjustment after investigation"),

    /** Write off small amount */
    WRITE_OFF("沖銷", "Written off due to immateriality"),

    /** Reversed transaction */
    REVERSAL("沖正", "Transaction reversed"),

    /** Duplicate removed */
    DUPLICATE_REMOVED("移除重複", "Duplicate record removed"),

    /** Missing transaction added */
    MISSING_ADDED("補登交易", "Missing transaction added to system"),

    /** False alarm - no actual discrepancy */
    FALSE_ALARM("誤報", "No actual discrepancy found"),

    /** System error corrected */
    SYSTEM_ERROR_FIXED("系統錯誤修正", "System error identified and corrected"),

    /** Timing difference - resolved by next day settlement */
    TIMING_RESOLVED("時間差異解決", "Timing difference resolved in subsequent settlement"),

    /** Escalated to management */
    ESCALATED("上報管理層", "Escalated to management for decision"),

    /** Other resolution */
    OTHER("其他", "Other resolution action");

    private final String chineseName;
    private final String description;

    ResolutionAction(String chineseName, String description) {
        this.chineseName = chineseName;
        this.description = description;
    }

    public String getChineseName() {
        return chineseName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this action results in a financial adjustment.
     */
    public boolean isFinancialAdjustment() {
        return this == ADJUST_INTERNAL ||
               this == MANUAL_ADJUSTMENT ||
               this == WRITE_OFF ||
               this == REVERSAL ||
               this == MISSING_ADDED;
    }

    /**
     * Check if this action requires approval.
     */
    public boolean requiresApproval() {
        return this == ADJUST_INTERNAL ||
               this == MANUAL_ADJUSTMENT ||
               this == WRITE_OFF ||
               this == MISSING_ADDED;
    }
}
