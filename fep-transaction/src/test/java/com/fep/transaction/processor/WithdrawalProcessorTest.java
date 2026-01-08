package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.AccountType;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WithdrawalProcessor.
 */
class WithdrawalProcessorTest {

    private WithdrawalProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new WithdrawalProcessor();
    }

    @Test
    @DisplayName("Should return WITHDRAWAL as supported type")
    void testSupportedType() {
        assertEquals(TransactionType.WITHDRAWAL, processor.getSupportedType());
    }

    @Test
    @DisplayName("Should process valid withdrawal request successfully")
    void testProcessValidWithdrawal() {
        TransactionRequest request = createValidWithdrawalRequest();

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
        assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        assertNotNull(response.getAuthorizationCode());
        assertEquals(new BigDecimal("5000"), response.getAmount());
    }

    @Test
    @DisplayName("Should reject withdrawal without source account")
    void testWithdrawalWithoutSourceAccount() {
        TransactionRequest request = createValidWithdrawalRequest();
        request.setSourceAccount(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
        assertNotNull(response.getErrorDetails());
    }

    @Test
    @DisplayName("Should reject withdrawal without PIN block")
    void testWithdrawalWithoutPinBlock() {
        TransactionRequest request = createValidWithdrawalRequest();
        request.setPinBlock(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject withdrawal exceeding limit")
    void testWithdrawalExceedsLimit() {
        TransactionRequest request = createValidWithdrawalRequest();
        request.setAmount(new BigDecimal("30000")); // Exceeds 20000 limit

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
        assertEquals("61", response.getResponseCode()); // Exceeds withdrawal limit
    }

    @Test
    @DisplayName("Should reject withdrawal with invalid amount multiple")
    void testWithdrawalInvalidMultiple() {
        TransactionRequest request = createValidWithdrawalRequest();
        request.setAmount(new BigDecimal("5050")); // Not multiple of 100

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
        assertEquals("13", response.getResponseCode()); // Invalid amount
    }

    @Test
    @DisplayName("Should reject withdrawal without terminal ID")
    void testWithdrawalWithoutTerminalId() {
        TransactionRequest request = createValidWithdrawalRequest();
        request.setTerminalId(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject withdrawal with invalid PAN")
    void testWithdrawalInvalidPan() {
        TransactionRequest request = createValidWithdrawalRequest();
        request.setPan("12345"); // Too short

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
        assertEquals("14", response.getResponseCode()); // Invalid card number
    }

    @Test
    @DisplayName("Should reject withdrawal with negative amount")
    void testWithdrawalNegativeAmount() {
        TransactionRequest request = createValidWithdrawalRequest();
        request.setAmount(new BigDecimal("-1000"));

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
        assertEquals("13", response.getResponseCode()); // Invalid amount
    }

    private TransactionRequest createValidWithdrawalRequest() {
        return TransactionRequest.builder()
                .transactionId("TXN001")
                .transactionType(TransactionType.WITHDRAWAL)
                .processingCode("011000")
                .pan("4111111111111111")
                .amount(new BigDecimal("5000"))
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .sourceAccountType(AccountType.SAVINGS)
                .pinBlock("1234567890ABCDEF")
                .terminalId("ATM00001")
                .merchantId("MERCHANT001")
                .acquiringBankCode("004")
                .stan("000001")
                .rrn("123456789012")
                .channel("ATM")
                .build();
    }
}
