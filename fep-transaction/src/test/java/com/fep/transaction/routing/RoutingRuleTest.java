package com.fep.transaction.routing;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RoutingRule.
 */
class RoutingRuleTest {

    @Test
    void testMatchesByTransactionType() {
        RoutingRule rule = RoutingRule.builder()
                .ruleName("withdrawal-rule")
                .transactionTypes(Set.of(TransactionType.WITHDRAWAL))
                .destination(RoutingDestination.FISC_INTERBANK)
                .build();

        TransactionRequest request = TransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .build();

        assertTrue(rule.matches(request));
    }

    @Test
    void testDoesNotMatchDifferentTransactionType() {
        RoutingRule rule = RoutingRule.builder()
                .ruleName("withdrawal-rule")
                .transactionTypes(Set.of(TransactionType.WITHDRAWAL))
                .destination(RoutingDestination.FISC_INTERBANK)
                .build();

        TransactionRequest request = TransactionRequest.builder()
                .transactionType(TransactionType.TRANSFER)
                .build();

        assertFalse(rule.matches(request));
    }

    @Test
    void testMatchesMultipleTransactionTypes() {
        RoutingRule rule = RoutingRule.builder()
                .ruleName("payment-rule")
                .transactionTypes(Set.of(TransactionType.QR_PAYMENT, TransactionType.P2P_TRANSFER))
                .destination(RoutingDestination.OPEN_SYSTEM_API)
                .build();

        TransactionRequest request1 = TransactionRequest.builder()
                .transactionType(TransactionType.QR_PAYMENT)
                .build();

        TransactionRequest request2 = TransactionRequest.builder()
                .transactionType(TransactionType.P2P_TRANSFER)
                .build();

        assertTrue(rule.matches(request1));
        assertTrue(rule.matches(request2));
    }

    @Test
    void testMatchesByChannel() {
        RoutingRule rule = RoutingRule.builder()
                .ruleName("atm-rule")
                .transactionTypes(Set.of(TransactionType.WITHDRAWAL))
                .channels(Set.of("ATM"))
                .destination(RoutingDestination.FISC_INTERBANK)
                .build();

        TransactionRequest atmRequest = TransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .channel("ATM")
                .build();

        TransactionRequest posRequest = TransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .channel("POS")
                .build();

        assertTrue(rule.matches(atmRequest));
        assertFalse(rule.matches(posRequest));
    }

    @Test
    void testMatchesByBankCode() {
        RoutingRule rule = RoutingRule.builder()
                .ruleName("specific-bank-rule")
                .transactionTypes(Set.of(TransactionType.TRANSFER))
                .bankCodes(Set.of("004", "005"))
                .destination(RoutingDestination.FISC_INTERBANK)
                .build();

        TransactionRequest matchRequest = TransactionRequest.builder()
                .transactionType(TransactionType.TRANSFER)
                .destinationBankCode("004")
                .build();

        TransactionRequest noMatchRequest = TransactionRequest.builder()
                .transactionType(TransactionType.TRANSFER)
                .destinationBankCode("006")
                .build();

        assertTrue(rule.matches(matchRequest));
        assertFalse(rule.matches(noMatchRequest));
    }

    @Test
    void testMatchesWithCustomCondition() {
        RoutingRule rule = RoutingRule.builder()
                .ruleName("large-amount-rule")
                .transactionTypes(Set.of(TransactionType.TRANSFER))
                .condition(req -> req.getAmount() != null &&
                        req.getAmount().doubleValue() > 100000)
                .destination(RoutingDestination.FISC_INTERBANK)
                .build();

        TransactionRequest largeRequest = TransactionRequest.builder()
                .transactionType(TransactionType.TRANSFER)
                .amount(new BigDecimal("200000"))
                .build();

        TransactionRequest smallRequest = TransactionRequest.builder()
                .transactionType(TransactionType.TRANSFER)
                .amount(new BigDecimal("50000"))
                .build();

        assertTrue(rule.matches(largeRequest));
        assertFalse(rule.matches(smallRequest));
    }

    @Test
    void testInactiveRuleDoesNotMatch() {
        RoutingRule rule = RoutingRule.builder()
                .ruleName("inactive-rule")
                .transactionTypes(Set.of(TransactionType.WITHDRAWAL))
                .destination(RoutingDestination.FISC_INTERBANK)
                .active(false)
                .build();

        TransactionRequest request = TransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .build();

        assertFalse(rule.matches(request));
    }

    @Test
    void testDefaultValues() {
        RoutingRule rule = RoutingRule.builder()
                .ruleName("default-values-rule")
                .destination(RoutingDestination.MAINFRAME_CBS)
                .build();

        assertEquals(100, rule.getPriority());
        assertEquals(30000, rule.getTimeoutMs());
        assertTrue(rule.isActive());
    }

    @Test
    void testNullTransactionTypeInRequest() {
        RoutingRule rule = RoutingRule.builder()
                .ruleName("type-rule")
                .transactionTypes(Set.of(TransactionType.WITHDRAWAL))
                .destination(RoutingDestination.FISC_INTERBANK)
                .build();

        TransactionRequest request = TransactionRequest.builder()
                .transactionType(null)
                .build();

        assertFalse(rule.matches(request));
    }

    @Test
    void testEmptyTransactionTypesMatchesAny() {
        RoutingRule rule = RoutingRule.builder()
                .ruleName("any-type-rule")
                .destination(RoutingDestination.MAINFRAME_CBS)
                .build();

        TransactionRequest request = TransactionRequest.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .build();

        assertTrue(rule.matches(request));
    }
}
