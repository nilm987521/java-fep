package com.fep.settlement.domain;

/**
 * Processing status for settlement files.
 */
public enum FileProcessingStatus {

    /** File received but not yet processed */
    RECEIVED("已接收"),

    /** File is being validated */
    VALIDATING("驗證中"),

    /** Validation failed */
    VALIDATION_FAILED("驗證失敗"),

    /** File is being parsed */
    PARSING("解析中"),

    /** Parsing failed */
    PARSING_FAILED("解析失敗"),

    /** Reconciliation in progress */
    RECONCILING("對帳中"),

    /** Reconciliation completed */
    RECONCILED("對帳完成"),

    /** Reconciliation completed with discrepancies */
    RECONCILED_WITH_DISCREPANCIES("對帳完成(有差異)"),

    /** Discrepancies are being resolved */
    RESOLVING_DISCREPANCIES("處理差異中"),

    /** All processing completed */
    COMPLETED("處理完成"),

    /** Processing failed with error */
    FAILED("處理失敗"),

    /** File archived */
    ARCHIVED("已歸檔");

    private final String chineseDescription;

    FileProcessingStatus(String chineseDescription) {
        this.chineseDescription = chineseDescription;
    }

    public String getChineseDescription() {
        return chineseDescription;
    }

    /**
     * Check if processing is complete (success or failure).
     */
    public boolean isComplete() {
        return this == COMPLETED ||
               this == FAILED ||
               this == ARCHIVED;
    }

    /**
     * Check if processing is in progress.
     */
    public boolean isInProgress() {
        return this == VALIDATING ||
               this == PARSING ||
               this == RECONCILING ||
               this == RESOLVING_DISCREPANCIES;
    }

    /**
     * Check if there was an error.
     */
    public boolean isError() {
        return this == VALIDATION_FAILED ||
               this == PARSING_FAILED ||
               this == FAILED;
    }

    /**
     * Check if file can be reprocessed.
     */
    public boolean canReprocess() {
        return isError() || this == RECEIVED;
    }
}
