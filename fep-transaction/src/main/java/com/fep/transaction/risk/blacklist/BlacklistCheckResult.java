package com.fep.transaction.risk.blacklist;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of a blacklist check.
 */
@Data
@Builder
public class BlacklistCheckResult {

    /** Whether any blacklist was matched */
    private boolean blocked;

    /** The entries that matched */
    @Builder.Default
    private List<BlacklistEntry> matchedEntries = new ArrayList<>();

    /** Overall risk level (0-100) */
    @Builder.Default
    private int riskScore = 0;

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
     * Recommended actions based on blacklist check.
     */
    public enum RecommendedAction {
        ALLOW("Allow transaction"),
        REVIEW("Manual review required"),
        DECLINE("Decline transaction"),
        BLOCK_AND_ALERT("Block and notify security team"),
        CAPTURE_CARD("Capture card (ATM only)");

        private final String description;

        RecommendedAction(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Creates a result indicating no match was found.
     */
    public static BlacklistCheckResult notBlocked() {
        return BlacklistCheckResult.builder()
                .blocked(false)
                .action(RecommendedAction.ALLOW)
                .riskScore(0)
                .message("No blacklist match found")
                .build();
    }

    /**
     * Creates a result indicating the transaction should be blocked.
     */
    public static BlacklistCheckResult blocked(BlacklistEntry entry, String message) {
        List<BlacklistEntry> entries = new ArrayList<>();
        entries.add(entry);
        return BlacklistCheckResult.builder()
                .blocked(true)
                .matchedEntries(entries)
                .action(determineAction(entry))
                .riskScore(calculateRiskScore(entry))
                .message(message)
                .build();
    }

    /**
     * Creates a result with multiple matches.
     */
    public static BlacklistCheckResult blockedMultiple(List<BlacklistEntry> entries, String message) {
        int maxRisk = entries.stream()
                .mapToInt(BlacklistCheckResult::calculateRiskScore)
                .max()
                .orElse(0);

        RecommendedAction action = entries.stream()
                .map(BlacklistCheckResult::determineAction)
                .max((a, b) -> a.ordinal() - b.ordinal())
                .orElse(RecommendedAction.DECLINE);

        return BlacklistCheckResult.builder()
                .blocked(true)
                .matchedEntries(entries)
                .action(action)
                .riskScore(maxRisk)
                .message(message)
                .build();
    }

    private static RecommendedAction determineAction(BlacklistEntry entry) {
        return switch (entry.getReason()) {
            case LOST, STOLEN, COUNTERFEIT -> RecommendedAction.CAPTURE_CARD;
            case FRAUD_CONFIRMED, MONEY_LAUNDERING, SANCTIONS -> RecommendedAction.BLOCK_AND_ALERT;
            case FRAUD_SUSPECTED, UNUSUAL_ACTIVITY -> RecommendedAction.REVIEW;
            case TEMPORARY_HOLD -> RecommendedAction.DECLINE;
            default -> entry.getPriority() <= 2 ? RecommendedAction.BLOCK_AND_ALERT : RecommendedAction.DECLINE;
        };
    }

    private static int calculateRiskScore(BlacklistEntry entry) {
        int baseScore = switch (entry.getReason()) {
            case FRAUD_CONFIRMED, STOLEN, COUNTERFEIT -> 100;
            case MONEY_LAUNDERING, SANCTIONS, IDENTITY_THEFT -> 95;
            case LOST, SECURITY_BREACH, COMPROMISED -> 90;
            case FRAUD_SUSPECTED, AML_VIOLATION -> 80;
            case UNUSUAL_ACTIVITY, REGULATORY_ACTION -> 70;
            case EXCESSIVE_CHARGEBACKS, PCI_NON_COMPLIANCE -> 60;
            case TEMPORARY_HOLD -> 50;
            default -> 40;
        };

        // Adjust based on priority
        int priorityAdjustment = (5 - entry.getPriority()) * 5;
        return Math.min(100, baseScore + priorityAdjustment);
    }

    /**
     * Adds a matched entry to the result.
     */
    public void addMatchedEntry(BlacklistEntry entry) {
        matchedEntries.add(entry);
        blocked = true;
        riskScore = Math.max(riskScore, calculateRiskScore(entry));
        action = determineAction(entry);
    }
}
