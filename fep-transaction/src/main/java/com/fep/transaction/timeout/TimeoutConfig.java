package com.fep.transaction.timeout;

import com.fep.transaction.enums.TransactionType;
import lombok.Data;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration for transaction timeout settings.
 */
@Data
public class TimeoutConfig {

    /** Default timeout in milliseconds */
    private static final long DEFAULT_TIMEOUT_MS = 30000;

    /** Timeout settings per transaction type */
    private final Map<TransactionType, Long> timeoutSettings;

    public TimeoutConfig() {
        this.timeoutSettings = new EnumMap<>(TransactionType.class);
        initializeDefaults();
    }

    /**
     * Initializes default timeout values based on FISC requirements.
     */
    private void initializeDefaults() {
        // Balance inquiry is fast
        timeoutSettings.put(TransactionType.BALANCE_INQUIRY, 5000L);

        // Standard ATM transactions
        timeoutSettings.put(TransactionType.WITHDRAWAL, 10000L);
        timeoutSettings.put(TransactionType.DEPOSIT, 10000L);

        // Transfer requires more time for interbank processing
        timeoutSettings.put(TransactionType.TRANSFER, 15000L);
        timeoutSettings.put(TransactionType.P2P_TRANSFER, 10000L);

        // Bill payment may involve external systems
        timeoutSettings.put(TransactionType.BILL_PAYMENT, 30000L);

        // Reversal should be processed quickly
        timeoutSettings.put(TransactionType.REVERSAL, 15000L);

        // Card transactions
        timeoutSettings.put(TransactionType.PURCHASE, 30000L);
        timeoutSettings.put(TransactionType.PURCHASE_WITH_CASHBACK, 30000L);

        // QR payment
        timeoutSettings.put(TransactionType.QR_PAYMENT, 10000L);
    }

    /**
     * Gets the timeout for a specific transaction type.
     */
    public long getTimeout(TransactionType type) {
        return timeoutSettings.getOrDefault(type, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Sets a custom timeout for a transaction type.
     */
    public void setTimeout(TransactionType type, long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        timeoutSettings.put(type, timeoutMs);
    }

    /**
     * Gets the default timeout value.
     */
    public long getDefaultTimeout() {
        return DEFAULT_TIMEOUT_MS;
    }
}
