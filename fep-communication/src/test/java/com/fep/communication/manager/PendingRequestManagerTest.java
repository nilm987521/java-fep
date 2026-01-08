package com.fep.communication.manager;

import com.fep.message.iso8583.Iso8583Message;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PendingRequestManager.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PendingRequestManagerTest {

    private PendingRequestManager manager;

    @BeforeEach
    void setUp() {
        manager = new PendingRequestManager("TEST", 5000L);
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
        }
    }

    // ==================== Basic Registration Tests ====================

    @Test
    @Order(1)
    @DisplayName("Should register pending request")
    void shouldRegisterPendingRequest() {
        CompletableFuture<Iso8583Message> future = manager.register("000001");

        assertNotNull(future, "Future should not be null");
        assertTrue(manager.isPending("000001"), "STAN should be pending");
        assertEquals(1, manager.getPendingCount());
    }

    @Test
    @Order(2)
    @DisplayName("Should complete pending request")
    void shouldCompletePendingRequest() throws Exception {
        CompletableFuture<Iso8583Message> future = manager.register("000001");

        Iso8583Message response = createMockResponse("000001", "00");
        boolean completed = manager.complete("000001", response);

        assertTrue(completed, "Complete should return true");
        assertFalse(manager.isPending("000001"), "STAN should not be pending after complete");
        assertEquals(0, manager.getPendingCount());

        Iso8583Message result = future.get(1, TimeUnit.SECONDS);
        assertEquals("00", result.getFieldAsString(39));
    }

    @Test
    @Order(3)
    @DisplayName("Should return false when completing non-existent STAN")
    void shouldReturnFalseForNonExistentStan() {
        boolean completed = manager.complete("999999", createMockResponse("999999", "00"));
        assertFalse(completed, "Complete should return false for non-existent STAN");
    }

    // ==================== Timeout Tests ====================

    @Test
    @Order(10)
    @DisplayName("Should timeout pending request")
    void shouldTimeoutPendingRequest() {
        CompletableFuture<Iso8583Message> future = manager.register("000001", 100);

        // CompletableFuture.get() wraps TimeoutException in ExecutionException
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
            future.get(500, TimeUnit.MILLISECONDS),
            "Should throw ExecutionException wrapping TimeoutException");

        assertInstanceOf(TimeoutException.class, ex.getCause(),
            "Cause should be TimeoutException");

        assertFalse(manager.isPending("000001"), "STAN should be removed after timeout");
    }

    @Test
    @Order(11)
    @DisplayName("Should track timeout statistics")
    void shouldTrackTimeoutStatistics() throws Exception {
        manager.register("000001", 50);

        Thread.sleep(200); // Wait for timeout

        PendingRequestManager.Statistics stats = manager.getStatistics();
        assertEquals(1, stats.totalTimedOut(), "Should track timed out requests");
    }

    // ==================== Cancellation Tests ====================

    @Test
    @Order(20)
    @DisplayName("Should cancel pending request")
    void shouldCancelPendingRequest() {
        CompletableFuture<Iso8583Message> future = manager.register("000001");

        boolean cancelled = manager.cancel("000001", new RuntimeException("Test cancel"));

        assertTrue(cancelled, "Cancel should return true");
        assertFalse(manager.isPending("000001"));
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @Order(21)
    @DisplayName("Should cancel all pending requests")
    void shouldCancelAllPendingRequests() {
        List<CompletableFuture<Iso8583Message>> futures = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            futures.add(manager.register(String.format("%06d", i)));
        }

        int cancelled = manager.cancelAll(new RuntimeException("Disconnect"));

        assertEquals(5, cancelled, "Should cancel all 5 requests");
        assertEquals(0, manager.getPendingCount());
        assertFalse(manager.hasPendingRequests());

        for (CompletableFuture<Iso8583Message> future : futures) {
            assertTrue(future.isCompletedExceptionally());
        }
    }

    // ==================== Duplicate STAN Tests ====================

    @Test
    @Order(30)
    @DisplayName("Should handle duplicate STAN by completing old request")
    void shouldHandleDuplicateStan() {
        CompletableFuture<Iso8583Message> future1 = manager.register("000001");
        CompletableFuture<Iso8583Message> future2 = manager.register("000001"); // Duplicate

        assertTrue(future1.isCompletedExceptionally(),
            "First future should be completed exceptionally");
        assertFalse(future2.isDone(), "Second future should still be pending");

        assertEquals(1, manager.getPendingCount(), "Should only have one pending request");
    }

    // ==================== Concurrent Tests ====================

    @Test
    @Order(40)
    @DisplayName("Should handle concurrent registrations")
    void shouldHandleConcurrentRegistrations() throws Exception {
        int numThreads = 10;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        List<Future<?>> tasks = new ArrayList<>();
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            tasks.add(executor.submit(() -> {
                try {
                    for (int i = 0; i < requestsPerThread; i++) {
                        String stan = String.format("%02d%04d", threadId, i);
                        manager.register(stan, 10000);
                    }
                } finally {
                    latch.countDown();
                }
            }));
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(numThreads * requestsPerThread, manager.getPendingCount());
    }

    @Test
    @Order(41)
    @DisplayName("Should handle concurrent completions")
    void shouldHandleConcurrentCompletions() throws Exception {
        int numRequests = 100;
        List<String> stans = new ArrayList<>();

        // Register all requests
        for (int i = 0; i < numRequests; i++) {
            String stan = String.format("%06d", i);
            stans.add(stan);
            manager.register(stan, 10000);
        }

        // Complete concurrently
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numRequests);

        for (String stan : stans) {
            executor.submit(() -> {
                try {
                    manager.complete(stan, createMockResponse(stan, "00"));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, manager.getPendingCount(), "All requests should be completed");
    }

    // ==================== Statistics Tests ====================

    @Test
    @Order(50)
    @DisplayName("Should track statistics correctly")
    void shouldTrackStatistics() throws Exception {
        // Register 3 requests
        manager.register("000001");
        manager.register("000002");
        manager.register("000003", 50); // Will timeout

        // Complete 1
        manager.complete("000001", createMockResponse("000001", "00"));

        // Cancel 1
        manager.cancel("000002", new RuntimeException("Cancelled"));

        // Wait for timeout
        Thread.sleep(200);

        PendingRequestManager.Statistics stats = manager.getStatistics();

        assertEquals(3, stats.totalRegistered());
        assertEquals(1, stats.totalCompleted());
        assertEquals(1, stats.totalTimedOut());
        assertEquals(1, stats.totalCancelled());
        assertEquals(0, stats.currentPending());
    }

    @Test
    @Order(51)
    @DisplayName("Should reset statistics")
    void shouldResetStatistics() {
        manager.register("000001");
        manager.complete("000001", createMockResponse("000001", "00"));

        manager.resetStatistics();

        PendingRequestManager.Statistics stats = manager.getStatistics();
        assertEquals(0, stats.totalRegistered());
        assertEquals(0, stats.totalCompleted());
    }

    // ==================== Validation Tests ====================

    @Test
    @Order(60)
    @DisplayName("Should reject null STAN")
    void shouldRejectNullStan() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.register(null));
    }

    @Test
    @Order(61)
    @DisplayName("Should reject empty STAN")
    void shouldRejectEmptyStan() {
        assertThrows(IllegalArgumentException.class, () ->
            manager.register(""));
    }

    @Test
    @Order(62)
    @DisplayName("Should reject registration after close")
    void shouldRejectRegistrationAfterClose() {
        manager.close();

        assertThrows(IllegalStateException.class, () ->
            manager.register("000001"));
    }

    // ==================== Close Tests ====================

    @Test
    @Order(70)
    @DisplayName("Should cancel all on close")
    void shouldCancelAllOnClose() {
        CompletableFuture<Iso8583Message> future1 = manager.register("000001");
        CompletableFuture<Iso8583Message> future2 = manager.register("000002");

        manager.close();

        assertTrue(future1.isCompletedExceptionally());
        assertTrue(future2.isCompletedExceptionally());
        assertEquals(0, manager.getPendingCount());
    }

    // ==================== Helper Methods ====================

    private Iso8583Message createMockResponse(String stan, String responseCode) {
        Iso8583Message message = new Iso8583Message("0210");
        message.setField(11, stan);
        message.setField(39, responseCode);
        return message;
    }
}
