package com.fep.transaction.risk.circuitbreaker;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics for circuit breaker monitoring.
 */
@Data
public class CircuitBreakerMetrics {

    private final String circuitName;

    // Counters
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successfulCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);
    private final AtomicLong rejectedCalls = new AtomicLong(0);
    private final AtomicLong slowCalls = new AtomicLong(0);

    // State transition counters
    private final AtomicInteger stateTransitions = new AtomicInteger(0);
    private final AtomicInteger timesOpened = new AtomicInteger(0);

    // Timing
    private final AtomicLong totalDurationMs = new AtomicLong(0);
    private LocalDateTime lastStateChange;
    private LocalDateTime lastFailure;
    private LocalDateTime lastSuccess;

    // Current state info
    private CircuitState currentState = CircuitState.CLOSED;

    public CircuitBreakerMetrics(String circuitName) {
        this.circuitName = circuitName;
        this.lastStateChange = LocalDateTime.now();
    }

    public void recordSuccess(long durationMs) {
        totalCalls.incrementAndGet();
        successfulCalls.incrementAndGet();
        totalDurationMs.addAndGet(durationMs);
        lastSuccess = LocalDateTime.now();
    }

    public void recordFailure(long durationMs) {
        totalCalls.incrementAndGet();
        failedCalls.incrementAndGet();
        totalDurationMs.addAndGet(durationMs);
        lastFailure = LocalDateTime.now();
    }

    public void recordRejection() {
        rejectedCalls.incrementAndGet();
    }

    public void recordSlowCall() {
        slowCalls.incrementAndGet();
    }

    public void recordStateChange(CircuitState newState) {
        this.currentState = newState;
        this.lastStateChange = LocalDateTime.now();
        stateTransitions.incrementAndGet();
        if (newState == CircuitState.OPEN) {
            timesOpened.incrementAndGet();
        }
    }

    public double getFailureRate() {
        long total = totalCalls.get();
        if (total == 0) return 0.0;
        return (double) failedCalls.get() / total * 100;
    }

    public double getSuccessRate() {
        long total = totalCalls.get();
        if (total == 0) return 0.0;
        return (double) successfulCalls.get() / total * 100;
    }

    public double getAverageDurationMs() {
        long total = totalCalls.get();
        if (total == 0) return 0.0;
        return (double) totalDurationMs.get() / total;
    }

    public void reset() {
        totalCalls.set(0);
        successfulCalls.set(0);
        failedCalls.set(0);
        rejectedCalls.set(0);
        slowCalls.set(0);
        totalDurationMs.set(0);
    }
}
