package com.fep.jmeter.sampler;

/**
 * Transaction types for FISC Sampler.
 *
 * <p>Defines the types of FISC transactions that can be performed.
 */
public enum FiscTransactionType {

    /** Cash withdrawal (提款). */
    WITHDRAWAL,

    /** Fund transfer (轉帳). */
    TRANSFER,

    /** Balance inquiry (餘額查詢). */
    BALANCE_INQUIRY,

    /** Bill payment (繳費). */
    BILL_PAYMENT,

    /** Sign-on request. */
    SIGN_ON,

    /** Sign-off request. */
    SIGN_OFF,

    /** Echo test for connectivity. */
    ECHO_TEST;

    /**
     * Get all type names as String array for JMeter GUI TAGS.
     */
    public static String[] names() {
        FiscTransactionType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    /**
     * Parse type from String, returning ECHO_TEST as default if invalid.
     */
    public static FiscTransactionType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return ECHO_TEST;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ECHO_TEST;
        }
    }
}
