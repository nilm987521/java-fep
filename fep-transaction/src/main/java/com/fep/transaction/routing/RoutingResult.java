package com.fep.transaction.routing;

import lombok.Builder;
import lombok.Data;

/**
 * Result of transaction routing decision.
 */
@Data
@Builder
public class RoutingResult {

    /** Whether routing was successful */
    private boolean routed;

    /** The matched routing rule */
    private RoutingRule matchedRule;

    /** Target destination */
    private RoutingDestination destination;

    /** Timeout for this route */
    private long timeoutMs;

    /** Message if routing failed */
    private String message;

    /**
     * Creates a successful routing result.
     */
    public static RoutingResult success(RoutingRule rule) {
        return RoutingResult.builder()
                .routed(true)
                .matchedRule(rule)
                .destination(rule.getDestination())
                .timeoutMs(rule.getTimeoutMs())
                .build();
    }

    /**
     * Creates a failed routing result.
     */
    public static RoutingResult notFound(String message) {
        return RoutingResult.builder()
                .routed(false)
                .message(message)
                .build();
    }
}
