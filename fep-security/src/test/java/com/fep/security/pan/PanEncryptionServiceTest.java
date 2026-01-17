package com.fep.security.pan;

import com.fep.security.crypto.CryptoException;
import com.fep.security.crypto.CryptoService;
import com.fep.security.key.KeyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PanEncryptionService.
 */
class PanEncryptionServiceTest {

    private CryptoService cryptoService;
    private KeyManager keyManager;
    private PanEncryptionService panEncryptionService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
        keyManager = new KeyManager(cryptoService);
        panEncryptionService = new PanEncryptionService(cryptoService, keyManager);
    }

    @Nested
    @DisplayName("Encryption Tests")
    class EncryptionTests {

        @Test
        @DisplayName("Should encrypt and decrypt PAN successfully")
        void shouldEncryptAndDecryptPan() {
            String pan = "4123456789012345";

            String encrypted = panEncryptionService.encrypt(pan);
            String decrypted = panEncryptionService.decrypt(encrypted);

            assertEquals(pan, decrypted);
        }

        @Test
        @DisplayName("Should encrypt different PANs to different ciphertext")
        void shouldEncryptDifferentPansToDifferentCiphertext() {
            String pan1 = "4123456789012345";
            String pan2 = "4123456789012346";

            String encrypted1 = panEncryptionService.encrypt(pan1);
            String encrypted2 = panEncryptionService.encrypt(pan2);

            assertNotEquals(encrypted1, encrypted2);
        }

        @Test
        @DisplayName("Should return null for null PAN")
        void shouldReturnNullForNullPan() {
            assertNull(panEncryptionService.encrypt(null));
            assertNull(panEncryptionService.decrypt(null));
        }

        @Test
        @DisplayName("Should return null for empty PAN")
        void shouldReturnNullForEmptyPan() {
            assertNull(panEncryptionService.encrypt(""));
            assertNull(panEncryptionService.decrypt(""));
        }

        @Test
        @DisplayName("Should produce hex string output")
        void shouldProduceHexStringOutput() {
            String pan = "4123456789012345";

            String encrypted = panEncryptionService.encrypt(pan);

            assertTrue(encrypted.matches("[0-9A-Fa-f]+"));
        }

        @Test
        @DisplayName("Should encrypt 13-digit PAN")
        void shouldEncrypt13DigitPan() {
            String pan = "4123456789012";

            String encrypted = panEncryptionService.encrypt(pan);
            String decrypted = panEncryptionService.decrypt(encrypted);

            assertEquals(pan, decrypted);
        }

        @Test
        @DisplayName("Should encrypt 19-digit PAN")
        void shouldEncrypt19DigitPan() {
            String pan = "4123456789012345678";

            String encrypted = panEncryptionService.encrypt(pan);
            String decrypted = panEncryptionService.decrypt(encrypted);

            assertEquals(pan, decrypted);
        }

        @Test
        @DisplayName("Should throw exception for PAN shorter than 13 digits")
        void shouldThrowForShortPan() {
            String shortPan = "412345678901";

            assertThrows(IllegalArgumentException.class, () ->
                    panEncryptionService.encrypt(shortPan));
        }

        @Test
        @DisplayName("Should throw exception for PAN longer than 19 digits")
        void shouldThrowForLongPan() {
            String longPan = "41234567890123456789";

            assertThrows(IllegalArgumentException.class, () ->
                    panEncryptionService.encrypt(longPan));
        }

        @Test
        @DisplayName("Should throw exception for PAN with non-digit characters")
        void shouldThrowForNonDigitPan() {
            String invalidPan = "4123-4567-8901-2345";

            assertThrows(IllegalArgumentException.class, () ->
                    panEncryptionService.encrypt(invalidPan));
        }

        @Test
        @DisplayName("Should throw exception for PAN with letters")
        void shouldThrowForAlphanumericPan() {
            String invalidPan = "412345678901234A";

            assertThrows(IllegalArgumentException.class, () ->
                    panEncryptionService.encrypt(invalidPan));
        }
    }

    @Nested
    @DisplayName("Masking Tests")
    class MaskingTests {

        @Test
        @DisplayName("Should mask 16-digit PAN correctly")
        void shouldMask16DigitPan() {
            String pan = "4123456789012345";

            String masked = panEncryptionService.mask(pan);

            assertEquals("412345******2345", masked);
        }

        @Test
        @DisplayName("Should mask 19-digit PAN correctly")
        void shouldMask19DigitPan() {
            String pan = "4123456789012345678";

            String masked = panEncryptionService.mask(pan);

            assertEquals("412345*********5678", masked);
        }

        @Test
        @DisplayName("Should mask 13-digit PAN correctly")
        void shouldMask13DigitPan() {
            String pan = "4123456789012";

            String masked = panEncryptionService.mask(pan);

            assertEquals("412345***9012", masked);
        }

        @Test
        @DisplayName("Should return null for null PAN")
        void shouldReturnNullForNullPan() {
            assertNull(panEncryptionService.mask(null));
        }

        @Test
        @DisplayName("Should return null for empty PAN")
        void shouldReturnNullForEmptyPan() {
            assertNull(panEncryptionService.mask(""));
        }

        @Test
        @DisplayName("Should fully mask PAN shorter than 13 digits")
        void shouldFullyMaskShortPan() {
            String shortPan = "123456789";

            String masked = panEncryptionService.mask(shortPan);

            assertEquals("*********", masked);
        }

        @Test
        @DisplayName("Should preserve first 6 and last 4 digits")
        void shouldPreserveFirstSixAndLastFour() {
            String pan = "1234567890123456";

            String masked = panEncryptionService.mask(pan);

            assertTrue(masked.startsWith("123456"));
            assertTrue(masked.endsWith("3456"));
        }
    }

    @Nested
    @DisplayName("Hashing Tests")
    class HashingTests {

        @Test
        @DisplayName("Should produce SHA-256 hash")
        void shouldProduceSha256Hash() {
            String pan = "4123456789012345";

            String hash = panEncryptionService.hash(pan);

            assertNotNull(hash);
            assertEquals(64, hash.length()); // SHA-256 = 32 bytes = 64 hex chars
        }

        @Test
        @DisplayName("Should produce same hash for same PAN")
        void shouldProduceSameHashForSamePan() {
            String pan = "4123456789012345";

            String hash1 = panEncryptionService.hash(pan);
            String hash2 = panEncryptionService.hash(pan);

            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("Should produce different hash for different PAN")
        void shouldProduceDifferentHashForDifferentPan() {
            String pan1 = "4123456789012345";
            String pan2 = "4123456789012346";

            String hash1 = panEncryptionService.hash(pan1);
            String hash2 = panEncryptionService.hash(pan2);

            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("Should return null for null PAN")
        void shouldReturnNullForNullPan() {
            assertNull(panEncryptionService.hash(null));
        }

        @Test
        @DisplayName("Should return null for empty PAN")
        void shouldReturnNullForEmptyPan() {
            assertNull(panEncryptionService.hash(""));
        }

        @Test
        @DisplayName("Should strip spaces before hashing")
        void shouldStripSpacesBeforeHashing() {
            String panWithSpaces = "4123 4567 8901 2345";
            String panWithoutSpaces = "4123456789012345";

            String hash1 = panEncryptionService.hash(panWithSpaces);
            String hash2 = panEncryptionService.hash(panWithoutSpaces);

            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("Should produce hex string output")
        void shouldProduceHexStringOutput() {
            String pan = "4123456789012345";

            String hash = panEncryptionService.hash(pan);

            assertTrue(hash.matches("[0-9A-Fa-f]+"));
        }
    }

    @Nested
    @DisplayName("isEncrypted Detection Tests")
    class IsEncryptedTests {

        @Test
        @DisplayName("Should detect encrypted PAN")
        void shouldDetectEncryptedPan() {
            String pan = "4123456789012345";
            String encrypted = panEncryptionService.encrypt(pan);

            assertTrue(panEncryptionService.isEncrypted(encrypted));
        }

        @Test
        @DisplayName("Should not detect plain PAN as encrypted")
        void shouldNotDetectPlainPanAsEncrypted() {
            String pan = "4123456789012345";

            assertFalse(panEncryptionService.isEncrypted(pan));
        }

        @Test
        @DisplayName("Should return false for null")
        void shouldReturnFalseForNull() {
            assertFalse(panEncryptionService.isEncrypted(null));
        }

        @Test
        @DisplayName("Should return false for empty string")
        void shouldReturnFalseForEmptyString() {
            assertFalse(panEncryptionService.isEncrypted(""));
        }

        @Test
        @DisplayName("Should return false for non-hex string")
        void shouldReturnFalseForNonHexString() {
            assertFalse(panEncryptionService.isEncrypted("GHIJKLMNOP123456"));
        }

        @Test
        @DisplayName("Should return false for odd-length hex string")
        void shouldReturnFalseForOddLengthHexString() {
            assertFalse(panEncryptionService.isEncrypted("ABC"));
        }

        @Test
        @DisplayName("Should return false for short hex string")
        void shouldReturnFalseForShortHexString() {
            assertFalse(panEncryptionService.isEncrypted("ABCD1234"));
        }
    }

    @Nested
    @DisplayName("Key Management Integration Tests")
    class KeyManagementTests {

        @Test
        @DisplayName("Should auto-generate key on first use")
        void shouldAutoGenerateKeyOnFirstUse() {
            String pan = "4123456789012345";

            // First encryption should create the key
            String encrypted = panEncryptionService.encrypt(pan);

            assertNotNull(encrypted);
            assertTrue(encrypted.length() > 0);
        }

        @Test
        @DisplayName("Should use same key for multiple operations")
        void shouldUseSameKeyForMultipleOperations() {
            String pan = "4123456789012345";

            String encrypted1 = panEncryptionService.encrypt(pan);
            String encrypted2 = panEncryptionService.encrypt(pan);

            // Same PAN with same key should produce same ciphertext
            assertEquals(encrypted1, encrypted2);
        }

        @Test
        @DisplayName("Should be able to decrypt after service recreation with same KeyManager")
        void shouldDecryptAfterServiceRecreation() {
            String pan = "4123456789012345";

            // Encrypt with first instance
            String encrypted = panEncryptionService.encrypt(pan);

            // Create new service instance with same KeyManager
            PanEncryptionService newService = new PanEncryptionService(cryptoService, keyManager);

            // Should be able to decrypt
            String decrypted = newService.decrypt(encrypted);

            assertEquals(pan, decrypted);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle PAN with all same digits")
        void shouldHandlePanWithAllSameDigits() {
            String pan = "1111111111111111";

            String encrypted = panEncryptionService.encrypt(pan);
            String decrypted = panEncryptionService.decrypt(encrypted);

            assertEquals(pan, decrypted);
        }

        @Test
        @DisplayName("Should handle PAN with all zeros")
        void shouldHandlePanWithAllZeros() {
            String pan = "0000000000000000";

            String encrypted = panEncryptionService.encrypt(pan);
            String decrypted = panEncryptionService.decrypt(encrypted);

            assertEquals(pan, decrypted);
        }

        @Test
        @DisplayName("Should handle PAN with all nines")
        void shouldHandlePanWithAllNines() {
            String pan = "9999999999999999";

            String encrypted = panEncryptionService.encrypt(pan);
            String decrypted = panEncryptionService.decrypt(encrypted);

            assertEquals(pan, decrypted);
        }

        @Test
        @DisplayName("Should throw CryptoException for corrupted ciphertext")
        void shouldThrowForCorruptedCiphertext() {
            String pan = "4123456789012345";
            String encrypted = panEncryptionService.encrypt(pan);

            // Corrupt the ciphertext
            String corrupted = encrypted.substring(0, encrypted.length() - 2) + "00";

            assertThrows(CryptoException.class, () ->
                    panEncryptionService.decrypt(corrupted));
        }

        @Test
        @DisplayName("Should throw exception for invalid hex in decrypt")
        void shouldThrowForInvalidHexInDecrypt() {
            assertThrows(CryptoException.class, () ->
                    panEncryptionService.decrypt("GHIJKLMNOPQRSTUV12345678901234567890123456789012"));
        }
    }

    @Nested
    @DisplayName("Performance and Consistency Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should handle multiple encryptions efficiently")
        void shouldHandleMultipleEncryptionsEfficiently() {
            String pan = "4123456789012345";

            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                panEncryptionService.encrypt(pan);
            }
            long endTime = System.currentTimeMillis();

            // Should complete 100 encryptions in less than 1 second
            assertTrue((endTime - startTime) < 1000, "100 encryptions should complete in less than 1 second");
        }

        @Test
        @DisplayName("Should maintain consistency across operations")
        void shouldMaintainConsistencyAcrossOperations() {
            String[] pans = {
                "4123456789012345",
                "5123456789012345",
                "3782822463100005",
                "6011111111111117"
            };

            for (String pan : pans) {
                String encrypted = panEncryptionService.encrypt(pan);
                String decrypted = panEncryptionService.decrypt(encrypted);
                String masked = panEncryptionService.mask(pan);
                String hash = panEncryptionService.hash(pan);

                assertEquals(pan, decrypted, "Decryption should match original for: " + pan);
                assertTrue(masked.contains("*"), "Masked should contain asterisks for: " + pan);
                assertEquals(64, hash.length(), "Hash should be 64 chars for: " + pan);
            }
        }
    }
}
