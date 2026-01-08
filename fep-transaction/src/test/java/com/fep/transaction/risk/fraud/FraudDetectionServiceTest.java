package com.fep.transaction.risk.fraud;

import com.fep.transaction.domain.TransactionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FraudDetectionService Tests")
class FraudDetectionServiceTest {

    private FraudDetectionService fraudDetectionService;

    @BeforeEach
    void setUp() {
        fraudDetectionService = new FraudDetectionService();
    }

    @Nested
    @DisplayName("Default Rules")
    class DefaultRulesTests {

        @Test
        @DisplayName("Should have default rules initialized")
        void shouldHaveDefaultRulesInitialized() {
            List<FraudRule> rules = fraudDetectionService.getAllRules();

            assertFalse(rules.isEmpty());
            assertTrue(rules.size() >= 10);
        }

        @Test
        @DisplayName("Should have velocity rules")
        void shouldHaveVelocityRules() {
            List<FraudRule> velocityRules = fraudDetectionService.getRulesByType(FraudRuleType.VELOCITY);

            assertFalse(velocityRules.isEmpty());
            assertTrue(velocityRules.stream().anyMatch(r -> r.getRuleId().equals("VEL-001")));
        }

        @Test
        @DisplayName("Should have amount threshold rules")
        void shouldHaveAmountThresholdRules() {
            List<FraudRule> amountRules = fraudDetectionService.getRulesByType(FraudRuleType.AMOUNT_THRESHOLD);

            assertFalse(amountRules.isEmpty());
        }
    }

    @Nested
    @DisplayName("Fraud Detection")
    class FraudDetectionTests {

        @Test
        @DisplayName("Should detect clean transaction")
        void shouldDetectCleanTransaction() {
            // Disable time-based rules that depend on execution time
            fraudDetectionService.setRuleActive("TIME-001", false);
            fraudDetectionService.setRuleActive("TIME-002", false);

            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("1000"))
                    .build();

            FraudRuleContext context = FraudRuleContext.builder()
                    .transactionsLastHour(2)
                    .transactionsLast24Hours(5)
                    .averageAmount(new BigDecimal("800"))
                    .knownDevice(true)
                    .build();

            FraudCheckResult result = fraudDetectionService.check(request, context);

            assertFalse(result.isFraudSuspected());
            assertEquals(RiskLevel.NONE, result.getRiskLevel());
            assertEquals(FraudCheckResult.RecommendedAction.ALLOW, result.getAction());
            assertTrue(result.getTriggeredRules().isEmpty());
        }

        @Test
        @DisplayName("Should detect high velocity fraud")
        void shouldDetectHighVelocityFraud() {
            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("1000"))
                    .build();

            FraudRuleContext context = FraudRuleContext.builder()
                    .transactionsLastHour(10) // Exceeds 5
                    .transactionsLast24Hours(5)
                    .knownDevice(true)
                    .build();

            FraudCheckResult result = fraudDetectionService.check(request, context);

            assertTrue(result.isFraudSuspected());
            assertTrue(result.getRiskScore() > 0);
            assertFalse(result.getTriggeredRules().isEmpty());
            assertTrue(result.getTriggeredRules().stream()
                    .anyMatch(r -> r.getRuleType() == FraudRuleType.VELOCITY));
        }

        @Test
        @DisplayName("Should detect large transaction")
        void shouldDetectLargeTransaction() {
            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("150000")) // Over 100,000
                    .build();

            FraudRuleContext context = FraudRuleContext.builder()
                    .transactionsLastHour(1)
                    .averageAmount(new BigDecimal("5000"))
                    .knownDevice(true)
                    .build();

            FraudCheckResult result = fraudDetectionService.check(request, context);

            assertTrue(result.isFraudSuspected());
            assertTrue(result.getTriggeredRules().stream()
                    .anyMatch(r -> r.getRuleType() == FraudRuleType.AMOUNT_THRESHOLD));
        }

        @Test
        @DisplayName("Should detect unusual amount pattern")
        void shouldDetectUnusualAmountPattern() {
            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("50000")) // 10x average
                    .build();

            FraudRuleContext context = FraudRuleContext.builder()
                    .averageAmount(new BigDecimal("5000"))
                    .transactionsLastHour(1)
                    .knownDevice(true)
                    .build();

            FraudCheckResult result = fraudDetectionService.check(request, context);

            assertTrue(result.isFraudSuspected());
        }

        @Test
        @DisplayName("Should detect unknown device")
        void shouldDetectUnknownDevice() {
            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("1000"))
                    .build();

            FraudRuleContext context = FraudRuleContext.builder()
                    .transactionsLastHour(1)
                    .knownDevice(false)
                    .build();

            FraudCheckResult result = fraudDetectionService.check(request, context);

            assertTrue(result.isFraudSuspected());
            assertTrue(result.getTriggeredRules().stream()
                    .anyMatch(r -> r.getRuleType() == FraudRuleType.DEVICE_CHECK));
        }

        @Test
        @DisplayName("Should detect dormant account activation")
        void shouldDetectDormantAccountActivation() {
            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("1000"))
                    .build();

            FraudRuleContext context = FraudRuleContext.builder()
                    .dormantAccount(true)
                    .knownDevice(true)
                    .build();

            FraudCheckResult result = fraudDetectionService.check(request, context);

            assertTrue(result.isFraudSuspected());
            assertTrue(result.getTriggeredRules().stream()
                    .anyMatch(r -> r.getRuleType() == FraudRuleType.DORMANT_ACCOUNT));
        }

        @Test
        @DisplayName("Should detect multiple cards on device")
        void shouldDetectMultipleCardsOnDevice() {
            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("1000"))
                    .build();

            FraudRuleContext context = FraudRuleContext.builder()
                    .cardsOnDevice(5) // More than 3
                    .knownDevice(true)
                    .build();

            FraudCheckResult result = fraudDetectionService.check(request, context);

            assertTrue(result.isFraudSuspected());
            assertTrue(result.getTriggeredRules().stream()
                    .anyMatch(r -> r.getRuleType() == FraudRuleType.DEVICE_CHECK));
        }

        @Test
        @DisplayName("Should accumulate risk scores from multiple rules")
        void shouldAccumulateRiskScores() {
            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("150000")) // Large amount
                    .build();

            FraudRuleContext context = FraudRuleContext.builder()
                    .transactionsLastHour(10) // High velocity
                    .knownDevice(false) // Unknown device
                    .dormantAccount(true) // Dormant
                    .build();

            FraudCheckResult result = fraudDetectionService.check(request, context);

            assertTrue(result.isFraudSuspected());
            assertTrue(result.getTriggeredRules().size() >= 3);
            assertTrue(result.getRiskScore() >= 50);
        }
    }

    @Nested
    @DisplayName("Rule Management")
    class RuleManagementTests {

        @Test
        @DisplayName("Should add custom rule")
        void shouldAddCustomRule() {
            FraudRule customRule = FraudRule.builder()
                    .ruleId("CUSTOM-001")
                    .name("Custom Test Rule")
                    .type(FraudRuleType.CUSTOM)
                    .riskScore(50)
                    .evaluator((req, ctx) -> req.getAmount() != null &&
                            req.getAmount().compareTo(new BigDecimal("999")) == 0)
                    .build();

            fraudDetectionService.addRule(customRule);
            Optional<FraudRule> found = fraudDetectionService.getRule("CUSTOM-001");

            assertTrue(found.isPresent());
            assertEquals("Custom Test Rule", found.get().getName());
        }

        @Test
        @DisplayName("Should remove rule")
        void shouldRemoveRule() {
            boolean removed = fraudDetectionService.removeRule("VEL-001");
            Optional<FraudRule> found = fraudDetectionService.getRule("VEL-001");

            assertTrue(removed);
            assertFalse(found.isPresent());
        }

        @Test
        @DisplayName("Should enable/disable rule")
        void shouldEnableDisableRule() {
            fraudDetectionService.setRuleActive("VEL-001", false);
            Optional<FraudRule> rule = fraudDetectionService.getRule("VEL-001");

            assertTrue(rule.isPresent());
            assertFalse(rule.get().isActive());

            // Re-enable
            fraudDetectionService.setRuleActive("VEL-001", true);
            rule = fraudDetectionService.getRule("VEL-001");
            assertTrue(rule.get().isActive());
        }

        @Test
        @DisplayName("Should not trigger disabled rules")
        void shouldNotTriggerDisabledRules() {
            fraudDetectionService.setRuleActive("VEL-001", false);
            fraudDetectionService.setRuleActive("VEL-002", false);

            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("1000"))
                    .build();

            FraudRuleContext context = FraudRuleContext.builder()
                    .transactionsLastHour(100) // Would normally trigger
                    .transactionsLast24Hours(500)
                    .knownDevice(true)
                    .build();

            FraudCheckResult result = fraudDetectionService.check(request, context);

            // Should not have velocity rules triggered
            assertTrue(result.getTriggeredRules().stream()
                    .noneMatch(r -> r.getRuleType() == FraudRuleType.VELOCITY));
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track statistics")
        void shouldTrackStatistics() {
            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("1000"))
                    .build();

            FraudRuleContext cleanContext = FraudRuleContext.builder()
                    .transactionsLastHour(1)
                    .knownDevice(true)
                    .build();

            FraudRuleContext suspiciousContext = FraudRuleContext.builder()
                    .transactionsLastHour(10)
                    .knownDevice(false)
                    .build();

            // Perform checks
            fraudDetectionService.check(request, cleanContext);
            fraudDetectionService.check(request, suspiciousContext);

            Map<String, Object> stats = fraudDetectionService.getStatistics();

            assertNotNull(stats.get("totalRules"));
            assertNotNull(stats.get("totalChecks"));
            assertNotNull(stats.get("fraudsDetected"));
        }

        @Test
        @DisplayName("Should get top triggered rules")
        void shouldGetTopTriggeredRules() {
            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("1000"))
                    .build();

            FraudRuleContext context = FraudRuleContext.builder()
                    .transactionsLastHour(10)
                    .knownDevice(false)
                    .build();

            // Trigger rules multiple times
            for (int i = 0; i < 5; i++) {
                fraudDetectionService.check(request, context);
            }

            Map<String, Object> stats = fraudDetectionService.getStatistics();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> topRules = (List<Map<String, Object>>) stats.get("topTriggeredRules");

            assertNotNull(topRules);
            assertFalse(topRules.isEmpty());
        }
    }

    @Nested
    @DisplayName("Risk Level")
    class RiskLevelTests {

        @Test
        @DisplayName("Should determine correct risk level from score")
        void shouldDetermineCorrectRiskLevel() {
            assertEquals(RiskLevel.NONE, RiskLevel.fromScore(10));
            assertEquals(RiskLevel.LOW, RiskLevel.fromScore(25));
            assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(50));
            assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(75));
            assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(95));
        }

        @Test
        @DisplayName("Should determine appropriate action for risk level")
        void shouldDetermineAppropriateAction() {
            TransactionRequest request = TransactionRequest.builder()
                    .amount(new BigDecimal("500000")) // Very large
                    .build();

            FraudRuleContext context = FraudRuleContext.builder()
                    .transactionsLastHour(50)
                    .transactionsLast24Hours(200)
                    .knownDevice(false)
                    .dormantAccount(true)
                    .amountLast24Hours(new BigDecimal("1000000"))
                    .cardsOnDevice(10)
                    .build();

            FraudCheckResult result = fraudDetectionService.check(request, context);

            // With many rules triggered, should be high/critical risk
            assertTrue(result.getRiskLevel() == RiskLevel.HIGH ||
                    result.getRiskLevel() == RiskLevel.CRITICAL);
            assertTrue(result.getAction() == FraudCheckResult.RecommendedAction.REVIEW ||
                    result.getAction() == FraudCheckResult.RecommendedAction.BLOCK_AND_ALERT);
        }
    }
}
