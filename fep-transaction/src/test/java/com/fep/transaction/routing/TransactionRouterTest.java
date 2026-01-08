package com.fep.transaction.routing;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionRouter.
 */
class TransactionRouterTest {

    private TransactionRouter router;

    @BeforeEach
    void setUp() {
        router = new TransactionRouter();
    }

    @Test
    void testRouteWithdrawalToFisc() {
        TransactionRequest request = createRequest(TransactionType.WITHDRAWAL);

        RoutingResult result = router.route(request);

        assertTrue(result.isRouted());
        assertEquals(RoutingDestination.FISC_INTERBANK, result.getDestination());
        assertEquals(10000, result.getTimeoutMs());
    }

    @Test
    void testRouteTransferToFisc() {
        TransactionRequest request = createRequest(TransactionType.TRANSFER);

        RoutingResult result = router.route(request);

        assertTrue(result.isRouted());
        assertEquals(RoutingDestination.FISC_INTERBANK, result.getDestination());
        assertEquals(15000, result.getTimeoutMs());
    }

    @Test
    void testRouteBalanceInquiryToMainframe() {
        TransactionRequest request = createRequest(TransactionType.BALANCE_INQUIRY);

        RoutingResult result = router.route(request);

        assertTrue(result.isRouted());
        assertEquals(RoutingDestination.MAINFRAME_CBS, result.getDestination());
        assertEquals(5000, result.getTimeoutMs());
    }

    @Test
    void testRouteBillPaymentToFiscBillPayment() {
        TransactionRequest request = createRequest(TransactionType.BILL_PAYMENT);

        RoutingResult result = router.route(request);

        assertTrue(result.isRouted());
        assertEquals(RoutingDestination.FISC_BILL_PAYMENT, result.getDestination());
        assertEquals(30000, result.getTimeoutMs());
    }

    @Test
    void testRouteDepositToMainframe() {
        TransactionRequest request = createRequest(TransactionType.DEPOSIT);

        RoutingResult result = router.route(request);

        assertTrue(result.isRouted());
        assertEquals(RoutingDestination.MAINFRAME_CBS, result.getDestination());
    }

    @Test
    void testRouteQrPaymentToOpenSystem() {
        TransactionRequest request = createRequest(TransactionType.QR_PAYMENT);

        RoutingResult result = router.route(request);

        assertTrue(result.isRouted());
        assertEquals(RoutingDestination.OPEN_SYSTEM_API, result.getDestination());
    }

    @Test
    void testRouteP2PTransferToOpenSystem() {
        TransactionRequest request = createRequest(TransactionType.P2P_TRANSFER);

        RoutingResult result = router.route(request);

        assertTrue(result.isRouted());
        assertEquals(RoutingDestination.OPEN_SYSTEM_API, result.getDestination());
    }

    @Test
    void testRoutePurchaseToCardNetwork() {
        TransactionRequest request = createRequest(TransactionType.PURCHASE);

        RoutingResult result = router.route(request);

        assertTrue(result.isRouted());
        assertEquals(RoutingDestination.CARD_NETWORK, result.getDestination());
        assertEquals(30000, result.getTimeoutMs());
    }

    @Test
    void testRouteReversalToFisc() {
        TransactionRequest request = createRequest(TransactionType.REVERSAL);

        RoutingResult result = router.route(request);

        assertTrue(result.isRouted());
        assertEquals(RoutingDestination.FISC_INTERBANK, result.getDestination());
    }

    @Test
    void testDefaultDestination() {
        TransactionRequest request = createRequest(TransactionType.PIN_CHANGE);

        RoutingResult result = router.route(request);

        assertTrue(result.isRouted());
        assertEquals(RoutingDestination.MAINFRAME_CBS, result.getDestination());
    }

    @Test
    void testSetDefaultDestination() {
        router.setDefaultDestination(RoutingDestination.OPEN_SYSTEM_API);

        TransactionRequest request = createRequest(TransactionType.PIN_CHANGE);
        RoutingResult result = router.route(request);

        assertTrue(result.isRouted());
        assertEquals(RoutingDestination.OPEN_SYSTEM_API, result.getDestination());
    }

    @Test
    void testAddCustomRule() {
        RoutingRule customRule = RoutingRule.builder()
                .ruleName("custom-withdrawal")
                .priority(1)
                .transactionTypes(Set.of(TransactionType.WITHDRAWAL))
                .destination(RoutingDestination.OPEN_SYSTEM_API)
                .timeoutMs(8000)
                .build();

        router.addRule(customRule);

        TransactionRequest request = createRequest(TransactionType.WITHDRAWAL);
        RoutingResult result = router.route(request);

        assertEquals(RoutingDestination.OPEN_SYSTEM_API, result.getDestination());
        assertEquals(8000, result.getTimeoutMs());
    }

    @Test
    void testRemoveRule() {
        boolean removed = router.removeRule("interbank-withdrawal");
        assertTrue(removed);

        TransactionRequest request = createRequest(TransactionType.WITHDRAWAL);
        RoutingResult result = router.route(request);

        assertEquals(RoutingDestination.MAINFRAME_CBS, result.getDestination());
    }

    @Test
    void testRemoveNonExistentRule() {
        boolean removed = router.removeRule("non-existent");
        assertFalse(removed);
    }

    @Test
    void testGetRuleCount() {
        int count = router.getRuleCount();
        assertTrue(count > 0);
    }

    @Test
    void testGetActiveRules() {
        var activeRules = router.getActiveRules();
        assertFalse(activeRules.isEmpty());
    }

    @Test
    void testRoutingResultMatchedRule() {
        TransactionRequest request = createRequest(TransactionType.WITHDRAWAL);

        RoutingResult result = router.route(request);

        assertNotNull(result.getMatchedRule());
        assertEquals("interbank-withdrawal", result.getMatchedRule().getRuleName());
    }

    private TransactionRequest createRequest(TransactionType type) {
        return TransactionRequest.builder()
                .transactionId("TXN-" + System.currentTimeMillis())
                .transactionType(type)
                .pan("4111111111111111")
                .amount(new BigDecimal("1000"))
                .build();
    }
}
