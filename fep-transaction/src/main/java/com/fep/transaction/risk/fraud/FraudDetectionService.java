package com.fep.transaction.risk.fraud;

import com.fep.transaction.domain.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for fraud detection using rule-based engine.
 */
@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    // Rules storage
    private final Map<String, FraudRule> rules = new ConcurrentHashMap<>();

    // Statistics
    private long totalChecks = 0;
    private long fraudsDetected = 0;

    public FraudDetectionService() {
        initializeDefaultRules();
    }

    /**
     * Initializes default fraud detection rules.
     */
    private void initializeDefaultRules() {
        // Velocity rule - too many transactions in short time
        addRule(FraudRule.builder()
                .ruleId("VEL-001")
                .name("High Velocity - Hourly")
                .description("More than 5 transactions in 1 hour")
                .type(FraudRuleType.VELOCITY)
                .priority(1)
                .riskScore(30)
                .parameters(Map.of("maxTransactions", 5, "periodHours", 1))
                .evaluator((req, ctx) -> ctx.getTransactionsLastHour() > 5)
                .build());

        addRule(FraudRule.builder()
                .ruleId("VEL-002")
                .name("High Velocity - Daily")
                .description("More than 20 transactions in 24 hours")
                .type(FraudRuleType.VELOCITY)
                .priority(2)
                .riskScore(25)
                .parameters(Map.of("maxTransactions", 20, "periodHours", 24))
                .evaluator((req, ctx) -> ctx.getTransactionsLast24Hours() > 20)
                .build());

        // Amount threshold rules
        addRule(FraudRule.builder()
                .ruleId("AMT-001")
                .name("Large Single Transaction")
                .description("Single transaction exceeds NT$100,000")
                .type(FraudRuleType.AMOUNT_THRESHOLD)
                .priority(2)
                .riskScore(20)
                .parameters(Map.of("threshold", 100000))
                .evaluator((req, ctx) -> req.getAmount() != null &&
                        req.getAmount().compareTo(new BigDecimal("100000")) > 0)
                .build());

        addRule(FraudRule.builder()
                .ruleId("AMT-002")
                .name("Unusual Amount Pattern")
                .description("Transaction amount is 3x higher than customer average")
                .type(FraudRuleType.AMOUNT_THRESHOLD)
                .priority(3)
                .riskScore(25)
                .parameters(Map.of("multiplier", 3))
                .evaluator((req, ctx) -> {
                    if (req.getAmount() == null || ctx.getAverageAmount().compareTo(BigDecimal.ZERO) == 0) {
                        return false;
                    }
                    BigDecimal threshold = ctx.getAverageAmount().multiply(new BigDecimal("3"));
                    return req.getAmount().compareTo(threshold) > 0;
                })
                .build());

        addRule(FraudRule.builder()
                .ruleId("AMT-003")
                .name("Daily Limit Approaching")
                .description("Daily total exceeds NT$300,000")
                .type(FraudRuleType.AMOUNT_THRESHOLD)
                .priority(2)
                .riskScore(15)
                .parameters(Map.of("dailyLimit", 300000))
                .evaluator((req, ctx) -> ctx.getAmountLast24Hours()
                        .compareTo(new BigDecimal("300000")) > 0)
                .build());

        // Geographic anomaly rules
        addRule(FraudRule.builder()
                .ruleId("GEO-001")
                .name("Impossible Travel")
                .description("Transaction location impossible given previous transaction time/location")
                .type(FraudRuleType.GEO_ANOMALY)
                .priority(1)
                .riskScore(50)
                .parameters(Map.of("maxSpeedKmh", 1000)) // Max realistic speed (airplane)
                .evaluator((req, ctx) -> {
                    if (ctx.getPreviousTransactionTime() == null || ctx.getDistanceFromPrevious() == 0) {
                        return false;
                    }
                    long minutesBetween = ChronoUnit.MINUTES.between(
                            ctx.getPreviousTransactionTime(), LocalDateTime.now());
                    if (minutesBetween <= 0) return false;
                    double speedKmh = (ctx.getDistanceFromPrevious() / minutesBetween) * 60;
                    return speedKmh > 1000; // Faster than airplane
                })
                .build());

        addRule(FraudRule.builder()
                .ruleId("GEO-002")
                .name("Multiple Countries")
                .description("Transactions in 3+ countries within 24 hours")
                .type(FraudRuleType.GEO_ANOMALY)
                .priority(2)
                .riskScore(35)
                .parameters(Map.of("maxCountries", 3))
                .evaluator((req, ctx) -> ctx.getCountriesLast24Hours() != null &&
                        ctx.getCountriesLast24Hours().size() >= 3)
                .build());

        // Time pattern rules
        addRule(FraudRule.builder()
                .ruleId("TIME-001")
                .name("Unusual Hour")
                .description("Transaction at unusual hour for this customer")
                .type(FraudRuleType.TIME_PATTERN)
                .priority(4)
                .riskScore(10)
                .evaluator((req, ctx) -> {
                    if (ctx.getUsualHours() == null || ctx.getUsualHours().isEmpty()) {
                        return false;
                    }
                    int currentHour = LocalDateTime.now().getHour();
                    return !ctx.getUsualHours().contains(currentHour);
                })
                .build());

        addRule(FraudRule.builder()
                .ruleId("TIME-002")
                .name("Late Night Transaction")
                .description("Transaction between 2 AM and 5 AM")
                .type(FraudRuleType.TIME_PATTERN)
                .priority(4)
                .riskScore(15)
                .evaluator((req, ctx) -> {
                    int hour = LocalDateTime.now().getHour();
                    return hour >= 2 && hour < 5;
                })
                .build());

        // Device rules
        addRule(FraudRule.builder()
                .ruleId("DEV-001")
                .name("Unknown Device")
                .description("Transaction from unrecognized device")
                .type(FraudRuleType.DEVICE_CHECK)
                .priority(3)
                .riskScore(20)
                .evaluator((req, ctx) -> !ctx.isKnownDevice())
                .build());

        addRule(FraudRule.builder()
                .ruleId("DEV-002")
                .name("Multiple Cards on Device")
                .description("More than 3 different cards used on this device")
                .type(FraudRuleType.DEVICE_CHECK)
                .priority(2)
                .riskScore(30)
                .parameters(Map.of("maxCards", 3))
                .evaluator((req, ctx) -> ctx.getCardsOnDevice() > 3)
                .build());

        // Behavioral rules
        addRule(FraudRule.builder()
                .ruleId("BEH-001")
                .name("Dormant Account Activation")
                .description("First transaction after 90+ days of inactivity")
                .type(FraudRuleType.DORMANT_ACCOUNT)
                .priority(2)
                .riskScore(25)
                .evaluator((req, ctx) -> ctx.isDormantAccount())
                .build());

        addRule(FraudRule.builder()
                .ruleId("BEH-002")
                .name("New Card High Value")
                .description("High value transaction on new card (< 30 days)")
                .type(FraudRuleType.FIRST_TIME)
                .priority(2)
                .riskScore(20)
                .evaluator((req, ctx) -> ctx.isNewCard() && req.getAmount() != null &&
                        req.getAmount().compareTo(new BigDecimal("50000")) > 0)
                .build());

        // Merchant risk
        addRule(FraudRule.builder()
                .ruleId("MERCH-001")
                .name("High Risk Merchant")
                .description("Transaction at high-risk merchant")
                .type(FraudRuleType.MERCHANT_RISK)
                .priority(3)
                .riskScore(15)
                .evaluator((req, ctx) -> ctx.getMerchantRiskScore() >= 70)
                .build());

        addRule(FraudRule.builder()
                .ruleId("MERCH-002")
                .name("First Time Merchant Large Amount")
                .description("First transaction at this merchant with large amount")
                .type(FraudRuleType.MERCHANT_RISK)
                .priority(3)
                .riskScore(15)
                .evaluator((req, ctx) -> ctx.isFirstTimeMerchant() && req.getAmount() != null &&
                        req.getAmount().compareTo(new BigDecimal("30000")) > 0)
                .build());

        log.info("Initialized {} default fraud detection rules", rules.size());
    }

    /**
     * Adds a fraud detection rule.
     */
    public void addRule(FraudRule rule) {
        rules.put(rule.getRuleId(), rule);
        log.debug("Added fraud rule: {} - {}", rule.getRuleId(), rule.getName());
    }

    /**
     * Removes a fraud detection rule.
     */
    public boolean removeRule(String ruleId) {
        FraudRule removed = rules.remove(ruleId);
        if (removed != null) {
            log.info("Removed fraud rule: {}", ruleId);
            return true;
        }
        return false;
    }

    /**
     * Enables or disables a rule.
     */
    public boolean setRuleActive(String ruleId, boolean active) {
        FraudRule rule = rules.get(ruleId);
        if (rule != null) {
            rule.setActive(active);
            log.info("{} fraud rule: {}", active ? "Enabled" : "Disabled", ruleId);
            return true;
        }
        return false;
    }

    /**
     * Performs fraud check on a transaction.
     */
    public FraudCheckResult check(TransactionRequest request, FraudRuleContext context) {
        long startTime = System.currentTimeMillis();
        totalChecks++;

        List<FraudCheckResult.TriggeredRule> triggeredRules = new ArrayList<>();
        int totalScore = 0;

        // Sort rules by priority and evaluate
        List<FraudRule> sortedRules = rules.values().stream()
                .filter(FraudRule::isActive)
                .sorted(Comparator.comparingInt(FraudRule::getPriority))
                .toList();

        for (FraudRule rule : sortedRules) {
            try {
                if (rule.evaluate(request, context)) {
                    FraudCheckResult.TriggeredRule triggered = FraudCheckResult.TriggeredRule.builder()
                            .ruleId(rule.getRuleId())
                            .ruleName(rule.getName())
                            .ruleType(rule.getType())
                            .riskScoreContribution(rule.getRiskScore())
                            .reason(rule.getDescription())
                            .build();
                    triggeredRules.add(triggered);
                    totalScore += rule.getRiskScore();

                    log.debug("Rule triggered: {} (+{} score)", rule.getRuleId(), rule.getRiskScore());
                }
            } catch (Exception e) {
                log.warn("Error evaluating rule {}: {}", rule.getRuleId(), e.getMessage());
            }
        }

        // Cap the score at 100
        totalScore = Math.min(100, totalScore);

        FraudCheckResult result;
        if (triggeredRules.isEmpty()) {
            result = FraudCheckResult.clean();
        } else {
            result = FraudCheckResult.suspected(totalScore, triggeredRules,
                    String.format("%d fraud indicator(s) detected", triggeredRules.size()));
            fraudsDetected++;
        }

        result.setCheckDurationMs(System.currentTimeMillis() - startTime);

        if (result.isFraudSuspected()) {
            log.warn("Fraud suspected for transaction: score={}, rules={}, action={}",
                    totalScore, triggeredRules.size(), result.getAction());
        }

        return result;
    }

    /**
     * Gets all rules.
     */
    public List<FraudRule> getAllRules() {
        return new ArrayList<>(rules.values());
    }

    /**
     * Gets active rules.
     */
    public List<FraudRule> getActiveRules() {
        return rules.values().stream()
                .filter(FraudRule::isActive)
                .toList();
    }

    /**
     * Gets a rule by ID.
     */
    public Optional<FraudRule> getRule(String ruleId) {
        return Optional.ofNullable(rules.get(ruleId));
    }

    /**
     * Gets rules by type.
     */
    public List<FraudRule> getRulesByType(FraudRuleType type) {
        return rules.values().stream()
                .filter(r -> r.getType() == type)
                .toList();
    }

    /**
     * Gets service statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRules", rules.size());
        stats.put("activeRules", getActiveRules().size());
        stats.put("totalChecks", totalChecks);
        stats.put("fraudsDetected", fraudsDetected);
        stats.put("detectionRate", totalChecks > 0 ?
                String.format("%.2f%%", (double) fraudsDetected / totalChecks * 100) : "0.00%");

        // Top triggered rules
        List<Map<String, Object>> topRules = rules.values().stream()
                .sorted((a, b) -> Long.compare(b.getTriggerCount(), a.getTriggerCount()))
                .limit(5)
                .map(r -> {
                    Map<String, Object> ruleInfo = new HashMap<>();
                    ruleInfo.put("ruleId", r.getRuleId());
                    ruleInfo.put("name", r.getName());
                    ruleInfo.put("triggerCount", r.getTriggerCount());
                    return ruleInfo;
                })
                .toList();
        stats.put("topTriggeredRules", topRules);

        return stats;
    }

    /**
     * Resets all rule trigger counts.
     */
    public void resetStatistics() {
        totalChecks = 0;
        fraudsDetected = 0;
        rules.values().forEach(r -> {
            r.setTriggerCount(0);
            r.setLastTriggeredAt(null);
        });
        log.info("Fraud detection statistics reset");
    }
}
