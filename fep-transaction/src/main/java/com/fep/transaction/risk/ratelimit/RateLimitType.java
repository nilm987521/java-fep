package com.fep.transaction.risk.ratelimit;

/**
 * Types of rate limiting strategies.
 */
public enum RateLimitType {

    /** Fixed window rate limiting */
    FIXED_WINDOW("Fixed Window", "Counts requests in fixed time windows"),

    /** Sliding window rate limiting */
    SLIDING_WINDOW("Sliding Window", "Uses a sliding time window for smoother limiting"),

    /** Token bucket algorithm */
    TOKEN_BUCKET("Token Bucket", "Allows burst traffic up to bucket capacity"),

    /** Leaky bucket algorithm */
    LEAKY_BUCKET("Leaky Bucket", "Processes requests at a constant rate");

    private final String name;
    private final String description;

    RateLimitType(String name, String description) {
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
