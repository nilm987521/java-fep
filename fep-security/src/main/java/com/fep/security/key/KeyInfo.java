package com.fep.security.key;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Information about a cryptographic key.
 */
@Data
@Builder
public class KeyInfo {

    /** Unique key identifier */
    private String keyId;

    /** Key type */
    private KeyType keyType;

    /** Key alias/name for reference */
    private String alias;

    /** Key Check Value (KCV) for verification */
    private String kcv;

    /** Key status */
    private KeyStatus status;

    /** Creation timestamp */
    private LocalDateTime createdAt;

    /** Expiration timestamp */
    private LocalDateTime expiresAt;

    /** Last used timestamp */
    private LocalDateTime lastUsedAt;

    /** Version number for key rotation */
    private int version;

    /** Associated terminal/institution ID */
    private String associatedId;

    /** Description or notes */
    private String description;

    /** Whether key is stored in HSM */
    private boolean hsmStored;

    /** Key length in bytes */
    private int keyLength;

    /**
     * Checks if the key has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Checks if the key is active and usable.
     */
    public boolean isActive() {
        return status == KeyStatus.ACTIVE && !isExpired();
    }
}
