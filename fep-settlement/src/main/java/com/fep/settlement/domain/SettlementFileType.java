package com.fep.settlement.domain;

/**
 * Types of settlement files from FISC.
 */
public enum SettlementFileType {

    /** Daily settlement file (日清算檔) */
    DAILY_SETTLEMENT("DS", "日清算檔", "每日營業結束後產生"),

    /** ATM transaction file (ATM交易檔) */
    ATM_TRANSACTION("AT", "ATM交易檔", "ATM跨行交易明細"),

    /** POS transaction file (POS交易檔) */
    POS_TRANSACTION("PS", "POS交易檔", "特約商店刷卡交易明細"),

    /** Bill payment file (代收代付檔) */
    BILL_PAYMENT("BP", "代收代付檔", "代收代付交易明細"),

    /** Transfer file (轉帳檔) */
    TRANSFER("TF", "轉帳檔", "跨行轉帳交易明細"),

    /** Fee file (手續費檔) */
    FEE("FE", "手續費檔", "手續費計算明細"),

    /** Clearing file (清算檔) */
    CLEARING("CL", "清算檔", "清算金額彙總"),

    /** Reversal file (沖正檔) */
    REVERSAL("RV", "沖正檔", "沖正交易明細"),

    /** Exception file (異常檔) */
    EXCEPTION("EX", "異常檔", "異常交易通知"),

    /** Reconciliation result file (對帳結果檔) */
    RECONCILIATION_RESULT("RR", "對帳結果檔", "對帳結果回報"),

    /** Monthly summary file (月結檔) */
    MONTHLY_SUMMARY("MS", "月結檔", "月份交易彙總");

    private final String code;
    private final String chineseName;
    private final String description;

    SettlementFileType(String code, String chineseName, String description) {
        this.code = code;
        this.chineseName = chineseName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getChineseName() {
        return chineseName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get file type by code.
     */
    public static SettlementFileType fromCode(String code) {
        for (SettlementFileType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown settlement file type code: " + code);
    }

    /**
     * Check if this is a transaction detail file.
     */
    public boolean isDetailFile() {
        return this == ATM_TRANSACTION ||
               this == POS_TRANSACTION ||
               this == BILL_PAYMENT ||
               this == TRANSFER;
    }

    /**
     * Check if this is a summary file.
     */
    public boolean isSummaryFile() {
        return this == DAILY_SETTLEMENT ||
               this == CLEARING ||
               this == MONTHLY_SUMMARY;
    }
}
