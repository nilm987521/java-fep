package com.fep.transaction.risk.circuitbreaker;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Configuration for a circuit breaker.
 */
@Data
@Builder
public class CircuitBreakerConfig {

    /** Name of the circuit breaker */
    private String name;

    /** Failure threshold to trip the circuit (percentage) */
    @Builder.Default
    private int failureThresholdPercent = 50;

    /** Minimum number of calls before calculating failure rate */
    @Builder.Default
    private int minimumCalls = 10;

    /** Size of the sliding window for failure rate calculation */
    @Builder.Default
    private int slidingWindowSize = 100;

    /** Duration to wait before transitioning from OPEN to HALF_OPEN */
    @Builder.Default
    private Duration waitDurationInOpenState = Duration.ofSeconds(60);

    /** Number of permitted calls in HALF_OPEN state */
    @Builder.Default
    private int permittedCallsInHalfOpen = 5;

    /** Success threshold to close circuit from HALF_OPEN (percentage) */
    @Builder.Default
    private int successThresholdInHalfOpen = 80;

    /** Timeout for individual calls */
    @Builder.Default
    private Duration callTimeout = Duration.ofSeconds(10);

    /** Whether to count slow calls as failures */
    @Builder.Default
    private boolean countSlowCallsAsFailures = true;

    /** Threshold for considering a call "slow" */
    @Builder.Default
    private Duration slowCallThreshold = Duration.ofSeconds(5);

    /** Slow call threshold percentage to consider as failure */
    @Builder.Default
    private int slowCallThresholdPercent = 100;

    /**
     * Creates a default configuration.
     */
    public static CircuitBreakerConfig defaultConfig(String name) {
        return CircuitBreakerConfig.builder()
                .name(name)
                .build();
    }

    /**
     * Creates a strict configuration (trips quickly).
     */
    public static CircuitBreakerConfig strict(String name) {
        return CircuitBreakerConfig.builder()
                .name(name)
                .failureThresholdPercent(30)
                .minimumCalls(5)
                .slidingWindowSize(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedCallsInHalfOpen(3)
                .build();
    }

    /**
     * Creates a lenient configuration (trips slowly).
     */
    public static CircuitBreakerConfig lenient(String name) {
        return CircuitBreakerConfig.builder()
                .name(name)
                .failureThresholdPercent(70)
                .minimumCalls(20)
                .slidingWindowSize(200)
                .waitDurationInOpenState(Duration.ofSeconds(120))
                .permittedCallsInHalfOpen(10)
                .build();
    }
}
