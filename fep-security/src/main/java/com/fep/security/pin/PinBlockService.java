package com.fep.security.pin;

import com.fep.security.crypto.CryptoException;
import com.fep.security.crypto.CryptoService;
import com.fep.security.key.KeyManager;
import com.fep.security.key.KeyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Service for PIN Block operations including creation, encryption,
 * decryption, and translation between formats.
 */
@Service
public class PinBlockService {

    private static final Logger log = LoggerFactory.getLogger(PinBlockService.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    private final CryptoService cryptoService;
    private final KeyManager keyManager;

    public PinBlockService(CryptoService cryptoService, KeyManager keyManager) {
        this.cryptoService = cryptoService;
        this.keyManager = keyManager;
    }

    /**
     * Creates a clear PIN block in Format 0.
     *
     * @param pin the PIN (4-12 digits)
     * @param pan the PAN (Primary Account Number)
     * @return clear PIN block
     */
    public PinBlock createPinBlockFormat0(String pin, String pan) {
        validatePin(pin);
        validatePan(pan);

        byte[] pinField = createPinFieldFormat0(pin);
        byte[] panField = createPanField(pan);
        byte[] pinBlock = cryptoService.xor(pinField, panField);

        return PinBlock.builder()
                .format(PinBlockFormat.FORMAT_0)
                .data(pinBlock)
                .encrypted(false)
                .build();
    }

    /**
     * Creates a clear PIN block in Format 1.
     *
     * @param pin the PIN (4-12 digits)
     * @return clear PIN block
     */
    public PinBlock createPinBlockFormat1(String pin) {
        validatePin(pin);

        byte[] pinBlock = createPinFieldFormat1(pin);

        return PinBlock.builder()
                .format(PinBlockFormat.FORMAT_1)
                .data(pinBlock)
                .encrypted(false)
                .build();
    }

    /**
     * Creates a clear PIN block in Format 2 (IC card).
     *
     * @param pin the PIN (4-12 digits)
     * @return clear PIN block
     */
    public PinBlock createPinBlockFormat2(String pin) {
        validatePin(pin);

        byte[] pinBlock = createPinFieldFormat2(pin);

        return PinBlock.builder()
                .format(PinBlockFormat.FORMAT_2)
                .data(pinBlock)
                .encrypted(false)
                .build();
    }

    /**
     * Creates a clear PIN block in Format 3.
     *
     * @param pin the PIN (4-12 digits)
     * @param pan the PAN (Primary Account Number)
     * @return clear PIN block
     */
    public PinBlock createPinBlockFormat3(String pin, String pan) {
        validatePin(pin);
        validatePan(pan);

        byte[] pinField = createPinFieldFormat3(pin);
        byte[] panField = createPanField(pan);
        byte[] pinBlock = cryptoService.xor(pinField, panField);

        return PinBlock.builder()
                .format(PinBlockFormat.FORMAT_3)
                .data(pinBlock)
                .encrypted(false)
                .build();
    }

    /**
     * Encrypts a PIN block using 3DES and the current PEK.
     */
    public PinBlock encryptPinBlock(PinBlock clearPinBlock) {
        String keyId = keyManager.getCurrentKeyId(KeyType.PEK);
        return encryptPinBlock(clearPinBlock, keyId);
    }

    /**
     * Encrypts a PIN block using 3DES with specified key.
     */
    public PinBlock encryptPinBlock(PinBlock clearPinBlock, String keyId) {
        if (clearPinBlock.isEncrypted()) {
            throw new CryptoException("PIN block is already encrypted");
        }

        byte[] key = keyManager.getKey(keyId);
        if (key == null) {
            throw new CryptoException("Encryption key not found: " + keyId);
        }

        byte[] encrypted = cryptoService.encryptTdes(key, clearPinBlock.getData());

        return PinBlock.builder()
                .format(clearPinBlock.getFormat())
                .data(encrypted)
                .encrypted(true)
                .keyId(keyId)
                .build();
    }

    /**
     * Decrypts a PIN block using 3DES.
     */
    public PinBlock decryptPinBlock(PinBlock encryptedPinBlock) {
        if (!encryptedPinBlock.isEncrypted()) {
            throw new CryptoException("PIN block is not encrypted");
        }

        String keyId = encryptedPinBlock.getKeyId();
        byte[] key = keyManager.getKey(keyId);
        if (key == null) {
            throw new CryptoException("Decryption key not found: " + keyId);
        }

        byte[] decrypted = cryptoService.decryptTdes(key, encryptedPinBlock.getData());

        return PinBlock.builder()
                .format(encryptedPinBlock.getFormat())
                .data(decrypted)
                .encrypted(false)
                .build();
    }

    /**
     * Translates an encrypted PIN block from source key to destination key.
     * This is used when transferring PIN blocks between zones.
     */
    public PinBlock translatePinBlock(PinBlock encryptedPinBlock, String sourceKeyId, String destKeyId) {
        // Decrypt with source key
        byte[] sourceKey = keyManager.getKey(sourceKeyId);
        if (sourceKey == null) {
            throw new CryptoException("Source key not found: " + sourceKeyId);
        }

        byte[] clearData = cryptoService.decryptTdes(sourceKey, encryptedPinBlock.getData());

        // Encrypt with destination key
        byte[] destKey = keyManager.getKey(destKeyId);
        if (destKey == null) {
            throw new CryptoException("Destination key not found: " + destKeyId);
        }

        byte[] encryptedData = cryptoService.encryptTdes(destKey, clearData);

        // Clear sensitive data
        Arrays.fill(clearData, (byte) 0);

        return PinBlock.builder()
                .format(encryptedPinBlock.getFormat())
                .data(encryptedData)
                .encrypted(true)
                .keyId(destKeyId)
                .build();
    }

    /**
     * Converts a PIN block from one format to another.
     * PIN block must be decrypted for conversion.
     */
    public PinBlock convertFormat(PinBlock pinBlock, PinBlockFormat targetFormat, String pan) {
        if (pinBlock.isEncrypted()) {
            throw new CryptoException("Cannot convert encrypted PIN block. Decrypt first.");
        }

        String pin = extractPin(pinBlock, pan);

        return switch (targetFormat) {
            case FORMAT_0 -> createPinBlockFormat0(pin, pan);
            case FORMAT_1 -> createPinBlockFormat1(pin);
            case FORMAT_2 -> createPinBlockFormat2(pin);
            case FORMAT_3 -> createPinBlockFormat3(pin, pan);
            default -> throw new CryptoException("Unsupported target format: " + targetFormat);
        };
    }

    /**
     * Extracts the clear PIN from a decrypted PIN block.
     */
    public String extractPin(PinBlock pinBlock, String pan) {
        if (pinBlock.isEncrypted()) {
            throw new CryptoException("Cannot extract PIN from encrypted block");
        }

        byte[] data = pinBlock.getData();

        return switch (pinBlock.getFormat()) {
            case FORMAT_0 -> extractPinFormat0(data, pan);
            case FORMAT_1 -> extractPinFormat1(data);
            case FORMAT_2 -> extractPinFormat2(data);
            case FORMAT_3 -> extractPinFormat3(data, pan);
            default -> throw new CryptoException("Unsupported format: " + pinBlock.getFormat());
        };
    }

    /**
     * Verifies a PIN by comparing PIN blocks.
     */
    public boolean verifyPin(PinBlock encryptedPinBlock, String expectedPin, String pan) {
        PinBlock clearBlock = decryptPinBlock(encryptedPinBlock);
        String extractedPin = extractPin(clearBlock, pan);

        // Clear sensitive data
        Arrays.fill(clearBlock.getData(), (byte) 0);

        return expectedPin.equals(extractedPin);
    }

    // Format 0: PIN Field = 0 || PIN Length || PIN || F padding
    private byte[] createPinFieldFormat0(String pin) {
        byte[] field = new byte[8];
        Arrays.fill(field, (byte) 0xFF);

        // First nibble is 0, second nibble is PIN length
        field[0] = (byte) (pin.length() & 0x0F);

        // Pack PIN digits
        for (int i = 0; i < pin.length(); i++) {
            int digit = pin.charAt(i) - '0';
            int byteIndex = (i + 2) / 2;
            if ((i + 2) % 2 == 0) {
                field[byteIndex] = (byte) ((digit << 4) | 0x0F);
            } else {
                field[byteIndex] = (byte) ((field[byteIndex] & 0xF0) | digit);
            }
        }

        return field;
    }

    // Format 1: PIN Field = 1 || PIN Length || PIN || Random padding
    private byte[] createPinFieldFormat1(String pin) {
        byte[] field = new byte[8];
        secureRandom.nextBytes(field);

        // First nibble is 1, second nibble is PIN length
        field[0] = (byte) (0x10 | (pin.length() & 0x0F));

        // Pack PIN digits
        for (int i = 0; i < pin.length(); i++) {
            int digit = pin.charAt(i) - '0';
            int byteIndex = (i + 2) / 2;
            if ((i + 2) % 2 == 0) {
                field[byteIndex] = (byte) ((digit << 4) | (field[byteIndex] & 0x0F));
            } else {
                field[byteIndex] = (byte) ((field[byteIndex] & 0xF0) | digit);
            }
        }

        return field;
    }

    // Format 2: PIN Field = 2 || PIN Length || PIN || F padding
    private byte[] createPinFieldFormat2(String pin) {
        byte[] field = new byte[8];
        Arrays.fill(field, (byte) 0xFF);

        // First nibble is 2, second nibble is PIN length
        field[0] = (byte) (0x20 | (pin.length() & 0x0F));

        // Pack PIN digits
        for (int i = 0; i < pin.length(); i++) {
            int digit = pin.charAt(i) - '0';
            int byteIndex = (i + 2) / 2;
            if ((i + 2) % 2 == 0) {
                field[byteIndex] = (byte) ((digit << 4) | 0x0F);
            } else {
                field[byteIndex] = (byte) ((field[byteIndex] & 0xF0) | digit);
            }
        }

        return field;
    }

    // Format 3: PIN Field = 3 || PIN Length || PIN || Random padding (A-F)
    private byte[] createPinFieldFormat3(String pin) {
        byte[] field = new byte[8];

        // Fill with random values A-F (10-15)
        for (int i = 0; i < 8; i++) {
            int highNibble = 10 + secureRandom.nextInt(6);
            int lowNibble = 10 + secureRandom.nextInt(6);
            field[i] = (byte) ((highNibble << 4) | lowNibble);
        }

        // First nibble is 3, second nibble is PIN length
        field[0] = (byte) (0x30 | (pin.length() & 0x0F));

        // Pack PIN digits
        for (int i = 0; i < pin.length(); i++) {
            int digit = pin.charAt(i) - '0';
            int byteIndex = (i + 2) / 2;
            if ((i + 2) % 2 == 0) {
                field[byteIndex] = (byte) ((digit << 4) | (field[byteIndex] & 0x0F));
            } else {
                field[byteIndex] = (byte) ((field[byteIndex] & 0xF0) | digit);
            }
        }

        return field;
    }

    // PAN Field = 0000 || rightmost 12 digits of PAN (excluding check digit)
    private byte[] createPanField(String pan) {
        byte[] field = new byte[8];
        Arrays.fill(field, (byte) 0);

        // Get rightmost 12 digits excluding check digit
        String panPart = pan.substring(pan.length() - 13, pan.length() - 1);

        // Pack into bytes (starting at position 4 in field)
        for (int i = 0; i < 12; i++) {
            int digit = panPart.charAt(i) - '0';
            int byteIndex = 2 + (i / 2);
            if (i % 2 == 0) {
                field[byteIndex] = (byte) (digit << 4);
            } else {
                field[byteIndex] |= (byte) digit;
            }
        }

        return field;
    }

    private String extractPinFormat0(byte[] data, String pan) {
        byte[] panField = createPanField(pan);
        byte[] pinField = cryptoService.xor(data, panField);
        return extractPinFromField(pinField);
    }

    private String extractPinFormat1(byte[] data) {
        return extractPinFromField(data);
    }

    private String extractPinFormat2(byte[] data) {
        return extractPinFromField(data);
    }

    private String extractPinFormat3(byte[] data, String pan) {
        byte[] panField = createPanField(pan);
        byte[] pinField = cryptoService.xor(data, panField);
        return extractPinFromField(pinField);
    }

    private String extractPinFromField(byte[] field) {
        int pinLength = field[0] & 0x0F;
        StringBuilder pin = new StringBuilder();

        for (int i = 0; i < pinLength; i++) {
            int byteIndex = (i + 2) / 2;
            int digit;
            if ((i + 2) % 2 == 0) {
                digit = (field[byteIndex] >> 4) & 0x0F;
            } else {
                digit = field[byteIndex] & 0x0F;
            }
            pin.append(digit);
        }

        return pin.toString();
    }

    private void validatePin(String pin) {
        if (pin == null || pin.isEmpty()) {
            throw new CryptoException("PIN cannot be null or empty");
        }
        if (pin.length() < 4 || pin.length() > 12) {
            throw new CryptoException("PIN must be 4-12 digits");
        }
        if (!pin.matches("\\d+")) {
            throw new CryptoException("PIN must contain only digits");
        }
    }

    private void validatePan(String pan) {
        if (pan == null || pan.length() < 13) {
            throw new CryptoException("PAN must be at least 13 digits");
        }
        if (!pan.matches("\\d+")) {
            throw new CryptoException("PAN must contain only digits");
        }
    }
}
