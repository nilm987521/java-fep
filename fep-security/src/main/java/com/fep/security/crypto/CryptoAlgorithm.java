package com.fep.security.crypto;

/**
 * Supported cryptographic algorithms.
 */
public enum CryptoAlgorithm {

    /** Triple DES (3DES) - current banking standard */
    TDES("DESede", "DESede/CBC/NoPadding", 24, 8),

    /** AES 128-bit */
    AES_128("AES", "AES/CBC/NoPadding", 16, 16),

    /** AES 256-bit */
    AES_256("AES", "AES/CBC/NoPadding", 32, 16),

    /** DES - legacy support */
    DES("DES", "DES/CBC/NoPadding", 8, 8);

    private final String algorithm;
    private final String transformation;
    private final int keyLength;
    private final int blockSize;

    CryptoAlgorithm(String algorithm, String transformation, int keyLength, int blockSize) {
        this.algorithm = algorithm;
        this.transformation = transformation;
        this.keyLength = keyLength;
        this.blockSize = blockSize;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getTransformation() {
        return transformation;
    }

    public int getKeyLength() {
        return keyLength;
    }

    public int getBlockSize() {
        return blockSize;
    }
}
