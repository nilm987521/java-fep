package com.fep.security.mac;

import com.fep.security.crypto.CryptoException;
import com.fep.security.crypto.CryptoService;
import com.fep.security.key.KeyManager;
import com.fep.security.key.KeyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

/**
 * Service for MAC (Message Authentication Code) calculation and verification.
 * Supports various MAC algorithms used in banking.
 */
@Service
public class MacService {

    private static final Logger log = LoggerFactory.getLogger(MacService.class);

    private final CryptoService cryptoService;
    private final KeyManager keyManager;

    public MacService(CryptoService cryptoService, KeyManager keyManager) {
        this.cryptoService = cryptoService;
        this.keyManager = keyManager;
    }

    /**
     * Calculates MAC using the specified algorithm and key.
     *
     * @param algorithm MAC algorithm
     * @param keyId     key identifier
     * @param data      data to authenticate
     * @return MAC value
     */
    public byte[] calculateMac(MacAlgorithm algorithm, String keyId, byte[] data) {
        byte[] key = keyManager.getKey(keyId);
        if (key == null) {
            throw new CryptoException("MAC key not found: " + keyId);
        }

        return switch (algorithm) {
            case ISO_9797_ALG1 -> calculateIso9797Alg1(key, data);
            case ISO_9797_ALG3 -> calculateIso9797Alg3(key, data);
            case ANSI_X9_19 -> calculateAnsiX919(key, data);
            case AES_CMAC -> calculateAesCmac(key, data);
            case HMAC_SHA256 -> calculateHmacSha256(key, data);
        };
    }

    /**
     * Calculates MAC using current MAK (MAC Key).
     */
    public byte[] calculateMac(MacAlgorithm algorithm, byte[] data) {
        String keyId = keyManager.getCurrentKeyId(KeyType.MAK);
        return calculateMac(algorithm, keyId, data);
    }

    /**
     * Verifies a MAC value.
     *
     * @param algorithm   MAC algorithm
     * @param keyId       key identifier
     * @param data        original data
     * @param expectedMac expected MAC value
     * @return true if MAC is valid
     */
    public boolean verifyMac(MacAlgorithm algorithm, String keyId, byte[] data, byte[] expectedMac) {
        byte[] calculatedMac = calculateMac(algorithm, keyId, data);
        return constantTimeEquals(calculatedMac, expectedMac);
    }

    /**
     * Verifies a MAC using current MAK.
     */
    public boolean verifyMac(MacAlgorithm algorithm, byte[] data, byte[] expectedMac) {
        String keyId = keyManager.getCurrentKeyId(KeyType.MAK);
        return verifyMac(algorithm, keyId, data, expectedMac);
    }

    /**
     * Calculates 4-byte MAC (commonly used in ATM transactions).
     */
    public byte[] calculate4ByteMac(MacAlgorithm algorithm, String keyId, byte[] data) {
        byte[] fullMac = calculateMac(algorithm, keyId, data);
        return Arrays.copyOf(fullMac, 4);
    }

    /**
     * ISO 9797-1 Algorithm 1 (DES-CBC MAC)
     *
     * Process: CBC encrypt all blocks, MAC = last block
     */
    private byte[] calculateIso9797Alg1(byte[] key, byte[] data) {
        // Use first 8 bytes of key for single DES
        byte[] desKey = Arrays.copyOf(key, 8);

        // Pad data to 8-byte boundary
        byte[] paddedData = padIso9797Method1(data);

        // CBC encrypt using zero IV
        byte[] iv = new byte[8];
        byte[] result = iv;

        for (int i = 0; i < paddedData.length; i += 8) {
            byte[] block = Arrays.copyOfRange(paddedData, i, i + 8);
            byte[] xored = cryptoService.xor(result, block);
            result = cryptoService.encryptDes(desKey, xored);
        }

        return result;
    }

    /**
     * ISO 9797-1 Algorithm 3 (Retail MAC / 3DES Final)
     *
     * Process: DES-CBC with K1, final block encrypted with full 3DES
     */
    private byte[] calculateIso9797Alg3(byte[] key, byte[] data) {
        // K1 = first 8 bytes, K2 = second 8 bytes
        byte[] k1 = Arrays.copyOf(key, 8);
        byte[] k2 = Arrays.copyOfRange(key, 8, 16);

        // Pad data
        byte[] paddedData = padIso9797Method1(data);

        // DES-CBC with K1
        byte[] iv = new byte[8];
        byte[] result = iv;

        for (int i = 0; i < paddedData.length; i += 8) {
            byte[] block = Arrays.copyOfRange(paddedData, i, i + 8);
            byte[] xored = cryptoService.xor(result, block);
            result = cryptoService.encryptDes(k1, xored);
        }

        // Final block: decrypt with K2, encrypt with K1
        byte[] decrypted = cryptoService.decryptDes(k2, result);
        result = cryptoService.encryptDes(k1, decrypted);

        return result;
    }

    /**
     * ANSI X9.19 MAC (Triple DES CBC-MAC)
     *
     * Process: Single DES CBC for all blocks except last,
     * then 3DES for final block
     */
    private byte[] calculateAnsiX919(byte[] key, byte[] data) {
        // Use full 24-byte key for 3DES, first 8 bytes for single DES
        byte[] desKey = Arrays.copyOf(key, 8);

        // Pad data
        byte[] paddedData = padIso9797Method2(data);

        // DES-CBC for all blocks
        byte[] iv = new byte[8];
        byte[] result = iv;

        for (int i = 0; i < paddedData.length; i += 8) {
            byte[] block = Arrays.copyOfRange(paddedData, i, i + 8);
            byte[] xored = cryptoService.xor(result, block);
            result = cryptoService.encryptDes(desKey, xored);
        }

        // Final encryption with 3DES
        result = cryptoService.encryptTdes(key, result);

        return result;
    }

    /**
     * AES-CMAC (Cipher-based MAC using AES)
     */
    private byte[] calculateAesCmac(byte[] key, byte[] data) {
        try {
            // Use first 16 bytes for AES-128 (key might be 24 bytes from 3DES store)
            byte[] aesKey = key.length > 16 ? Arrays.copyOf(key, 16) : key;

            // Generate subkeys
            byte[] zeroBlock = new byte[16];
            byte[] l = cryptoService.encryptAes(aesKey, zeroBlock);
            byte[] k1 = generateSubkey(l);
            byte[] k2 = generateSubkey(k1);

            // Determine if complete block
            int n = (data.length + 15) / 16;
            if (n == 0) n = 1;

            boolean complete = (data.length > 0) && (data.length % 16 == 0);

            // Pad if needed and XOR with subkey
            byte[] mn;
            if (complete) {
                mn = Arrays.copyOfRange(data, (n - 1) * 16, n * 16);
                mn = xor16(mn, k1);
            } else {
                byte[] padded = new byte[16];
                int remaining = data.length - (n - 1) * 16;
                System.arraycopy(data, (n - 1) * 16, padded, 0, remaining);
                padded[remaining] = (byte) 0x80;
                mn = xor16(padded, k2);
            }

            // CBC-MAC
            byte[] x = new byte[16];
            for (int i = 0; i < n - 1; i++) {
                byte[] block = Arrays.copyOfRange(data, i * 16, (i + 1) * 16);
                x = cryptoService.encryptAes(aesKey, xor16(x, block));
            }
            x = cryptoService.encryptAes(aesKey, xor16(x, mn));

            return x;
        } catch (Exception e) {
            throw new CryptoException("AES-CMAC calculation failed", e);
        }
    }

    /**
     * HMAC-SHA256
     */
    private byte[] calculateHmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new CryptoException("HMAC-SHA256 calculation failed", e);
        }
    }

    // ISO 9797-1 Padding Method 1 (zero padding to block boundary)
    private byte[] padIso9797Method1(byte[] data) {
        int blockSize = 8;
        int padLength = blockSize - (data.length % blockSize);
        if (padLength == blockSize && data.length > 0) {
            return data;
        }
        byte[] padded = new byte[data.length + padLength];
        System.arraycopy(data, 0, padded, 0, data.length);
        return padded;
    }

    // ISO 9797-1 Padding Method 2 (0x80 followed by zeros)
    private byte[] padIso9797Method2(byte[] data) {
        int blockSize = 8;
        int padLength = blockSize - (data.length % blockSize);
        byte[] padded = new byte[data.length + padLength];
        System.arraycopy(data, 0, padded, 0, data.length);
        padded[data.length] = (byte) 0x80;
        return padded;
    }

    // Generate CMAC subkey
    private byte[] generateSubkey(byte[] input) {
        byte[] output = new byte[16];
        boolean msb = (input[0] & 0x80) != 0;

        // Left shift by 1
        for (int i = 0; i < 15; i++) {
            output[i] = (byte) ((input[i] << 1) | ((input[i + 1] & 0x80) >> 7));
        }
        output[15] = (byte) (input[15] << 1);

        // XOR with Rb if MSB was 1
        if (msb) {
            output[15] ^= 0x87;
        }

        return output;
    }

    // XOR two 16-byte arrays
    private byte[] xor16(byte[] a, byte[] b) {
        byte[] result = new byte[16];
        for (int i = 0; i < 16; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    // Constant-time comparison to prevent timing attacks
    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
