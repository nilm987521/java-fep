package com.fep.communication.manager;

import com.fep.message.iso8583.Iso8583Message;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages pending requests across dual channels for STAN-based matching.
 *
 * <p>In FISC dual-channel architecture:
 * <ul>
 *   <li>Send Channel sends requests and registers them here</li>
 *   <li>Receive Channel receives responses and completes them here</li>
 *   <li>STAN (System Trace Audit Number) is used to match request/response</li>
 * </ul>
 *
 * <p>This class is thread-safe and designed for high-concurrency scenarios.
 */
@Slf4j
public class PendingRequestManager implements AutoCloseable {

    /** Pending requests waiting for responses, keyed by STAN */
    private final ConcurrentHashMap<String, PendingRequest> pendingRequests;

    /** Scheduler for timeout cleanup */
    private final ScheduledExecutorService timeoutScheduler;

    /** Default timeout in milliseconds */
    private final long defaultTimeoutMs;

    /** Statistics: total registered requests */
    private final AtomicLong totalRegistered = new AtomicLong(0);

    /** Statistics: total completed requests */
    private final AtomicLong totalCompleted = new AtomicLong(0);

    /** Statistics: total timed out requests */
    private final AtomicLong totalTimedOut = new AtomicLong(0);

    /** Statistics: total cancelled requests */
    private final AtomicLong totalCancelled = new AtomicLong(0);

    /** Manager name for logging */
    private final String name;

    /** Whether the manager is closed */
    private volatile boolean closed = false;

    /**
     * Creates a PendingRequestManager with default settings.
     *
     * @param name the manager name for logging
     */
    public PendingRequestManager(String name) {
        this(name, 30000L);
    }

    /**
     * Creates a PendingRequestManager with specified default timeout.
     *
     * @param name the manager name for logging
     * @param defaultTimeoutMs default timeout in milliseconds
     */
    public PendingRequestManager(String name, long defaultTimeoutMs) {
        this.name = name;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.pendingRequests = new ConcurrentHashMap<>();
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pending-request-timeout-" + name);
            t.setDaemon(true);
            return t;
        });

        log.info("[{}] PendingRequestManager initialized with default timeout {}ms",
            name, defaultTimeoutMs);
    }

    /**
     * Registers a pending request for STAN-based matching.
     *
     * <p>Called by Send Channel when sending a request.
     *
     * @param stan the System Trace Audit Number
     * @return CompletableFuture that will complete with the response
     */
    public CompletableFuture<Iso8583Message> register(String stan) {
        return register(stan, defaultTimeoutMs);
    }

    /**
     * Registers a pending request with custom timeout.
     *
     * <p>Called by Send Channel when sending a request.
     *
     * @param stan the System Trace Audit Number
     * @param timeoutMs timeout in milliseconds
     * @return CompletableFuture that will complete with the response
     * @throws IllegalStateException if manager is closed
     * @throws IllegalArgumentException if STAN is null or empty
     */
    public CompletableFuture<Iso8583Message> register(String stan, long timeoutMs) {
        if (closed) {
            throw new IllegalStateException("PendingRequestManager is closed");
        }
        if (stan == null || stan.isEmpty()) {
            throw new IllegalArgumentException("STAN cannot be null or empty");
        }

        CompletableFuture<Iso8583Message> future = new CompletableFuture<>();
        long registeredAt = System.currentTimeMillis();

        PendingRequest pending = new PendingRequest(future, registeredAt, timeoutMs);

        // Check for duplicate STAN
        PendingRequest existing = pendingRequests.putIfAbsent(stan, pending);
        if (existing != null) {
            log.warn("[{}] Duplicate STAN detected: {}. Completing old request with error.", name, stan);
            existing.future.completeExceptionally(
                new IllegalStateException("Duplicate STAN: " + stan));
            pendingRequests.put(stan, pending);
        }

        totalRegistered.incrementAndGet();

        // Schedule timeout
        ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(() -> {
            PendingRequest removed = pendingRequests.remove(stan);
            if (removed != null && !removed.future.isDone()) {
                totalTimedOut.incrementAndGet();
                log.warn("[{}] Request timeout for STAN={} after {}ms", name, stan, timeoutMs);
                removed.future.completeExceptionally(
                    new TimeoutException("Request timeout for STAN: " + stan));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        // Cancel timeout when future completes
        future.whenComplete((result, ex) -> {
            timeoutFuture.cancel(false);
            pendingRequests.remove(stan);
        });

        log.debug("[{}] Registered pending request: STAN={}, timeout={}ms", name, stan, timeoutMs);
        return future;
    }

    /**
     * Completes a pending request with the received response.
     *
     * <p>Called by Receive Channel when a response arrives.
     *
     * @param stan the System Trace Audit Number
     * @param response the response message
     * @return true if a matching request was found and completed
     */
    public boolean complete(String stan, Iso8583Message response) {
        if (stan == null || stan.isEmpty()) {
            log.warn("[{}] Cannot complete request with null/empty STAN", name);
            return false;
        }

        PendingRequest pending = pendingRequests.remove(stan);
        if (pending != null) {
            if (!pending.future.isDone()) {
                pending.future.complete(response);
                totalCompleted.incrementAndGet();
                long duration = System.currentTimeMillis() - pending.registeredAt;
                log.debug("[{}] Completed request: STAN={}, duration={}ms", name, stan, duration);
                return true;
            } else {
                log.debug("[{}] Request already completed/cancelled: STAN={}", name, stan);
                return false;
            }
        } else {
            log.warn("[{}] No pending request found for STAN={}", name, stan);
            return false;
        }
    }

    /**
     * Cancels a specific pending request.
     *
     * @param stan the System Trace Audit Number
     * @param cause the cancellation reason
     * @return true if a matching request was found and cancelled
     */
    public boolean cancel(String stan, Throwable cause) {
        if (stan == null || stan.isEmpty()) {
            return false;
        }

        PendingRequest pending = pendingRequests.remove(stan);
        if (pending != null && !pending.future.isDone()) {
            pending.future.completeExceptionally(cause);
            totalCancelled.incrementAndGet();
            log.debug("[{}] Cancelled request: STAN={}, reason={}", name, stan, cause.getMessage());
            return true;
        }
        return false;
    }

    /**
     * Cancels all pending requests.
     *
     * <p>Called when connection is lost or during shutdown.
     *
     * @param cause the cancellation reason
     * @return number of requests cancelled
     */
    public int cancelAll(Throwable cause) {
        AtomicInteger cancelled = new AtomicInteger(0);

        pendingRequests.forEach((stan, pending) -> {
            if (!pending.future.isDone()) {
                pending.future.completeExceptionally(cause);
                cancelled.incrementAndGet();
            }
        });
        pendingRequests.clear();

        int count = cancelled.get();
        totalCancelled.addAndGet(count);
        log.info("[{}] Cancelled all {} pending requests: {}", name, count, cause.getMessage());
        return count;
    }

    /**
     * Gets the number of currently pending requests.
     *
     * @return pending request count
     */
    public int getPendingCount() {
        return pendingRequests.size();
    }

    /**
     * Checks if there are any pending requests.
     *
     * @return true if there are pending requests
     */
    public boolean hasPendingRequests() {
        return !pendingRequests.isEmpty();
    }

    /**
     * Checks if a specific STAN has a pending request.
     *
     * @param stan the System Trace Audit Number
     * @return true if there is a pending request for this STAN
     */
    public boolean isPending(String stan) {
        return stan != null && pendingRequests.containsKey(stan);
    }

    /**
     * Gets statistics about this manager.
     *
     * @return statistics object
     */
    public Statistics getStatistics() {
        return new Statistics(
            totalRegistered.get(),
            totalCompleted.get(),
            totalTimedOut.get(),
            totalCancelled.get(),
            pendingRequests.size()
        );
    }

    /**
     * Resets statistics counters.
     */
    public void resetStatistics() {
        totalRegistered.set(0);
        totalCompleted.set(0);
        totalTimedOut.set(0);
        totalCancelled.set(0);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        log.info("[{}] Closing PendingRequestManager", name);

        // Cancel all pending requests
        cancelAll(new IllegalStateException("PendingRequestManager closed"));

        // Shutdown scheduler
        timeoutScheduler.shutdown();
        try {
            if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("[{}] PendingRequestManager closed. Final stats: {}", name, getStatistics());
    }

    /**
     * Internal class to track pending requests.
     */
    private record PendingRequest(
        CompletableFuture<Iso8583Message> future,
        long registeredAt,
        long timeoutMs
    ) {}

    /**
     * Statistics about the pending request manager.
     */
    public record Statistics(
        long totalRegistered,
        long totalCompleted,
        long totalTimedOut,
        long totalCancelled,
        int currentPending
    ) {
        @Override
        public String toString() {
            return String.format(
                "Statistics{registered=%d, completed=%d, timedOut=%d, cancelled=%d, pending=%d}",
                totalRegistered, totalCompleted, totalTimedOut, totalCancelled, currentPending);
        }
    }
}
