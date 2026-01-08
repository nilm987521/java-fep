package com.fep.security.key;

/**
 * Key types used in the banking security hierarchy.
 */
public enum KeyType {

    /** Master Key - highest level key stored in HSM */
    MK("Master Key", "MK", 24),

    /** Zone Master Key - used for key exchange between institutions */
    ZMK("Zone Master Key", "ZMK", 24),

    /** Terminal Master Key - used for ATM/POS terminal key derivation */
    TMK("Terminal Master Key", "TMK", 24),

    /** PIN Encryption Key - used for PIN block encryption */
    PEK("PIN Encryption Key", "PEK", 24),

    /** MAC Key - used for MAC calculation */
    MAK("MAC Key", "MAK", 24),

    /** Data Encryption Key - used for data encryption */
    DEK("Data Encryption Key", "DEK", 24),

    /** Key Encryption Key - used for encrypting other keys */
    KEK("Key Encryption Key", "KEK", 24),

    /** Session Key - temporary key for a session */
    SK("Session Key", "SK", 24),

    /** Base Derivation Key - used for key derivation */
    BDK("Base Derivation Key", "BDK", 24),

    /** Initial PIN Encryption Key - for initial PIN */
    IPEK("Initial PIN Encryption Key", "IPEK", 24);

    private final String description;
    private final String shortName;
    private final int keyLength;

    KeyType(String description, String shortName, int keyLength) {
        this.description = description;
        this.shortName = shortName;
        this.keyLength = keyLength;
    }

    public String getDescription() {
        return description;
    }

    public String getShortName() {
        return shortName;
    }

    public int getKeyLength() {
        return keyLength;
    }
}
