package com.fep.transaction.risk.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RateLimiter Tests")
class RateLimiterTest {

    @Nested
    @DisplayName("Token Bucket")
    class TokenBucketTests {

        private RateLimiter rateLimiter;

        @BeforeEach
        void setUp() {
            RateLimitConfig config = RateLimitConfig.builder()
                    .name("token-bucket-test")
                    .type(RateLimitType.TOKEN_BUCKET)
                    .maxRequests(10)
                    .period(Duration.ofSeconds(1))
                    .bucketCapacity(10)
                    .refillRate(10.0)
                    .build();
            rateLimiter = new RateLimiter(config);
        }

        @Test
        @DisplayName("Should allow requests within limit")
        void shouldAllowRequestsWithinLimit() {
            for (int i = 0; i < 5; i++) {
                RateLimitResult result = rateLimiter.tryAcquire();
                assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
            }
        }

        @Test
        @DisplayName("Should reject requests exceeding limit")
        void shouldRejectRequestsExceedingLimit() {
            // Exhaust the bucket
            for (int i = 0; i < 10; i++) {
                rateLimiter.tryAcquire();
            }

            // Next request should be rejected
            RateLimitResult result = rateLimiter.tryAcquire();
            assertFalse(result.isAllowed());
            assertNotNull(result.getReason());
        }

        @Test
        @DisplayName("Should refill tokens over time")
        void shouldRefillTokensOverTime() throws InterruptedException {
            // Exhaust the bucket
            for (int i = 0; i < 10; i++) {
                rateLimiter.tryAcquire();
            }

            // Wait for refill (1 second = 10 tokens)
            Thread.sleep(500); // Wait 500ms = ~5 tokens

            // Should be able to make some requests again
            RateLimitResult result = rateLimiter.tryAcquire();
            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("Should handle burst traffic")
        void shouldHandleBurstTraffic() {
            RateLimitConfig burstConfig = RateLimitConfig.builder()
                    .name("burst-test")
                    .type(RateLimitType.TOKEN_BUCKET)
                    .maxRequests(10)
                    .bucketCapacity(20) // Allow 2x burst
                    .refillRate(10.0)
                    .build();
            RateLimiter burstLimiter = new RateLimiter(burstConfig);

            // Should allow burst up to capacity
            int allowed = 0;
            for (int i = 0; i < 25; i++) {
                if (burstLimiter.tryAcquire().isAllowed()) {
                    allowed++;
                }
            }

            assertEquals(20, allowed); // Should allow up to bucket capacity
        }
    }

    @Nested
    @DisplayName("Fixed Window")
    class FixedWindowTests {

        private RateLimiter rateLimiter;

        @BeforeEach
        void setUp() {
            RateLimitConfig config = RateLimitConfig.builder()
                    .name("fixed-window-test")
                    .type(RateLimitType.FIXED_WINDOW)
                    .maxRequests(5)
                    .period(Duration.ofSeconds(1))
                    .build();
            rateLimiter = new RateLimiter(config);
        }

        @Test
        @DisplayName("Should allow requests within window limit")
        void shouldAllowRequestsWithinWindowLimit() {
            for (int i = 0; i < 5; i++) {
                RateLimitResult result = rateLimiter.tryAcquire();
                assertTrue(result.isAllowed());
            }
        }

        @Test
        @DisplayName("Should reject requests exceeding window limit")
        void shouldRejectRequestsExceedingWindowLimit() {
            // Use up the limit
            for (int i = 0; i < 5; i++) {
                rateLimiter.tryAcquire();
            }

            // Next should be rejected
            RateLimitResult result = rateLimiter.tryAcquire();
            assertFalse(result.isAllowed());
        }

        @Test
        @DisplayName("Should reset after window period")
        void shouldResetAfterWindowPeriod() throws InterruptedException {
            // Use up the limit
            for (int i = 0; i < 5; i++) {
                rateLimiter.tryAcquire();
            }

            // Wait for new window
            Thread.sleep(1100);

            // Should be allowed again
            RateLimitResult result = rateLimiter.tryAcquire();
            assertTrue(result.isAllowed());
        }
    }

    @Nested
    @DisplayName("Sliding Window")
    class SlidingWindowTests {

        private RateLimiter rateLimiter;

        @BeforeEach
        void setUp() {
            RateLimitConfig config = RateLimitConfig.builder()
                    .name("sliding-window-test")
                    .type(RateLimitType.SLIDING_WINDOW)
                    .maxRequests(10)
                    .period(Duration.ofSeconds(1))
                    .build();
            rateLimiter = new RateLimiter(config);
        }

        @Test
        @DisplayName("Should allow requests within sliding window limit")
        void shouldAllowRequestsWithinSlidingWindowLimit() {
            for (int i = 0; i < 10; i++) {
                RateLimitResult result = rateLimiter.tryAcquire();
                assertTrue(result.isAllowed());
            }
        }

        @Test
        @DisplayName("Should provide smoother rate limiting")
        void shouldProvideSmoothRateLimiting() throws InterruptedException {
            // Use half the limit
            for (int i = 0; i < 5; i++) {
                rateLimiter.tryAcquire();
            }

            // Wait half window
            Thread.sleep(500);

            // Due to sliding window, some previous requests should "age out"
            // Should be able to make more requests
            int additionalAllowed = 0;
            for (int i = 0; i < 5; i++) {
                if (rateLimiter.tryAcquire().isAllowed()) {
                    additionalAllowed++;
                }
            }

            assertTrue(additionalAllowed >= 2); // At least some should be allowed
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track request statistics")
        void shouldTrackRequestStatistics() {
            RateLimitConfig config = RateLimitConfig.builder()
                    .name("stats-test")
                    .type(RateLimitType.TOKEN_BUCKET)
                    .maxRequests(5)
                    .bucketCapacity(5)
                    .refillRate(5.0)
                    .build();
            RateLimiter limiter = new RateLimiter(config);

            // Make some requests
            for (int i = 0; i < 7; i++) {
                limiter.tryAcquire();
            }

            RateLimiter.RateLimiterStats stats = limiter.getStats();
            assertEquals(7, stats.totalRequests());
            assertEquals(5, stats.allowedRequests());
            assertEquals(2, stats.rejectedRequests());
        }

        @Test
        @DisplayName("Should calculate rejection rate")
        void shouldCalculateRejectionRate() {
            RateLimitConfig config = RateLimitConfig.builder()
                    .name("rejection-rate-test")
                    .type(RateLimitType.FIXED_WINDOW)
                    .maxRequests(5)
                    .period(Duration.ofSeconds(1))
                    .build();
            RateLimiter limiter = new RateLimiter(config);

            // Make 10 requests (5 allowed, 5 rejected)
            for (int i = 0; i < 10; i++) {
                limiter.tryAcquire();
            }

            RateLimiter.RateLimiterStats stats = limiter.getStats();
            assertEquals(50.0, stats.getRejectionRate(), 0.01);
        }
    }

    @Nested
    @DisplayName("Result Information")
    class ResultInformationTests {

        @Test
        @DisplayName("Should provide remaining count")
        void shouldProvideRemainingCount() {
            RateLimitConfig config = RateLimitConfig.builder()
                    .name("remaining-test")
                    .type(RateLimitType.TOKEN_BUCKET)
                    .maxRequests(10)
                    .bucketCapacity(10)
                    .refillRate(10.0)
                    .build();
            RateLimiter limiter = new RateLimiter(config);

            // Use 3 tokens
            for (int i = 0; i < 3; i++) {
                limiter.tryAcquire();
            }

            RateLimitResult result = limiter.tryAcquire();
            assertTrue(result.isAllowed());
            assertEquals(10, result.getLimit());
        }

        @Test
        @DisplayName("Should provide reset time")
        void shouldProvideResetTime() {
            RateLimitConfig config = RateLimitConfig.builder()
                    .name("reset-time-test")
                    .type(RateLimitType.FIXED_WINDOW)
                    .maxRequests(5)
                    .period(Duration.ofSeconds(10))
                    .build();
            RateLimiter limiter = new RateLimiter(config);

            RateLimitResult result = limiter.tryAcquire();

            assertTrue(result.isAllowed());
            assertTrue(result.getResetInSeconds() >= 0);
            assertTrue(result.getResetInSeconds() <= 10);
        }
    }

    @Nested
    @DisplayName("Multiple Permits")
    class MultiplePermitsTests {

        @Test
        @DisplayName("Should acquire multiple permits at once")
        void shouldAcquireMultiplePermitsAtOnce() {
            RateLimitConfig config = RateLimitConfig.builder()
                    .name("multi-permit-test")
                    .type(RateLimitType.TOKEN_BUCKET)
                    .maxRequests(10)
                    .bucketCapacity(10)
                    .refillRate(10.0)
                    .build();
            RateLimiter limiter = new RateLimiter(config);

            RateLimitResult result = limiter.tryAcquire(5);
            assertTrue(result.isAllowed());

            // Should have 5 remaining
            result = limiter.tryAcquire(5);
            assertTrue(result.isAllowed());

            // Should be exhausted
            result = limiter.tryAcquire(1);
            assertFalse(result.isAllowed());
        }
    }

    @Nested
    @DisplayName("Registry")
    class RegistryTests {

        @Test
        @DisplayName("Should create rate limiter from registry")
        void shouldCreateRateLimiterFromRegistry() {
            RateLimiterRegistry registry = new RateLimiterRegistry();

            RateLimiter limiter = registry.getOrCreate("test-service");

            assertNotNull(limiter);
        }

        @Test
        @DisplayName("Should return same limiter for same name")
        void shouldReturnSameLimiterForSameName() {
            RateLimiterRegistry registry = new RateLimiterRegistry();

            RateLimiter limiter1 = registry.getOrCreate("service-a");
            RateLimiter limiter2 = registry.getOrCreate("service-a");

            assertSame(limiter1, limiter2);
        }

        @Test
        @DisplayName("Should check multiple limiters")
        void shouldCheckMultipleLimiters() {
            RateLimiterRegistry registry = new RateLimiterRegistry();
            registry.getOrCreate("global", RateLimitConfig.tpsLimit("global", 100));
            registry.getOrCreate("per-user", RateLimitConfig.tpsLimit("per-user", 10));

            RateLimitResult result = registry.checkAll("global", "per-user");

            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("Should initialize default limiters")
        void shouldInitializeDefaultLimiters() {
            RateLimiterRegistry registry = new RateLimiterRegistry();
            registry.initializeDefaultLimiters();

            assertTrue(registry.get("global").isPresent());
            assertTrue(registry.get("channel-atm").isPresent());
            assertTrue(registry.get("fisc").isPresent());
        }

        @Test
        @DisplayName("Should get statistics for all limiters")
        void shouldGetStatisticsForAllLimiters() {
            RateLimiterRegistry registry = new RateLimiterRegistry();
            registry.getOrCreate("limiter-1");
            registry.getOrCreate("limiter-2");

            var stats = registry.getStatistics();

            assertEquals(2, stats.get("totalLimiters"));
            assertNotNull(stats.get("limiters"));
        }
    }

    @Nested
    @DisplayName("Config Factory Methods")
    class ConfigFactoryMethodsTests {

        @Test
        @DisplayName("Should create TPS limit config")
        void shouldCreateTpsLimitConfig() {
            RateLimitConfig config = RateLimitConfig.tpsLimit("test", 1000);

            assertEquals("test", config.getName());
            assertEquals(RateLimitType.TOKEN_BUCKET, config.getType());
            assertEquals(1000, config.getMaxRequests());
            assertEquals(2000, config.getBucketCapacity()); // 2x burst
        }

        @Test
        @DisplayName("Should create per-minute config")
        void shouldCreatePerMinuteConfig() {
            RateLimitConfig config = RateLimitConfig.perMinute("test", 60);

            assertEquals(RateLimitType.SLIDING_WINDOW, config.getType());
            assertEquals(60, config.getMaxRequests());
            assertEquals(Duration.ofMinutes(1), config.getPeriod());
        }

        @Test
        @DisplayName("Should create strict config")
        void shouldCreateStrictConfig() {
            RateLimitConfig config = RateLimitConfig.strict("test", 50);

            assertEquals(RateLimitType.FIXED_WINDOW, config.getType());
            assertEquals(50, config.getMaxRequests());
            assertFalse(config.isQueueExcessRequests());
        }
    }

    @Nested
    @DisplayName("Reset Operations")
    class ResetOperationsTests {

        @Test
        @DisplayName("Should reset rate limiter")
        void shouldResetRateLimiter() {
            // Use strict config with no burst allowance
            RateLimitConfig config = RateLimitConfig.builder()
                    .name("reset-test")
                    .type(RateLimitType.TOKEN_BUCKET)
                    .maxRequests(5)
                    .bucketCapacity(5)  // No burst
                    .refillRate(5.0)
                    .build();
            RateLimiter limiter = new RateLimiter(config);

            // Use up the limit
            for (int i = 0; i < 5; i++) {
                limiter.tryAcquire();
            }
            assertFalse(limiter.tryAcquire().isAllowed());

            // Reset
            limiter.reset();

            // Should be allowed again
            assertTrue(limiter.tryAcquire().isAllowed());
            assertEquals(0, limiter.getStats().rejectedRequests());
        }
    }
}
