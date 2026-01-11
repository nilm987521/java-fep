package com.fep.jmeter.sampler;

/**
 * Transaction types for ATM Simulator Sampler.
 *
 * <p>Defines the types of ATM transactions that can be simulated.
 */
public enum AtmTransactionType {

    /** Cash withdrawal (提款). */
    WITHDRAWAL,

    /** Balance inquiry (餘額查詢). */
    BALANCE_INQUIRY,

    /** Fund transfer (轉帳). */
    TRANSFER,

    /** Cash deposit (存款). */
    DEPOSIT,

    /** PIN change (密碼變更). */
    PIN_CHANGE,

    /** Transaction history / Mini statement (交易明細). */
    MINI_STATEMENT,

    /** Cardless withdrawal (無卡提款). */
    CARDLESS_WITHDRAWAL;

    /**
     * Get all type names as String array for JMeter GUI TAGS.
     */
    public static String[] names() {
        AtmTransactionType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Parse type from String, returning BALANCE_INQUIRY as default if invalid.
     */
    public static AtmTransactionType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return BALANCE_INQUIRY;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BALANCE_INQUIRY;
        }
    }
}
