package com.fep.settlement.domain;

/**
 * Types of discrepancies found during reconciliation.
 */
public enum DiscrepancyType {

    /** Amount difference between settlement and internal */
    AMOUNT_MISMATCH("金額不符", "Settlement and internal amounts do not match"),

    /** Record exists in settlement file but not in internal system */
    MISSING_INTERNAL("本行查無", "Transaction not found in internal system"),

    /** Record exists in internal system but not in settlement file */
    MISSING_SETTLEMENT("對方無記錄", "Transaction not found in settlement file"),

    /** Duplicate record in settlement file */
    DUPLICATE_SETTLEMENT("對方重複", "Duplicate record in settlement file"),

    /** Duplicate record in internal system */
    DUPLICATE_INTERNAL("本行重複", "Duplicate transaction in internal system"),

    /** Fee amount mismatch */
    FEE_MISMATCH("手續費不符", "Fee amounts do not match"),

    /** Transaction date mismatch */
    DATE_MISMATCH("日期不符", "Transaction dates do not match"),

    /** Transaction type mismatch */
    TYPE_MISMATCH("類型不符", "Transaction types do not match"),

    /** Account number mismatch */
    ACCOUNT_MISMATCH("帳號不符", "Account numbers do not match"),

    /** Card number mismatch */
    CARD_MISMATCH("卡號不符", "Card numbers do not match"),

    /** Response code mismatch */
    RESPONSE_CODE_MISMATCH("回應碼不符", "Response codes do not match"),

    /** Partial match - some fields match but others don't */
    PARTIAL_MATCH("部分符合", "Some fields match but others don't"),

    /** Settlement file integrity error */
    FILE_INTEGRITY_ERROR("檔案完整性錯誤", "Settlement file failed integrity check"),

    /** Unknown discrepancy */
    UNKNOWN("未知差異", "Unknown discrepancy type");

    private final String chineseName;
    private final String description;

    DiscrepancyType(String chineseName, String description) {
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
     * Check if this is a critical discrepancy type.
     */
    public boolean isCritical() {
        return this == AMOUNT_MISMATCH ||
               this == MISSING_INTERNAL ||
               this == MISSING_SETTLEMENT ||
               this == FILE_INTEGRITY_ERROR;
    }

    /**
     * Get default priority for this type.
     */
    public DiscrepancyPriority getDefaultPriority() {
        return switch (this) {
            case AMOUNT_MISMATCH, FILE_INTEGRITY_ERROR -> DiscrepancyPriority.HIGH;
            case MISSING_INTERNAL, MISSING_SETTLEMENT, DUPLICATE_SETTLEMENT,
                 DUPLICATE_INTERNAL -> DiscrepancyPriority.HIGH;
            case FEE_MISMATCH, DATE_MISMATCH -> DiscrepancyPriority.MEDIUM;
            case TYPE_MISMATCH, ACCOUNT_MISMATCH, CARD_MISMATCH,
                 RESPONSE_CODE_MISMATCH, PARTIAL_MATCH -> DiscrepancyPriority.MEDIUM;
            case UNKNOWN -> DiscrepancyPriority.LOW;
        };
    }
}
