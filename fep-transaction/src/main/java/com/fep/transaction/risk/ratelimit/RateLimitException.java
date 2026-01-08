package com.fep.transaction.risk.ratelimit;

/**
 * Exception thrown when rate limit is exceeded.
 */
public class RateLimitException extends RuntimeException {

    private final String limiterName;
    private final RateLimitResult result;

    public RateLimitException(String limiterName, RateLimitResult result) {
        super(String.format("Rate limit exceeded for '%s': %d/%d (resets in %ds)",
                limiterName, result.getCurrentCount(), result.getLimit(), result.getResetInSeconds()));
        this.limiterName = limiterName;
        this.result = result;
    }

    public String getLimiterName() {
        return limiterName;
    }

    public RateLimitResult getResult() {
        return result;
    }

    public long getRetryAfterSeconds() {
        return result.getResetInSeconds();
    }
}
