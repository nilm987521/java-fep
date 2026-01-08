package com.fep.security.pin;

import com.fep.security.crypto.CryptoException;
import com.fep.security.crypto.CryptoService;
import com.fep.security.key.KeyInfo;
import com.fep.security.key.KeyManager;
import com.fep.security.key.KeyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PinBlockService.
 */
class PinBlockServiceTest {

    private CryptoService cryptoService;
    private KeyManager keyManager;
    private PinBlockService pinBlockService;

    private static final String TEST_PIN = "1234";
    private static final String TEST_PAN = "4111111111111111";

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
        keyManager = new KeyManager(cryptoService);
        pinBlockService = new PinBlockService(cryptoService, keyManager);

        // Set up a current PEK
        KeyInfo pek = keyManager.generateKey(KeyType.PEK, "test-pek");
        keyManager.setCurrentKey(KeyType.PEK, pek.getKeyId());
    }

    @Nested
    @DisplayName("PIN Block Format 0 Tests")
    class Format0Tests {

        @Test
        @DisplayName("Should create Format 0 PIN block")
        void shouldCreateFormat0PinBlock() {
            PinBlock pinBlock = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);

            assertNotNull(pinBlock);
            assertEquals(PinBlockFormat.FORMAT_0, pinBlock.getFormat());
            assertEquals(8, pinBlock.getData().length);
            assertFalse(pinBlock.isEncrypted());
        }

        @Test
        @DisplayName("Should extract PIN from Format 0 block")
        void shouldExtractPinFromFormat0() {
            PinBlock pinBlock = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);

            String extractedPin = pinBlockService.extractPin(pinBlock, TEST_PAN);

            assertEquals(TEST_PIN, extractedPin);
        }

        @Test
        @DisplayName("Should handle different PIN lengths")
        void shouldHandleDifferentPinLengths() {
            String[] pins = {"1234", "12345", "123456", "1234567890"};

            for (String pin : pins) {
                PinBlock block = pinBlockService.createPinBlockFormat0(pin, TEST_PAN);
                String extracted = pinBlockService.extractPin(block, TEST_PAN);
                assertEquals(pin, extracted);
            }
        }
    }

    @Nested
    @DisplayName("PIN Block Format 1 Tests")
    class Format1Tests {

        @Test
        @DisplayName("Should create Format 1 PIN block")
        void shouldCreateFormat1PinBlock() {
            PinBlock pinBlock = pinBlockService.createPinBlockFormat1(TEST_PIN);

            assertNotNull(pinBlock);
            assertEquals(PinBlockFormat.FORMAT_1, pinBlock.getFormat());
            assertEquals(8, pinBlock.getData().length);
        }

        @Test
        @DisplayName("Should extract PIN from Format 1 block")
        void shouldExtractPinFromFormat1() {
            PinBlock pinBlock = pinBlockService.createPinBlockFormat1(TEST_PIN);

            String extractedPin = pinBlockService.extractPin(pinBlock, TEST_PAN);

            assertEquals(TEST_PIN, extractedPin);
        }

        @Test
        @DisplayName("Format 1 should have random padding")
        void format1ShouldHaveRandomPadding() {
            PinBlock block1 = pinBlockService.createPinBlockFormat1(TEST_PIN);
            PinBlock block2 = pinBlockService.createPinBlockFormat1(TEST_PIN);

            // Different blocks for same PIN due to random padding
            assertFalse(java.util.Arrays.equals(block1.getData(), block2.getData()));
        }
    }

    @Nested
    @DisplayName("PIN Block Format 2 Tests")
    class Format2Tests {

        @Test
        @DisplayName("Should create Format 2 PIN block")
        void shouldCreateFormat2PinBlock() {
            PinBlock pinBlock = pinBlockService.createPinBlockFormat2(TEST_PIN);

            assertNotNull(pinBlock);
            assertEquals(PinBlockFormat.FORMAT_2, pinBlock.getFormat());
            assertEquals(8, pinBlock.getData().length);
        }

        @Test
        @DisplayName("Should extract PIN from Format 2 block")
        void shouldExtractPinFromFormat2() {
            PinBlock pinBlock = pinBlockService.createPinBlockFormat2(TEST_PIN);

            String extractedPin = pinBlockService.extractPin(pinBlock, TEST_PAN);

            assertEquals(TEST_PIN, extractedPin);
        }
    }

    @Nested
    @DisplayName("PIN Block Format 3 Tests")
    class Format3Tests {

        @Test
        @DisplayName("Should create Format 3 PIN block")
        void shouldCreateFormat3PinBlock() {
            PinBlock pinBlock = pinBlockService.createPinBlockFormat3(TEST_PIN, TEST_PAN);

            assertNotNull(pinBlock);
            assertEquals(PinBlockFormat.FORMAT_3, pinBlock.getFormat());
            assertEquals(8, pinBlock.getData().length);
        }

        @Test
        @DisplayName("Should extract PIN from Format 3 block")
        void shouldExtractPinFromFormat3() {
            PinBlock pinBlock = pinBlockService.createPinBlockFormat3(TEST_PIN, TEST_PAN);

            String extractedPin = pinBlockService.extractPin(pinBlock, TEST_PAN);

            assertEquals(TEST_PIN, extractedPin);
        }

        @Test
        @DisplayName("Format 3 should have random padding")
        void format3ShouldHaveRandomPadding() {
            PinBlock block1 = pinBlockService.createPinBlockFormat3(TEST_PIN, TEST_PAN);
            PinBlock block2 = pinBlockService.createPinBlockFormat3(TEST_PIN, TEST_PAN);

            assertFalse(java.util.Arrays.equals(block1.getData(), block2.getData()));
        }
    }

    @Nested
    @DisplayName("PIN Block Encryption Tests")
    class EncryptionTests {

        @Test
        @DisplayName("Should encrypt PIN block")
        void shouldEncryptPinBlock() {
            PinBlock clearBlock = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);

            PinBlock encryptedBlock = pinBlockService.encryptPinBlock(clearBlock);

            assertTrue(encryptedBlock.isEncrypted());
            assertNotNull(encryptedBlock.getKeyId());
            assertFalse(java.util.Arrays.equals(clearBlock.getData(), encryptedBlock.getData()));
        }

        @Test
        @DisplayName("Should decrypt PIN block")
        void shouldDecryptPinBlock() {
            PinBlock clearBlock = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);
            PinBlock encryptedBlock = pinBlockService.encryptPinBlock(clearBlock);

            PinBlock decryptedBlock = pinBlockService.decryptPinBlock(encryptedBlock);

            assertFalse(decryptedBlock.isEncrypted());
            assertArrayEquals(clearBlock.getData(), decryptedBlock.getData());
        }

        @Test
        @DisplayName("Should encrypt with specific key")
        void shouldEncryptWithSpecificKey() {
            KeyInfo keyInfo = keyManager.generateKey(KeyType.PEK, "specific-pek");
            PinBlock clearBlock = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);

            PinBlock encryptedBlock = pinBlockService.encryptPinBlock(clearBlock, keyInfo.getKeyId());

            assertEquals(keyInfo.getKeyId(), encryptedBlock.getKeyId());
        }

        @Test
        @DisplayName("Should throw exception when encrypting already encrypted block")
        void shouldThrowWhenEncryptingEncryptedBlock() {
            PinBlock clearBlock = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);
            PinBlock encryptedBlock = pinBlockService.encryptPinBlock(clearBlock);

            assertThrows(CryptoException.class, () ->
                    pinBlockService.encryptPinBlock(encryptedBlock));
        }
    }

    @Nested
    @DisplayName("PIN Block Translation Tests")
    class TranslationTests {

        @Test
        @DisplayName("Should translate PIN block between keys")
        void shouldTranslatePinBlockBetweenKeys() {
            KeyInfo sourceKey = keyManager.generateKey(KeyType.PEK, "source-pek");
            KeyInfo destKey = keyManager.generateKey(KeyType.PEK, "dest-pek");

            PinBlock clearBlock = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);
            PinBlock encryptedBlock = pinBlockService.encryptPinBlock(clearBlock, sourceKey.getKeyId());

            PinBlock translatedBlock = pinBlockService.translatePinBlock(
                    encryptedBlock, sourceKey.getKeyId(), destKey.getKeyId());

            assertEquals(destKey.getKeyId(), translatedBlock.getKeyId());

            // Verify by decrypting
            PinBlock decrypted = pinBlockService.decryptPinBlock(translatedBlock);
            String extractedPin = pinBlockService.extractPin(decrypted, TEST_PAN);
            assertEquals(TEST_PIN, extractedPin);
        }
    }

    @Nested
    @DisplayName("Format Conversion Tests")
    class FormatConversionTests {

        @Test
        @DisplayName("Should convert from Format 0 to Format 3")
        void shouldConvertFormat0ToFormat3() {
            PinBlock format0Block = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);

            PinBlock format3Block = pinBlockService.convertFormat(format0Block, PinBlockFormat.FORMAT_3, TEST_PAN);

            assertEquals(PinBlockFormat.FORMAT_3, format3Block.getFormat());
            String extractedPin = pinBlockService.extractPin(format3Block, TEST_PAN);
            assertEquals(TEST_PIN, extractedPin);
        }

        @Test
        @DisplayName("Should convert from Format 3 to Format 0")
        void shouldConvertFormat3ToFormat0() {
            PinBlock format3Block = pinBlockService.createPinBlockFormat3(TEST_PIN, TEST_PAN);

            PinBlock format0Block = pinBlockService.convertFormat(format3Block, PinBlockFormat.FORMAT_0, TEST_PAN);

            assertEquals(PinBlockFormat.FORMAT_0, format0Block.getFormat());
            String extractedPin = pinBlockService.extractPin(format0Block, TEST_PAN);
            assertEquals(TEST_PIN, extractedPin);
        }

        @Test
        @DisplayName("Should throw exception when converting encrypted block")
        void shouldThrowWhenConvertingEncryptedBlock() {
            PinBlock clearBlock = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);
            PinBlock encryptedBlock = pinBlockService.encryptPinBlock(clearBlock);

            assertThrows(CryptoException.class, () ->
                    pinBlockService.convertFormat(encryptedBlock, PinBlockFormat.FORMAT_3, TEST_PAN));
        }
    }

    @Nested
    @DisplayName("PIN Verification Tests")
    class VerificationTests {

        @Test
        @DisplayName("Should verify correct PIN")
        void shouldVerifyCorrectPin() {
            PinBlock clearBlock = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);
            PinBlock encryptedBlock = pinBlockService.encryptPinBlock(clearBlock);

            boolean verified = pinBlockService.verifyPin(encryptedBlock, TEST_PIN, TEST_PAN);

            assertTrue(verified);
        }

        @Test
        @DisplayName("Should reject incorrect PIN")
        void shouldRejectIncorrectPin() {
            PinBlock clearBlock = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);
            PinBlock encryptedBlock = pinBlockService.encryptPinBlock(clearBlock);

            boolean verified = pinBlockService.verifyPin(encryptedBlock, "9999", TEST_PAN);

            assertFalse(verified);
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject null PIN")
        void shouldRejectNullPin() {
            assertThrows(CryptoException.class, () ->
                    pinBlockService.createPinBlockFormat0(null, TEST_PAN));
        }

        @Test
        @DisplayName("Should reject empty PIN")
        void shouldRejectEmptyPin() {
            assertThrows(CryptoException.class, () ->
                    pinBlockService.createPinBlockFormat0("", TEST_PAN));
        }

        @Test
        @DisplayName("Should reject PIN shorter than 4 digits")
        void shouldRejectShortPin() {
            assertThrows(CryptoException.class, () ->
                    pinBlockService.createPinBlockFormat0("123", TEST_PAN));
        }

        @Test
        @DisplayName("Should reject PIN longer than 12 digits")
        void shouldRejectLongPin() {
            assertThrows(CryptoException.class, () ->
                    pinBlockService.createPinBlockFormat0("1234567890123", TEST_PAN));
        }

        @Test
        @DisplayName("Should reject non-numeric PIN")
        void shouldRejectNonNumericPin() {
            assertThrows(CryptoException.class, () ->
                    pinBlockService.createPinBlockFormat0("12AB", TEST_PAN));
        }

        @Test
        @DisplayName("Should reject invalid PAN")
        void shouldRejectInvalidPan() {
            assertThrows(CryptoException.class, () ->
                    pinBlockService.createPinBlockFormat0(TEST_PIN, "123"));
        }
    }

    @Nested
    @DisplayName("PinBlock Utility Tests")
    class PinBlockUtilityTests {

        @Test
        @DisplayName("Should convert PIN block to hex string")
        void shouldConvertToHexString() {
            PinBlock pinBlock = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);

            String hex = pinBlock.toHexString();

            assertNotNull(hex);
            assertEquals(16, hex.length());
        }

        @Test
        @DisplayName("Should create PIN block from hex string")
        void shouldCreateFromHexString() {
            PinBlock original = pinBlockService.createPinBlockFormat0(TEST_PIN, TEST_PAN);
            String hex = original.toHexString();

            PinBlock recreated = PinBlock.fromHexString(hex, PinBlockFormat.FORMAT_0);

            assertArrayEquals(original.getData(), recreated.getData());
        }
    }
}
