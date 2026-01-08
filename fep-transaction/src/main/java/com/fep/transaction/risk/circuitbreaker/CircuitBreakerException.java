package com.fep.transaction.risk.circuitbreaker;

/**
 * Exception thrown when circuit breaker is open.
 */
public class CircuitBreakerException extends RuntimeException {

    private final String circuitName;
    private final CircuitState state;

    public CircuitBreakerException(String circuitName, CircuitState state) {
        super(String.format("Circuit breaker '%s' is %s", circuitName, state));
        this.circuitName = circuitName;
        this.state = state;
    }

    public CircuitBreakerException(String circuitName, CircuitState state, String message) {
        super(message);
        this.circuitName = circuitName;
        this.state = state;
    }

    public String getCircuitName() {
        return circuitName;
    }

    public CircuitState getState() {
        return state;
    }
}
