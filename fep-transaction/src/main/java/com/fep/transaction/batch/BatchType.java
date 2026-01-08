package com.fep.transaction.batch;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Types of batch transactions.
 */
@Getter
@RequiredArgsConstructor
public enum BatchType {

    /** Bulk transfer (salary, pension, etc.) */
    BULK_TRANSFER("批次轉帳"),

    /** Bill payment collection */
    BILL_PAYMENT("代收代付"),

    /** Tax payment collection */
    TAX_PAYMENT("稅款代收"),

    /** Utility payment (water, electricity, gas) */
    UTILITY_PAYMENT("公用事業代收"),

    /** Insurance premium collection */
    INSURANCE_PAYMENT("保費代收"),

    /** Tuition fee collection */
    TUITION_PAYMENT("學費代收"),

    /** Credit card payment */
    CREDIT_CARD_PAYMENT("信用卡繳費"),

    /** Loan repayment */
    LOAN_REPAYMENT("貸款還款"),

    /** Dividend distribution */
    DIVIDEND_PAYMENT("股利發放"),

    /** Government subsidy distribution */
    SUBSIDY_PAYMENT("補助金發放"),

    /** General batch processing */
    GENERAL("一般批次");

    private final String chineseDescription;
}
