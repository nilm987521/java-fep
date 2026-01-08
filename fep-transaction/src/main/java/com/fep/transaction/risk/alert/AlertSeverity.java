package com.fep.transaction.risk.alert;

/**
 * Severity levels for alerts.
 */
public enum AlertSeverity {

    /** Informational - no action required */
    INFO(1, "Information", "INFO"),

    /** Low severity - monitor situation */
    LOW(2, "Low", "LOW"),

    /** Medium severity - attention required */
    MEDIUM(3, "Medium", "MED"),

    /** High severity - immediate attention */
    HIGH(4, "High", "HIGH"),

    /** Critical - immediate action required */
    CRITICAL(5, "Critical", "CRIT");

    private final int level;
    private final String description;
    private final String code;

    AlertSeverity(int level, String description, String code) {
        this.level = level;
        this.description = description;
        this.code = code;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    public String getCode() {
        return code;
    }

    public boolean isHigherThan(AlertSeverity other) {
        return this.level > other.level;
    }
}
