package com.fep.transaction.risk.ratelimit;

import lombok.Builder;
import lombok.Data;

/**
 * Result of a rate limit check.
 */
@Data
@Builder
public class RateLimitResult {

    /** Whether the request is allowed */
    private boolean allowed;

    /** Current request count in the window */
    private long currentCount;

    /** Maximum allowed requests */
    private long limit;

    /** Remaining requests allowed */
    private long remaining;

    /** Seconds until the limit resets */
    private long resetInSeconds;

    /** Wait time if queued (milliseconds) */
    private long waitTimeMs;

    /** Reason if rejected */
    private String reason;

    /**
     * Creates an allowed result.
     */
    public static RateLimitResult allowed(long currentCount, long limit, long resetInSeconds) {
        return RateLimitResult.builder()
                .allowed(true)
                .currentCount(currentCount)
                .limit(limit)
                .remaining(Math.max(0, limit - currentCount))
                .resetInSeconds(resetInSeconds)
                .build();
    }

    /**
     * Creates a rejected result.
     */
    public static RateLimitResult rejected(long currentCount, long limit, long resetInSeconds, String reason) {
        return RateLimitResult.builder()
                .allowed(false)
                .currentCount(currentCount)
                .limit(limit)
                .remaining(0)
                .resetInSeconds(resetInSeconds)
                .reason(reason)
                .build();
    }

    /**
     * Creates a queued result.
     */
    public static RateLimitResult queued(long waitTimeMs) {
        return RateLimitResult.builder()
                .allowed(true)
                .waitTimeMs(waitTimeMs)
                .reason("Request queued")
                .build();
    }
}
