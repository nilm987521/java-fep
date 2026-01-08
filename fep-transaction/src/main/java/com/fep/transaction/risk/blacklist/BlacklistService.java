package com.fep.transaction.risk.blacklist;

import com.fep.transaction.domain.TransactionRequest;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing blacklists and checking transactions against them.
 */
@Service
public class BlacklistService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistService.class);

    // Blacklist storage by type
    private final Map<BlacklistType, Map<String, BlacklistEntry>> blacklists = new EnumMap<>(BlacklistType.class);

    // Cache for quick lookups
    private final Cache<String, BlacklistEntry> lookupCache;

    // Statistics
    private final Map<BlacklistType, Long> checkCounts = new EnumMap<>(BlacklistType.class);
    private final Map<BlacklistType, Long> hitCounts = new EnumMap<>(BlacklistType.class);

    public BlacklistService() {
        // Initialize blacklist maps
        for (BlacklistType type : BlacklistType.values()) {
            blacklists.put(type, new ConcurrentHashMap<>());
            checkCounts.put(type, 0L);
            hitCounts.put(type, 0L);
        }

        // Initialize cache with 15-minute TTL
        this.lookupCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(Duration.ofMinutes(15))
                .build();
    }

    /**
     * Adds an entry to the blacklist.
     */
    public BlacklistEntry addToBlacklist(BlacklistType type, String value, BlacklistReason reason,
                                          String description, String createdBy) {
        return addToBlacklist(type, value, reason, description, createdBy, null);
    }

    /**
     * Adds an entry to the blacklist with expiration.
     */
    public BlacklistEntry addToBlacklist(BlacklistType type, String value, BlacklistReason reason,
                                          String description, String createdBy, Duration validity) {
        String entryId = generateEntryId(type);
        String normalizedValue = normalizeValue(value, type);
        String maskedValue = BlacklistEntry.maskValue(value, type);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = validity != null ? now.plus(validity) : null;

        BlacklistEntry entry = BlacklistEntry.builder()
                .entryId(entryId)
                .type(type)
                .value(normalizedValue)
                .maskedValue(maskedValue)
                .reason(reason)
                .description(description)
                .createdAt(now)
                .expiresAt(expiresAt)
                .createdBy(createdBy)
                .active(true)
                .priority(determinePriority(reason))
                .build();

        blacklists.get(type).put(normalizedValue, entry);
        lookupCache.invalidate(createCacheKey(type, normalizedValue));

        log.info("Added {} blacklist entry: {} (reason: {})", type, maskedValue, reason);
        return entry;
    }

    /**
     * Removes an entry from the blacklist.
     */
    public boolean removeFromBlacklist(BlacklistType type, String value) {
        String normalizedValue = normalizeValue(value, type);
        BlacklistEntry removed = blacklists.get(type).remove(normalizedValue);

        if (removed != null) {
            lookupCache.invalidate(createCacheKey(type, normalizedValue));
            log.info("Removed {} blacklist entry: {}", type, removed.getMaskedValue());
            return true;
        }
        return false;
    }

    /**
     * Deactivates an entry without removing it.
     */
    public boolean deactivateEntry(String entryId) {
        for (Map<String, BlacklistEntry> entries : blacklists.values()) {
            for (BlacklistEntry entry : entries.values()) {
                if (entryId.equals(entry.getEntryId())) {
                    entry.setActive(false);
                    lookupCache.invalidate(createCacheKey(entry.getType(), entry.getValue()));
                    log.info("Deactivated blacklist entry: {}", entryId);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if a value is on the blacklist.
     */
    public BlacklistCheckResult check(BlacklistType type, String value) {
        long startTime = System.currentTimeMillis();
        incrementCheckCount(type);

        String normalizedValue = normalizeValue(value, type);
        String cacheKey = createCacheKey(type, normalizedValue);

        // Check cache first
        BlacklistEntry cached = lookupCache.getIfPresent(cacheKey);
        if (cached != null && cached.isEffective()) {
            cached.recordHit();
            incrementHitCount(type);
            BlacklistCheckResult result = BlacklistCheckResult.blocked(cached,
                    String.format("%s is on %s", cached.getMaskedValue(), type.getDescription()));
            result.setCheckDurationMs(System.currentTimeMillis() - startTime);
            return result;
        }

        // Check the actual blacklist
        BlacklistEntry entry = blacklists.get(type).get(normalizedValue);
        if (entry != null && entry.isEffective()) {
            entry.recordHit();
            lookupCache.put(cacheKey, entry);
            incrementHitCount(type);
            BlacklistCheckResult result = BlacklistCheckResult.blocked(entry,
                    String.format("%s is on %s", entry.getMaskedValue(), type.getDescription()));
            result.setCheckDurationMs(System.currentTimeMillis() - startTime);
            return result;
        }

        BlacklistCheckResult result = BlacklistCheckResult.notBlocked();
        result.setCheckDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * Performs comprehensive blacklist check for a transaction.
     */
    public BlacklistCheckResult checkTransaction(TransactionRequest request) {
        long startTime = System.currentTimeMillis();
        List<BlacklistEntry> matches = new ArrayList<>();

        // Check card/PAN
        if (request.getPan() != null) {
            BlacklistCheckResult cardResult = check(BlacklistType.CARD, request.getPan());
            if (cardResult.isBlocked()) {
                matches.addAll(cardResult.getMatchedEntries());
            }
        }

        // Check source account
        if (request.getSourceAccount() != null) {
            BlacklistCheckResult accountResult = check(BlacklistType.ACCOUNT, request.getSourceAccount());
            if (accountResult.isBlocked()) {
                matches.addAll(accountResult.getMatchedEntries());
            }
        }

        // Check destination account
        if (request.getDestinationAccount() != null) {
            BlacklistCheckResult destResult = check(BlacklistType.ACCOUNT, request.getDestinationAccount());
            if (destResult.isBlocked()) {
                matches.addAll(destResult.getMatchedEntries());
            }
        }

        // Check merchant
        if (request.getMerchantId() != null) {
            BlacklistCheckResult merchantResult = check(BlacklistType.MERCHANT, request.getMerchantId());
            if (merchantResult.isBlocked()) {
                matches.addAll(merchantResult.getMatchedEntries());
            }
        }

        // Check terminal
        if (request.getTerminalId() != null) {
            BlacklistCheckResult terminalResult = check(BlacklistType.TERMINAL, request.getTerminalId());
            if (terminalResult.isBlocked()) {
                matches.addAll(terminalResult.getMatchedEntries());
            }
        }

        BlacklistCheckResult result;
        if (matches.isEmpty()) {
            result = BlacklistCheckResult.notBlocked();
        } else {
            result = BlacklistCheckResult.blockedMultiple(matches,
                    String.format("Transaction blocked: %d blacklist match(es) found", matches.size()));
        }
        result.setCheckDurationMs(System.currentTimeMillis() - startTime);

        return result;
    }

    /**
     * Checks if a card is blacklisted.
     */
    public BlacklistCheckResult checkCard(String pan) {
        return check(BlacklistType.CARD, pan);
    }

    /**
     * Checks if an account is blacklisted.
     */
    public BlacklistCheckResult checkAccount(String accountNumber) {
        return check(BlacklistType.ACCOUNT, accountNumber);
    }

    /**
     * Checks if a merchant is blacklisted.
     */
    public BlacklistCheckResult checkMerchant(String merchantId) {
        return check(BlacklistType.MERCHANT, merchantId);
    }

    /**
     * Gets all entries for a blacklist type.
     */
    public List<BlacklistEntry> getEntries(BlacklistType type) {
        return new ArrayList<>(blacklists.get(type).values());
    }

    /**
     * Gets active entries for a blacklist type.
     */
    public List<BlacklistEntry> getActiveEntries(BlacklistType type) {
        return blacklists.get(type).values().stream()
                .filter(BlacklistEntry::isEffective)
                .collect(Collectors.toList());
    }

    /**
     * Gets entry by ID.
     */
    public Optional<BlacklistEntry> getEntryById(String entryId) {
        for (Map<String, BlacklistEntry> entries : blacklists.values()) {
            for (BlacklistEntry entry : entries.values()) {
                if (entryId.equals(entry.getEntryId())) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Searches entries by reason.
     */
    public List<BlacklistEntry> searchByReason(BlacklistReason reason) {
        List<BlacklistEntry> results = new ArrayList<>();
        for (Map<String, BlacklistEntry> entries : blacklists.values()) {
            entries.values().stream()
                    .filter(e -> e.getReason() == reason)
                    .forEach(results::add);
        }
        return results;
    }

    /**
     * Gets entries that are about to expire.
     */
    public List<BlacklistEntry> getExpiringEntries(Duration within) {
        LocalDateTime threshold = LocalDateTime.now().plus(within);
        List<BlacklistEntry> expiring = new ArrayList<>();

        for (Map<String, BlacklistEntry> entries : blacklists.values()) {
            entries.values().stream()
                    .filter(e -> e.getExpiresAt() != null && e.getExpiresAt().isBefore(threshold))
                    .filter(BlacklistEntry::isEffective)
                    .forEach(expiring::add);
        }
        return expiring;
    }

    /**
     * Cleans up expired entries.
     */
    public int cleanupExpiredEntries() {
        int removed = 0;
        for (Map<String, BlacklistEntry> entries : blacklists.values()) {
            Iterator<Map.Entry<String, BlacklistEntry>> it = entries.entrySet().iterator();
            while (it.hasNext()) {
                BlacklistEntry entry = it.next().getValue();
                if (entry.isExpired()) {
                    it.remove();
                    lookupCache.invalidate(createCacheKey(entry.getType(), entry.getValue()));
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired blacklist entries", removed);
        }
        return removed;
    }

    /**
     * Gets statistics for all blacklists.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        for (BlacklistType type : BlacklistType.values()) {
            Map<String, Object> typeStats = new HashMap<>();
            Map<String, BlacklistEntry> entries = blacklists.get(type);

            typeStats.put("totalEntries", entries.size());
            typeStats.put("activeEntries", entries.values().stream().filter(BlacklistEntry::isEffective).count());
            typeStats.put("checksPerformed", checkCounts.get(type));
            typeStats.put("hitsRecorded", hitCounts.get(type));

            double hitRate = checkCounts.get(type) > 0
                    ? (double) hitCounts.get(type) / checkCounts.get(type) * 100
                    : 0;
            typeStats.put("hitRatePercent", String.format("%.2f", hitRate));

            stats.put(type.name(), typeStats);
        }

        return stats;
    }

    /**
     * Gets top hit entries.
     */
    public List<BlacklistEntry> getTopHitEntries(int limit) {
        return blacklists.values().stream()
                .flatMap(m -> m.values().stream())
                .filter(BlacklistEntry::isEffective)
                .sorted((a, b) -> Long.compare(b.getHitCount(), a.getHitCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Imports entries in bulk.
     */
    public int importEntries(List<BlacklistEntry> entries) {
        int imported = 0;
        for (BlacklistEntry entry : entries) {
            String normalizedValue = normalizeValue(entry.getValue(), entry.getType());
            entry.setValue(normalizedValue);
            if (entry.getEntryId() == null) {
                entry.setEntryId(generateEntryId(entry.getType()));
            }
            blacklists.get(entry.getType()).put(normalizedValue, entry);
            imported++;
        }
        log.info("Imported {} blacklist entries", imported);
        return imported;
    }

    /**
     * Exports all entries.
     */
    public List<BlacklistEntry> exportEntries() {
        return blacklists.values().stream()
                .flatMap(m -> m.values().stream())
                .collect(Collectors.toList());
    }

    /**
     * Clears all blacklists (use with caution!).
     */
    public void clearAll() {
        for (Map<String, BlacklistEntry> entries : blacklists.values()) {
            entries.clear();
        }
        lookupCache.invalidateAll();
        log.warn("All blacklists cleared!");
    }

    // Helper methods

    private String generateEntryId(BlacklistType type) {
        return type.getCode() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String normalizeValue(String value, BlacklistType type) {
        if (value == null) {
            return "";
        }
        // Remove spaces and convert to uppercase for consistent matching
        String normalized = value.replaceAll("\\s+", "").toUpperCase();

        // For cards, ensure only digits
        if (type == BlacklistType.CARD) {
            normalized = normalized.replaceAll("[^0-9]", "");
        }

        return normalized;
    }

    private String createCacheKey(BlacklistType type, String value) {
        return type.name() + ":" + value;
    }

    private int determinePriority(BlacklistReason reason) {
        return switch (reason) {
            case FRAUD_CONFIRMED, STOLEN, COUNTERFEIT, MONEY_LAUNDERING, SANCTIONS -> 1;
            case LOST, IDENTITY_THEFT, SECURITY_BREACH -> 2;
            case FRAUD_SUSPECTED, AML_VIOLATION, COMPROMISED -> 3;
            case UNUSUAL_ACTIVITY, EXCESSIVE_CHARGEBACKS -> 4;
            default -> 5;
        };
    }

    private void incrementCheckCount(BlacklistType type) {
        checkCounts.merge(type, 1L, Long::sum);
    }

    private void incrementHitCount(BlacklistType type) {
        hitCounts.merge(type, 1L, Long::sum);
    }
}
