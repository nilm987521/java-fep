package com.fep.transaction.timeout;

import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Manages transaction timeouts with automatic monitoring and callbacks.
 */
public class TimeoutManager {

    private static final Logger log = LoggerFactory.getLogger(TimeoutManager.class);

    /** Active timeout contexts */
    private final Map<String, TimeoutContext> activeContexts;

    /** Timeout configuration */
    private final TimeoutConfig config;

    /** Callback handler */
    private final TimeoutCallback callback;

    /** Executor for async operations */
    private final ScheduledExecutorService scheduler;

    /** Executor for transaction execution */
    private final ExecutorService executor;

    /** Monitoring interval in milliseconds */
    private static final long MONITOR_INTERVAL_MS = 1000;

    public TimeoutManager() {
        this(new TimeoutConfig(), new DefaultTimeoutCallback());
    }

    public TimeoutManager(TimeoutConfig config) {
        this(config, new DefaultTimeoutCallback());
    }

    public TimeoutManager(TimeoutConfig config, TimeoutCallback callback) {
        this.activeContexts = new ConcurrentHashMap<>();
        this.config = config;
        this.callback = callback;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "timeout-monitor");
            t.setDaemon(true);
            return t;
        });
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "timeout-executor");
            t.setDaemon(true);
            return t;
        });

        startMonitoring();
    }

    /**
     * Starts the background monitoring task.
     */
    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(
                this::checkTimeouts,
                MONITOR_INTERVAL_MS,
                MONITOR_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        log.debug("Timeout monitoring started with {}ms interval", MONITOR_INTERVAL_MS);
    }

    /**
     * Checks all active contexts for timeout/warning.
     */
    private void checkTimeouts() {
        for (TimeoutContext context : activeContexts.values()) {
            TimeoutStatus previousStatus = context.getStatus();
            TimeoutStatus currentStatus = context.checkStatus();

            if (previousStatus != currentStatus) {
                switch (currentStatus) {
                    case WARNING -> callback.onWarning(context);
                    case EXPIRED -> {
                        callback.onTimeout(context);
                        activeContexts.remove(context.getTransactionId());
                    }
                    default -> { /* No action for ACTIVE or COMPLETED */ }
                }
            }
        }
    }

    /**
     * Starts tracking a transaction for timeout.
     */
    public TimeoutContext startTracking(String transactionId, TransactionType type) {
        long timeout = config.getTimeout(type);
        TimeoutContext context = new TimeoutContext(transactionId, type, timeout);
        activeContexts.put(transactionId, context);
        log.debug("[{}] Started timeout tracking: type={}, timeout={}ms",
                transactionId, type, timeout);
        return context;
    }

    /**
     * Starts tracking with a custom timeout.
     */
    public TimeoutContext startTracking(String transactionId, TransactionType type, long timeoutMs) {
        TimeoutContext context = new TimeoutContext(transactionId, type, timeoutMs);
        activeContexts.put(transactionId, context);
        log.debug("[{}] Started timeout tracking: type={}, timeout={}ms (custom)",
                transactionId, type, timeoutMs);
        return context;
    }

    /**
     * Completes tracking for a transaction.
     */
    public void completeTracking(String transactionId) {
        TimeoutContext context = activeContexts.remove(transactionId);
        if (context != null) {
            context.markCompleted();
            callback.onComplete(context);
        }
    }

    /**
     * Gets the timeout context for a transaction.
     */
    public TimeoutContext getContext(String transactionId) {
        return activeContexts.get(transactionId);
    }

    /**
     * Executes a task with timeout.
     *
     * @param transactionId Transaction identifier
     * @param type Transaction type for timeout lookup
     * @param task The task to execute
     * @param <T> Result type
     * @return The result of the task
     * @throws TransactionException if timeout occurs
     */
    public <T> T executeWithTimeout(String transactionId, TransactionType type,
                                     Supplier<T> task) {
        long timeout = config.getTimeout(type);
        return executeWithTimeout(transactionId, type, timeout, task);
    }

    /**
     * Executes a task with custom timeout.
     */
    public <T> T executeWithTimeout(String transactionId, TransactionType type,
                                     long timeoutMs, Supplier<T> task) {
        TimeoutContext context = startTracking(transactionId, type, timeoutMs);

        Future<T> future = executor.submit(task::get);

        try {
            T result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            completeTracking(transactionId);
            return result;

        } catch (TimeoutException e) {
            future.cancel(true);
            context.checkStatus(); // Update to EXPIRED
            callback.onTimeout(context);
            activeContexts.remove(transactionId);
            log.error("[{}] Transaction execution timed out after {}ms", transactionId, timeoutMs);
            throw TransactionException.timeout();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            activeContexts.remove(transactionId);
            throw TransactionException.systemError("Transaction interrupted");

        } catch (ExecutionException e) {
            activeContexts.remove(transactionId);
            Throwable cause = e.getCause();
            if (cause instanceof TransactionException te) {
                throw te;
            }
            throw TransactionException.systemError("Transaction execution failed: " + cause.getMessage());
        }
    }

    /**
     * Gets the number of active timeout contexts.
     */
    public int getActiveCount() {
        return activeContexts.size();
    }

    /**
     * Checks if a transaction is being tracked.
     */
    public boolean isTracking(String transactionId) {
        return activeContexts.containsKey(transactionId);
    }

    /**
     * Gets remaining time for a transaction.
     */
    public long getRemainingTime(String transactionId) {
        TimeoutContext context = activeContexts.get(transactionId);
        return context != null ? context.getRemainingMs() : 0;
    }

    /**
     * Shuts down the timeout manager.
     */
    public void shutdown() {
        scheduler.shutdown();
        executor.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        activeContexts.clear();
        log.info("Timeout manager shut down");
    }
}
