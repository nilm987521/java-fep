package com.fep.transaction.risk.fraud;

/**
 * Risk levels for fraud detection.
 */
public enum RiskLevel {

    /** No risk detected */
    NONE(0, "No Risk", "GREEN"),

    /** Low risk - proceed with caution */
    LOW(25, "Low Risk", "YELLOW"),

    /** Medium risk - additional verification recommended */
    MEDIUM(50, "Medium Risk", "ORANGE"),

    /** High risk - manual review required */
    HIGH(75, "High Risk", "RED"),

    /** Critical - block transaction */
    CRITICAL(100, "Critical Risk", "BLACK");

    private final int score;
    private final String description;
    private final String colorCode;

    RiskLevel(int score, String description, String colorCode) {
        this.score = score;
        this.description = description;
        this.colorCode = colorCode;
    }

    public int getScore() {
        return score;
    }

    public String getDescription() {
        return description;
    }

    public String getColorCode() {
        return colorCode;
    }

    /**
     * Gets risk level from score.
     */
    public static RiskLevel fromScore(int score) {
        if (score >= 90) return CRITICAL;
        if (score >= 70) return HIGH;
        if (score >= 40) return MEDIUM;
        if (score >= 20) return LOW;
        return NONE;
    }
}
