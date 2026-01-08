package com.fep.transaction.risk.alert;

/**
 * Types of alerts.
 */
public enum AlertType {

    // Fraud alerts
    FRAUD_SUSPECTED("Suspected Fraud", "FRD"),
    FRAUD_CONFIRMED("Confirmed Fraud", "FRDC"),
    BLACKLIST_HIT("Blacklist Match", "BLK"),

    // Transaction alerts
    HIGH_VALUE_TRANSACTION("High Value Transaction", "HVT"),
    UNUSUAL_PATTERN("Unusual Transaction Pattern", "UTP"),
    VELOCITY_BREACH("Velocity Limit Breach", "VEL"),
    LIMIT_EXCEEDED("Limit Exceeded", "LMT"),

    // System alerts
    SYSTEM_ERROR("System Error", "ERR"),
    CONNECTION_LOST("Connection Lost", "CONN"),
    HIGH_LATENCY("High Latency", "LAT"),
    CIRCUIT_BREAKER_OPEN("Circuit Breaker Open", "CBR"),
    RATE_LIMIT_EXCEEDED("Rate Limit Exceeded", "RLM"),

    // Security alerts
    UNAUTHORIZED_ACCESS("Unauthorized Access Attempt", "UAA"),
    MULTIPLE_FAILED_AUTH("Multiple Failed Authentications", "MFA"),
    KEY_EXPIRY("Key Expiring Soon", "KEY"),

    // Operational alerts
    BATCH_FAILURE("Batch Processing Failure", "BTF"),
    RECONCILIATION_MISMATCH("Reconciliation Mismatch", "RCM"),
    THRESHOLD_BREACH("Threshold Breach", "THR"),

    // Other
    CUSTOM("Custom Alert", "CUS");

    private final String description;
    private final String code;

    AlertType(String description, String code) {
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
