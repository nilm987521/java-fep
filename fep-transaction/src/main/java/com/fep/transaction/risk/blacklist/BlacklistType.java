package com.fep.transaction.risk.blacklist;

/**
 * Types of blacklist entries.
 */
public enum BlacklistType {

    /** Card blacklist - lost, stolen, or counterfeit cards */
    CARD("Card Blacklist", "CARD"),

    /** Account blacklist - suspicious or fraudulent accounts */
    ACCOUNT("Account Blacklist", "ACCT"),

    /** Merchant blacklist - fraudulent or non-compliant merchants */
    MERCHANT("Merchant Blacklist", "MERCH"),

    /** Terminal blacklist - compromised terminals */
    TERMINAL("Terminal Blacklist", "TERM"),

    /** IP address blacklist - suspicious IPs */
    IP_ADDRESS("IP Address Blacklist", "IP"),

    /** Device blacklist - suspicious devices */
    DEVICE("Device Blacklist", "DEV");

    private final String description;
    private final String code;

    BlacklistType(String description, String code) {
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
