package com.fep.security.key;

import com.fep.security.crypto.CryptoException;
import com.fep.security.crypto.CryptoService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Key management service for managing cryptographic keys.
 * In production, keys should be stored in HSM. This implementation
 * provides a software-based key storage for development/testing.
 */
@Service
public class KeyManager {

    private static final Logger log = LoggerFactory.getLogger(KeyManager.class);

    private final CryptoService cryptoService;

    // Key storage (in production, this would be HSM)
    private final Map<String, byte[]> keyStore = new ConcurrentHashMap<>();
    private final Map<String, KeyInfo> keyInfoStore = new ConcurrentHashMap<>();

    // Key cache for performance
    private final Cache<String, byte[]> keyCache;

    // Current working keys by type
    private final Map<KeyType, String> currentKeys = new ConcurrentHashMap<>();

    public KeyManager(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
        this.keyCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();
    }

    /**
     * Generates a new key of the specified type.
     */
    public KeyInfo generateKey(KeyType keyType, String alias) {
        return generateKey(keyType, alias, null);
    }

    /**
     * Generates a new key with optional expiration.
     */
    public KeyInfo generateKey(KeyType keyType, String alias, Duration validity) {
        String keyId = generateKeyId(keyType);
        byte[] keyData = cryptoService.generateTdesKey();
        String kcv = calculateKcv(keyData);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = validity != null ? now.plus(validity) : null;

        KeyInfo keyInfo = KeyInfo.builder()
                .keyId(keyId)
                .keyType(keyType)
                .alias(alias)
                .kcv(kcv)
                .status(KeyStatus.ACTIVE)
                .createdAt(now)
                .expiresAt(expiresAt)
                .version(1)
                .keyLength(keyData.length)
                .hsmStored(false)
                .build();

        // Store key
        keyStore.put(keyId, keyData);
        keyInfoStore.put(keyId, keyInfo);

        log.info("Generated new {} key: {} (KCV: {})", keyType, keyId, kcv);
        return keyInfo;
    }

    /**
     * Imports a key from external source.
     */
    public KeyInfo importKey(KeyType keyType, String alias, byte[] keyData) {
        validateKeyLength(keyData, keyType);

        String keyId = generateKeyId(keyType);
        String kcv = calculateKcv(keyData);

        KeyInfo keyInfo = KeyInfo.builder()
                .keyId(keyId)
                .keyType(keyType)
                .alias(alias)
                .kcv(kcv)
                .status(KeyStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .version(1)
                .keyLength(keyData.length)
                .hsmStored(false)
                .build();

        keyStore.put(keyId, keyData.clone());
        keyInfoStore.put(keyId, keyInfo);

        log.info("Imported {} key: {} (KCV: {})", keyType, keyId, kcv);
        return keyInfo;
    }

    /**
     * Imports an encrypted key (encrypted under KEK).
     */
    public KeyInfo importEncryptedKey(KeyType keyType, String alias, byte[] encryptedKey, String kekId) {
        byte[] kek = getKey(kekId);
        if (kek == null) {
            throw new CryptoException("KEK not found: " + kekId);
        }

        byte[] decryptedKey = cryptoService.decryptTdes(kek, encryptedKey);
        return importKey(keyType, alias, decryptedKey);
    }

    /**
     * Exports a key encrypted under KEK.
     */
    public byte[] exportKeyEncrypted(String keyId, String kekId) {
        byte[] key = getKey(keyId);
        byte[] kek = getKey(kekId);

        if (key == null) {
            throw new CryptoException("Key not found: " + keyId);
        }
        if (kek == null) {
            throw new CryptoException("KEK not found: " + kekId);
        }

        return cryptoService.encryptTdes(kek, key);
    }

    /**
     * Gets a key by ID.
     */
    public byte[] getKey(String keyId) {
        // Check cache first
        byte[] cached = keyCache.getIfPresent(keyId);
        if (cached != null) {
            return cached.clone();
        }

        // Get from store
        byte[] key = keyStore.get(keyId);
        if (key != null) {
            keyCache.put(keyId, key);
            return key.clone();
        }

        return null;
    }

    /**
     * Gets key information by ID.
     */
    public KeyInfo getKeyInfo(String keyId) {
        return keyInfoStore.get(keyId);
    }

    /**
     * Gets key by alias.
     */
    public byte[] getKeyByAlias(String alias) {
        for (Map.Entry<String, KeyInfo> entry : keyInfoStore.entrySet()) {
            if (alias.equals(entry.getValue().getAlias())) {
                return getKey(entry.getKey());
            }
        }
        return null;
    }

    /**
     * Gets key info by alias.
     */
    public KeyInfo getKeyInfoByAlias(String alias) {
        for (KeyInfo keyInfo : keyInfoStore.values()) {
            if (alias.equals(keyInfo.getAlias())) {
                return keyInfo;
            }
        }
        return null;
    }

    /**
     * Sets the current working key for a type.
     */
    public void setCurrentKey(KeyType keyType, String keyId) {
        KeyInfo keyInfo = getKeyInfo(keyId);
        if (keyInfo == null) {
            throw new CryptoException("Key not found: " + keyId);
        }
        if (keyInfo.getKeyType() != keyType) {
            throw new CryptoException("Key type mismatch: expected " + keyType + ", got " + keyInfo.getKeyType());
        }
        currentKeys.put(keyType, keyId);
        log.info("Set current {} key to: {}", keyType, keyId);
    }

    /**
     * Gets the current working key for a type.
     */
    public byte[] getCurrentKey(KeyType keyType) {
        String keyId = currentKeys.get(keyType);
        if (keyId == null) {
            throw new CryptoException("No current key set for type: " + keyType);
        }
        return getKey(keyId);
    }

    /**
     * Gets current key ID for a type.
     */
    public String getCurrentKeyId(KeyType keyType) {
        return currentKeys.get(keyType);
    }

    /**
     * Rotates a key - generates new key and marks old one as expired.
     */
    public KeyInfo rotateKey(String oldKeyId) {
        KeyInfo oldKeyInfo = getKeyInfo(oldKeyId);
        if (oldKeyInfo == null) {
            throw new CryptoException("Key not found: " + oldKeyId);
        }

        // Mark old key as rotating
        oldKeyInfo.setStatus(KeyStatus.ROTATING);

        // Generate new key
        String newAlias = oldKeyInfo.getAlias() + "_v" + (oldKeyInfo.getVersion() + 1);
        KeyInfo newKeyInfo = generateKey(oldKeyInfo.getKeyType(), newAlias);
        newKeyInfo.setVersion(oldKeyInfo.getVersion() + 1);

        // Mark old key as expired
        oldKeyInfo.setStatus(KeyStatus.EXPIRED);
        oldKeyInfo.setExpiresAt(LocalDateTime.now());

        // Update current key if applicable
        if (oldKeyId.equals(currentKeys.get(oldKeyInfo.getKeyType()))) {
            currentKeys.put(oldKeyInfo.getKeyType(), newKeyInfo.getKeyId());
        }

        log.info("Rotated key {} -> {} (version {})", oldKeyId, newKeyInfo.getKeyId(), newKeyInfo.getVersion());
        return newKeyInfo;
    }

    /**
     * Revokes a key.
     */
    public void revokeKey(String keyId) {
        KeyInfo keyInfo = getKeyInfo(keyId);
        if (keyInfo == null) {
            throw new CryptoException("Key not found: " + keyId);
        }

        keyInfo.setStatus(KeyStatus.REVOKED);
        keyCache.invalidate(keyId);

        // Remove from current keys if set
        currentKeys.remove(keyInfo.getKeyType(), keyId);

        log.info("Revoked key: {}", keyId);
    }

    /**
     * Destroys a key completely.
     */
    public void destroyKey(String keyId) {
        KeyInfo keyInfo = getKeyInfo(keyId);
        if (keyInfo == null) {
            return;
        }

        // Securely erase key data
        byte[] keyData = keyStore.remove(keyId);
        if (keyData != null) {
            Arrays.fill(keyData, (byte) 0);
        }

        keyCache.invalidate(keyId);
        keyInfo.setStatus(KeyStatus.DESTROYED);

        // Remove from current keys if set
        currentKeys.remove(keyInfo.getKeyType(), keyId);

        log.info("Destroyed key: {}", keyId);
    }

    /**
     * Verifies a key using its KCV.
     */
    public boolean verifyKcv(String keyId, String expectedKcv) {
        byte[] key = getKey(keyId);
        if (key == null) {
            return false;
        }
        String actualKcv = calculateKcv(key);
        return actualKcv.equals(expectedKcv);
    }

    /**
     * Calculates KCV (Key Check Value) for a key.
     * KCV is first 6 hex digits of encrypting 8 zero bytes.
     */
    public String calculateKcv(byte[] key) {
        byte[] zeros = new byte[8];
        byte[] encrypted = cryptoService.encryptTdes(key, zeros);
        return cryptoService.bytesToHex(encrypted).substring(0, 6);
    }

    /**
     * Lists all keys of a specific type.
     */
    public List<KeyInfo> listKeys(KeyType keyType) {
        return keyInfoStore.values().stream()
                .filter(info -> info.getKeyType() == keyType)
                .toList();
    }

    /**
     * Lists all active keys.
     */
    public List<KeyInfo> listActiveKeys() {
        return keyInfoStore.values().stream()
                .filter(KeyInfo::isActive)
                .toList();
    }

    /**
     * Checks for expired keys and returns count.
     */
    public int checkExpiredKeys() {
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (KeyInfo keyInfo : keyInfoStore.values()) {
            if (keyInfo.getStatus() == KeyStatus.ACTIVE && keyInfo.isExpired()) {
                keyInfo.setStatus(KeyStatus.EXPIRED);
                count++;
                log.warn("Key {} has expired", keyInfo.getKeyId());
            }
        }
        return count;
    }

    /**
     * Gets statistics about key inventory.
     */
    public Map<String, Object> getKeyStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalKeys", keyInfoStore.size());

        Map<KeyType, Long> byType = new EnumMap<>(KeyType.class);
        Map<KeyStatus, Long> byStatus = new EnumMap<>(KeyStatus.class);

        for (KeyInfo keyInfo : keyInfoStore.values()) {
            byType.merge(keyInfo.getKeyType(), 1L, Long::sum);
            byStatus.merge(keyInfo.getStatus(), 1L, Long::sum);
        }

        stats.put("byType", byType);
        stats.put("byStatus", byStatus);
        return stats;
    }

    private String generateKeyId(KeyType keyType) {
        return keyType.getShortName() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void validateKeyLength(byte[] key, KeyType keyType) {
        if (key == null || key.length != keyType.getKeyLength()) {
            throw new CryptoException(
                    String.format("%s key must be %d bytes", keyType, keyType.getKeyLength()));
        }
    }
}
