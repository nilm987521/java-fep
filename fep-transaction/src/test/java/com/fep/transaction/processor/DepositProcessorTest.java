package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.AccountType;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DepositProcessor.
 */
class DepositProcessorTest {

    private DepositProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DepositProcessor();
    }

    @Test
    @DisplayName("Should return DEPOSIT as supported type")
    void testSupportedType() {
        assertEquals(TransactionType.DEPOSIT, processor.getSupportedType());
    }

    @Test
    @DisplayName("Should process valid deposit request successfully")
    void testProcessValidDeposit() {
        TransactionRequest request = createValidDepositRequest();

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
        assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        assertNotNull(response.getAuthorizationCode());
    }

    @Test
    @DisplayName("Should reject deposit exceeding maximum limit")
    void testDepositExceedsLimit() {
        TransactionRequest request = createValidDepositRequest();
        request.setAmount(new BigDecimal("250000")); // Exceeds 200000 limit

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
        assertEquals("61", response.getResponseCode());
    }

    @Test
    @DisplayName("Should reject deposit below minimum amount")
    void testDepositBelowMinimum() {
        TransactionRequest request = createValidDepositRequest();
        request.setAmount(new BigDecimal("50")); // Below 100 minimum

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
        assertEquals("13", response.getResponseCode());
    }

    @Test
    @DisplayName("Should reject deposit with invalid amount multiple")
    void testDepositInvalidMultiple() {
        TransactionRequest request = createValidDepositRequest();
        request.setAmount(new BigDecimal("5050")); // Not multiple of 100

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
        assertEquals("13", response.getResponseCode());
    }

    @Test
    @DisplayName("Should reject deposit without terminal ID")
    void testDepositWithoutTerminalId() {
        TransactionRequest request = createValidDepositRequest();
        request.setTerminalId(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should accept deposit to destination account")
    void testDepositToDestinationAccount() {
        TransactionRequest request = createValidDepositRequest();
        request.setSourceAccount(null);
        request.setDestinationAccount("98765432109876");

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
    }

    private TransactionRequest createValidDepositRequest() {
        return TransactionRequest.builder()
                .transactionId("DEP001")
                .transactionType(TransactionType.DEPOSIT)
                .processingCode("211000")
                .pan("4111111111111111")
                .amount(new BigDecimal("10000"))
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .sourceAccountType(AccountType.SAVINGS)
                .terminalId("ATM00001")
                .merchantId("MERCHANT001")
                .acquiringBankCode("004")
                .stan("000001")
                .rrn("123456789012")
                .channel("ATM")
                .build();
    }
}
