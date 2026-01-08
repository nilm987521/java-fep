package com.fep.security.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CryptoService.
 */
class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
    }

    @Nested
    @DisplayName("3DES Encryption Tests")
    class TdesEncryptionTests {

        @Test
        @DisplayName("Should encrypt and decrypt data with 3DES")
        void shouldEncryptAndDecryptWithTdes() {
            byte[] key = cryptoService.generateTdesKey();
            byte[] plaintext = "12345678".getBytes();

            byte[] encrypted = cryptoService.encryptTdes(key, plaintext);
            byte[] decrypted = cryptoService.decryptTdes(key, encrypted);

            assertArrayEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should produce different ciphertext for different keys")
        void shouldProduceDifferentCiphertextForDifferentKeys() {
            byte[] key1 = cryptoService.generateTdesKey();
            byte[] key2 = cryptoService.generateTdesKey();
            byte[] plaintext = "12345678".getBytes();

            byte[] encrypted1 = cryptoService.encryptTdes(key1, plaintext);
            byte[] encrypted2 = cryptoService.encryptTdes(key2, plaintext);

            assertFalse(Arrays.equals(encrypted1, encrypted2));
        }

        @Test
        @DisplayName("Should handle 16-byte key (double length)")
        void shouldHandleDoubleLengthKey() {
            byte[] key16 = new byte[16];
            System.arraycopy(cryptoService.generateTdesKey(), 0, key16, 0, 16);
            byte[] key24 = new byte[24];
            System.arraycopy(key16, 0, key24, 0, 16);
            System.arraycopy(key16, 0, key24, 16, 8);

            byte[] plaintext = "12345678".getBytes();

            byte[] encrypted = cryptoService.encryptTdes(key24, plaintext);
            byte[] decrypted = cryptoService.decryptTdes(key24, encrypted);

            assertArrayEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should throw exception for invalid key length")
        void shouldThrowForInvalidKeyLength() {
            byte[] invalidKey = new byte[20];
            byte[] plaintext = "12345678".getBytes();

            assertThrows(CryptoException.class, () ->
                    cryptoService.encryptTdes(invalidKey, plaintext));
        }

        @Test
        @DisplayName("Should encrypt with custom IV")
        void shouldEncryptWithCustomIv() {
            byte[] key = cryptoService.generateTdesKey();
            byte[] plaintext = "12345678".getBytes();
            byte[] iv = cryptoService.generateRandom(8);

            byte[] encrypted = cryptoService.encryptTdes(key, plaintext, iv);
            byte[] decrypted = cryptoService.decryptTdes(key, encrypted, iv);

            assertArrayEquals(plaintext, decrypted);
        }
    }

    @Nested
    @DisplayName("AES Encryption Tests")
    class AesEncryptionTests {

        @Test
        @DisplayName("Should encrypt and decrypt with AES-128")
        void shouldEncryptAndDecryptWithAes128() {
            byte[] key = cryptoService.generateAes128Key();
            byte[] plaintext = new byte[16];
            Arrays.fill(plaintext, (byte) 0x41);

            byte[] encrypted = cryptoService.encryptAes(key, plaintext);
            byte[] decrypted = cryptoService.decryptAes(key, encrypted);

            assertArrayEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should encrypt and decrypt with AES-256")
        void shouldEncryptAndDecryptWithAes256() {
            byte[] key = cryptoService.generateAes256Key();
            byte[] plaintext = new byte[16];
            Arrays.fill(plaintext, (byte) 0x42);

            byte[] encrypted = cryptoService.encryptAes(key, plaintext);
            byte[] decrypted = cryptoService.decryptAes(key, encrypted);

            assertArrayEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("Should throw exception for invalid AES key length")
        void shouldThrowForInvalidAesKeyLength() {
            byte[] invalidKey = new byte[20];
            byte[] plaintext = new byte[16];

            assertThrows(CryptoException.class, () ->
                    cryptoService.encryptAes(invalidKey, plaintext));
        }
    }

    @Nested
    @DisplayName("DES Encryption Tests")
    class DesEncryptionTests {

        @Test
        @DisplayName("Should encrypt and decrypt with DES")
        void shouldEncryptAndDecryptWithDes() {
            byte[] key = new byte[8];
            System.arraycopy(cryptoService.generateTdesKey(), 0, key, 0, 8);
            byte[] plaintext = "12345678".getBytes();

            byte[] encrypted = cryptoService.encryptDes(key, plaintext);
            byte[] decrypted = cryptoService.decryptDes(key, encrypted);

            assertArrayEquals(plaintext, decrypted);
        }
    }

    @Nested
    @DisplayName("Hash Function Tests")
    class HashTests {

        @Test
        @DisplayName("Should calculate SHA-256 hash")
        void shouldCalculateSha256() {
            byte[] data = "test data".getBytes();
            byte[] hash = cryptoService.sha256(data);

            assertEquals(32, hash.length);
        }

        @Test
        @DisplayName("Should produce same hash for same data")
        void shouldProduceSameHashForSameData() {
            byte[] data = "test data".getBytes();

            byte[] hash1 = cryptoService.sha256(data);
            byte[] hash2 = cryptoService.sha256(data);

            assertArrayEquals(hash1, hash2);
        }

        @Test
        @DisplayName("Should produce different hash for different data")
        void shouldProduceDifferentHashForDifferentData() {
            byte[] hash1 = cryptoService.sha256("data1".getBytes());
            byte[] hash2 = cryptoService.sha256("data2".getBytes());

            assertFalse(Arrays.equals(hash1, hash2));
        }

        @Test
        @DisplayName("Should calculate SHA-1 hash")
        void shouldCalculateSha1() {
            byte[] data = "test data".getBytes();
            byte[] hash = cryptoService.sha1(data);

            assertEquals(20, hash.length);
        }

        @Test
        @DisplayName("Should calculate MD5 hash")
        void shouldCalculateMd5() {
            byte[] data = "test data".getBytes();
            byte[] hash = cryptoService.md5(data);

            assertEquals(16, hash.length);
        }
    }

    @Nested
    @DisplayName("XOR Operation Tests")
    class XorTests {

        @Test
        @DisplayName("Should XOR two byte arrays")
        void shouldXorArrays() {
            byte[] a = {0x0F, 0x0F, 0x0F, 0x0F};
            byte[] b = {(byte) 0xF0, (byte) 0xF0, (byte) 0xF0, (byte) 0xF0};
            byte[] expected = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

            byte[] result = cryptoService.xor(a, b);

            assertArrayEquals(expected, result);
        }

        @Test
        @DisplayName("Should throw exception for different length arrays")
        void shouldThrowForDifferentLengthArrays() {
            byte[] a = new byte[4];
            byte[] b = new byte[8];

            assertThrows(IllegalArgumentException.class, () ->
                    cryptoService.xor(a, b));
        }

        @Test
        @DisplayName("XOR with self should produce zeros")
        void xorWithSelfShouldProduceZeros() {
            byte[] a = {0x12, 0x34, 0x56, 0x78};

            byte[] result = cryptoService.xor(a, a);

            assertArrayEquals(new byte[4], result);
        }
    }

    @Nested
    @DisplayName("Key Generation Tests")
    class KeyGenerationTests {

        @Test
        @DisplayName("Should generate random 3DES key")
        void shouldGenerateTdesKey() {
            byte[] key = cryptoService.generateTdesKey();

            assertEquals(24, key.length);
        }

        @Test
        @DisplayName("Should generate unique keys")
        void shouldGenerateUniqueKeys() {
            byte[] key1 = cryptoService.generateTdesKey();
            byte[] key2 = cryptoService.generateTdesKey();

            assertFalse(Arrays.equals(key1, key2));
        }

        @Test
        @DisplayName("Should generate AES-128 key")
        void shouldGenerateAes128Key() {
            byte[] key = cryptoService.generateAes128Key();

            assertEquals(16, key.length);
        }

        @Test
        @DisplayName("Should generate AES-256 key")
        void shouldGenerateAes256Key() {
            byte[] key = cryptoService.generateAes256Key();

            assertEquals(32, key.length);
        }

        @Test
        @DisplayName("Should generate random bytes")
        void shouldGenerateRandomBytes() {
            byte[] random1 = cryptoService.generateRandom(32);
            byte[] random2 = cryptoService.generateRandom(32);

            assertEquals(32, random1.length);
            assertFalse(Arrays.equals(random1, random2));
        }
    }

    @Nested
    @DisplayName("Hex Conversion Tests")
    class HexConversionTests {

        @Test
        @DisplayName("Should convert bytes to hex")
        void shouldConvertBytesToHex() {
            byte[] bytes = {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF};

            String hex = cryptoService.bytesToHex(bytes);

            assertEquals("0123456789ABCDEF", hex);
        }

        @Test
        @DisplayName("Should convert hex to bytes")
        void shouldConvertHexToBytes() {
            String hex = "0123456789ABCDEF";
            byte[] expected = {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF};

            byte[] bytes = cryptoService.hexToBytes(hex);

            assertArrayEquals(expected, bytes);
        }

        @Test
        @DisplayName("Should handle lowercase hex")
        void shouldHandleLowercaseHex() {
            String hex = "abcdef";
            byte[] expected = {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF};

            byte[] bytes = cryptoService.hexToBytes(hex);

            assertArrayEquals(expected, bytes);
        }

        @Test
        @DisplayName("Should throw exception for invalid hex")
        void shouldThrowForInvalidHex() {
            assertThrows(CryptoException.class, () ->
                    cryptoService.hexToBytes("GHIJ"));
        }
    }

    @Nested
    @DisplayName("Padding Tests")
    class PaddingTests {

        @Test
        @DisplayName("Should pad data using ISO 9797-1 Method 2")
        void shouldPadIso9797Method2() {
            byte[] data = {0x01, 0x02, 0x03};
            byte[] padded = cryptoService.padIso9797Method2(data, 8);

            assertEquals(8, padded.length);
            assertEquals((byte) 0x80, padded[3]);
            assertEquals((byte) 0x00, padded[4]);
        }

        @Test
        @DisplayName("Should unpad ISO 9797-1 Method 2")
        void shouldUnpadIso9797Method2() {
            byte[] padded = {0x01, 0x02, 0x03, (byte) 0x80, 0x00, 0x00, 0x00, 0x00};
            byte[] unpadded = cryptoService.unpadIso9797Method2(padded);

            assertArrayEquals(new byte[]{0x01, 0x02, 0x03}, unpadded);
        }

        @Test
        @DisplayName("Should throw exception for invalid padding")
        void shouldThrowForInvalidPadding() {
            byte[] invalidPadding = {0x01, 0x02, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00};

            assertThrows(CryptoException.class, () ->
                    cryptoService.unpadIso9797Method2(invalidPadding));
        }
    }
}
