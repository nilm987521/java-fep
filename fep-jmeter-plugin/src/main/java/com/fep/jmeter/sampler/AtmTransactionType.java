package com.fep.jmeter.sampler;

/**
 * Transaction types for ATM Simulator.
 *
 * <p>Defines the types of ATM transactions that can be simulated.
 * Each type has associated MTI and default processing code.
 *
 * <p>For full customization, use CUSTOM type with JSON message template.
 */
public enum AtmTransactionType {

    /** Cash withdrawal (提款). MTI: 0200, Processing Code: 010000 */
    WITHDRAWAL("0200", "010000", "Cash Withdrawal"),

    /** Fund transfer (轉帳). MTI: 0200, Processing Code: 400000 */
    TRANSFER("0200", "400000", "Fund Transfer"),

    /** Balance inquiry (餘額查詢). MTI: 0200, Processing Code: 310000 */
    BALANCE_INQUIRY("0200", "310000", "Balance Inquiry"),

    /** PIN change (變更密碼). MTI: 0200, Processing Code: 920000 */
    PIN_CHANGE("0200", "920000", "PIN Change"),

    /** Cash deposit (存款). MTI: 0200, Processing Code: 210000 */
    DEPOSIT("0200", "210000", "Cash Deposit"),

    /** Bill payment (繳費). MTI: 0200, Processing Code: 500000 */
    BILL_PAYMENT("0200", "500000", "Bill Payment"),

    /** Mini statement (交易明細查詢). MTI: 0200, Processing Code: 380000 */
    MINI_STATEMENT("0200", "380000", "Mini Statement"),

    /** Cardless withdrawal request (無卡提款預約). MTI: 0200, Processing Code: 010001 */
    CARDLESS_WITHDRAWAL("0200", "010001", "Cardless Withdrawal"),

    /** Authorization request (授權). MTI: 0100, Processing Code: 000000 */
    AUTHORIZATION("0100", "000000", "Authorization"),

    /** Reversal (沖正). MTI: 0400, Processing Code: varies */
    REVERSAL("0400", "010000", "Reversal"),

    /** Sign-on (簽到). MTI: 0800, Network Code: 001 */
    SIGN_ON("0800", "920000", "Sign On"),

    /** Sign-off (簽退). MTI: 0800, Network Code: 002 */
    SIGN_OFF("0800", "920000", "Sign Off"),

    /** Echo test (連線測試). MTI: 0800, Network Code: 301 */
    ECHO_TEST("0800", "990000", "Echo Test"),

    /** Key exchange (換鑰). MTI: 0800, Network Code: 101 */
    KEY_EXCHANGE("0800", "960000", "Key Exchange"),

    /** Custom transaction with full field control (自訂電文). */
    CUSTOM("0200", "000000", "Custom Message");

    private final String defaultMti;
    private final String defaultProcessingCode;
    private final String displayName;

    AtmTransactionType(String mti, String processingCode, String displayName) {
        this.defaultMti = mti;
        this.defaultProcessingCode = processingCode;
        this.displayName = displayName;
    }

    /**
     * Get the default MTI for this transaction type.
     */
    public String getDefaultMti() {
        return defaultMti;
    }

    /**
     * Get the default processing code (Field 3) for this transaction type.
     */
    public String getDefaultProcessingCode() {
        return defaultProcessingCode;
    }

    /**
     * Get the human-readable display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this is a network management message.
     */
    public boolean isNetworkManagement() {
        return this == SIGN_ON || this == SIGN_OFF || this == ECHO_TEST || this == KEY_EXCHANGE;
    }

    /**
     * Check if this is a reversal message.
     */
    public boolean isReversal() {
        return this == REVERSAL;
    }

    /**
     * Check if this is a custom message type.
     */
    public boolean isCustom() {
        return this == CUSTOM;
    }

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
     * Get display names for JMeter GUI (more readable).
     */
    public static String[] displayNames() {
        AtmTransactionType[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name() + " - " + values[i].displayName;
        }
        return names;
    }

    /**
     * Parse type from String, returning ECHO_TEST as default if invalid.
     */
    public static AtmTransactionType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return ECHO_TEST;
        }
        // Handle display name format "TYPE - Display Name"
        if (value.contains(" - ")) {
            value = value.split(" - ")[0].trim();
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ECHO_TEST;
        }
    }
}
