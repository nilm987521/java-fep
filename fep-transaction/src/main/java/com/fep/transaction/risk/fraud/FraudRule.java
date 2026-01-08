package com.fep.transaction.risk.fraud;

import com.fep.transaction.domain.TransactionRequest;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Represents a fraud detection rule.
 */
@Data
@Builder
public class FraudRule {

    /** Unique rule identifier */
    private String ruleId;

    /** Rule name */
    private String name;

    /** Rule description */
    private String description;

    /** Rule type */
    private FraudRuleType type;

    /** Whether the rule is active */
    @Builder.Default
    private boolean active = true;

    /** Priority (1-10, 1 being highest) */
    @Builder.Default
    private int priority = 5;

    /** Risk score to add if rule triggers (0-100) */
    @Builder.Default
    private int riskScore = 0;

    /** Rule parameters */
    private Map<String, Object> parameters;

    /** The evaluation function */
    private transient BiPredicate<TransactionRequest, FraudRuleContext> evaluator;

    /** Created timestamp */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Last modified timestamp */
    private LocalDateTime updatedAt;

    /** Number of times this rule has been triggered */
    @Builder.Default
    private long triggerCount = 0;

    /** Last time this rule was triggered */
    private LocalDateTime lastTriggeredAt;

    /**
     * Evaluates this rule against a transaction.
     */
    public boolean evaluate(TransactionRequest request, FraudRuleContext context) {
        if (!active || evaluator == null) {
            return false;
        }
        boolean triggered = evaluator.test(request, context);
        if (triggered) {
            triggerCount++;
            lastTriggeredAt = LocalDateTime.now();
        }
        return triggered;
    }

    /**
     * Gets a parameter value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        if (parameters == null || !parameters.containsKey(key)) {
            return defaultValue;
        }
        return (T) parameters.get(key);
    }
}
