package com.fep.transaction.risk.blacklist;

/**
 * Reasons for blacklisting.
 */
public enum BlacklistReason {

    // Card reasons
    LOST("Lost Card", "01"),
    STOLEN("Stolen Card", "02"),
    COUNTERFEIT("Counterfeit Card", "03"),
    EXPIRED_CARD("Expired Card", "04"),
    CLOSED_ACCOUNT("Account Closed", "05"),

    // Fraud reasons
    FRAUD_CONFIRMED("Confirmed Fraud", "10"),
    FRAUD_SUSPECTED("Suspected Fraud", "11"),
    UNUSUAL_ACTIVITY("Unusual Activity", "12"),
    IDENTITY_THEFT("Identity Theft", "13"),
    MONEY_LAUNDERING("Money Laundering Suspected", "14"),

    // Compliance reasons
    AML_VIOLATION("AML Violation", "20"),
    REGULATORY_ACTION("Regulatory Action", "21"),
    LEGAL_ORDER("Legal/Court Order", "22"),
    SANCTIONS("Sanctions List", "23"),

    // Merchant reasons
    MERCHANT_FRAUD("Merchant Fraud", "30"),
    EXCESSIVE_CHARGEBACKS("Excessive Chargebacks", "31"),
    PCI_NON_COMPLIANCE("PCI Non-Compliance", "32"),
    CONTRACT_VIOLATION("Contract Violation", "33"),

    // Technical reasons
    SECURITY_BREACH("Security Breach", "40"),
    COMPROMISED("Compromised", "41"),
    INVALID_DATA("Invalid/Fake Data", "42"),

    // Other
    MANUAL_BLOCK("Manual Block by Staff", "90"),
    TEMPORARY_HOLD("Temporary Hold", "91"),
    OTHER("Other Reason", "99");

    private final String description;
    private final String code;

    BlacklistReason(String description, String code) {
        this.description = description;
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public String getCode() {
        return code;
    }

    public static BlacklistReason fromCode(String code) {
        for (BlacklistReason reason : values()) {
            if (reason.code.equals(code)) {
                return reason;
            }
        }
        return OTHER;
    }
}
