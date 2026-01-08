package com.fep.transaction.risk.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Implementation of the Circuit Breaker pattern.
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final CircuitBreakerConfig config;
    private final CircuitBreakerMetrics metrics;

    // State management
    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private volatile LocalDateTime lastStateChangeTime = LocalDateTime.now();
    private volatile LocalDateTime openedAt;

    // Sliding window for failure tracking
    private final ConcurrentLinkedQueue<CallResult> slidingWindow = new ConcurrentLinkedQueue<>();

    // Half-open state tracking
    private final AtomicInteger halfOpenCalls = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);

    // Listeners
    private Runnable onOpen;
    private Runnable onClose;
    private Runnable onHalfOpen;

    private record CallResult(boolean success, LocalDateTime timestamp) {}

    public CircuitBreaker(CircuitBreakerConfig config) {
        this.config = config;
        this.metrics = new CircuitBreakerMetrics(config.getName());
    }

    /**
     * Executes a call through the circuit breaker.
     */
    public <T> T execute(Supplier<T> call) {
        return execute(call, null);
    }

    /**
     * Executes a call through the circuit breaker with fallback.
     */
    public <T> T execute(Supplier<T> call, Supplier<T> fallback) {
        // Check if call is permitted
        if (!isCallPermitted()) {
            metrics.recordRejection();
            if (fallback != null) {
                log.debug("Circuit {} is open, using fallback", config.getName());
                return fallback.get();
            }
            throw new CircuitBreakerException(config.getName(), state.get());
        }

        long startTime = System.currentTimeMillis();
        try {
            T result = call.get();
            long duration = System.currentTimeMillis() - startTime;

            // Check for slow call
            if (config.isCountSlowCallsAsFailures() &&
                    duration > config.getSlowCallThreshold().toMillis()) {
                metrics.recordSlowCall();
                if (config.getSlowCallThresholdPercent() >= 100) {
                    recordFailure(duration);
                    return result;
                }
            }

            recordSuccess(duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            recordFailure(duration);

            if (fallback != null) {
                log.debug("Call failed for circuit {}, using fallback", config.getName());
                return fallback.get();
            }
            throw e;
        }
    }

    /**
     * Executes a runnable through the circuit breaker.
     */
    public void executeRunnable(Runnable runnable) {
        execute(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Checks if a call is permitted based on circuit state.
     */
    public boolean isCallPermitted() {
        CircuitState currentState = state.get();

        switch (currentState) {
            case CLOSED -> {
                return true;
            }
            case OPEN -> {
                // Check if wait duration has passed
                if (shouldTransitionToHalfOpen()) {
                    transitionTo(CircuitState.HALF_OPEN);
                    return true;
                }
                return false;
            }
            case HALF_OPEN -> {
                // Only allow limited calls in half-open state
                int calls = halfOpenCalls.incrementAndGet();
                return calls <= config.getPermittedCallsInHalfOpen();
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Gets the current circuit state.
     */
    public CircuitState getState() {
        return state.get();
    }

    /**
     * Checks if the circuit is closed.
     */
    public boolean isClosed() {
        return state.get() == CircuitState.CLOSED;
    }

    /**
     * Checks if the circuit is open.
     */
    public boolean isOpen() {
        return state.get() == CircuitState.OPEN;
    }

    /**
     * Gets the circuit breaker metrics.
     */
    public CircuitBreakerMetrics getMetrics() {
        return metrics;
    }

    /**
     * Gets the configuration.
     */
    public CircuitBreakerConfig getConfig() {
        return config;
    }

    /**
     * Forces the circuit to open.
     */
    public void forceOpen() {
        transitionTo(CircuitState.OPEN);
        log.warn("Circuit {} forced open", config.getName());
    }

    /**
     * Forces the circuit to close.
     */
    public void forceClose() {
        transitionTo(CircuitState.CLOSED);
        resetSlidingWindow();
        log.info("Circuit {} forced closed", config.getName());
    }

    /**
     * Resets the circuit breaker.
     */
    public void reset() {
        transitionTo(CircuitState.CLOSED);
        resetSlidingWindow();
        metrics.reset();
        log.info("Circuit {} reset", config.getName());
    }

    /**
     * Sets callback for when circuit opens.
     */
    public void onOpen(Runnable callback) {
        this.onOpen = callback;
    }

    /**
     * Sets callback for when circuit closes.
     */
    public void onClose(Runnable callback) {
        this.onClose = callback;
    }

    /**
     * Sets callback for when circuit transitions to half-open.
     */
    public void onHalfOpen(Runnable callback) {
        this.onHalfOpen = callback;
    }

    // Private methods

    private void recordSuccess(long durationMs) {
        metrics.recordSuccess(durationMs);
        addToSlidingWindow(true);

        if (state.get() == CircuitState.HALF_OPEN) {
            int successes = halfOpenSuccesses.incrementAndGet();
            int calls = halfOpenCalls.get();
            double successRate = (double) successes / calls * 100;

            if (calls >= config.getPermittedCallsInHalfOpen() &&
                    successRate >= config.getSuccessThresholdInHalfOpen()) {
                transitionTo(CircuitState.CLOSED);
            }
        }
    }

    private void recordFailure(long durationMs) {
        metrics.recordFailure(durationMs);
        addToSlidingWindow(false);

        CircuitState currentState = state.get();

        if (currentState == CircuitState.CLOSED) {
            // Check if we should trip the circuit
            if (shouldTripCircuit()) {
                transitionTo(CircuitState.OPEN);
            }
        } else if (currentState == CircuitState.HALF_OPEN) {
            // Any failure in half-open state trips the circuit
            transitionTo(CircuitState.OPEN);
        }
    }

    private void addToSlidingWindow(boolean success) {
        slidingWindow.add(new CallResult(success, LocalDateTime.now()));

        // Trim window to max size
        while (slidingWindow.size() > config.getSlidingWindowSize()) {
            slidingWindow.poll();
        }
    }

    private boolean shouldTripCircuit() {
        if (slidingWindow.size() < config.getMinimumCalls()) {
            return false;
        }

        long failures = slidingWindow.stream()
                .filter(r -> !r.success())
                .count();
        double failureRate = (double) failures / slidingWindow.size() * 100;

        return failureRate >= config.getFailureThresholdPercent();
    }

    private boolean shouldTransitionToHalfOpen() {
        if (openedAt == null) {
            return false;
        }
        long elapsedMs = ChronoUnit.MILLIS.between(openedAt, LocalDateTime.now());
        return elapsedMs >= config.getWaitDurationInOpenState().toMillis();
    }

    private void transitionTo(CircuitState newState) {
        CircuitState oldState = state.getAndSet(newState);
        if (oldState != newState) {
            lastStateChangeTime = LocalDateTime.now();
            metrics.recordStateChange(newState);

            log.info("Circuit {} state changed: {} -> {}", config.getName(), oldState, newState);

            if (newState == CircuitState.OPEN) {
                openedAt = LocalDateTime.now();
                if (onOpen != null) onOpen.run();
            } else if (newState == CircuitState.CLOSED) {
                openedAt = null;
                halfOpenCalls.set(0);
                halfOpenSuccesses.set(0);
                if (onClose != null) onClose.run();
            } else if (newState == CircuitState.HALF_OPEN) {
                halfOpenCalls.set(0);
                halfOpenSuccesses.set(0);
                if (onHalfOpen != null) onHalfOpen.run();
            }
        }
    }

    private void resetSlidingWindow() {
        slidingWindow.clear();
        halfOpenCalls.set(0);
        halfOpenSuccesses.set(0);
    }
}
