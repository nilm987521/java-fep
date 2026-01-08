package com.fep.transaction.limit;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages transaction limits and usage tracking.
 */
public class LimitManager {

    private static final Logger log = LoggerFactory.getLogger(LimitManager.class);

    /** Default limits by transaction type */
    private final Map<TransactionType, Map<LimitType, TransactionLimit>> defaultLimits;

    /** Account-specific limits: accountId -> transactionType -> limitType -> limit */
    private final Map<String, Map<TransactionType, Map<LimitType, TransactionLimit>>> accountLimits;

    /** Daily usage tracking: accountId -> transactionType -> date -> used amount */
    private final Map<String, Map<TransactionType, Map<String, BigDecimal>>> dailyUsage;

    /** Daily count tracking: accountId -> transactionType -> date -> count */
    private final Map<String, Map<TransactionType, Map<String, Integer>>> dailyCount;

    public LimitManager() {
        this.defaultLimits = new EnumMap<>(TransactionType.class);
        this.accountLimits = new ConcurrentHashMap<>();
        this.dailyUsage = new ConcurrentHashMap<>();
        this.dailyCount = new ConcurrentHashMap<>();
        initializeDefaultLimits();
    }

    /**
     * Initializes default limits for all transaction types.
     */
    private void initializeDefaultLimits() {
        // Withdrawal limits
        setDefaultLimit(TransactionType.WITHDRAWAL, LimitType.SINGLE_TRANSACTION, new BigDecimal("20000"));
        setDefaultLimit(TransactionType.WITHDRAWAL, LimitType.DAILY_CUMULATIVE, new BigDecimal("100000"));
        setDefaultLimit(TransactionType.WITHDRAWAL, LimitType.DAILY_COUNT, new BigDecimal("10"));

        // Transfer limits
        setDefaultLimit(TransactionType.TRANSFER, LimitType.SINGLE_TRANSACTION, new BigDecimal("2000000"));
        setDefaultLimit(TransactionType.TRANSFER, LimitType.DAILY_CUMULATIVE, new BigDecimal("3000000"));
        setDefaultLimit(TransactionType.TRANSFER, LimitType.NON_DESIGNATED_TRANSFER, new BigDecimal("50000"));

        // Deposit limits
        setDefaultLimit(TransactionType.DEPOSIT, LimitType.SINGLE_TRANSACTION, new BigDecimal("200000"));
        setDefaultLimit(TransactionType.DEPOSIT, LimitType.DAILY_CUMULATIVE, new BigDecimal("1000000"));

        // Bill payment limits
        setDefaultLimit(TransactionType.BILL_PAYMENT, LimitType.SINGLE_TRANSACTION, new BigDecimal("500000"));
        setDefaultLimit(TransactionType.BILL_PAYMENT, LimitType.DAILY_CUMULATIVE, new BigDecimal("2000000"));

        // QR Payment limits
        setDefaultLimit(TransactionType.QR_PAYMENT, LimitType.SINGLE_TRANSACTION, new BigDecimal("50000"));
        setDefaultLimit(TransactionType.QR_PAYMENT, LimitType.DAILY_CUMULATIVE, new BigDecimal("100000"));

        // P2P Transfer limits
        setDefaultLimit(TransactionType.P2P_TRANSFER, LimitType.SINGLE_TRANSACTION, new BigDecimal("50000"));
        setDefaultLimit(TransactionType.P2P_TRANSFER, LimitType.DAILY_CUMULATIVE, new BigDecimal("100000"));
    }

    /**
     * Sets a default limit for a transaction type.
     */
    public void setDefaultLimit(TransactionType txnType, LimitType limitType, BigDecimal value) {
        defaultLimits.computeIfAbsent(txnType, k -> new EnumMap<>(LimitType.class))
                .put(limitType, TransactionLimit.builder()
                        .transactionType(txnType)
                        .limitType(limitType)
                        .limitValue(value)
                        .build());
    }

    /**
     * Sets a custom limit for a specific account.
     */
    public void setAccountLimit(String accountId, TransactionType txnType,
                                LimitType limitType, BigDecimal value) {
        accountLimits.computeIfAbsent(accountId, k -> new EnumMap<>(TransactionType.class))
                .computeIfAbsent(txnType, k -> new EnumMap<>(LimitType.class))
                .put(limitType, TransactionLimit.builder()
                        .transactionType(txnType)
                        .limitType(limitType)
                        .limitValue(value)
                        .build());
    }

    /**
     * Checks all applicable limits for a transaction.
     */
    public LimitCheckResult checkLimits(TransactionRequest request) {
        String accountId = request.getSourceAccount();
        TransactionType txnType = request.getTransactionType();
        BigDecimal amount = request.getAmount();

        if (accountId == null || txnType == null || amount == null) {
            return LimitCheckResult.passed(BigDecimal.ZERO);
        }

        log.debug("[{}] Checking limits for {} {} TWD",
                request.getTransactionId(), txnType, amount);

        List<TransactionLimit> limitsToCheck = getLimitsForAccount(accountId, txnType);
        BigDecimal minRemaining = null;
        List<TransactionLimit> checkedLimits = new ArrayList<>();

        String today = java.time.LocalDate.now().toString();

        for (TransactionLimit limit : limitsToCheck) {
            // Update used value based on limit type
            updateLimitUsage(limit, accountId, txnType, today);
            checkedLimits.add(limit);

            // Check if limit would be exceeded
            // For count limits, check with 1 instead of amount
            BigDecimal checkAmount = limit.getLimitType() == LimitType.DAILY_COUNT ?
                    BigDecimal.ONE : amount;
            if (limit.wouldExceed(checkAmount)) {
                String message = String.format("Exceeds %s limit: %s + %s > %s",
                        limit.getLimitType().getChineseDescription(),
                        limit.getUsedValue(), amount, limit.getLimitValue());

                log.warn("[{}] {}", request.getTransactionId(), message);

                LimitCheckResult result = LimitCheckResult.failed(limit, message);
                result.setCheckedLimits(checkedLimits);
                return result;
            }

            // Track minimum remaining (skip count limits for remaining calculation)
            if (limit.getLimitType() != LimitType.DAILY_COUNT) {
                BigDecimal remaining = limit.getRemainingValue().subtract(amount);
                if (minRemaining == null || remaining.compareTo(minRemaining) < 0) {
                    minRemaining = remaining;
                }
            }
        }

        LimitCheckResult result = LimitCheckResult.passed(
                minRemaining != null ? minRemaining : BigDecimal.ZERO);
        result.setCheckedLimits(checkedLimits);

        log.debug("[{}] All limits passed, remaining: {}",
                request.getTransactionId(), result.getRemainingAmount());

        return result;
    }

    /**
     * Records usage after a successful transaction.
     */
    public void recordUsage(TransactionRequest request) {
        String accountId = request.getSourceAccount();
        TransactionType txnType = request.getTransactionType();
        BigDecimal amount = request.getAmount();

        if (accountId == null || txnType == null || amount == null) {
            return;
        }

        String today = java.time.LocalDate.now().toString();

        // Update daily usage
        dailyUsage.computeIfAbsent(accountId, k -> new EnumMap<>(TransactionType.class))
                .computeIfAbsent(txnType, k -> new ConcurrentHashMap<>())
                .merge(today, amount, BigDecimal::add);

        // Update daily count
        dailyCount.computeIfAbsent(accountId, k -> new EnumMap<>(TransactionType.class))
                .computeIfAbsent(txnType, k -> new ConcurrentHashMap<>())
                .merge(today, 1, Integer::sum);

        log.debug("[{}] Recorded usage: {} {} TWD for account {}",
                request.getTransactionId(), txnType, amount, maskAccount(accountId));
    }

    /**
     * Gets remaining limit for a specific limit type.
     */
    public BigDecimal getRemainingLimit(String accountId, TransactionType txnType, LimitType limitType) {
        TransactionLimit limit = getLimit(accountId, txnType, limitType);
        if (limit == null) {
            return BigDecimal.ZERO;
        }

        String today = java.time.LocalDate.now().toString();
        updateLimitUsage(limit, accountId, txnType, today);
        return limit.getRemainingValue();
    }

    /**
     * Gets all limits for an account and transaction type.
     */
    private List<TransactionLimit> getLimitsForAccount(String accountId, TransactionType txnType) {
        List<TransactionLimit> limits = new ArrayList<>();

        // Get account-specific limits first
        Map<TransactionType, Map<LimitType, TransactionLimit>> acctLimits = accountLimits.get(accountId);
        if (acctLimits != null && acctLimits.containsKey(txnType)) {
            limits.addAll(acctLimits.get(txnType).values());
        }

        // Fill in with default limits for any missing limit types
        Map<LimitType, TransactionLimit> defaults = defaultLimits.get(txnType);
        if (defaults != null) {
            Set<LimitType> existingTypes = limits.stream()
                    .map(TransactionLimit::getLimitType)
                    .collect(java.util.stream.Collectors.toSet());

            for (Map.Entry<LimitType, TransactionLimit> entry : defaults.entrySet()) {
                if (!existingTypes.contains(entry.getKey())) {
                    // Create a copy of the default limit
                    limits.add(TransactionLimit.builder()
                            .transactionType(txnType)
                            .limitType(entry.getKey())
                            .limitValue(entry.getValue().getLimitValue())
                            .build());
                }
            }
        }

        return limits;
    }

    /**
     * Gets a specific limit.
     */
    private TransactionLimit getLimit(String accountId, TransactionType txnType, LimitType limitType) {
        // Check account-specific first
        Map<TransactionType, Map<LimitType, TransactionLimit>> acctLimits = accountLimits.get(accountId);
        if (acctLimits != null) {
            Map<LimitType, TransactionLimit> typeLimits = acctLimits.get(txnType);
            if (typeLimits != null && typeLimits.containsKey(limitType)) {
                return typeLimits.get(limitType);
            }
        }

        // Fall back to default
        Map<LimitType, TransactionLimit> defaults = defaultLimits.get(txnType);
        if (defaults != null) {
            return defaults.get(limitType);
        }

        return null;
    }

    /**
     * Updates the used value on a limit based on tracked usage.
     */
    private void updateLimitUsage(TransactionLimit limit, String accountId,
                                  TransactionType txnType, String date) {
        switch (limit.getLimitType()) {
            case DAILY_CUMULATIVE, NON_DESIGNATED_TRANSFER -> {
                BigDecimal used = getDailyUsage(accountId, txnType, date);
                limit.setUsedValue(used);
            }
            case DAILY_COUNT -> {
                int count = getDailyCount(accountId, txnType, date);
                limit.setUsedValue(BigDecimal.valueOf(count));
            }
            case SINGLE_TRANSACTION, MONTHLY_CUMULATIVE -> {
                // Single transaction doesn't accumulate
                // Monthly would need different date handling
                limit.setUsedValue(BigDecimal.ZERO);
            }
        }
    }

    /**
     * Gets daily usage for an account and transaction type.
     */
    private BigDecimal getDailyUsage(String accountId, TransactionType txnType, String date) {
        return Optional.ofNullable(dailyUsage.get(accountId))
                .map(m -> m.get(txnType))
                .map(m -> m.get(date))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Gets daily transaction count.
     */
    private int getDailyCount(String accountId, TransactionType txnType, String date) {
        return Optional.ofNullable(dailyCount.get(accountId))
                .map(m -> m.get(txnType))
                .map(m -> m.get(date))
                .orElse(0);
    }

    /**
     * Clears daily usage (for testing or daily reset).
     */
    public void clearDailyUsage() {
        dailyUsage.clear();
        dailyCount.clear();
        log.info("Daily usage cleared");
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 8) {
            return "****";
        }
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }
}
