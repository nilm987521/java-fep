package com.fep.transaction.limit;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LimitManager.
 */
class LimitManagerTest {

    private LimitManager limitManager;

    @BeforeEach
    void setUp() {
        limitManager = new LimitManager();
        limitManager.clearDailyUsage();
    }

    @Test
    @DisplayName("Should pass limit check for valid withdrawal")
    void testValidWithdrawalLimitCheck() {
        TransactionRequest request = createWithdrawalRequest(new BigDecimal("5000"));

        LimitCheckResult result = limitManager.checkLimits(request);

        assertTrue(result.isPassed(), "Result message: " + result.getMessage());
        assertNull(result.getExceededLimit());
    }

    @Test
    @DisplayName("Should fail limit check when single withdrawal exceeds limit")
    void testWithdrawalExceedsSingleLimit() {
        TransactionRequest request = createWithdrawalRequest(new BigDecimal("25000")); // > 20000

        LimitCheckResult result = limitManager.checkLimits(request);

        assertFalse(result.isPassed());
        assertEquals(LimitType.SINGLE_TRANSACTION, result.getExceededLimitType());
        assertEquals("61", result.getResponseCode());
    }

    @Test
    @DisplayName("Should fail limit check when daily count exceeded")
    void testWithdrawalExceedsDailyCount() {
        // Record 10 withdrawals (the daily count limit)
        for (int i = 0; i < 10; i++) {
            TransactionRequest request = createWithdrawalRequest(new BigDecimal("1000"));
            LimitCheckResult result = limitManager.checkLimits(request);
            if (result.isPassed()) {
                limitManager.recordUsage(request);
            }
        }

        // This should exceed daily count limit of 10
        TransactionRequest finalRequest = createWithdrawalRequest(new BigDecimal("1000"));
        LimitCheckResult result = limitManager.checkLimits(finalRequest);

        assertFalse(result.isPassed());
        assertEquals(LimitType.DAILY_COUNT, result.getExceededLimitType());
        assertEquals("65", result.getResponseCode()); // Exceeds frequency limit
    }

    @Test
    @DisplayName("Should track remaining limit correctly")
    void testRemainingLimit() {
        String accountId = "12345678901234";
        TransactionRequest request = createWithdrawalRequest(new BigDecimal("10000"));

        // Record usage
        limitManager.recordUsage(request);

        // Check remaining
        BigDecimal remaining = limitManager.getRemainingLimit(
                accountId, TransactionType.WITHDRAWAL, LimitType.DAILY_CUMULATIVE);

        assertEquals(new BigDecimal("90000"), remaining); // 100000 - 10000
    }

    @Test
    @DisplayName("Should allow custom account limits")
    void testCustomAccountLimit() {
        String accountId = "12345678901234";

        // Set custom higher limit for this account
        limitManager.setAccountLimit(accountId, TransactionType.WITHDRAWAL,
                LimitType.SINGLE_TRANSACTION, new BigDecimal("50000"));

        TransactionRequest request = createWithdrawalRequest(new BigDecimal("30000"));
        LimitCheckResult result = limitManager.checkLimits(request);

        assertTrue(result.isPassed()); // Would fail with default 20000 limit
    }

    @Test
    @DisplayName("Should pass limit check for transfer within limit")
    void testValidTransferLimitCheck() {
        // NON_DESIGNATED_TRANSFER limit is 50000, so use amount below that
        TransactionRequest request = createTransferRequest(new BigDecimal("30000"));

        LimitCheckResult result = limitManager.checkLimits(request);

        assertTrue(result.isPassed(), "Result message: " + result.getMessage());
    }

    @Test
    @DisplayName("Should fail when transfer exceeds limit")
    void testTransferExceedsLimit() {
        TransactionRequest request = createTransferRequest(new BigDecimal("3000000")); // > 2M

        LimitCheckResult result = limitManager.checkLimits(request);

        assertFalse(result.isPassed());
        assertEquals(LimitType.SINGLE_TRANSACTION, result.getExceededLimitType());
    }

    @Test
    @DisplayName("Should clear daily usage")
    void testClearDailyUsage() {
        String accountId = "12345678901234";
        TransactionRequest request = createWithdrawalRequest(new BigDecimal("50000"));
        limitManager.recordUsage(request);

        // Clear usage
        limitManager.clearDailyUsage();

        // Should have full limit available again
        BigDecimal remaining = limitManager.getRemainingLimit(
                accountId, TransactionType.WITHDRAWAL, LimitType.DAILY_CUMULATIVE);

        assertEquals(new BigDecimal("100000"), remaining);
    }

    private TransactionRequest createWithdrawalRequest(BigDecimal amount) {
        return TransactionRequest.builder()
                .transactionId("TXN001")
                .transactionType(TransactionType.WITHDRAWAL)
                .sourceAccount("12345678901234")
                .amount(amount)
                .build();
    }

    private TransactionRequest createTransferRequest(BigDecimal amount) {
        return TransactionRequest.builder()
                .transactionId("TXN002")
                .transactionType(TransactionType.TRANSFER)
                .sourceAccount("12345678901234")
                .destinationAccount("98765432109876")
                .amount(amount)
                .build();
    }
}
