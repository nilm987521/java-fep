package com.fep.transaction.risk.blacklist;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Represents an entry in a blacklist.
 */
@Data
@Builder
public class BlacklistEntry {

    /** Unique identifier for the entry */
    private String entryId;

    /** Type of blacklist */
    private BlacklistType type;

    /** The value being blacklisted (card number, account number, etc.) */
    private String value;

    /** Masked value for display */
    private String maskedValue;

    /** Reason for blacklisting */
    private BlacklistReason reason;

    /** Additional description or notes */
    private String description;

    /** When the entry was added */
    private LocalDateTime createdAt;

    /** When the entry expires (null = permanent) */
    private LocalDateTime expiresAt;

    /** Who added the entry */
    private String createdBy;

    /** Associated case/incident number */
    private String caseNumber;

    /** Whether the entry is active */
    @Builder.Default
    private boolean active = true;

    /** Priority level (1-5, 1 being highest) */
    @Builder.Default
    private int priority = 3;

    /** Source of the blacklist entry */
    private String source;

    /** Number of times this entry has been matched */
    @Builder.Default
    private long hitCount = 0;

    /** Last time this entry was matched */
    private LocalDateTime lastHitAt;

    /**
     * Checks if the entry has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Checks if the entry is currently effective.
     */
    public boolean isEffective() {
        return active && !isExpired();
    }

    /**
     * Records a hit on this blacklist entry.
     */
    public void recordHit() {
        this.hitCount++;
        this.lastHitAt = LocalDateTime.now();
    }

    /**
     * Creates a masked version of the value for display.
     */
    public static String maskValue(String value, BlacklistType type) {
        if (value == null || value.length() < 4) {
            return "****";
        }

        return switch (type) {
            case CARD -> value.substring(0, 4) + "****" + value.substring(value.length() - 4);
            case ACCOUNT -> value.substring(0, 3) + "****" + value.substring(value.length() - 3);
            case MERCHANT -> value.length() > 6 ? value.substring(0, 3) + "***" + value.substring(value.length() - 3) : "***";
            case IP_ADDRESS -> {
                String[] parts = value.split("\\.");
                if (parts.length == 4) {
                    yield parts[0] + ".***.***." + parts[3];
                }
                yield "***";
            }
            default -> value.substring(0, 2) + "****" + value.substring(value.length() - 2);
        };
    }
}
