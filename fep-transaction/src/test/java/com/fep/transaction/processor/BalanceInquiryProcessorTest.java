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
 * Unit tests for BalanceInquiryProcessor.
 */
class BalanceInquiryProcessorTest {

    private BalanceInquiryProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new BalanceInquiryProcessor();
    }

    @Test
    @DisplayName("Should return BALANCE_INQUIRY as supported type")
    void testSupportedType() {
        assertEquals(TransactionType.BALANCE_INQUIRY, processor.getSupportedType());
    }

    @Test
    @DisplayName("Should process valid balance inquiry successfully")
    void testProcessValidBalanceInquiry() {
        TransactionRequest request = createValidBalanceInquiryRequest();

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
        assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        assertNotNull(response.getAvailableBalance());
        assertNotNull(response.getLedgerBalance());
    }

    @Test
    @DisplayName("Should return balance values")
    void testBalanceValues() {
        TransactionRequest request = createValidBalanceInquiryRequest();

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
        assertTrue(response.getAvailableBalance().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(response.getLedgerBalance().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should reject balance inquiry without source account")
    void testBalanceInquiryWithoutSourceAccount() {
        TransactionRequest request = createValidBalanceInquiryRequest();
        request.setSourceAccount(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject balance inquiry without PIN block")
    void testBalanceInquiryWithoutPinBlock() {
        TransactionRequest request = createValidBalanceInquiryRequest();
        request.setPinBlock(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject balance inquiry without terminal ID")
    void testBalanceInquiryWithoutTerminalId() {
        TransactionRequest request = createValidBalanceInquiryRequest();
        request.setTerminalId(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should accept balance inquiry without amount")
    void testBalanceInquiryWithoutAmount() {
        TransactionRequest request = createValidBalanceInquiryRequest();
        request.setAmount(null);

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
    }

    @Test
    @DisplayName("Should reject balance inquiry with invalid PAN")
    void testBalanceInquiryInvalidPan() {
        TransactionRequest request = createValidBalanceInquiryRequest();
        request.setPan("12345"); // Too short

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
        assertEquals("14", response.getResponseCode()); // Invalid card number
    }

    private TransactionRequest createValidBalanceInquiryRequest() {
        return TransactionRequest.builder()
                .transactionId("TXN003")
                .transactionType(TransactionType.BALANCE_INQUIRY)
                .processingCode("311000")
                .pan("4111111111111111")
                .amount(BigDecimal.ZERO) // Balance inquiry doesn't need amount
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .sourceAccountType(AccountType.SAVINGS)
                .pinBlock("1234567890ABCDEF")
                .terminalId("ATM00001")
                .merchantId("MERCHANT001")
                .acquiringBankCode("004")
                .stan("000003")
                .rrn("123456789014")
                .channel("ATM")
                .build();
    }
}
