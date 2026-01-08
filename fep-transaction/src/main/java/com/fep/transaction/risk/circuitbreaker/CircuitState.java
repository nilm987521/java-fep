package com.fep.transaction.risk.circuitbreaker;

/**
 * States of a circuit breaker.
 */
public enum CircuitState {

    /** Circuit is closed - requests flow normally */
    CLOSED("Closed", "Normal operation - all requests allowed"),

    /** Circuit is open - requests are blocked */
    OPEN("Open", "Circuit tripped - requests blocked"),

    /** Circuit is half-open - testing if service recovered */
    HALF_OPEN("Half-Open", "Testing service recovery");

    private final String name;
    private final String description;

    CircuitState(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
