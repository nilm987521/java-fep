package com.fep.transaction.timeout;

import com.fep.transaction.enums.TransactionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TimeoutContext.
 */
class TimeoutContextTest {

    @Test
    void testConstructor() {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 10000);

        assertEquals("TXN001", context.getTransactionId());
        assertEquals(TransactionType.WITHDRAWAL, context.getTransactionType());
        assertEquals(10000, context.getTimeoutMs());
        assertEquals(TimeoutStatus.ACTIVE, context.getStatus());
        assertNotNull(context.getStartTime());
    }

    @Test
    void testGetElapsedMs() throws InterruptedException {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 10000);

        Thread.sleep(100);

        long elapsed = context.getElapsedMs();
        assertTrue(elapsed >= 100, "Elapsed time should be at least 100ms");
        assertTrue(elapsed < 500, "Elapsed time should be less than 500ms");
    }

    @Test
    void testGetRemainingMs() {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 5000);

        long remaining = context.getRemainingMs();

        assertTrue(remaining > 0);
        assertTrue(remaining <= 5000);
    }

    @Test
    void testGetRemainingMsNeverNegative() throws InterruptedException {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 50);

        Thread.sleep(100);

        assertEquals(0, context.getRemainingMs());
    }

    @Test
    void testIsTimedOutFalse() {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 10000);

        assertFalse(context.isTimedOut());
    }

    @Test
    void testIsTimedOutTrue() throws InterruptedException {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 50);

        Thread.sleep(100);

        assertTrue(context.isTimedOut());
    }

    @Test
    void testIsInWarningZone() throws InterruptedException {
        // 80% of 200ms = 160ms
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 200);

        // Initially not in warning zone
        assertFalse(context.isInWarningZone());

        // Wait until warning zone (>160ms but <200ms)
        Thread.sleep(170);

        // Should now be in warning zone if not timed out
        if (!context.isTimedOut()) {
            assertTrue(context.isInWarningZone());
        }
    }

    @Test
    void testCheckStatusActive() {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 10000);

        assertEquals(TimeoutStatus.ACTIVE, context.checkStatus());
    }

    @Test
    void testCheckStatusExpired() throws InterruptedException {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 50);

        Thread.sleep(100);

        assertEquals(TimeoutStatus.EXPIRED, context.checkStatus());
    }

    @Test
    void testCheckStatusWarning() throws InterruptedException {
        // 80% of 500ms = 400ms
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 500);

        Thread.sleep(420); // Just past warning threshold

        TimeoutStatus status = context.checkStatus();
        // Could be WARNING or EXPIRED depending on timing
        assertTrue(status == TimeoutStatus.WARNING || status == TimeoutStatus.EXPIRED);
    }

    @Test
    void testMarkCompleted() {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 10000);
        context.markCompleted();

        assertEquals(TimeoutStatus.COMPLETED, context.getStatus());
        // Status should remain COMPLETED even after checkStatus
        assertEquals(TimeoutStatus.COMPLETED, context.checkStatus());
    }

    @Test
    void testMarkCompletedDoesNotOverrideExpired() throws InterruptedException {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 50);

        Thread.sleep(100);
        context.checkStatus(); // This will set to EXPIRED

        context.markCompleted();

        assertEquals(TimeoutStatus.EXPIRED, context.getStatus());
    }

    @Test
    void testGetElapsedPercentage() throws InterruptedException {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 1000);

        double initialPercentage = context.getElapsedPercentage();
        assertTrue(initialPercentage < 10); // Should be very low initially

        Thread.sleep(500);

        double laterPercentage = context.getElapsedPercentage();
        assertTrue(laterPercentage >= 40 && laterPercentage <= 70,
                "Percentage should be around 50% after 500ms of 1000ms timeout");
    }

    @Test
    void testGetElapsedPercentageCappedAt100() throws InterruptedException {
        TimeoutContext context = new TimeoutContext("TXN001", TransactionType.WITHDRAWAL, 50);

        Thread.sleep(200);

        assertEquals(100.0, context.getElapsedPercentage());
    }
}
