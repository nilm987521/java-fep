package com.fep.transaction.limit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Types of transaction limits.
 */
@Getter
@RequiredArgsConstructor
public enum LimitType {

    /** Single transaction limit */
    SINGLE_TRANSACTION("單筆限額"),

    /** Daily cumulative limit */
    DAILY_CUMULATIVE("日累計限額"),

    /** Monthly cumulative limit */
    MONTHLY_CUMULATIVE("月累計限額"),

    /** Daily transaction count limit */
    DAILY_COUNT("日交易次數限額"),

    /** Non-designated transfer limit */
    NON_DESIGNATED_TRANSFER("非約定轉帳限額");

    private final String chineseDescription;
}
