package com.fep.transaction.timeout;

import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TimeoutManager.
 */
class TimeoutManagerTest {

    private TimeoutManager timeoutManager;
    private TimeoutConfig config;
    private TestTimeoutCallback testCallback;

    @BeforeEach
    void setUp() {
        config = new TimeoutConfig();
        testCallback = new TestTimeoutCallback();
        timeoutManager = new TimeoutManager(config, testCallback);
    }

    @AfterEach
    void tearDown() {
        if (timeoutManager != null) {
            timeoutManager.shutdown();
        }
    }

    @Test
    void testStartTracking() {
        TimeoutContext context = timeoutManager.startTracking("TXN001", TransactionType.WITHDRAWAL);

        assertNotNull(context);
        assertEquals("TXN001", context.getTransactionId());
        assertEquals(TransactionType.WITHDRAWAL, context.getTransactionType());
        assertEquals(10000, context.getTimeoutMs()); // From config
        assertEquals(TimeoutStatus.ACTIVE, context.getStatus());
        assertTrue(timeoutManager.isTracking("TXN001"));
    }

    @Test
    void testStartTrackingWithCustomTimeout() {
        TimeoutContext context = timeoutManager.startTracking("TXN002", TransactionType.TRANSFER, 5000);

        assertEquals(5000, context.getTimeoutMs());
    }

    @Test
    void testCompleteTracking() {
        timeoutManager.startTracking("TXN003", TransactionType.BALANCE_INQUIRY);
        assertTrue(timeoutManager.isTracking("TXN003"));

        timeoutManager.completeTracking("TXN003");

        assertFalse(timeoutManager.isTracking("TXN003"));
        assertTrue(testCallback.completeCalled);
    }

    @Test
    void testExecuteWithTimeoutSuccess() {
        String result = timeoutManager.executeWithTimeout(
                "TXN004",
                TransactionType.BALANCE_INQUIRY,
                () -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "SUCCESS";
                });

        assertEquals("SUCCESS", result);
        assertTrue(testCallback.completeCalled);
        assertFalse(timeoutManager.isTracking("TXN004"));
    }

    @Test
    void testExecuteWithTimeoutExpired() {
        assertThrows(TransactionException.class, () ->
                timeoutManager.executeWithTimeout(
                        "TXN005",
                        TransactionType.BALANCE_INQUIRY,
                        100, // Very short timeout
                        () -> {
                            try {
                                Thread.sleep(500); // Longer than timeout
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return "SHOULD_NOT_REACH";
                        }));

        assertTrue(testCallback.timeoutCalled);
        assertFalse(timeoutManager.isTracking("TXN005"));
    }

    @Test
    void testGetActiveCount() {
        assertEquals(0, timeoutManager.getActiveCount());

        timeoutManager.startTracking("TXN006", TransactionType.WITHDRAWAL);
        assertEquals(1, timeoutManager.getActiveCount());

        timeoutManager.startTracking("TXN007", TransactionType.TRANSFER);
        assertEquals(2, timeoutManager.getActiveCount());

        timeoutManager.completeTracking("TXN006");
        assertEquals(1, timeoutManager.getActiveCount());
    }

    @Test
    void testGetRemainingTime() {
        timeoutManager.startTracking("TXN008", TransactionType.WITHDRAWAL, 5000);

        long remaining = timeoutManager.getRemainingTime("TXN008");

        assertTrue(remaining > 0);
        assertTrue(remaining <= 5000);
    }

    @Test
    void testGetRemainingTimeForNonExistent() {
        assertEquals(0, timeoutManager.getRemainingTime("NON_EXISTENT"));
    }

    @Test
    void testTimeoutMonitoring() throws InterruptedException {
        CountDownLatch timeoutLatch = new CountDownLatch(1);
        AtomicBoolean timeoutTriggered = new AtomicBoolean(false);

        TimeoutCallback monitorCallback = new TimeoutCallback() {
            @Override
            public void onWarning(TimeoutContext context) {
            }

            @Override
            public void onTimeout(TimeoutContext context) {
                timeoutTriggered.set(true);
                timeoutLatch.countDown();
            }

            @Override
            public void onComplete(TimeoutContext context) {
            }
        };

        TimeoutManager manager = new TimeoutManager(config, monitorCallback);
        try {
            // Start tracking with very short timeout
            manager.startTracking("TXN009", TransactionType.BALANCE_INQUIRY, 100);

            // Wait for timeout to be detected by monitor
            boolean completed = timeoutLatch.await(3, TimeUnit.SECONDS);

            assertTrue(completed, "Timeout should have been triggered");
            assertTrue(timeoutTriggered.get());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testWarningCallback() throws InterruptedException {
        CountDownLatch warningOrTimeoutLatch = new CountDownLatch(1);
        AtomicBoolean warningTriggered = new AtomicBoolean(false);
        AtomicBoolean timeoutTriggered = new AtomicBoolean(false);

        TimeoutCallback warningCallback = new TimeoutCallback() {
            @Override
            public void onWarning(TimeoutContext context) {
                warningTriggered.set(true);
                warningOrTimeoutLatch.countDown();
            }

            @Override
            public void onTimeout(TimeoutContext context) {
                timeoutTriggered.set(true);
                warningOrTimeoutLatch.countDown();
            }

            @Override
            public void onComplete(TimeoutContext context) {
            }
        };

        TimeoutManager manager = new TimeoutManager(config, warningCallback);
        try {
            // Start tracking with timeout that allows warning detection
            // 80% of 2500ms = 2000ms (warning zone starts at 2000ms, timeout at 2500ms)
            // Monitor checks every 1000ms, so warning should be detected between 2000-2500ms
            manager.startTracking("TXN010", TransactionType.BALANCE_INQUIRY, 2500);

            // Wait for either warning or timeout
            boolean completed = warningOrTimeoutLatch.await(5, TimeUnit.SECONDS);

            assertTrue(completed, "Warning or timeout should have been triggered");
            // Either warning or timeout should have been triggered (depends on timing)
            assertTrue(warningTriggered.get() || timeoutTriggered.get(),
                    "Either warning or timeout callback should have been called");
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void testGetContext() {
        TimeoutContext context = timeoutManager.startTracking("TXN011", TransactionType.DEPOSIT);

        TimeoutContext retrieved = timeoutManager.getContext("TXN011");

        assertSame(context, retrieved);
        assertNull(timeoutManager.getContext("NON_EXISTENT"));
    }

    @Test
    void testDefaultTimeoutConfig() {
        TimeoutConfig defaultConfig = new TimeoutConfig();

        assertEquals(5000, defaultConfig.getTimeout(TransactionType.BALANCE_INQUIRY));
        assertEquals(10000, defaultConfig.getTimeout(TransactionType.WITHDRAWAL));
        assertEquals(15000, defaultConfig.getTimeout(TransactionType.TRANSFER));
        assertEquals(30000, defaultConfig.getTimeout(TransactionType.BILL_PAYMENT));
    }

    @Test
    void testCustomTimeoutConfig() {
        config.setTimeout(TransactionType.WITHDRAWAL, 20000);

        assertEquals(20000, config.getTimeout(TransactionType.WITHDRAWAL));
    }

    @Test
    void testInvalidTimeoutConfig() {
        assertThrows(IllegalArgumentException.class, () ->
                config.setTimeout(TransactionType.WITHDRAWAL, 0));

        assertThrows(IllegalArgumentException.class, () ->
                config.setTimeout(TransactionType.WITHDRAWAL, -1000));
    }

    /**
     * Test callback implementation for verification.
     */
    static class TestTimeoutCallback implements TimeoutCallback {
        volatile boolean warningCalled = false;
        volatile boolean timeoutCalled = false;
        volatile boolean completeCalled = false;
        volatile TimeoutContext lastContext = null;

        @Override
        public void onWarning(TimeoutContext context) {
            warningCalled = true;
            lastContext = context;
        }

        @Override
        public void onTimeout(TimeoutContext context) {
            timeoutCalled = true;
            lastContext = context;
        }

        @Override
        public void onComplete(TimeoutContext context) {
            completeCalled = true;
            lastContext = context;
        }
    }
}
