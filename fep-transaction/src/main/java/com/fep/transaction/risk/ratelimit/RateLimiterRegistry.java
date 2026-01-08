package com.fep.transaction.risk.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing multiple rate limiters.
 */
@Component
public class RateLimiterRegistry {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterRegistry.class);

    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    /**
     * Gets or creates a rate limiter with default configuration.
     */
    public RateLimiter getOrCreate(String name) {
        return rateLimiters.computeIfAbsent(name, n -> {
            RateLimiter rl = new RateLimiter(RateLimitConfig.tpsLimit(n, 100));
            log.info("Created rate limiter: {} (100 TPS default)", n);
            return rl;
        });
    }

    /**
     * Gets or creates a rate limiter with custom configuration.
     */
    public RateLimiter getOrCreate(String name, RateLimitConfig config) {
        return rateLimiters.computeIfAbsent(name, n -> {
            RateLimiter rl = new RateLimiter(config);
            log.info("Created rate limiter: {} ({} requests per {})",
                    n, config.getMaxRequests(), config.getPeriod());
            return rl;
        });
    }

    /**
     * Gets an existing rate limiter.
     */
    public Optional<RateLimiter> get(String name) {
        return Optional.ofNullable(rateLimiters.get(name));
    }

    /**
     * Removes a rate limiter.
     */
    public boolean remove(String name) {
        RateLimiter removed = rateLimiters.remove(name);
        if (removed != null) {
            log.info("Removed rate limiter: {}", name);
            return true;
        }
        return false;
    }

    /**
     * Gets all rate limiter names.
     */
    public Set<String> getNames() {
        return new HashSet<>(rateLimiters.keySet());
    }

    /**
     * Gets all rate limiters.
     */
    public Collection<RateLimiter> getAll() {
        return new ArrayList<>(rateLimiters.values());
    }

    /**
     * Resets all rate limiters.
     */
    public void resetAll() {
        rateLimiters.values().forEach(RateLimiter::reset);
        log.info("Reset all {} rate limiters", rateLimiters.size());
    }

    /**
     * Gets statistics for all rate limiters.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLimiters", rateLimiters.size());

        Map<String, Map<String, Object>> limiterStats = new HashMap<>();
        for (Map.Entry<String, RateLimiter> entry : rateLimiters.entrySet()) {
            RateLimiter.RateLimiterStats rlStats = entry.getValue().getStats();

            Map<String, Object> ls = new HashMap<>();
            ls.put("totalRequests", rlStats.totalRequests());
            ls.put("allowedRequests", rlStats.allowedRequests());
            ls.put("rejectedRequests", rlStats.rejectedRequests());
            ls.put("currentRate", String.format("%.2f/s", rlStats.currentRate()));
            ls.put("rejectionRate", String.format("%.2f%%", rlStats.getRejectionRate()));

            limiterStats.put(entry.getKey(), ls);
        }
        stats.put("limiters", limiterStats);

        return stats;
    }

    /**
     * Creates pre-configured rate limiters for FEP.
     */
    public void initializeDefaultLimiters() {
        // Global TPS limit (2000 TPS as per requirements)
        getOrCreate("global", RateLimitConfig.builder()
                .name("global")
                .type(RateLimitType.TOKEN_BUCKET)
                .maxRequests(2000)
                .period(Duration.ofSeconds(1))
                .bucketCapacity(3000) // Allow 50% burst
                .refillRate(2000)
                .build());

        // Per-channel limits
        getOrCreate("channel-atm", RateLimitConfig.tpsLimit("channel-atm", 500));
        getOrCreate("channel-pos", RateLimitConfig.tpsLimit("channel-pos", 800));
        getOrCreate("channel-web", RateLimitConfig.tpsLimit("channel-web", 400));
        getOrCreate("channel-mobile", RateLimitConfig.tpsLimit("channel-mobile", 300));

        // Per-merchant limits
        getOrCreate("merchant-default", RateLimitConfig.builder()
                .name("merchant-default")
                .type(RateLimitType.SLIDING_WINDOW)
                .maxRequests(100)
                .period(Duration.ofMinutes(1))
                .build());

        // FISC connection limit
        getOrCreate("fisc", RateLimitConfig.builder()
                .name("fisc")
                .type(RateLimitType.TOKEN_BUCKET)
                .maxRequests(1000)
                .period(Duration.ofSeconds(1))
                .bucketCapacity(1500)
                .refillRate(1000)
                .build());

        // Card-level limits (per card per day)
        getOrCreate("per-card-daily", RateLimitConfig.builder()
                .name("per-card-daily")
                .type(RateLimitType.FIXED_WINDOW)
                .maxRequests(50)
                .period(Duration.ofHours(24))
                .build());

        log.info("Initialized {} default rate limiters", rateLimiters.size());
    }

    /**
     * Checks if a request should be allowed based on multiple limiters.
     */
    public RateLimitResult checkAll(String... limiterNames) {
        for (String name : limiterNames) {
            RateLimiter limiter = rateLimiters.get(name);
            if (limiter != null) {
                RateLimitResult result = limiter.tryAcquire();
                if (!result.isAllowed()) {
                    return result;
                }
            }
        }
        return RateLimitResult.allowed(0, 0, 0);
    }
}
