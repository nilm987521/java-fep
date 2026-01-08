package com.fep.security.key;

import com.fep.security.crypto.CryptoException;
import com.fep.security.crypto.CryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KeyManager.
 */
class KeyManagerTest {

    private CryptoService cryptoService;
    private KeyManager keyManager;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService();
        keyManager = new KeyManager(cryptoService);
    }

    @Nested
    @DisplayName("Key Generation Tests")
    class KeyGenerationTests {

        @Test
        @DisplayName("Should generate a new key")
        void shouldGenerateKey() {
            KeyInfo keyInfo = keyManager.generateKey(KeyType.PEK, "test-pek");

            assertNotNull(keyInfo);
            assertNotNull(keyInfo.getKeyId());
            assertEquals(KeyType.PEK, keyInfo.getKeyType());
            assertEquals("test-pek", keyInfo.getAlias());
            assertEquals(KeyStatus.ACTIVE, keyInfo.getStatus());
            assertNotNull(keyInfo.getKcv());
            assertEquals(6, keyInfo.getKcv().length());
        }

        @Test
        @DisplayName("Should generate key with expiration")
        void shouldGenerateKeyWithExpiration() {
            KeyInfo keyInfo = keyManager.generateKey(KeyType.MAK, "test-mak", Duration.ofDays(30));

            assertNotNull(keyInfo.getExpiresAt());
            assertTrue(keyInfo.getExpiresAt().isAfter(keyInfo.getCreatedAt()));
        }

        @Test
        @DisplayName("Should generate unique key IDs")
        void shouldGenerateUniqueKeyIds() {
            KeyInfo key1 = keyManager.generateKey(KeyType.PEK, "pek1");
            KeyInfo key2 = keyManager.generateKey(KeyType.PEK, "pek2");

            assertNotEquals(key1.getKeyId(), key2.getKeyId());
        }
    }

    @Nested
    @DisplayName("Key Storage and Retrieval Tests")
    class KeyStorageTests {

        @Test
        @DisplayName("Should retrieve key by ID")
        void shouldRetrieveKeyById() {
            KeyInfo keyInfo = keyManager.generateKey(KeyType.DEK, "test-dek");

            byte[] key = keyManager.getKey(keyInfo.getKeyId());

            assertNotNull(key);
            assertEquals(24, key.length);
        }

        @Test
        @DisplayName("Should retrieve key info by ID")
        void shouldRetrieveKeyInfoById() {
            KeyInfo created = keyManager.generateKey(KeyType.TMK, "test-tmk");

            KeyInfo retrieved = keyManager.getKeyInfo(created.getKeyId());

            assertNotNull(retrieved);
            assertEquals(created.getKeyId(), retrieved.getKeyId());
            assertEquals(created.getAlias(), retrieved.getAlias());
        }

        @Test
        @DisplayName("Should retrieve key by alias")
        void shouldRetrieveKeyByAlias() {
            keyManager.generateKey(KeyType.PEK, "my-special-key");

            byte[] key = keyManager.getKeyByAlias("my-special-key");

            assertNotNull(key);
        }

        @Test
        @DisplayName("Should return null for non-existent key")
        void shouldReturnNullForNonExistentKey() {
            byte[] key = keyManager.getKey("non-existent-key");

            assertNull(key);
        }
    }

    @Nested
    @DisplayName("Key Import Tests")
    class KeyImportTests {

        @Test
        @DisplayName("Should import key")
        void shouldImportKey() {
            byte[] keyData = cryptoService.generateTdesKey();

            KeyInfo keyInfo = keyManager.importKey(KeyType.ZMK, "imported-zmk", keyData);

            assertNotNull(keyInfo);
            assertEquals(KeyType.ZMK, keyInfo.getKeyType());
            assertEquals("imported-zmk", keyInfo.getAlias());
        }

        @Test
        @DisplayName("Should verify imported key with KCV")
        void shouldVerifyImportedKeyWithKcv() {
            byte[] keyData = cryptoService.generateTdesKey();
            String expectedKcv = keyManager.calculateKcv(keyData);

            KeyInfo keyInfo = keyManager.importKey(KeyType.KEK, "test-kek", keyData);

            assertEquals(expectedKcv, keyInfo.getKcv());
            assertTrue(keyManager.verifyKcv(keyInfo.getKeyId(), expectedKcv));
        }

        @Test
        @DisplayName("Should import encrypted key")
        void shouldImportEncryptedKey() {
            // Create KEK
            KeyInfo kekInfo = keyManager.generateKey(KeyType.KEK, "test-kek");

            // Create and encrypt a key
            byte[] clearKey = cryptoService.generateTdesKey();
            byte[] kek = keyManager.getKey(kekInfo.getKeyId());
            byte[] encryptedKey = cryptoService.encryptTdes(kek, clearKey);

            // Import encrypted key
            KeyInfo imported = keyManager.importEncryptedKey(KeyType.PEK, "imported-pek",
                    encryptedKey, kekInfo.getKeyId());

            assertNotNull(imported);
            assertEquals(KeyType.PEK, imported.getKeyType());
        }
    }

    @Nested
    @DisplayName("Key Export Tests")
    class KeyExportTests {

        @Test
        @DisplayName("Should export key encrypted under KEK")
        void shouldExportKeyEncrypted() {
            KeyInfo kekInfo = keyManager.generateKey(KeyType.KEK, "export-kek");
            KeyInfo pekInfo = keyManager.generateKey(KeyType.PEK, "export-pek");

            byte[] encryptedKey = keyManager.exportKeyEncrypted(pekInfo.getKeyId(), kekInfo.getKeyId());

            assertNotNull(encryptedKey);
            assertEquals(24, encryptedKey.length);

            // Verify by decrypting
            byte[] kek = keyManager.getKey(kekInfo.getKeyId());
            byte[] decryptedKey = cryptoService.decryptTdes(kek, encryptedKey);
            byte[] originalKey = keyManager.getKey(pekInfo.getKeyId());
            assertArrayEquals(originalKey, decryptedKey);
        }

        @Test
        @DisplayName("Should throw exception for non-existent key on export")
        void shouldThrowForNonExistentKeyOnExport() {
            KeyInfo kekInfo = keyManager.generateKey(KeyType.KEK, "export-kek");

            assertThrows(CryptoException.class, () ->
                    keyManager.exportKeyEncrypted("non-existent", kekInfo.getKeyId()));
        }
    }

    @Nested
    @DisplayName("Current Key Management Tests")
    class CurrentKeyTests {

        @Test
        @DisplayName("Should set and get current key")
        void shouldSetAndGetCurrentKey() {
            KeyInfo pekInfo = keyManager.generateKey(KeyType.PEK, "current-pek");

            keyManager.setCurrentKey(KeyType.PEK, pekInfo.getKeyId());
            byte[] currentKey = keyManager.getCurrentKey(KeyType.PEK);

            assertNotNull(currentKey);
            assertArrayEquals(keyManager.getKey(pekInfo.getKeyId()), currentKey);
        }

        @Test
        @DisplayName("Should throw exception when no current key set")
        void shouldThrowWhenNoCurrentKeySet() {
            assertThrows(CryptoException.class, () ->
                    keyManager.getCurrentKey(KeyType.BDK));
        }

        @Test
        @DisplayName("Should get current key ID")
        void shouldGetCurrentKeyId() {
            KeyInfo makInfo = keyManager.generateKey(KeyType.MAK, "current-mak");
            keyManager.setCurrentKey(KeyType.MAK, makInfo.getKeyId());

            String currentKeyId = keyManager.getCurrentKeyId(KeyType.MAK);

            assertEquals(makInfo.getKeyId(), currentKeyId);
        }
    }

    @Nested
    @DisplayName("Key Rotation Tests")
    class KeyRotationTests {

        @Test
        @DisplayName("Should rotate key")
        void shouldRotateKey() {
            KeyInfo oldKey = keyManager.generateKey(KeyType.PEK, "rotate-pek");
            keyManager.setCurrentKey(KeyType.PEK, oldKey.getKeyId());

            KeyInfo newKey = keyManager.rotateKey(oldKey.getKeyId());

            assertNotNull(newKey);
            assertNotEquals(oldKey.getKeyId(), newKey.getKeyId());
            assertEquals(oldKey.getVersion() + 1, newKey.getVersion());
            assertEquals(KeyStatus.EXPIRED, keyManager.getKeyInfo(oldKey.getKeyId()).getStatus());
            assertEquals(newKey.getKeyId(), keyManager.getCurrentKeyId(KeyType.PEK));
        }
    }

    @Nested
    @DisplayName("Key Revocation Tests")
    class KeyRevocationTests {

        @Test
        @DisplayName("Should revoke key")
        void shouldRevokeKey() {
            KeyInfo keyInfo = keyManager.generateKey(KeyType.DEK, "revoke-dek");

            keyManager.revokeKey(keyInfo.getKeyId());

            assertEquals(KeyStatus.REVOKED, keyManager.getKeyInfo(keyInfo.getKeyId()).getStatus());
        }

        @Test
        @DisplayName("Should destroy key")
        void shouldDestroyKey() {
            KeyInfo keyInfo = keyManager.generateKey(KeyType.SK, "destroy-sk");

            keyManager.destroyKey(keyInfo.getKeyId());

            assertEquals(KeyStatus.DESTROYED, keyManager.getKeyInfo(keyInfo.getKeyId()).getStatus());
            assertNull(keyManager.getKey(keyInfo.getKeyId()));
        }
    }

    @Nested
    @DisplayName("Key Listing Tests")
    class KeyListingTests {

        @Test
        @DisplayName("Should list keys by type")
        void shouldListKeysByType() {
            keyManager.generateKey(KeyType.PEK, "pek1");
            keyManager.generateKey(KeyType.PEK, "pek2");
            keyManager.generateKey(KeyType.MAK, "mak1");

            List<KeyInfo> pekKeys = keyManager.listKeys(KeyType.PEK);

            assertEquals(2, pekKeys.size());
            assertTrue(pekKeys.stream().allMatch(k -> k.getKeyType() == KeyType.PEK));
        }

        @Test
        @DisplayName("Should list active keys")
        void shouldListActiveKeys() {
            KeyInfo active = keyManager.generateKey(KeyType.DEK, "active-dek");
            KeyInfo revoked = keyManager.generateKey(KeyType.DEK, "revoked-dek");
            keyManager.revokeKey(revoked.getKeyId());

            List<KeyInfo> activeKeys = keyManager.listActiveKeys();

            assertTrue(activeKeys.stream().anyMatch(k -> k.getKeyId().equals(active.getKeyId())));
            assertFalse(activeKeys.stream().anyMatch(k -> k.getKeyId().equals(revoked.getKeyId())));
        }

        @Test
        @DisplayName("Should get key statistics")
        void shouldGetKeyStats() {
            keyManager.generateKey(KeyType.PEK, "stat-pek");
            keyManager.generateKey(KeyType.MAK, "stat-mak");

            Map<String, Object> stats = keyManager.getKeyStats();

            assertNotNull(stats.get("totalKeys"));
            assertNotNull(stats.get("byType"));
            assertNotNull(stats.get("byStatus"));
        }
    }

    @Nested
    @DisplayName("KCV Tests")
    class KcvTests {

        @Test
        @DisplayName("Should calculate KCV")
        void shouldCalculateKcv() {
            byte[] key = cryptoService.generateTdesKey();

            String kcv = keyManager.calculateKcv(key);

            assertNotNull(kcv);
            assertEquals(6, kcv.length());
            assertTrue(kcv.matches("[0-9A-F]+"));
        }

        @Test
        @DisplayName("Should verify correct KCV")
        void shouldVerifyCorrectKcv() {
            KeyInfo keyInfo = keyManager.generateKey(KeyType.PEK, "kcv-test");

            assertTrue(keyManager.verifyKcv(keyInfo.getKeyId(), keyInfo.getKcv()));
        }

        @Test
        @DisplayName("Should fail verification for wrong KCV")
        void shouldFailVerificationForWrongKcv() {
            KeyInfo keyInfo = keyManager.generateKey(KeyType.PEK, "kcv-test2");

            assertFalse(keyManager.verifyKcv(keyInfo.getKeyId(), "ABCDEF"));
        }
    }
}
