package com.fep.transaction.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Account types for processing code (positions 3-4 and 5-6 in field 3).
 */
@Getter
@RequiredArgsConstructor
public enum AccountType {

    DEFAULT("00", "Default/Unspecified", "預設"),
    SAVINGS("10", "Savings Account", "活期儲蓄"),
    CHECKING("20", "Checking Account", "支票帳戶"),
    CREDIT("30", "Credit Card Account", "信用卡帳戶"),
    UNIVERSAL("40", "Universal Account", "綜合帳戶"),
    INVESTMENT("50", "Investment Account", "投資帳戶"),
    MONEY_MARKET("60", "Money Market Account", "貨幣市場帳戶");

    private final String code;
    private final String description;
    private final String chineseDescription;

    private static final Map<String, AccountType> CODE_MAP = new HashMap<>();

    static {
        for (AccountType type : values()) {
            CODE_MAP.put(type.code, type);
        }
    }

    /**
     * Gets AccountType from code.
     *
     * @param code the 2-digit account type code
     * @return the AccountType, or null if not found
     */
    public static AccountType fromCode(String code) {
        return CODE_MAP.get(code);
    }

    /**
     * Extracts source account type from processing code.
     *
     * @param processingCode the 6-digit processing code
     * @return the source AccountType
     */
    public static AccountType getSourceAccountType(String processingCode) {
        if (processingCode == null || processingCode.length() < 4) {
            return DEFAULT;
        }
        AccountType type = fromCode(processingCode.substring(2, 4));
        return type != null ? type : DEFAULT;
    }

    /**
     * Extracts destination account type from processing code.
     *
     * @param processingCode the 6-digit processing code
     * @return the destination AccountType
     */
    public static AccountType getDestAccountType(String processingCode) {
        if (processingCode == null || processingCode.length() < 6) {
            return DEFAULT;
        }
        AccountType type = fromCode(processingCode.substring(4, 6));
        return type != null ? type : DEFAULT;
    }
}
