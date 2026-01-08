package com.fep.transaction.retry;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.Set;

/**
 * Configuration for transaction retry behavior.
 */
@Data
@Builder
public class RetryPolicy {

    /** Maximum number of retry attempts */
    @Builder.Default
    private int maxRetries = 3;

    /** Initial delay before first retry */
    @Builder.Default
    private Duration initialDelay = Duration.ofMillis(100);

    /** Maximum delay between retries */
    @Builder.Default
    private Duration maxDelay = Duration.ofSeconds(30);

    /** Multiplier for exponential backoff */
    @Builder.Default
    private double backoffMultiplier = 2.0;

    /** Whether to use exponential backoff */
    @Builder.Default
    private boolean exponentialBackoff = true;

    /** Jitter factor (0.0 to 1.0) to add randomness to delays */
    @Builder.Default
    private double jitterFactor = 0.1;

    /** Response codes that should trigger a retry */
    @Builder.Default
    private Set<String> retryableResponseCodes = Set.of(
            "91",  // Issuer or switch inoperative
            "96",  // System malfunction
            "68",  // Response received too late
            "ND"   // No response (timeout)
    );

    /** Exception types that should trigger a retry */
    @Builder.Default
    private Set<Class<? extends Exception>> retryableExceptions = Set.of(
            java.net.SocketTimeoutException.class,
            java.net.ConnectException.class,
            java.io.IOException.class
    );

    /** Whether to retry on timeout */
    @Builder.Default
    private boolean retryOnTimeout = true;

    /** Whether to retry on connection error */
    @Builder.Default
    private boolean retryOnConnectionError = true;

    /**
     * Calculates the delay for a given retry attempt.
     *
     * @param attempt the retry attempt number (1-based)
     * @return the delay before the retry
     */
    public Duration getDelayForAttempt(int attempt) {
        if (attempt <= 0) {
            return Duration.ZERO;
        }

        long delayMs;
        if (exponentialBackoff) {
            delayMs = (long) (initialDelay.toMillis() * Math.pow(backoffMultiplier, attempt - 1));
        } else {
            delayMs = initialDelay.toMillis();
        }

        // Cap at max delay
        delayMs = Math.min(delayMs, maxDelay.toMillis());

        // Add jitter
        if (jitterFactor > 0) {
            long jitter = (long) (delayMs * jitterFactor * (Math.random() * 2 - 1));
            delayMs = Math.max(0, delayMs + jitter);
        }

        return Duration.ofMillis(delayMs);
    }

    /**
     * Checks if a response code is retryable.
     */
    public boolean isRetryableResponseCode(String responseCode) {
        return responseCode != null && retryableResponseCodes.contains(responseCode);
    }

    /**
     * Checks if an exception is retryable.
     */
    public boolean isRetryableException(Exception exception) {
        if (exception == null) {
            return false;
        }

        // Check if timeout
        if (retryOnTimeout && exception instanceof java.util.concurrent.TimeoutException) {
            return true;
        }

        // Check configured exception types
        for (Class<? extends Exception> retryableClass : retryableExceptions) {
            if (retryableClass.isInstance(exception)) {
                return true;
            }
        }

        // Check cause chain
        Throwable cause = exception.getCause();
        while (cause != null) {
            for (Class<? extends Exception> retryableClass : retryableExceptions) {
                if (retryableClass.isInstance(cause)) {
                    return true;
                }
            }
            cause = cause.getCause();
        }

        return false;
    }

    /**
     * Checks if another retry attempt is allowed.
     * @param currentAttempt the current retry attempt number (1-based)
     * @return true if retry is allowed
     */
    public boolean shouldRetry(int currentAttempt) {
        return currentAttempt <= maxRetries;
    }

    // Pre-configured policies

    /**
     * Creates a policy for immediate retries (no delay).
     */
    public static RetryPolicy immediate(int maxRetries) {
        return RetryPolicy.builder()
                .maxRetries(maxRetries)
                .initialDelay(Duration.ZERO)
                .exponentialBackoff(false)
                .build();
    }

    /**
     * Creates a policy with fixed delay between retries.
     */
    public static RetryPolicy fixedDelay(int maxRetries, Duration delay) {
        return RetryPolicy.builder()
                .maxRetries(maxRetries)
                .initialDelay(delay)
                .exponentialBackoff(false)
                .build();
    }

    /**
     * Creates a policy with exponential backoff.
     */
    public static RetryPolicy exponentialBackoff(int maxRetries, Duration initialDelay, double multiplier) {
        return RetryPolicy.builder()
                .maxRetries(maxRetries)
                .initialDelay(initialDelay)
                .backoffMultiplier(multiplier)
                .exponentialBackoff(true)
                .build();
    }

    /**
     * Creates a conservative policy for financial transactions.
     */
    public static RetryPolicy financialTransaction() {
        return RetryPolicy.builder()
                .maxRetries(2)  // Limited retries for financial transactions
                .initialDelay(Duration.ofMillis(500))
                .maxDelay(Duration.ofSeconds(5))
                .backoffMultiplier(2.0)
                .exponentialBackoff(true)
                .jitterFactor(0.2)
                .build();
    }

    /**
     * Creates an aggressive policy for non-critical operations.
     */
    public static RetryPolicy aggressive() {
        return RetryPolicy.builder()
                .maxRetries(5)
                .initialDelay(Duration.ofMillis(100))
                .maxDelay(Duration.ofSeconds(60))
                .backoffMultiplier(2.0)
                .exponentialBackoff(true)
                .jitterFactor(0.3)
                .build();
    }
}
