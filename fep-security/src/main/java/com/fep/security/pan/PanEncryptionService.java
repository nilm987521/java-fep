package com.fep.security.pan;

import com.fep.security.crypto.CryptoException;
import com.fep.security.crypto.CryptoService;
import com.fep.security.key.KeyManager;
import com.fep.security.key.KeyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Service for PAN (Primary Account Number) encryption and decryption.
 * Implements PCI-DSS compliant encryption for sensitive card data.
 *
 * <p>Uses 3DES (Triple DES) encryption with ISO 9797-1 padding method 2.
 * Encrypted PAN is stored as hex string in the database.</p>
 */
@Slf4j
@Service
public class PanEncryptionService {

    private static final int TDES_BLOCK_SIZE = 8;
    private static final String PAN_KEY_ALIAS = "PAN_DEK";

    private final CryptoService cryptoService;
    private final KeyManager keyManager;

    public PanEncryptionService(CryptoService cryptoService, KeyManager keyManager) {
        this.cryptoService = cryptoService;
        this.keyManager = keyManager;
    }

    /**
     * Encrypts a PAN value.
     *
     * @param pan the plaintext PAN (13-19 digits)
     * @return encrypted PAN as hex string, or null if input is null
     * @throws CryptoException if encryption fails
     */
    public String encrypt(String pan) {
        if (pan == null || pan.isEmpty()) {
            return null;
        }

        validatePan(pan);

        byte[] key = getOrCreatePanKey();
        byte[] panBytes = pan.getBytes(StandardCharsets.UTF_8);
        byte[] padded = cryptoService.padIso9797Method2(panBytes, TDES_BLOCK_SIZE);
        byte[] encrypted = cryptoService.encryptTdes(key, padded);

        return cryptoService.bytesToHex(encrypted);
    }

    /**
     * Decrypts an encrypted PAN value.
     *
     * @param encryptedPan the encrypted PAN as hex string
     * @return the plaintext PAN, or null if input is null
     * @throws CryptoException if decryption fails
     */
    public String decrypt(String encryptedPan) {
        if (encryptedPan == null || encryptedPan.isEmpty()) {
            return null;
        }

        byte[] key = getOrCreatePanKey();
        byte[] encrypted = cryptoService.hexToBytes(encryptedPan);
        byte[] decrypted = cryptoService.decryptTdes(key, encrypted);
        byte[] unpadded = cryptoService.unpadIso9797Method2(decrypted);

        return new String(unpadded, StandardCharsets.UTF_8);
    }

    /**
     * Creates a masked version of the PAN for display.
     * Format: First 6 digits + asterisks + last 4 digits
     * Example: 4123 **** **** 5678
     *
     * @param pan the plaintext PAN
     * @return masked PAN, or null if input is null
     */
    public String mask(String pan) {
        if (pan == null || pan.isEmpty()) {
            return null;
        }

        String digitsOnly = pan.replaceAll("\\s", "");
        if (digitsOnly.length() < 13) {
            // Invalid PAN length, return fully masked
            return "*".repeat(digitsOnly.length());
        }

        String first6 = digitsOnly.substring(0, 6);
        String last4 = digitsOnly.substring(digitsOnly.length() - 4);
        int middleLength = digitsOnly.length() - 10;

        return first6 + "*".repeat(middleLength) + last4;
    }

    /**
     * Calculates SHA-256 hash of PAN for indexing purposes.
     * Hash allows searching without exposing the actual PAN.
     *
     * @param pan the plaintext PAN
     * @return SHA-256 hash as hex string, or null if input is null
     */
    public String hash(String pan) {
        if (pan == null || pan.isEmpty()) {
            return null;
        }

        String digitsOnly = pan.replaceAll("\\s", "");
        byte[] hashBytes = cryptoService.sha256(digitsOnly.getBytes(StandardCharsets.UTF_8));
        return cryptoService.bytesToHex(hashBytes);
    }

    /**
     * Validates PAN format.
     *
     * @param pan the PAN to validate
     * @throws IllegalArgumentException if PAN format is invalid
     */
    private void validatePan(String pan) {
        String digitsOnly = pan.replaceAll("\\s", "");

        if (!digitsOnly.matches("\\d+")) {
            throw new IllegalArgumentException("PAN must contain only digits");
        }

        if (digitsOnly.length() < 13 || digitsOnly.length() > 19) {
            throw new IllegalArgumentException("PAN must be between 13 and 19 digits");
        }
    }

    /**
     * Gets or creates the PAN encryption key.
     * In production, this key should be pre-loaded from HSM during startup.
     *
     * @return the 3DES key (24 bytes) for PAN encryption
     */
    private byte[] getOrCreatePanKey() {
        byte[] key = keyManager.getKeyByAlias(PAN_KEY_ALIAS);

        if (key == null) {
            log.info("PAN encryption key not found, generating new key with alias: {}", PAN_KEY_ALIAS);
            // Generate 3DES key (24 bytes)
            key = cryptoService.generateTdesKey();
            keyManager.importKey(KeyType.DEK, PAN_KEY_ALIAS, key);
            keyManager.setCurrentKey(KeyType.DEK, keyManager.getKeyInfoByAlias(PAN_KEY_ALIAS).getKeyId());
        }

        return key;
    }

    /**
     * Checks if a string is an encrypted PAN (hex format).
     *
     * @param value the value to check
     * @return true if the value appears to be encrypted PAN
     */
    public boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        // Encrypted PAN should be hex string with even length (at least 16 chars for 3DES min block)
        // and contains only hex characters (not just digits like a raw PAN)
        return value.matches("[0-9A-Fa-f]+")
            && value.length() >= 16
            && value.length() % 2 == 0
            && value.matches(".*[A-Fa-f].*"); // Contains at least one letter (distinguishes from plain PAN)
    }
}
