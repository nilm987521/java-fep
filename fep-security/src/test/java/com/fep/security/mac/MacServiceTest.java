package com.fep.security.mac;

import com.fep.security.crypto.CryptoService;
import com.fep.security.key.KeyInfo;
import com.fep.security.key.KeyManager;
import com.fep.security.key.KeyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MacService.
 */
class MacServiceTest {

    private CryptoService cryptoService;
    private KeyManager keyManager;
    private MacService macService;
    private String makKeyId;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
        keyManager = new KeyManager(cryptoService);
        macService = new MacService(cryptoService, keyManager);

        // Set up a current MAK
        KeyInfo mak = keyManager.generateKey(KeyType.MAK, "test-mak");
        keyManager.setCurrentKey(KeyType.MAK, mak.getKeyId());
        makKeyId = mak.getKeyId();
    }

    @Nested
    @DisplayName("ISO 9797-1 Algorithm 1 Tests")
    class Iso9797Alg1Tests {

        @Test
        @DisplayName("Should calculate MAC using Algorithm 1")
        void shouldCalculateMacAlg1() {
            byte[] data = "Test message for MAC".getBytes();

            byte[] mac = macService.calculateMac(MacAlgorithm.ISO_9797_ALG1, makKeyId, data);

            assertNotNull(mac);
            assertEquals(8, mac.length);
        }

        @Test
        @DisplayName("Should produce consistent MAC for same data")
        void shouldProduceConsistentMac() {
            byte[] data = "Same data".getBytes();

            byte[] mac1 = macService.calculateMac(MacAlgorithm.ISO_9797_ALG1, makKeyId, data);
            byte[] mac2 = macService.calculateMac(MacAlgorithm.ISO_9797_ALG1, makKeyId, data);

            assertArrayEquals(mac1, mac2);
        }

        @Test
        @DisplayName("Should produce different MAC for different data")
        void shouldProduceDifferentMacForDifferentData() {
            byte[] mac1 = macService.calculateMac(MacAlgorithm.ISO_9797_ALG1, makKeyId, "Data1".getBytes());
            byte[] mac2 = macService.calculateMac(MacAlgorithm.ISO_9797_ALG1, makKeyId, "Data2".getBytes());

            assertFalse(Arrays.equals(mac1, mac2));
        }
    }

    @Nested
    @DisplayName("ISO 9797-1 Algorithm 3 Tests")
    class Iso9797Alg3Tests {

        @Test
        @DisplayName("Should calculate MAC using Algorithm 3 (Retail MAC)")
        void shouldCalculateMacAlg3() {
            byte[] data = "Test message for Retail MAC".getBytes();

            byte[] mac = macService.calculateMac(MacAlgorithm.ISO_9797_ALG3, makKeyId, data);

            assertNotNull(mac);
            assertEquals(8, mac.length);
        }

        @Test
        @DisplayName("Should verify MAC correctly")
        void shouldVerifyMacCorrectly() {
            byte[] data = "Verify this message".getBytes();
            byte[] mac = macService.calculateMac(MacAlgorithm.ISO_9797_ALG3, makKeyId, data);

            boolean verified = macService.verifyMac(MacAlgorithm.ISO_9797_ALG3, makKeyId, data, mac);

            assertTrue(verified);
        }

        @Test
        @DisplayName("Should reject tampered data")
        void shouldRejectTamperedData() {
            byte[] data = "Original message".getBytes();
            byte[] mac = macService.calculateMac(MacAlgorithm.ISO_9797_ALG3, makKeyId, data);

            byte[] tamperedData = "Tampered message".getBytes();
            boolean verified = macService.verifyMac(MacAlgorithm.ISO_9797_ALG3, makKeyId, tamperedData, mac);

            assertFalse(verified);
        }
    }

    @Nested
    @DisplayName("ANSI X9.19 Tests")
    class AnsiX919Tests {

        @Test
        @DisplayName("Should calculate ANSI X9.19 MAC")
        void shouldCalculateAnsiX919Mac() {
            byte[] data = "ANSI X9.19 test data".getBytes();

            byte[] mac = macService.calculateMac(MacAlgorithm.ANSI_X9_19, makKeyId, data);

            assertNotNull(mac);
            assertEquals(8, mac.length);
        }

        @Test
        @DisplayName("Should verify ANSI X9.19 MAC")
        void shouldVerifyAnsiX919Mac() {
            byte[] data = "Verify ANSI X9.19".getBytes();
            byte[] mac = macService.calculateMac(MacAlgorithm.ANSI_X9_19, makKeyId, data);

            assertTrue(macService.verifyMac(MacAlgorithm.ANSI_X9_19, makKeyId, data, mac));
        }
    }

    @Nested
    @DisplayName("AES-CMAC Tests")
    class AesCmacTests {

        private String aesKeyId;

        @BeforeEach
        void setUpAesKey() {
            // Create a 16-byte AES key for CMAC
            byte[] aesKey = cryptoService.generateAes128Key();
            // Expand to 24 bytes for key manager (which expects 3DES keys)
            byte[] expandedKey = new byte[24];
            System.arraycopy(aesKey, 0, expandedKey, 0, 16);
            System.arraycopy(aesKey, 0, expandedKey, 16, 8);
            KeyInfo aesKeyInfo = keyManager.importKey(KeyType.MAK, "aes-mak", expandedKey);
            aesKeyId = aesKeyInfo.getKeyId();
        }

        @Test
        @DisplayName("Should calculate AES-CMAC")
        void shouldCalculateAesCmac() {
            byte[] data = "AES-CMAC test data".getBytes();

            byte[] mac = macService.calculateMac(MacAlgorithm.AES_CMAC, aesKeyId, data);

            assertNotNull(mac);
            assertEquals(16, mac.length);
        }

        @Test
        @DisplayName("Should verify AES-CMAC")
        void shouldVerifyAesCmac() {
            byte[] data = "Verify AES-CMAC".getBytes();
            byte[] mac = macService.calculateMac(MacAlgorithm.AES_CMAC, aesKeyId, data);

            assertTrue(macService.verifyMac(MacAlgorithm.AES_CMAC, aesKeyId, data, mac));
        }
    }

    @Nested
    @DisplayName("HMAC-SHA256 Tests")
    class HmacSha256Tests {

        @Test
        @DisplayName("Should calculate HMAC-SHA256")
        void shouldCalculateHmacSha256() {
            byte[] data = "HMAC-SHA256 test data".getBytes();

            byte[] mac = macService.calculateMac(MacAlgorithm.HMAC_SHA256, makKeyId, data);

            assertNotNull(mac);
            assertEquals(32, mac.length);
        }

        @Test
        @DisplayName("Should verify HMAC-SHA256")
        void shouldVerifyHmacSha256() {
            byte[] data = "Verify HMAC-SHA256".getBytes();
            byte[] mac = macService.calculateMac(MacAlgorithm.HMAC_SHA256, makKeyId, data);

            assertTrue(macService.verifyMac(MacAlgorithm.HMAC_SHA256, makKeyId, data, mac));
        }

        @Test
        @DisplayName("Should reject wrong MAC")
        void shouldRejectWrongMac() {
            byte[] data = "Test data".getBytes();
            byte[] wrongMac = new byte[32];

            assertFalse(macService.verifyMac(MacAlgorithm.HMAC_SHA256, makKeyId, data, wrongMac));
        }
    }

    @Nested
    @DisplayName("4-Byte MAC Tests")
    class FourByteMacTests {

        @Test
        @DisplayName("Should calculate 4-byte MAC")
        void shouldCalculate4ByteMac() {
            byte[] data = "Short MAC test".getBytes();

            byte[] mac = macService.calculate4ByteMac(MacAlgorithm.ISO_9797_ALG3, makKeyId, data);

            assertNotNull(mac);
            assertEquals(4, mac.length);
        }

        @Test
        @DisplayName("4-byte MAC should be first 4 bytes of full MAC")
        void fourByteMacShouldBeFirst4Bytes() {
            byte[] data = "Compare MACs".getBytes();

            byte[] fullMac = macService.calculateMac(MacAlgorithm.ISO_9797_ALG3, makKeyId, data);
            byte[] shortMac = macService.calculate4ByteMac(MacAlgorithm.ISO_9797_ALG3, makKeyId, data);

            assertArrayEquals(Arrays.copyOf(fullMac, 4), shortMac);
        }
    }

    @Nested
    @DisplayName("Current Key Tests")
    class CurrentKeyTests {

        @Test
        @DisplayName("Should use current MAK when no key specified")
        void shouldUseCurrentMak() {
            byte[] data = "Use current MAK".getBytes();

            byte[] mac1 = macService.calculateMac(MacAlgorithm.ISO_9797_ALG3, data);
            byte[] mac2 = macService.calculateMac(MacAlgorithm.ISO_9797_ALG3, makKeyId, data);

            assertArrayEquals(mac1, mac2);
        }

        @Test
        @DisplayName("Should verify using current MAK")
        void shouldVerifyUsingCurrentMak() {
            byte[] data = "Verify with current MAK".getBytes();
            byte[] mac = macService.calculateMac(MacAlgorithm.ANSI_X9_19, data);

            assertTrue(macService.verifyMac(MacAlgorithm.ANSI_X9_19, data, mac));
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty data")
        void shouldHandleEmptyData() {
            byte[] emptyData = new byte[0];

            byte[] mac = macService.calculateMac(MacAlgorithm.ISO_9797_ALG1, makKeyId, emptyData);

            assertNotNull(mac);
            assertEquals(8, mac.length);
        }

        @Test
        @DisplayName("Should handle data exactly block size")
        void shouldHandleDataExactlyBlockSize() {
            byte[] data = new byte[8]; // Exactly one block
            Arrays.fill(data, (byte) 0x41);

            byte[] mac = macService.calculateMac(MacAlgorithm.ISO_9797_ALG3, makKeyId, data);

            assertNotNull(mac);
            assertEquals(8, mac.length);
        }

        @Test
        @DisplayName("Should handle large data")
        void shouldHandleLargeData() {
            byte[] largeData = new byte[10000];
            Arrays.fill(largeData, (byte) 0x42);

            byte[] mac = macService.calculateMac(MacAlgorithm.ANSI_X9_19, makKeyId, largeData);

            assertNotNull(mac);
            assertEquals(8, mac.length);
        }
    }
}
