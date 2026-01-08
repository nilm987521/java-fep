package com.fep.transaction.batch;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Status of batch processing.
 */
@Getter
@RequiredArgsConstructor
public enum BatchStatus {

    /** Batch received, pending processing */
    PENDING("待處理"),

    /** Batch validated, ready to process */
    VALIDATED("已驗證"),

    /** Batch is being processed */
    PROCESSING("處理中"),

    /** Batch processing partially complete */
    PARTIAL("部分完成"),

    /** Batch processing completed successfully */
    COMPLETED("完成"),

    /** Batch processing completed with errors */
    COMPLETED_WITH_ERRORS("完成但有錯誤"),

    /** Batch processing failed */
    FAILED("失敗"),

    /** Batch was cancelled */
    CANCELLED("已取消"),

    /** Batch scheduled for later processing */
    SCHEDULED("已排程");

    private final String chineseDescription;

    /**
     * Checks if this status is terminal.
     */
    public boolean isTerminal() {
        return this == COMPLETED ||
               this == COMPLETED_WITH_ERRORS ||
               this == FAILED ||
               this == CANCELLED;
    }

    /**
     * Checks if processing can continue from this status.
     */
    public boolean canProcess() {
        return this == PENDING ||
               this == VALIDATED ||
               this == SCHEDULED;
    }
}
