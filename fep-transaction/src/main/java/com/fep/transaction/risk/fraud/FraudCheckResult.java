package com.fep.transaction.risk.fraud;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of fraud detection analysis.
 */
@Data
@Builder
public class FraudCheckResult {

    /** Whether fraud is suspected */
    private boolean fraudSuspected;

    /** Overall risk level */
    private RiskLevel riskLevel;

    /** Combined risk score (0-100) */
    @Builder.Default
    private int riskScore = 0;

    /** Rules that were triggered */
    @Builder.Default
    private List<TriggeredRule> triggeredRules = new ArrayList<>();

    /** Recommended action */
    private RecommendedAction action;

    /** Timestamp of the check */
    @Builder.Default
    private LocalDateTime checkedAt = LocalDateTime.now();

    /** Time taken for the check in milliseconds */
    private long checkDurationMs;

    /** Additional message */
    private String message;

    /**
     * Recommended actions based on fraud analysis.
     */
    public enum RecommendedAction {
        ALLOW("Allow transaction"),
        ALLOW_WITH_VERIFICATION("Allow with additional verification"),
        REVIEW("Queue for manual review"),
        CHALLENGE("Challenge customer (OTP/Security question)"),
        DECLINE("Decline transaction"),
        BLOCK_AND_ALERT("Block and alert fraud team");

        private final String description;

        RecommendedAction(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Represents a triggered rule.
     */
    @Data
    @Builder
    public static class TriggeredRule {
        private String ruleId;
        private String ruleName;
        private FraudRuleType ruleType;
        private int riskScoreContribution;
        private String reason;
    }

    /**
     * Creates a clean result (no fraud detected).
     */
    public static FraudCheckResult clean() {
        return FraudCheckResult.builder()
                .fraudSuspected(false)
                .riskLevel(RiskLevel.NONE)
                .riskScore(0)
                .action(RecommendedAction.ALLOW)
                .message("No fraud indicators detected")
                .build();
    }

    /**
     * Creates a result with suspected fraud.
     */
    public static FraudCheckResult suspected(int score, List<TriggeredRule> rules, String message) {
        RiskLevel level = RiskLevel.fromScore(score);
        RecommendedAction action = determineAction(level);

        return FraudCheckResult.builder()
                .fraudSuspected(true)
                .riskLevel(level)
                .riskScore(score)
                .triggeredRules(rules)
                .action(action)
                .message(message)
                .build();
    }

    private static RecommendedAction determineAction(RiskLevel level) {
        return switch (level) {
            case NONE -> RecommendedAction.ALLOW;
            case LOW -> RecommendedAction.ALLOW_WITH_VERIFICATION;
            case MEDIUM -> RecommendedAction.CHALLENGE;
            case HIGH -> RecommendedAction.REVIEW;
            case CRITICAL -> RecommendedAction.BLOCK_AND_ALERT;
        };
    }

    /**
     * Adds a triggered rule to the result.
     */
    public void addTriggeredRule(TriggeredRule rule) {
        triggeredRules.add(rule);
        riskScore = Math.min(100, riskScore + rule.getRiskScoreContribution());
        riskLevel = RiskLevel.fromScore(riskScore);
        fraudSuspected = riskScore >= 20;
        action = determineAction(riskLevel);
    }
}
