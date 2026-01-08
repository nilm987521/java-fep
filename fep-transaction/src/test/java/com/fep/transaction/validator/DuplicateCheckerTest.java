package com.fep.transaction.validator;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.exception.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DuplicateChecker.
 */
class DuplicateCheckerTest {

    private DuplicateChecker duplicateChecker;

    @BeforeEach
    void setUp() {
        duplicateChecker = new DuplicateChecker();
    }

    @Test
    void testFirstTransactionPasses() {
        TransactionRequest request = createRequest("123456789012", "123456", "ATM001");

        assertDoesNotThrow(() -> duplicateChecker.validate(request));
    }

    @Test
    void testDuplicateTransactionThrows() {
        TransactionRequest request1 = createRequest("123456789012", "123456", "ATM001");
        TransactionRequest request2 = createRequest("123456789012", "123456", "ATM001");

        duplicateChecker.validate(request1);

        assertThrows(TransactionException.class, () -> duplicateChecker.validate(request2));
    }

    @Test
    void testDifferentRrnPasses() {
        TransactionRequest request1 = createRequest("123456789012", "123456", "ATM001");
        TransactionRequest request2 = createRequest("987654321098", "123456", "ATM001");

        duplicateChecker.validate(request1);

        assertDoesNotThrow(() -> duplicateChecker.validate(request2));
    }

    @Test
    void testDifferentStanPasses() {
        TransactionRequest request1 = createRequest("123456789012", "123456", "ATM001");
        TransactionRequest request2 = createRequest("123456789012", "654321", "ATM001");

        duplicateChecker.validate(request1);

        assertDoesNotThrow(() -> duplicateChecker.validate(request2));
    }

    @Test
    void testDifferentTerminalPasses() {
        TransactionRequest request1 = createRequest("123456789012", "123456", "ATM001");
        TransactionRequest request2 = createRequest("123456789012", "123456", "ATM002");

        duplicateChecker.validate(request1);

        assertDoesNotThrow(() -> duplicateChecker.validate(request2));
    }

    @Test
    void testNullFieldsHandled() {
        TransactionRequest request1 = createRequest(null, "123456", "ATM001");
        TransactionRequest request2 = createRequest(null, "123456", "ATM001");

        duplicateChecker.validate(request1);

        assertThrows(TransactionException.class, () -> duplicateChecker.validate(request2));
    }

    @Test
    void testClear() {
        TransactionRequest request = createRequest("123456789012", "123456", "ATM001");
        duplicateChecker.validate(request);

        assertEquals(1, duplicateChecker.getCacheSize());

        duplicateChecker.clear();

        assertEquals(0, duplicateChecker.getCacheSize());
    }

    @Test
    void testGetOrder() {
        assertEquals(5, duplicateChecker.getOrder());
    }

    @Test
    void testCacheSize() {
        TransactionRequest request1 = createRequest("111111111111", "111111", "ATM001");
        TransactionRequest request2 = createRequest("222222222222", "222222", "ATM002");
        TransactionRequest request3 = createRequest("333333333333", "333333", "ATM003");

        duplicateChecker.validate(request1);
        duplicateChecker.validate(request2);
        duplicateChecker.validate(request3);

        assertEquals(3, duplicateChecker.getCacheSize());
    }

    @Test
    void testClearAndReuse() {
        TransactionRequest request = createRequest("123456789012", "123456", "ATM001");
        duplicateChecker.validate(request);

        // Clear the cache
        duplicateChecker.clear();

        // Same request should pass now
        assertDoesNotThrow(() -> duplicateChecker.validate(request));
    }

    private TransactionRequest createRequest(String rrn, String stan, String terminalId) {
        return TransactionRequest.builder()
                .transactionId("TXN-" + System.currentTimeMillis())
                .rrn(rrn)
                .stan(stan)
                .terminalId(terminalId)
                .build();
    }
}
