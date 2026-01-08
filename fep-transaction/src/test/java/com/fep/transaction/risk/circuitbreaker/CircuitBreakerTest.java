package com.fep.transaction.risk.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CircuitBreaker Tests")
class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;
    private CircuitBreakerConfig config;

    @BeforeEach
    void setUp() {
        config = CircuitBreakerConfig.builder()
                .name("test-circuit")
                .failureThresholdPercent(50)
                .minimumCalls(5)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofMillis(500))
                .permittedCallsInHalfOpen(3)
                .successThresholdInHalfOpen(66)
                .build();
        circuitBreaker = new CircuitBreaker(config);
    }

    @Nested
    @DisplayName("Initial State")
    class InitialStateTests {

        @Test
        @DisplayName("Should start in closed state")
        void shouldStartInClosedState() {
            assertEquals(CircuitState.CLOSED, circuitBreaker.getState());
            assertTrue(circuitBreaker.isClosed());
            assertFalse(circuitBreaker.isOpen());
        }

        @Test
        @DisplayName("Should allow calls in closed state")
        void shouldAllowCallsInClosedState() {
            assertTrue(circuitBreaker.isCallPermitted());
        }
    }

    @Nested
    @DisplayName("Normal Operation")
    class NormalOperationTests {

        @Test
        @DisplayName("Should execute successful calls")
        void shouldExecuteSuccessfulCalls() {
            String result = circuitBreaker.execute(() -> "success");

            assertEquals("success", result);
            assertEquals(CircuitState.CLOSED, circuitBreaker.getState());
        }

        @Test
        @DisplayName("Should track successful calls in metrics")
        void shouldTrackSuccessfulCalls() {
            for (int i = 0; i < 5; i++) {
                circuitBreaker.execute(() -> "success");
            }

            CircuitBreakerMetrics metrics = circuitBreaker.getMetrics();
            assertEquals(5, metrics.getTotalCalls().get());
            assertEquals(5, metrics.getSuccessfulCalls().get());
            assertEquals(0, metrics.getFailedCalls().get());
        }

        @Test
        @DisplayName("Should propagate exceptions")
        void shouldPropagateExceptions() {
            assertThrows(RuntimeException.class, () ->
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Test error");
                })
            );
        }
    }

    @Nested
    @DisplayName("Circuit Opening")
    class CircuitOpeningTests {

        @Test
        @DisplayName("Should open circuit after failure threshold")
        void shouldOpenCircuitAfterFailureThreshold() {
            // Minimum 5 calls, 50% failure = 3 failures needed
            // Create 5 failures out of 5 calls = 100% failure rate
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            assertEquals(CircuitState.OPEN, circuitBreaker.getState());
            assertTrue(circuitBreaker.isOpen());
        }

        @Test
        @DisplayName("Should not open circuit below minimum calls")
        void shouldNotOpenCircuitBelowMinimumCalls() {
            // Only 3 failures (below minimum of 5)
            for (int i = 0; i < 3; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            assertEquals(CircuitState.CLOSED, circuitBreaker.getState());
        }

        @Test
        @DisplayName("Should reject calls when open")
        void shouldRejectCallsWhenOpen() {
            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            // Attempt another call
            assertThrows(CircuitBreakerException.class, () ->
                circuitBreaker.execute(() -> "should not execute")
            );
        }

        @Test
        @DisplayName("Should track rejected calls")
        void shouldTrackRejectedCalls() {
            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            // Try to call when open
            try {
                circuitBreaker.execute(() -> "test");
            } catch (CircuitBreakerException ignored) {}

            assertEquals(1, circuitBreaker.getMetrics().getRejectedCalls().get());
        }

        @Test
        @DisplayName("Should use fallback when open")
        void shouldUseFallbackWhenOpen() {
            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            String result = circuitBreaker.execute(
                    () -> "primary",
                    () -> "fallback"
            );

            assertEquals("fallback", result);
        }
    }

    @Nested
    @DisplayName("Half-Open State")
    class HalfOpenStateTests {

        @Test
        @DisplayName("Should transition to half-open after wait duration")
        void shouldTransitionToHalfOpenAfterWaitDuration() throws InterruptedException {
            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            assertEquals(CircuitState.OPEN, circuitBreaker.getState());

            // Wait for the circuit to transition to half-open
            Thread.sleep(600); // Wait duration is 500ms

            // Next call should be permitted and transition to half-open
            assertTrue(circuitBreaker.isCallPermitted());
            assertEquals(CircuitState.HALF_OPEN, circuitBreaker.getState());
        }

        @Test
        @DisplayName("Should close circuit after successful calls in half-open")
        void shouldCloseCircuitAfterSuccessfulCallsInHalfOpen() throws InterruptedException {
            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            assertEquals(CircuitState.OPEN, circuitBreaker.getState());

            // Wait for half-open
            Thread.sleep(600);

            // First call triggers transition to half-open via isCallPermitted
            // Then make enough successful calls to meet the threshold
            // Need permittedCallsInHalfOpen (3) successful calls with 66% success rate
            for (int i = 0; i < 4; i++) {
                circuitBreaker.execute(() -> "success");
            }

            // After sufficient successes, circuit should close
            assertEquals(CircuitState.CLOSED, circuitBreaker.getState());
        }

        @Test
        @DisplayName("Should reopen circuit on failure in half-open")
        void shouldReopenCircuitOnFailureInHalfOpen() throws InterruptedException {
            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            // Wait for half-open
            Thread.sleep(600);

            // Make one call to enter half-open
            circuitBreaker.isCallPermitted();

            // Fail in half-open
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Failure in half-open");
                });
            } catch (RuntimeException ignored) {}

            assertEquals(CircuitState.OPEN, circuitBreaker.getState());
        }
    }

    @Nested
    @DisplayName("Callbacks")
    class CallbackTests {

        @Test
        @DisplayName("Should invoke onOpen callback")
        void shouldInvokeOnOpenCallback() {
            AtomicBoolean called = new AtomicBoolean(false);
            circuitBreaker.onOpen(() -> called.set(true));

            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            assertTrue(called.get());
        }

        @Test
        @DisplayName("Should invoke onClose callback")
        void shouldInvokeOnCloseCallback() throws InterruptedException {
            AtomicBoolean called = new AtomicBoolean(false);
            circuitBreaker.onClose(() -> called.set(true));

            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            // Wait for half-open
            Thread.sleep(600);

            // Succeed in half-open to close (need 4 calls due to how counters work)
            for (int i = 0; i < 4; i++) {
                circuitBreaker.execute(() -> "success");
            }

            assertTrue(called.get());
        }
    }

    @Nested
    @DisplayName("Force Operations")
    class ForceOperationsTests {

        @Test
        @DisplayName("Should force open circuit")
        void shouldForceOpenCircuit() {
            circuitBreaker.forceOpen();

            assertEquals(CircuitState.OPEN, circuitBreaker.getState());
        }

        @Test
        @DisplayName("Should force close circuit")
        void shouldForceCloseCircuit() {
            // First open it
            circuitBreaker.forceOpen();
            assertEquals(CircuitState.OPEN, circuitBreaker.getState());

            // Then force close
            circuitBreaker.forceClose();
            assertEquals(CircuitState.CLOSED, circuitBreaker.getState());
        }

        @Test
        @DisplayName("Should reset circuit")
        void shouldResetCircuit() {
            // Generate some metrics
            for (int i = 0; i < 3; i++) {
                circuitBreaker.execute(() -> "success");
            }

            circuitBreaker.reset();

            assertEquals(CircuitState.CLOSED, circuitBreaker.getState());
            // Metrics should be reset
            CircuitBreakerMetrics metrics = circuitBreaker.getMetrics();
            assertEquals(0, metrics.getTotalCalls().get());
        }
    }

    @Nested
    @DisplayName("Metrics")
    class MetricsTests {

        @Test
        @DisplayName("Should calculate failure rate")
        void shouldCalculateFailureRate() {
            // 3 successes, 2 failures = 40% failure rate
            for (int i = 0; i < 3; i++) {
                circuitBreaker.execute(() -> "success");
            }
            for (int i = 0; i < 2; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            CircuitBreakerMetrics metrics = circuitBreaker.getMetrics();
            assertEquals(40.0, metrics.getFailureRate(), 0.01);
        }

        @Test
        @DisplayName("Should track state transitions")
        void shouldTrackStateTransitions() {
            // Open the circuit
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        throw new RuntimeException("Failure");
                    });
                } catch (RuntimeException ignored) {}
            }

            CircuitBreakerMetrics metrics = circuitBreaker.getMetrics();
            assertTrue(metrics.getStateTransitions().get() >= 1);
            assertEquals(1, metrics.getTimesOpened().get());
        }
    }

    @Nested
    @DisplayName("Registry")
    class RegistryTests {

        @Test
        @DisplayName("Should create circuit breaker from registry")
        void shouldCreateCircuitBreakerFromRegistry() {
            CircuitBreakerRegistry registry = new CircuitBreakerRegistry();

            CircuitBreaker cb = registry.getOrCreate("test-service");

            assertNotNull(cb);
            assertEquals(CircuitState.CLOSED, cb.getState());
        }

        @Test
        @DisplayName("Should return same circuit breaker for same name")
        void shouldReturnSameCircuitBreaker() {
            CircuitBreakerRegistry registry = new CircuitBreakerRegistry();

            CircuitBreaker cb1 = registry.getOrCreate("service-a");
            CircuitBreaker cb2 = registry.getOrCreate("service-a");

            assertSame(cb1, cb2);
        }

        @Test
        @DisplayName("Should get statistics for all circuits")
        void shouldGetStatisticsForAllCircuits() {
            CircuitBreakerRegistry registry = new CircuitBreakerRegistry();
            registry.getOrCreate("service-1");
            registry.getOrCreate("service-2");

            var stats = registry.getStatistics();

            assertEquals(2, stats.get("totalCircuits"));
            assertNotNull(stats.get("circuits"));
        }
    }
}
