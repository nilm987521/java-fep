package com.fep.transaction.report;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Types of transaction reports.
 */
@Getter
@RequiredArgsConstructor
public enum ReportType {

    /** Daily transaction summary */
    DAILY_SUMMARY("日報表", "Daily Summary"),

    /** Monthly transaction summary */
    MONTHLY_SUMMARY("月報表", "Monthly Summary"),

    /** Transaction detail report */
    TRANSACTION_DETAIL("交易明細", "Transaction Detail"),

    /** Success rate analysis */
    SUCCESS_RATE("成功率分析", "Success Rate Analysis"),

    /** Channel distribution report */
    CHANNEL_DISTRIBUTION("通路分佈", "Channel Distribution"),

    /** Transaction type distribution */
    TYPE_DISTRIBUTION("交易類型分佈", "Type Distribution"),

    /** Peak hour analysis */
    PEAK_HOUR_ANALYSIS("尖峰時段分析", "Peak Hour Analysis"),

    /** Response time analysis */
    RESPONSE_TIME("回應時間分析", "Response Time Analysis"),

    /** Error analysis report */
    ERROR_ANALYSIS("錯誤分析", "Error Analysis"),

    /** Settlement report */
    SETTLEMENT("清算報表", "Settlement Report"),

    /** Reconciliation report */
    RECONCILIATION("對帳報表", "Reconciliation Report"),

    /** Regulatory compliance report */
    REGULATORY("法遵報表", "Regulatory Report");

    private final String chineseName;
    private final String englishName;

    /**
     * Gets display name based on locale.
     */
    public String getDisplayName(boolean chinese) {
        return chinese ? chineseName : englishName;
    }
}
