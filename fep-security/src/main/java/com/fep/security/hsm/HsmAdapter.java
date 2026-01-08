package com.fep.security.hsm;

/**
 * Interface for HSM adapters.
 * Each HSM vendor has its own adapter implementation.
 */
public interface HsmAdapter {

    /**
     * Gets the HSM vendor.
     */
    HsmVendor getVendor();

    /**
     * Connects to the HSM.
     */
    void connect() throws HsmException;

    /**
     * Disconnects from the HSM.
     */
    void disconnect();

    /**
     * Checks if connected to HSM.
     */
    boolean isConnected();

    /**
     * Executes an HSM command.
     */
    HsmResponse execute(HsmRequest request) throws HsmException;

    /**
     * Generates a key in HSM.
     */
    HsmResponse generateKey(String keyType, String keyId) throws HsmException;

    /**
     * Imports a key to HSM (encrypted under LMK).
     */
    HsmResponse importKey(String keyType, byte[] encryptedKey, String keyId) throws HsmException;

    /**
     * Exports a key from HSM (encrypted under specified KEK).
     */
    HsmResponse exportKey(String keyId, String kekId) throws HsmException;

    /**
     * Translates a PIN block from one key to another.
     */
    HsmResponse translatePinBlock(byte[] pinBlock, String sourceKeyId, String destKeyId,
                                   String sourceFormat, String destFormat, String pan) throws HsmException;

    /**
     * Generates MAC using HSM.
     */
    HsmResponse generateMac(byte[] data, String keyId, String algorithm) throws HsmException;

    /**
     * Verifies MAC using HSM.
     */
    HsmResponse verifyMac(byte[] data, byte[] mac, String keyId, String algorithm) throws HsmException;

    /**
     * Encrypts data using HSM.
     */
    HsmResponse encryptData(byte[] data, String keyId, String algorithm) throws HsmException;

    /**
     * Decrypts data using HSM.
     */
    HsmResponse decryptData(byte[] encryptedData, String keyId, String algorithm) throws HsmException;

    /**
     * Performs HSM diagnostics.
     */
    HsmResponse getDiagnostics() throws HsmException;

    /**
     * Gets HSM status.
     */
    HsmResponse getStatus() throws HsmException;
}
