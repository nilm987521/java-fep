package com.fep.transaction.retry;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Transaction service wrapper that provides retry capabilities.
 * Implements configurable retry logic with exponential backoff.
 */
public class RetryableTransactionService {

    private static final Logger log = LoggerFactory.getLogger(RetryableTransactionService.class);

    private final TransactionService transactionService;
    private final RetryPolicy defaultPolicy;
    private final ScheduledExecutorService scheduler;

    /** Listeners for retry events */
    private RetryListener listener;

    public RetryableTransactionService(TransactionService transactionService) {
        this(transactionService, RetryPolicy.financialTransaction());
    }

    public RetryableTransactionService(TransactionService transactionService, RetryPolicy defaultPolicy) {
        this.transactionService = transactionService;
        this.defaultPolicy = defaultPolicy;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    /**
     * Sets the retry listener.
     */
    public void setListener(RetryListener listener) {
        this.listener = listener;
    }

    /**
     * Processes a transaction with retry using default policy.
     */
    public TransactionResponse processWithRetry(TransactionRequest request) {
        return processWithRetry(request, defaultPolicy);
    }

    /**
     * Processes a transaction with retry using specified policy.
     */
    public TransactionResponse processWithRetry(TransactionRequest request, RetryPolicy policy) {
        RetryContext context = RetryContext.create(request, policy);

        log.info("[{}] Starting transaction with retry policy: maxRetries={}",
                request.getTransactionId(), policy.getMaxRetries());

        notifyRetryStart(context);

        while (!context.isComplete()) {
            long startTime = System.currentTimeMillis();

            try {
                TransactionResponse response = transactionService.process(request);
                Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);

                if (isSuccessful(response)) {
                    context.recordSuccess(response, duration);
                    notifyRetrySuccess(context);
                    log.info("[{}] Transaction successful on attempt {}",
                            request.getTransactionId(), context.getCurrentAttempt() + 1);
                    return response;
                }

                // Check if should retry
                context.recordFailure(response, duration);

                if (policy.isRetryableResponseCode(response.getResponseCode())) {
                    if (context.prepareNextAttempt()) {
                        Duration delay = context.getNextRetryDelay();
                        notifyRetryAttempt(context, delay);
                        log.warn("[{}] Retryable response code {}, attempt {}/{}, waiting {}ms",
                                request.getTransactionId(),
                                response.getResponseCode(),
                                context.getCurrentAttempt(),
                                policy.getMaxRetries(),
                                delay.toMillis());
                        sleep(delay);
                    } else {
                        notifyRetryExhausted(context);
                        return response;
                    }
                } else {
                    // Not retryable
                    context.markFailed();
                    notifyRetryFailed(context);
                    log.info("[{}] Non-retryable response code: {}",
                            request.getTransactionId(), response.getResponseCode());
                    return response;
                }

            } catch (Exception e) {
                Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
                context.recordException(e, duration);

                if (policy.isRetryableException(e)) {
                    if (context.prepareNextAttempt()) {
                        Duration delay = context.getNextRetryDelay();
                        notifyRetryAttempt(context, delay);
                        log.warn("[{}] Retryable exception, attempt {}/{}, waiting {}ms: {}",
                                request.getTransactionId(),
                                context.getCurrentAttempt(),
                                policy.getMaxRetries(),
                                delay.toMillis(),
                                e.getMessage());
                        sleep(delay);
                    } else {
                        notifyRetryExhausted(context);
                        return createErrorResponse(request, e);
                    }
                } else {
                    context.markFailed();
                    notifyRetryFailed(context);
                    log.error("[{}] Non-retryable exception: {}", request.getTransactionId(), e.getMessage(), e);
                    return createErrorResponse(request, e);
                }
            }
        }

        // Exhausted all retries
        log.warn("[{}] All retry attempts exhausted after {} attempts",
                request.getTransactionId(), context.getTotalAttempts());

        if (context.getLastResponse() != null) {
            return context.getLastResponse();
        }
        return createErrorResponse(request, context.getLastException());
    }

    /**
     * Processes a transaction with retry asynchronously.
     */
    public CompletableFuture<TransactionResponse> processWithRetryAsync(TransactionRequest request) {
        return processWithRetryAsync(request, defaultPolicy);
    }

    /**
     * Processes a transaction with retry asynchronously using specified policy.
     */
    public CompletableFuture<TransactionResponse> processWithRetryAsync(TransactionRequest request,
                                                                         RetryPolicy policy) {
        return CompletableFuture.supplyAsync(
                () -> processWithRetry(request, policy),
                scheduler
        );
    }

    /**
     * Executes an operation with retry.
     */
    public <T> T executeWithRetry(Supplier<T> operation, RetryPolicy policy) {
        int attempt = 0;

        while (true) {
            try {
                return operation.get();
            } catch (Exception e) {
                if (policy.isRetryableException(e) && policy.shouldRetry(attempt + 1)) {
                    attempt++;
                    Duration delay = policy.getDelayForAttempt(attempt);
                    log.warn("Operation failed, retrying attempt {}/{} after {}ms: {}",
                            attempt, policy.getMaxRetries(), delay.toMillis(), e.getMessage());
                    sleep(delay);
                } else {
                    throw new RetryExhaustedException("Retry exhausted after " + attempt + " attempts", e);
                }
            }
        }
    }

    /**
     * Checks if a response indicates success.
     */
    private boolean isSuccessful(TransactionResponse response) {
        return response != null && response.isApproved();
    }

    /**
     * Creates an error response from an exception.
     */
    private TransactionResponse createErrorResponse(TransactionRequest request, Exception e) {
        String errorMessage = e != null ? e.getMessage() : "Unknown error after retry";
        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(ResponseCode.SYSTEM_MALFUNCTION.getCode())
                .responseCodeEnum(ResponseCode.SYSTEM_MALFUNCTION)
                .responseDescription("System error: " + errorMessage)
                .responseDescriptionChinese("系統錯誤: " + errorMessage)
                .approved(false)
                .errorDetails(errorMessage)
                .build();
    }

    /**
     * Sleeps for the specified duration.
     */
    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryInterruptedException("Retry interrupted", e);
        }
    }

    // Listener notification methods

    private void notifyRetryStart(RetryContext context) {
        if (listener != null) {
            try {
                listener.onRetryStart(context);
            } catch (Exception e) {
                log.error("Error in retry listener onRetryStart", e);
            }
        }
    }

    private void notifyRetryAttempt(RetryContext context, Duration delay) {
        if (listener != null) {
            try {
                listener.onRetryAttempt(context, delay);
            } catch (Exception e) {
                log.error("Error in retry listener onRetryAttempt", e);
            }
        }
    }

    private void notifyRetrySuccess(RetryContext context) {
        if (listener != null) {
            try {
                listener.onRetrySuccess(context);
            } catch (Exception e) {
                log.error("Error in retry listener onRetrySuccess", e);
            }
        }
    }

    private void notifyRetryFailed(RetryContext context) {
        if (listener != null) {
            try {
                listener.onRetryFailed(context);
            } catch (Exception e) {
                log.error("Error in retry listener onRetryFailed", e);
            }
        }
    }

    private void notifyRetryExhausted(RetryContext context) {
        if (listener != null) {
            try {
                listener.onRetryExhausted(context);
            } catch (Exception e) {
                log.error("Error in retry listener onRetryExhausted", e);
            }
        }
    }

    /**
     * Shuts down the service.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Listener interface for retry events.
     */
    public interface RetryListener {
        default void onRetryStart(RetryContext context) {}
        default void onRetryAttempt(RetryContext context, Duration delay) {}
        default void onRetrySuccess(RetryContext context) {}
        default void onRetryFailed(RetryContext context) {}
        default void onRetryExhausted(RetryContext context) {}
    }

    /**
     * Exception thrown when all retries are exhausted.
     */
    public static class RetryExhaustedException extends RuntimeException {
        public RetryExhaustedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when retry is interrupted.
     */
    public static class RetryInterruptedException extends RuntimeException {
        public RetryInterruptedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
