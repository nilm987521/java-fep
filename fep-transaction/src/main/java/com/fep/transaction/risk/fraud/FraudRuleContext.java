package com.fep.transaction.risk.fraud;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context data for fraud rule evaluation.
 * Contains historical data and computed metrics.
 */
@Data
@Builder
public class FraudRuleContext {

    /** Card number (masked) */
    private String cardNumber;

    /** Account number */
    private String accountNumber;

    /** Customer ID */
    private String customerId;

    // Velocity data
    /** Number of transactions in last hour */
    @Builder.Default
    private int transactionsLastHour = 0;

    /** Number of transactions in last 24 hours */
    @Builder.Default
    private int transactionsLast24Hours = 0;

    /** Total amount in last hour */
    @Builder.Default
    private BigDecimal amountLastHour = BigDecimal.ZERO;

    /** Total amount in last 24 hours */
    @Builder.Default
    private BigDecimal amountLast24Hours = BigDecimal.ZERO;

    // Geographic data
    /** Previous transaction country */
    private String previousCountry;

    /** Previous transaction city */
    private String previousCity;

    /** Previous transaction timestamp */
    private LocalDateTime previousTransactionTime;

    /** Distance from previous transaction (km) */
    @Builder.Default
    private double distanceFromPrevious = 0;

    /** Countries used in last 24 hours */
    private List<String> countriesLast24Hours;

    // Behavioral data
    /** Average transaction amount for this customer */
    @Builder.Default
    private BigDecimal averageAmount = BigDecimal.ZERO;

    /** Maximum transaction amount for this customer */
    @Builder.Default
    private BigDecimal maxAmount = BigDecimal.ZERO;

    /** Usual transaction hours (0-23) */
    private List<Integer> usualHours;

    /** Days since last transaction */
    @Builder.Default
    private int daysSinceLastTransaction = 0;

    /** Is this a new card? (less than 30 days) */
    @Builder.Default
    private boolean newCard = false;

    /** Is this a dormant account? (no activity > 90 days) */
    @Builder.Default
    private boolean dormantAccount = false;

    // Device data
    /** Device fingerprint */
    private String deviceFingerprint;

    /** Is this a known device? */
    @Builder.Default
    private boolean knownDevice = true;

    /** Number of cards used on this device */
    @Builder.Default
    private int cardsOnDevice = 1;

    // Merchant data
    /** Merchant risk score (0-100) */
    @Builder.Default
    private int merchantRiskScore = 0;

    /** Is this a first-time merchant for this customer? */
    @Builder.Default
    private boolean firstTimeMerchant = false;

    // Additional context
    /** Custom attributes */
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * Gets a custom attribute.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        Object value = attributes.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Sets a custom attribute.
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
}
