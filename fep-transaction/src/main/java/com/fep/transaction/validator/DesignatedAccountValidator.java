package com.fep.transaction.validator;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validator for designated vs non-designated account transfers (約定/非約定轉帳驗證).
 *
 * <p>Per Taiwan banking regulations:
 * <ul>
 *   <li>Designated (約定) accounts: Pre-registered recipient accounts with higher limits</li>
 *   <li>Non-designated (非約定) accounts: Any recipient account with lower limits</li>
 * </ul>
 */
public class DesignatedAccountValidator implements TransactionValidator {

    private static final Logger log = LoggerFactory.getLogger(DesignatedAccountValidator.class);

    /** Default non-designated transfer limit per transaction (ATM/Internet Banking) */
    private static final BigDecimal DEFAULT_NON_DESIGNATED_LIMIT = new BigDecimal("30000");

    /** Non-designated transfer limit for mobile banking */
    private static final BigDecimal MOBILE_NON_DESIGNATED_LIMIT = new BigDecimal("50000");

    /** Designated transfer limit per transaction */
    private static final BigDecimal DESIGNATED_LIMIT = new BigDecimal("2000000");

    /** Daily non-designated transfer limit */
    private static final BigDecimal DAILY_NON_DESIGNATED_LIMIT = new BigDecimal("100000");

    /** Daily designated transfer limit */
    private static final BigDecimal DAILY_DESIGNATED_LIMIT = new BigDecimal("10000000");

    /** Night time restriction start (11 PM) */
    private static final LocalTime NIGHT_START = LocalTime.of(23, 0);

    /** Night time restriction end (6 AM) */
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    /** Night time non-designated limit */
    private static final BigDecimal NIGHT_NON_DESIGNATED_LIMIT = new BigDecimal("10000");

    /** Transaction types that require designated account validation */
    private static final Set<TransactionType> APPLICABLE_TYPES = Set.of(
            TransactionType.TRANSFER,
            TransactionType.P2P_TRANSFER
    );

    /** Channel-specific limits for non-designated transfers */
    private static final Map<String, BigDecimal> CHANNEL_LIMITS = Map.of(
            "ATM", new BigDecimal("30000"),
            "INTERNET", new BigDecimal("50000"),
            "MOBILE", new BigDecimal("50000"),
            "COUNTER", new BigDecimal("100000")
    );

    /** Simulated designated account registry (customerId -> Set of designated accounts) */
    private final Map<String, Set<String>> designatedAccounts = new ConcurrentHashMap<>();

    /** Track daily transfer amounts (sourceAccount -> daily total) */
    private final Map<String, DailyTransferTracker> dailyTransfers = new ConcurrentHashMap<>();

    /**
     * Daily transfer tracking information.
     */
    public static class DailyTransferTracker {
        private BigDecimal designatedTotal = BigDecimal.ZERO;
        private BigDecimal nonDesignatedTotal = BigDecimal.ZERO;
        private int designatedCount = 0;
        private int nonDesignatedCount = 0;

        public synchronized void addDesignated(BigDecimal amount) {
            designatedTotal = designatedTotal.add(amount);
            designatedCount++;
        }

        public synchronized void addNonDesignated(BigDecimal amount) {
            nonDesignatedTotal = nonDesignatedTotal.add(amount);
            nonDesignatedCount++;
        }

        public BigDecimal getDesignatedTotal() { return designatedTotal; }
        public BigDecimal getNonDesignatedTotal() { return nonDesignatedTotal; }
        public int getDesignatedCount() { return designatedCount; }
        public int getNonDesignatedCount() { return nonDesignatedCount; }

        public void reset() {
            designatedTotal = BigDecimal.ZERO;
            nonDesignatedTotal = BigDecimal.ZERO;
            designatedCount = 0;
            nonDesignatedCount = 0;
        }
    }

    @Override
    public void validate(TransactionRequest request) {
        // Only validate applicable transaction types
        if (!isApplicable(request)) {
            return;
        }

        String sourceAccount = request.getSourceAccount();
        String destAccount = request.getDestinationAccount();
        BigDecimal amount = request.getAmount();
        String channel = request.getChannel();

        if (sourceAccount == null || destAccount == null || amount == null) {
            return; // Let other validators handle missing fields
        }

        boolean isDesignated = isDesignatedAccount(sourceAccount, destAccount);

        // Override with explicit flag if provided
        if (request.getIsDesignatedAccount() != null) {
            isDesignated = request.getIsDesignatedAccount();
        }

        log.debug("[{}] Validating transfer: {} -> {}, amount={}, designated={}, channel={}",
                request.getTransactionId(),
                maskAccount(sourceAccount),
                maskAccount(destAccount),
                amount,
                isDesignated,
                channel);

        if (isDesignated) {
            validateDesignatedTransfer(request, amount);
        } else {
            validateNonDesignatedTransfer(request, amount, channel);
        }
    }

    /**
     * Validates a designated account transfer.
     */
    private void validateDesignatedTransfer(TransactionRequest request, BigDecimal amount) {
        // Check single transaction limit
        if (amount.compareTo(DESIGNATED_LIMIT) > 0) {
            throw TransactionException.exceedsLimit("designated transfer");
        }

        // Check daily limit
        DailyTransferTracker tracker = getOrCreateTracker(request.getSourceAccount());
        BigDecimal projectedTotal = tracker.getDesignatedTotal().add(amount);

        if (projectedTotal.compareTo(DAILY_DESIGNATED_LIMIT) > 0) {
            throw TransactionException.invalidRequest(
                    "Exceeds daily designated transfer limit (remaining: " +
                    DAILY_DESIGNATED_LIMIT.subtract(tracker.getDesignatedTotal()) + " TWD)");
        }

        log.debug("[{}] Designated transfer validated: amount={}, daily total={}",
                request.getTransactionId(),
                amount,
                projectedTotal);
    }

    /**
     * Validates a non-designated account transfer.
     */
    private void validateNonDesignatedTransfer(TransactionRequest request,
                                               BigDecimal amount, String channel) {
        // Determine applicable limit based on channel
        BigDecimal channelLimit = CHANNEL_LIMITS.getOrDefault(
                channel != null ? channel.toUpperCase() : "ATM",
                DEFAULT_NON_DESIGNATED_LIMIT);

        // Apply night time restrictions
        if (isNightTime()) {
            channelLimit = NIGHT_NON_DESIGNATED_LIMIT;
            log.debug("[{}] Night time restriction applied, limit={}",
                    request.getTransactionId(), channelLimit);
        }

        // Check single transaction limit
        if (amount.compareTo(channelLimit) > 0) {
            throw TransactionException.invalidRequest(
                    "Non-designated transfer exceeds limit: " + channelLimit + " TWD" +
                    (isNightTime() ? " (night time restriction)" : ""));
        }

        // Check daily limit
        DailyTransferTracker tracker = getOrCreateTracker(request.getSourceAccount());
        BigDecimal projectedTotal = tracker.getNonDesignatedTotal().add(amount);

        if (projectedTotal.compareTo(DAILY_NON_DESIGNATED_LIMIT) > 0) {
            throw TransactionException.invalidRequest(
                    "Exceeds daily non-designated transfer limit (remaining: " +
                    DAILY_NON_DESIGNATED_LIMIT.subtract(tracker.getNonDesignatedTotal()) + " TWD)");
        }

        log.debug("[{}] Non-designated transfer validated: amount={}, channel limit={}, daily total={}",
                request.getTransactionId(),
                amount,
                channelLimit,
                projectedTotal);
    }

    /**
     * Checks if current time is within night restriction period.
     */
    private boolean isNightTime() {
        LocalTime now = LocalTime.now();
        return now.isAfter(NIGHT_START) || now.isBefore(NIGHT_END);
    }

    /**
     * Checks if this validator applies to the request.
     */
    private boolean isApplicable(TransactionRequest request) {
        return request.getTransactionType() != null &&
               APPLICABLE_TYPES.contains(request.getTransactionType());
    }

    /**
     * Checks if the destination account is designated for the source account.
     */
    public boolean isDesignatedAccount(String sourceAccount, String destinationAccount) {
        Set<String> designated = designatedAccounts.get(sourceAccount);
        return designated != null && designated.contains(destinationAccount);
    }

    /**
     * Registers a designated account relationship.
     *
     * @param sourceAccount the source account
     * @param destinationAccount the destination account to designate
     */
    public void registerDesignatedAccount(String sourceAccount, String destinationAccount) {
        designatedAccounts.computeIfAbsent(sourceAccount, k -> ConcurrentHashMap.newKeySet())
                .add(destinationAccount);
        log.info("Registered designated account: {} -> {}",
                maskAccount(sourceAccount), maskAccount(destinationAccount));
    }

    /**
     * Removes a designated account relationship.
     *
     * @param sourceAccount the source account
     * @param destinationAccount the destination account to remove
     */
    public void removeDesignatedAccount(String sourceAccount, String destinationAccount) {
        Set<String> designated = designatedAccounts.get(sourceAccount);
        if (designated != null) {
            designated.remove(destinationAccount);
            log.info("Removed designated account: {} -> {}",
                    maskAccount(sourceAccount), maskAccount(destinationAccount));
        }
    }

    /**
     * Gets all designated accounts for a source account.
     *
     * @param sourceAccount the source account
     * @return set of designated destination accounts
     */
    public Set<String> getDesignatedAccounts(String sourceAccount) {
        return designatedAccounts.getOrDefault(sourceAccount, Set.of());
    }

    /**
     * Records a completed transfer for daily limit tracking.
     *
     * @param sourceAccount the source account
     * @param amount the transfer amount
     * @param isDesignated whether it's a designated transfer
     */
    public void recordTransfer(String sourceAccount, BigDecimal amount, boolean isDesignated) {
        DailyTransferTracker tracker = getOrCreateTracker(sourceAccount);
        if (isDesignated) {
            tracker.addDesignated(amount);
        } else {
            tracker.addNonDesignated(amount);
        }
    }

    /**
     * Gets daily transfer statistics for an account.
     *
     * @param sourceAccount the source account
     * @return the daily transfer tracker
     */
    public DailyTransferTracker getDailyTransferStats(String sourceAccount) {
        return dailyTransfers.get(sourceAccount);
    }

    /**
     * Resets daily limits (should be called at day boundary).
     */
    public void resetDailyLimits() {
        dailyTransfers.values().forEach(DailyTransferTracker::reset);
        log.info("Daily transfer limits reset for {} accounts", dailyTransfers.size());
    }

    /**
     * Gets or creates a daily tracker for the account.
     */
    private DailyTransferTracker getOrCreateTracker(String sourceAccount) {
        return dailyTransfers.computeIfAbsent(sourceAccount, k -> new DailyTransferTracker());
    }

    /**
     * Gets the applicable limit for a non-designated transfer.
     *
     * @param channel the transaction channel
     * @param isNightTime whether it's night time
     * @return the applicable limit
     */
    public BigDecimal getNonDesignatedLimit(String channel, boolean isNightTime) {
        if (isNightTime) {
            return NIGHT_NON_DESIGNATED_LIMIT;
        }
        return CHANNEL_LIMITS.getOrDefault(
                channel != null ? channel.toUpperCase() : "ATM",
                DEFAULT_NON_DESIGNATED_LIMIT);
    }

    /**
     * Gets the designated transfer limit.
     */
    public BigDecimal getDesignatedLimit() {
        return DESIGNATED_LIMIT;
    }

    /**
     * Gets the daily non-designated limit.
     */
    public BigDecimal getDailyNonDesignatedLimit() {
        return DAILY_NON_DESIGNATED_LIMIT;
    }

    /**
     * Gets the daily designated limit.
     */
    public BigDecimal getDailyDesignatedLimit() {
        return DAILY_DESIGNATED_LIMIT;
    }

    /**
     * Masks account number for logging.
     */
    private String maskAccount(String account) {
        if (account == null || account.length() < 8) {
            return "****";
        }
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }
}
