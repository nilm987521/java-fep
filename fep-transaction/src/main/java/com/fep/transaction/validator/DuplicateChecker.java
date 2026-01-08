package com.fep.transaction.validator;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.exception.TransactionException;
import com.fep.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks for duplicate transactions to prevent double processing.
 */
public class DuplicateChecker implements TransactionValidator {

    private static final Logger log = LoggerFactory.getLogger(DuplicateChecker.class);

    /** Default time window for duplicate detection (minutes) */
    private static final int DEFAULT_WINDOW_MINUTES = 5;

    /** In-memory cache for fast duplicate detection */
    private final Map<String, LocalDateTime> recentTransactions;

    /** Optional repository for persistent duplicate checking */
    private final TransactionRepository repository;

    /** Time window for duplicate detection */
    private final int windowMinutes;

    public DuplicateChecker() {
        this(null, DEFAULT_WINDOW_MINUTES);
    }

    public DuplicateChecker(TransactionRepository repository) {
        this(repository, DEFAULT_WINDOW_MINUTES);
    }

    public DuplicateChecker(TransactionRepository repository, int windowMinutes) {
        this.recentTransactions = new ConcurrentHashMap<>();
        this.repository = repository;
        this.windowMinutes = windowMinutes;
    }

    @Override
    public void validate(TransactionRequest request) {
        String duplicateKey = generateDuplicateKey(request);

        // Check in-memory cache first
        if (isInMemoryDuplicate(duplicateKey)) {
            log.warn("[{}] Duplicate transaction detected (in-memory): key={}",
                    request.getTransactionId(), duplicateKey);
            throw TransactionException.duplicateTransaction();
        }

        // Check repository if available
        if (repository != null && isRepositoryDuplicate(request)) {
            log.warn("[{}] Duplicate transaction detected (repository): key={}",
                    request.getTransactionId(), duplicateKey);
            throw TransactionException.duplicateTransaction();
        }

        // Record this transaction
        recordTransaction(duplicateKey);
    }

    /**
     * Generates a unique key for duplicate detection.
     * Key is based on: RRN + STAN + Terminal ID
     */
    private String generateDuplicateKey(TransactionRequest request) {
        StringBuilder key = new StringBuilder();

        if (request.getRrn() != null) {
            key.append(request.getRrn());
        }
        key.append("|");

        if (request.getStan() != null) {
            key.append(request.getStan());
        }
        key.append("|");

        if (request.getTerminalId() != null) {
            key.append(request.getTerminalId());
        }

        return key.toString();
    }

    /**
     * Checks if transaction is duplicate in memory cache.
     */
    private boolean isInMemoryDuplicate(String key) {
        LocalDateTime existingTime = recentTransactions.get(key);
        if (existingTime == null) {
            return false;
        }

        // Check if within time window
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(windowMinutes);
        if (existingTime.isAfter(cutoff)) {
            return true;
        }

        // Entry is stale, remove it
        recentTransactions.remove(key);
        return false;
    }

    /**
     * Checks if transaction is duplicate in repository.
     */
    private boolean isRepositoryDuplicate(TransactionRequest request) {
        if (request.getRrn() == null || request.getStan() == null || request.getTerminalId() == null) {
            return false;
        }
        return repository.isDuplicate(
                request.getRrn(),
                request.getStan(),
                request.getTerminalId(),
                windowMinutes);
    }

    /**
     * Records the transaction in memory cache.
     */
    private void recordTransaction(String key) {
        recentTransactions.put(key, LocalDateTime.now());
        log.trace("Recorded transaction key: {}", key);
    }

    /**
     * Cleans up stale entries from the cache.
     */
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(windowMinutes);
        int beforeSize = recentTransactions.size();

        recentTransactions.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));

        int removed = beforeSize - recentTransactions.size();
        if (removed > 0) {
            log.debug("Cleaned up {} stale duplicate check entries", removed);
        }
    }

    /**
     * Clears all cached entries (for testing).
     */
    public void clear() {
        recentTransactions.clear();
    }

    /**
     * Gets the number of cached entries.
     */
    public int getCacheSize() {
        return recentTransactions.size();
    }

    @Override
    public int getOrder() {
        return 5; // Run early, before other validations
    }
}
