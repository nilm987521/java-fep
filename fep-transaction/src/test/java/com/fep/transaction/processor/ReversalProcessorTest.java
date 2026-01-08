package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReversalProcessor.
 */
class ReversalProcessorTest {

    private ReversalProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ReversalProcessor();
    }

    @Test
    @DisplayName("Should return REVERSAL as supported type")
    void testSupportedType() {
        assertEquals(TransactionType.REVERSAL, processor.getSupportedType());
    }

    @Test
    @DisplayName("Should process valid reversal request successfully")
    void testProcessValidReversal() {
        TransactionRequest request = createValidReversalRequest();

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
        assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
    }

    @Test
    @DisplayName("Should reject reversal without original transaction ID")
    void testReversalWithoutOriginalId() {
        TransactionRequest request = createValidReversalRequest();
        request.setOriginalTransactionId(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject reversal without original RRN")
    void testReversalWithoutRrn() {
        TransactionRequest request = createValidReversalRequest();
        request.setRrn(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject reversal without original STAN")
    void testReversalWithoutStan() {
        TransactionRequest request = createValidReversalRequest();
        request.setStan(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject reversal without terminal ID")
    void testReversalWithoutTerminalId() {
        TransactionRequest request = createValidReversalRequest();
        request.setTerminalId(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject reversal without acquiring bank code")
    void testReversalWithoutAcquiringBank() {
        TransactionRequest request = createValidReversalRequest();
        request.setAcquiringBankCode(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should include original transaction reference in response")
    void testReversalResponseContainsOriginalRef() {
        TransactionRequest request = createValidReversalRequest();

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
        assertNotNull(response.getAdditionalData());
        assertTrue(response.getAdditionalData().contains("ORIG001"));
    }

    private TransactionRequest createValidReversalRequest() {
        return TransactionRequest.builder()
                .transactionId("REV001")
                .transactionType(TransactionType.REVERSAL)
                .processingCode("201000")
                .pan("4111111111111111")
                .amount(new BigDecimal("5000"))
                .currencyCode("901")
                .terminalId("ATM00001")
                .acquiringBankCode("004")
                .stan("000002")
                .rrn("123456789012")
                .originalTransactionId("ORIG001")
                .channel("ATM")
                .build();
    }
}
