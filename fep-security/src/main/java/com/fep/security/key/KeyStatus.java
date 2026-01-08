package com.fep.security.key;

/**
 * Key lifecycle status.
 */
public enum KeyStatus {

    /** Key is generated but not yet activated */
    PENDING("Pending activation"),

    /** Key is active and can be used */
    ACTIVE("Active"),

    /** Key is suspended temporarily */
    SUSPENDED("Suspended"),

    /** Key is expired and should not be used for new operations */
    EXPIRED("Expired"),

    /** Key is revoked and must not be used */
    REVOKED("Revoked"),

    /** Key is being rotated (new key being prepared) */
    ROTATING("Rotation in progress"),

    /** Key is destroyed and no longer exists */
    DESTROYED("Destroyed");

    private final String description;

    KeyStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Checks if the key status allows encryption operations.
     */
    public boolean canEncrypt() {
        return this == ACTIVE;
    }

    /**
     * Checks if the key status allows decryption operations.
     * Expired keys can still decrypt for backward compatibility.
     */
    public boolean canDecrypt() {
        return this == ACTIVE || this == EXPIRED;
    }
}
