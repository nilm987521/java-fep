package com.fep.transaction.risk.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of various rate limiting algorithms.
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RateLimitConfig config;

    // For fixed/sliding window
    private final ConcurrentHashMap<Long, AtomicLong> windowCounts = new ConcurrentHashMap<>();

    // For token bucket
    private final AtomicReference<TokenBucket> tokenBucket;

    // Statistics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);

    private record TokenBucket(double tokens, long lastRefillTime) {}

    public RateLimiter(RateLimitConfig config) {
        this.config = config;
        this.tokenBucket = new AtomicReference<>(
                new TokenBucket(config.getBucketCapacity(), System.currentTimeMillis()));
    }

    /**
     * Attempts to acquire permission for a request.
     */
    public RateLimitResult tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * Attempts to acquire permission for multiple requests.
     */
    public RateLimitResult tryAcquire(int permits) {
        totalRequests.addAndGet(permits);

        RateLimitResult result = switch (config.getType()) {
            case FIXED_WINDOW -> tryAcquireFixedWindow(permits);
            case SLIDING_WINDOW -> tryAcquireSlidingWindow(permits);
            case TOKEN_BUCKET -> tryAcquireTokenBucket(permits);
            case LEAKY_BUCKET -> tryAcquireLeakyBucket(permits);
        };

        if (result.isAllowed()) {
            allowedRequests.addAndGet(permits);
        } else {
            rejectedRequests.addAndGet(permits);
            log.debug("Rate limit exceeded for {}: {}/{}", config.getName(),
                    result.getCurrentCount(), result.getLimit());
        }

        return result;
    }

    /**
     * Acquires permission, blocking if necessary.
     */
    public RateLimitResult acquire() {
        return acquire(1);
    }

    /**
     * Acquires permission for multiple requests, blocking if necessary.
     */
    public RateLimitResult acquire(int permits) {
        RateLimitResult result = tryAcquire(permits);

        if (!result.isAllowed() && config.isQueueExcessRequests()) {
            long waitTime = result.getResetInSeconds() * 1000;
            if (waitTime <= config.getMaxQueueWait().toMillis()) {
                try {
                    Thread.sleep(waitTime);
                    return tryAcquire(permits);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return RateLimitResult.rejected(result.getCurrentCount(), result.getLimit(),
                            result.getResetInSeconds(), "Interrupted while waiting");
                }
            }
        }

        return result;
    }

    /**
     * Gets the current rate (requests per second).
     */
    public double getCurrentRate() {
        long periodMs = config.getPeriod().toMillis();
        long currentWindow = System.currentTimeMillis() / periodMs;
        AtomicLong count = windowCounts.get(currentWindow);
        return count != null ? count.get() * (1000.0 / periodMs) : 0.0;
    }

    /**
     * Gets statistics.
     */
    public RateLimiterStats getStats() {
        return new RateLimiterStats(
                config.getName(),
                totalRequests.get(),
                allowedRequests.get(),
                rejectedRequests.get(),
                getCurrentRate()
        );
    }

    /**
     * Resets the rate limiter.
     */
    public void reset() {
        windowCounts.clear();
        tokenBucket.set(new TokenBucket(config.getBucketCapacity(), System.currentTimeMillis()));
        totalRequests.set(0);
        allowedRequests.set(0);
        rejectedRequests.set(0);
        log.info("Rate limiter {} reset", config.getName());
    }

    // Fixed window implementation
    private RateLimitResult tryAcquireFixedWindow(int permits) {
        long periodMs = config.getPeriod().toMillis();
        long currentWindow = System.currentTimeMillis() / periodMs;

        // Clean up old windows
        cleanupOldWindows(currentWindow - 2);

        AtomicLong counter = windowCounts.computeIfAbsent(currentWindow, k -> new AtomicLong(0));
        long newCount = counter.addAndGet(permits);
        long resetIn = (periodMs - (System.currentTimeMillis() % periodMs)) / 1000;

        if (newCount <= config.getMaxRequests()) {
            return RateLimitResult.allowed(newCount, config.getMaxRequests(), resetIn);
        } else {
            counter.addAndGet(-permits); // Rollback
            return RateLimitResult.rejected(newCount - permits, config.getMaxRequests(),
                    resetIn, "Fixed window limit exceeded");
        }
    }

    // Sliding window implementation
    private RateLimitResult tryAcquireSlidingWindow(int permits) {
        long periodMs = config.getPeriod().toMillis();
        long now = System.currentTimeMillis();
        long currentWindow = now / periodMs;
        long previousWindow = currentWindow - 1;

        // Clean up old windows
        cleanupOldWindows(previousWindow - 1);

        AtomicLong currentCounter = windowCounts.computeIfAbsent(currentWindow, k -> new AtomicLong(0));
        AtomicLong previousCounter = windowCounts.get(previousWindow);

        // Calculate weighted count
        double windowProgress = (double) (now % periodMs) / periodMs;
        long previousCount = previousCounter != null ? previousCounter.get() : 0;
        long currentCount = currentCounter.get();

        double weightedCount = (previousCount * (1 - windowProgress)) + currentCount;
        long resetIn = (periodMs - (now % periodMs)) / 1000;

        if (weightedCount + permits <= config.getMaxRequests()) {
            currentCounter.addAndGet(permits);
            return RateLimitResult.allowed((long) (weightedCount + permits), config.getMaxRequests(), resetIn);
        } else {
            return RateLimitResult.rejected((long) weightedCount, config.getMaxRequests(),
                    resetIn, "Sliding window limit exceeded");
        }
    }

    // Token bucket implementation
    private RateLimitResult tryAcquireTokenBucket(int permits) {
        while (true) {
            TokenBucket current = tokenBucket.get();
            long now = System.currentTimeMillis();
            long elapsed = now - current.lastRefillTime();

            // Calculate tokens to add
            double tokensToAdd = elapsed * config.getRefillRate() / 1000.0;
            double newTokens = Math.min(config.getBucketCapacity(), current.tokens() + tokensToAdd);

            if (newTokens >= permits) {
                TokenBucket newBucket = new TokenBucket(newTokens - permits, now);
                if (tokenBucket.compareAndSet(current, newBucket)) {
                    long resetIn = (long) ((permits - newTokens + permits) / config.getRefillRate());
                    return RateLimitResult.allowed((long) (config.getBucketCapacity() - newBucket.tokens()),
                            config.getBucketCapacity(), Math.max(0, resetIn));
                }
                // CAS failed, retry
            } else {
                // Not enough tokens
                long waitTime = (long) ((permits - newTokens) / config.getRefillRate() * 1000);
                return RateLimitResult.rejected((long) (config.getBucketCapacity() - newTokens),
                        config.getBucketCapacity(), waitTime / 1000, "Insufficient tokens");
            }
        }
    }

    // Leaky bucket implementation (simplified as constant rate)
    private RateLimitResult tryAcquireLeakyBucket(int permits) {
        // For simplicity, leaky bucket uses the same logic as token bucket
        // but with bucket capacity = 1 (no burst allowed)
        return tryAcquireTokenBucket(permits);
    }

    private void cleanupOldWindows(long oldestToKeep) {
        windowCounts.entrySet().removeIf(entry -> entry.getKey() < oldestToKeep);
    }

    /**
     * Rate limiter statistics.
     */
    public record RateLimiterStats(
            String name,
            long totalRequests,
            long allowedRequests,
            long rejectedRequests,
            double currentRate
    ) {
        public double getRejectionRate() {
            return totalRequests > 0 ? (double) rejectedRequests / totalRequests * 100 : 0;
        }
    }
}
