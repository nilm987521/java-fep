package com.fep.transaction.risk.ratelimit;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Configuration for rate limiting.
 */
@Data
@Builder
public class RateLimitConfig {

    /** Name of the rate limiter */
    private String name;

    /** Rate limit type */
    @Builder.Default
    private RateLimitType type = RateLimitType.TOKEN_BUCKET;

    /** Maximum requests per period */
    @Builder.Default
    private int maxRequests = 100;

    /** Time period for rate limit */
    @Builder.Default
    private Duration period = Duration.ofSeconds(1);

    /** Bucket capacity for token bucket (burst allowance) */
    @Builder.Default
    private int bucketCapacity = 100;

    /** Token refill rate per second for token bucket */
    @Builder.Default
    private double refillRate = 100.0;

    /** Whether to queue requests that exceed limit */
    @Builder.Default
    private boolean queueExcessRequests = false;

    /** Maximum queue size if queuing is enabled */
    @Builder.Default
    private int maxQueueSize = 1000;

    /** Maximum wait time in queue */
    @Builder.Default
    private Duration maxQueueWait = Duration.ofSeconds(5);

    /**
     * Creates a default configuration for TPS limiting.
     */
    public static RateLimitConfig tpsLimit(String name, int tps) {
        return RateLimitConfig.builder()
                .name(name)
                .type(RateLimitType.TOKEN_BUCKET)
                .maxRequests(tps)
                .period(Duration.ofSeconds(1))
                .bucketCapacity(tps * 2) // Allow 2x burst
                .refillRate(tps)
                .build();
    }

    /**
     * Creates a configuration for per-minute limiting.
     */
    public static RateLimitConfig perMinute(String name, int requestsPerMinute) {
        return RateLimitConfig.builder()
                .name(name)
                .type(RateLimitType.SLIDING_WINDOW)
                .maxRequests(requestsPerMinute)
                .period(Duration.ofMinutes(1))
                .build();
    }

    /**
     * Creates a configuration for per-hour limiting.
     */
    public static RateLimitConfig perHour(String name, int requestsPerHour) {
        return RateLimitConfig.builder()
                .name(name)
                .type(RateLimitType.SLIDING_WINDOW)
                .maxRequests(requestsPerHour)
                .period(Duration.ofHours(1))
                .build();
    }

    /**
     * Creates a strict configuration.
     */
    public static RateLimitConfig strict(String name, int tps) {
        return RateLimitConfig.builder()
                .name(name)
                .type(RateLimitType.FIXED_WINDOW)
                .maxRequests(tps)
                .period(Duration.ofSeconds(1))
                .queueExcessRequests(false)
                .build();
    }
}
