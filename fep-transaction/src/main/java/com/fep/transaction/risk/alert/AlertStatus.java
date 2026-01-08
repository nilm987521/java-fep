package com.fep.transaction.risk.alert;

/**
 * Status of an alert.
 */
public enum AlertStatus {

    /** Alert has been created but not yet processed */
    NEW("New"),

    /** Alert has been sent/notified */
    SENT("Sent"),

    /** Alert has been acknowledged */
    ACKNOWLEDGED("Acknowledged"),

    /** Alert is being investigated */
    INVESTIGATING("Investigating"),

    /** Alert has been resolved */
    RESOLVED("Resolved"),

    /** Alert has been escalated */
    ESCALATED("Escalated"),

    /** Alert was dismissed as false positive */
    DISMISSED("Dismissed"),

    /** Alert delivery failed */
    FAILED("Failed");

    private final String description;

    AlertStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
