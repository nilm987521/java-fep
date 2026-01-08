package com.fep.transaction.risk.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing multiple circuit breakers.
 */
@Component
public class CircuitBreakerRegistry {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerRegistry.class);

    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    /**
     * Gets or creates a circuit breaker with default configuration.
     */
    public CircuitBreaker getOrCreate(String name) {
        return circuitBreakers.computeIfAbsent(name, n -> {
            CircuitBreaker cb = new CircuitBreaker(CircuitBreakerConfig.defaultConfig(n));
            log.info("Created circuit breaker: {}", n);
            return cb;
        });
    }

    /**
     * Gets or creates a circuit breaker with custom configuration.
     */
    public CircuitBreaker getOrCreate(String name, CircuitBreakerConfig config) {
        return circuitBreakers.computeIfAbsent(name, n -> {
            CircuitBreaker cb = new CircuitBreaker(config);
            log.info("Created circuit breaker: {} with custom config", n);
            return cb;
        });
    }

    /**
     * Gets an existing circuit breaker.
     */
    public Optional<CircuitBreaker> get(String name) {
        return Optional.ofNullable(circuitBreakers.get(name));
    }

    /**
     * Removes a circuit breaker.
     */
    public boolean remove(String name) {
        CircuitBreaker removed = circuitBreakers.remove(name);
        if (removed != null) {
            log.info("Removed circuit breaker: {}", name);
            return true;
        }
        return false;
    }

    /**
     * Gets all circuit breaker names.
     */
    public Set<String> getNames() {
        return new HashSet<>(circuitBreakers.keySet());
    }

    /**
     * Gets all circuit breakers.
     */
    public Collection<CircuitBreaker> getAll() {
        return new ArrayList<>(circuitBreakers.values());
    }

    /**
     * Gets all open circuit breakers.
     */
    public List<CircuitBreaker> getOpenCircuits() {
        return circuitBreakers.values().stream()
                .filter(CircuitBreaker::isOpen)
                .toList();
    }

    /**
     * Resets all circuit breakers.
     */
    public void resetAll() {
        circuitBreakers.values().forEach(CircuitBreaker::reset);
        log.info("Reset all {} circuit breakers", circuitBreakers.size());
    }

    /**
     * Gets statistics for all circuit breakers.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCircuits", circuitBreakers.size());
        stats.put("openCircuits", getOpenCircuits().size());

        Map<String, Map<String, Object>> circuitStats = new HashMap<>();
        for (Map.Entry<String, CircuitBreaker> entry : circuitBreakers.entrySet()) {
            CircuitBreaker cb = entry.getValue();
            CircuitBreakerMetrics metrics = cb.getMetrics();

            Map<String, Object> cbStats = new HashMap<>();
            cbStats.put("state", cb.getState().name());
            cbStats.put("totalCalls", metrics.getTotalCalls().get());
            cbStats.put("successfulCalls", metrics.getSuccessfulCalls().get());
            cbStats.put("failedCalls", metrics.getFailedCalls().get());
            cbStats.put("rejectedCalls", metrics.getRejectedCalls().get());
            cbStats.put("failureRate", String.format("%.2f%%", metrics.getFailureRate()));
            cbStats.put("averageDuration", String.format("%.2fms", metrics.getAverageDurationMs()));
            cbStats.put("timesOpened", metrics.getTimesOpened().get());

            circuitStats.put(entry.getKey(), cbStats);
        }
        stats.put("circuits", circuitStats);

        return stats;
    }

    /**
     * Creates pre-configured circuit breakers for common services.
     */
    public void initializeDefaultCircuits() {
        // FISC connection
        getOrCreate("fisc-connection", CircuitBreakerConfig.builder()
                .name("fisc-connection")
                .failureThresholdPercent(40)
                .minimumCalls(5)
                .waitDurationInOpenState(java.time.Duration.ofSeconds(30))
                .build());

        // Core banking
        getOrCreate("core-banking", CircuitBreakerConfig.builder()
                .name("core-banking")
                .failureThresholdPercent(50)
                .minimumCalls(10)
                .waitDurationInOpenState(java.time.Duration.ofSeconds(45))
                .build());

        // HSM
        getOrCreate("hsm", CircuitBreakerConfig.builder()
                .name("hsm")
                .failureThresholdPercent(30)
                .minimumCalls(3)
                .waitDurationInOpenState(java.time.Duration.ofSeconds(20))
                .build());

        // External services
        getOrCreate("external-api", CircuitBreakerConfig.lenient("external-api"));

        log.info("Initialized {} default circuit breakers", circuitBreakers.size());
    }
}
