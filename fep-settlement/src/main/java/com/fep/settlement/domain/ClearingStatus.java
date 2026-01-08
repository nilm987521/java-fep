package com.fep.settlement.domain;

/**
 * Status of a clearing record.
 */
public enum ClearingStatus {

    /** Pending calculation */
    PENDING("待計算"),

    /** Calculated but not confirmed */
    CALCULATED("已計算"),

    /** Confirmed and ready for settlement */
    CONFIRMED("已確認"),

    /** Sent to clearing house */
    SUBMITTED("已送出"),

    /** Settlement in progress */
    SETTLING("清算中"),

    /** Settled successfully */
    SETTLED("已清算"),

    /** Settlement failed */
    FAILED("清算失敗"),

    /** Cancelled */
    CANCELLED("已取消");

    private final String chineseDescription;

    ClearingStatus(String chineseDescription) {
        this.chineseDescription = chineseDescription;
    }

    public String getChineseDescription() {
        return chineseDescription;
    }

    /**
     * Check if this status allows modification.
     */
    public boolean isModifiable() {
        return this == PENDING || this == CALCULATED;
    }

    /**
     * Check if this is a terminal status.
     */
    public boolean isTerminal() {
        return this == SETTLED || this == FAILED || this == CANCELLED;
    }

    /**
     * Check if settlement is in progress.
     */
    public boolean isInProgress() {
        return this == CONFIRMED || this == SUBMITTED || this == SETTLING;
    }
}
