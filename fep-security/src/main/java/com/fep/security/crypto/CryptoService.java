package com.fep.security.crypto;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Cryptographic service providing encryption, decryption, and hashing operations.
 */
@Service
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    // Zero IV for CBC mode (common in banking)
    private static final byte[] ZERO_IV_8 = new byte[8];
    private static final byte[] ZERO_IV_16 = new byte[16];

    /**
     * Encrypts data using 3DES with CBC mode.
     *
     * @param key  24-byte 3DES key
     * @param data data to encrypt (must be multiple of 8 bytes)
     * @return encrypted data
     */
    public byte[] encryptTdes(byte[] key, byte[] data) {
        return encryptTdes(key, data, ZERO_IV_8);
    }

    /**
     * Encrypts data using 3DES with CBC mode and custom IV.
     */
    public byte[] encryptTdes(byte[] key, byte[] data, byte[] iv) {
        try {
            validateKeyLength(key, 24, "3DES");
            byte[] adjustedKey = adjustTdesKey(key);

            DESedeKeySpec keySpec = new DESedeKeySpec(adjustedKey);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DESede");
            SecretKey secretKey = keyFactory.generateSecret(keySpec);

            Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new CryptoException("3DES encryption failed", e);
        }
    }

    /**
     * Decrypts data using 3DES with CBC mode.
     */
    public byte[] decryptTdes(byte[] key, byte[] encryptedData) {
        return decryptTdes(key, encryptedData, ZERO_IV_8);
    }

    /**
     * Decrypts data using 3DES with CBC mode and custom IV.
     */
    public byte[] decryptTdes(byte[] key, byte[] encryptedData, byte[] iv) {
        try {
            validateKeyLength(key, 24, "3DES");
            byte[] adjustedKey = adjustTdesKey(key);

            DESedeKeySpec keySpec = new DESedeKeySpec(adjustedKey);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DESede");
            SecretKey secretKey = keyFactory.generateSecret(keySpec);

            Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            throw new CryptoException("3DES decryption failed", e);
        }
    }

    /**
     * Encrypts data using AES with CBC mode.
     *
     * @param key  16 or 32 byte AES key
     * @param data data to encrypt
     * @return encrypted data
     */
    public byte[] encryptAes(byte[] key, byte[] data) {
        return encryptAes(key, data, ZERO_IV_16);
    }

    /**
     * Encrypts data using AES with CBC mode and custom IV.
     */
    public byte[] encryptAes(byte[] key, byte[] data, byte[] iv) {
        try {
            validateAesKeyLength(key);

            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new CryptoException("AES encryption failed", e);
        }
    }

    /**
     * Decrypts data using AES with CBC mode.
     */
    public byte[] decryptAes(byte[] key, byte[] encryptedData) {
        return decryptAes(key, encryptedData, ZERO_IV_16);
    }

    /**
     * Decrypts data using AES with CBC mode and custom IV.
     */
    public byte[] decryptAes(byte[] key, byte[] encryptedData, byte[] iv) {
        try {
            validateAesKeyLength(key);

            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            throw new CryptoException("AES decryption failed", e);
        }
    }

    /**
     * Encrypts data using single DES (legacy support).
     */
    public byte[] encryptDes(byte[] key, byte[] data) {
        try {
            validateKeyLength(key, 8, "DES");

            SecretKeySpec keySpec = new SecretKeySpec(key, "DES");
            Cipher cipher = Cipher.getInstance("DES/CBC/NoPadding");
            IvParameterSpec ivSpec = new IvParameterSpec(ZERO_IV_8);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new CryptoException("DES encryption failed", e);
        }
    }

    /**
     * Decrypts data using single DES (legacy support).
     */
    public byte[] decryptDes(byte[] key, byte[] encryptedData) {
        try {
            validateKeyLength(key, 8, "DES");

            SecretKeySpec keySpec = new SecretKeySpec(key, "DES");
            Cipher cipher = Cipher.getInstance("DES/CBC/NoPadding");
            IvParameterSpec ivSpec = new IvParameterSpec(ZERO_IV_8);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            throw new CryptoException("DES decryption failed", e);
        }
    }

    /**
     * Calculates SHA-256 hash.
     */
    public byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new CryptoException("SHA-256 hash failed", e);
        }
    }

    /**
     * Calculates SHA-1 hash (legacy support).
     */
    public byte[] sha1(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return digest.digest(data);
        } catch (Exception e) {
            throw new CryptoException("SHA-1 hash failed", e);
        }
    }

    /**
     * Calculates MD5 hash (legacy support only).
     */
    public byte[] md5(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return digest.digest(data);
        } catch (Exception e) {
            throw new CryptoException("MD5 hash failed", e);
        }
    }

    /**
     * XORs two byte arrays.
     */
    public byte[] xor(byte[] a, byte[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Arrays must have equal length");
        }
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    /**
     * Generates random bytes.
     */
    public byte[] generateRandom(int length) {
        byte[] random = new byte[length];
        secureRandom.nextBytes(random);
        return random;
    }

    /**
     * Generates a random 3DES key.
     */
    public byte[] generateTdesKey() {
        byte[] key = generateRandom(24);
        // Ensure proper parity for each DES key component
        adjustParity(key, 0, 8);
        adjustParity(key, 8, 8);
        adjustParity(key, 16, 8);
        return key;
    }

    /**
     * Generates a random AES-128 key.
     */
    public byte[] generateAes128Key() {
        return generateRandom(16);
    }

    /**
     * Generates a random AES-256 key.
     */
    public byte[] generateAes256Key() {
        return generateRandom(32);
    }

    /**
     * Converts hex string to bytes.
     */
    public byte[] hexToBytes(String hex) {
        try {
            return Hex.decodeHex(hex);
        } catch (Exception e) {
            throw new CryptoException("Invalid hex string", e);
        }
    }

    /**
     * Converts bytes to hex string.
     */
    public String bytesToHex(byte[] bytes) {
        return Hex.encodeHexString(bytes).toUpperCase();
    }

    /**
     * Pads data to block size using ISO 9797-1 padding method 2.
     */
    public byte[] padIso9797Method2(byte[] data, int blockSize) {
        int padLength = blockSize - (data.length % blockSize);
        byte[] padded = new byte[data.length + padLength];
        System.arraycopy(data, 0, padded, 0, data.length);
        padded[data.length] = (byte) 0x80;
        // Remaining bytes are already 0x00
        return padded;
    }

    /**
     * Removes ISO 9797-1 padding method 2.
     */
    public byte[] unpadIso9797Method2(byte[] data) {
        int i = data.length - 1;
        while (i >= 0 && data[i] == 0x00) {
            i--;
        }
        if (i < 0 || data[i] != (byte) 0x80) {
            throw new CryptoException("Invalid ISO 9797-1 padding");
        }
        return Arrays.copyOf(data, i);
    }

    /**
     * Adjusts key parity for DES keys.
     */
    private void adjustParity(byte[] key, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            int bits = Integer.bitCount(key[i] & 0xFF);
            if (bits % 2 == 0) {
                key[i] ^= 0x01;
            }
        }
    }

    /**
     * Adjusts 3DES key to ensure it's a valid 24-byte key.
     */
    private byte[] adjustTdesKey(byte[] key) {
        if (key.length == 16) {
            // Double-length key: K1-K2-K1
            byte[] adjustedKey = new byte[24];
            System.arraycopy(key, 0, adjustedKey, 0, 16);
            System.arraycopy(key, 0, adjustedKey, 16, 8);
            return adjustedKey;
        }
        return key;
    }

    private void validateKeyLength(byte[] key, int expectedLength, String algorithm) {
        if (key == null || key.length != expectedLength) {
            throw new CryptoException(
                    String.format("%s key must be %d bytes, got %d",
                            algorithm, expectedLength, key == null ? 0 : key.length));
        }
    }

    private void validateAesKeyLength(byte[] key) {
        if (key == null || (key.length != 16 && key.length != 32)) {
            throw new CryptoException(
                    String.format("AES key must be 16 or 32 bytes, got %d",
                            key == null ? 0 : key.length));
        }
    }
}
