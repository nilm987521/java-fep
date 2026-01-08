package com.fep.security.mac;

/**
 * MAC (Message Authentication Code) algorithms.
 */
public enum MacAlgorithm {

    /** ISO 9797-1 Algorithm 1 (DES-CBC) */
    ISO_9797_ALG1("ISO 9797-1 Algorithm 1", 8),

    /** ISO 9797-1 Algorithm 3 (Retail MAC) */
    ISO_9797_ALG3("ISO 9797-1 Algorithm 3", 8),

    /** ANSI X9.19 (Triple DES MAC) */
    ANSI_X9_19("ANSI X9.19", 8),

    /** CMAC using AES */
    AES_CMAC("AES-CMAC", 16),

    /** HMAC-SHA256 */
    HMAC_SHA256("HMAC-SHA256", 32);

    private final String description;
    private final int outputLength;

    MacAlgorithm(String description, int outputLength) {
        this.description = description;
        this.outputLength = outputLength;
    }

    public String getDescription() {
        return description;
    }

    public int getOutputLength() {
        return outputLength;
    }
}
