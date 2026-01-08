package com.fep.transaction.risk.fraud;

/**
 * Types of fraud detection rules.
 */
public enum FraudRuleType {

    /** Velocity check - transaction frequency */
    VELOCITY("Velocity Check", "VEL"),

    /** Amount threshold check */
    AMOUNT_THRESHOLD("Amount Threshold", "AMT"),

    /** Geographic anomaly detection */
    GEO_ANOMALY("Geographic Anomaly", "GEO"),

    /** Time-based pattern check */
    TIME_PATTERN("Time Pattern", "TIME"),

    /** Device fingerprint check */
    DEVICE_CHECK("Device Check", "DEV"),

    /** Behavioral pattern analysis */
    BEHAVIORAL("Behavioral Pattern", "BEH"),

    /** Cross-channel analysis */
    CROSS_CHANNEL("Cross Channel", "XCHANN"),

    /** Merchant category risk */
    MERCHANT_RISK("Merchant Risk", "MERCH"),

    /** Card not present risk */
    CNP_RISK("Card Not Present Risk", "CNP"),

    /** First-time transaction */
    FIRST_TIME("First Time Transaction", "FIRST"),

    /** Sequential card number detection */
    SEQUENTIAL_CARD("Sequential Card", "SEQ"),

    /** Dormant account activity */
    DORMANT_ACCOUNT("Dormant Account", "DORM"),

    /** High-risk country */
    HIGH_RISK_COUNTRY("High Risk Country", "HRC"),

    /** Custom rule */
    CUSTOM("Custom Rule", "CUST");

    private final String description;
    private final String code;

    FraudRuleType(String description, String code) {
        this.description = description;
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public String getCode() {
        return code;
    }
}
