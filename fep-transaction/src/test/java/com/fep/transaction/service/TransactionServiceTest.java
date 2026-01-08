package com.fep.transaction.service;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.AccountType;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.processor.BalanceInquiryProcessor;
import com.fep.transaction.processor.TransactionProcessor;
import com.fep.transaction.processor.TransferProcessor;
import com.fep.transaction.processor.WithdrawalProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionServiceImpl.
 */
class TransactionServiceTest {

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        List<TransactionProcessor> processors = Arrays.asList(
                new WithdrawalProcessor(),
                new TransferProcessor(),
                new BalanceInquiryProcessor()
        );
        transactionService = new TransactionServiceImpl(processors);
    }

    @Test
    @DisplayName("Should support withdrawal transaction type")
    void testSupportsWithdrawal() {
        assertTrue(transactionService.isSupported(TransactionType.WITHDRAWAL));
    }

    @Test
    @DisplayName("Should support transfer transaction type")
    void testSupportsTransfer() {
        assertTrue(transactionService.isSupported(TransactionType.TRANSFER));
    }

    @Test
    @DisplayName("Should support balance inquiry transaction type")
    void testSupportsBalanceInquiry() {
        assertTrue(transactionService.isSupported(TransactionType.BALANCE_INQUIRY));
    }

    @Test
    @DisplayName("Should not support unregistered transaction types")
    void testUnsupportedType() {
        assertFalse(transactionService.isSupported(TransactionType.DEPOSIT));
        assertFalse(transactionService.isSupported(TransactionType.PURCHASE));
    }

    @Test
    @DisplayName("Should return all supported types")
    void testGetSupportedTypes() {
        TransactionType[] supportedTypes = transactionService.getSupportedTypes();

        assertEquals(3, supportedTypes.length);
        assertTrue(Arrays.asList(supportedTypes).contains(TransactionType.WITHDRAWAL));
        assertTrue(Arrays.asList(supportedTypes).contains(TransactionType.TRANSFER));
        assertTrue(Arrays.asList(supportedTypes).contains(TransactionType.BALANCE_INQUIRY));
    }

    @Test
    @DisplayName("Should route withdrawal to WithdrawalProcessor")
    void testRouteWithdrawal() {
        TransactionRequest request = createWithdrawalRequest();

        TransactionResponse response = transactionService.process(request);

        assertTrue(response.isApproved());
        assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
    }

    @Test
    @DisplayName("Should route transfer to TransferProcessor")
    void testRouteTransfer() {
        TransactionRequest request = createTransferRequest();

        TransactionResponse response = transactionService.process(request);

        assertTrue(response.isApproved());
        assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
    }

    @Test
    @DisplayName("Should route balance inquiry to BalanceInquiryProcessor")
    void testRouteBalanceInquiry() {
        TransactionRequest request = createBalanceInquiryRequest();

        TransactionResponse response = transactionService.process(request);

        assertTrue(response.isApproved());
        assertNotNull(response.getAvailableBalance());
    }

    @Test
    @DisplayName("Should reject transaction with null type")
    void testNullTransactionType() {
        TransactionRequest request = createWithdrawalRequest();
        request.setTransactionType(null);

        TransactionResponse response = transactionService.process(request);

        assertFalse(response.isApproved());
        assertEquals("30", response.getResponseCode()); // Format error
    }

    @Test
    @DisplayName("Should reject unsupported transaction type")
    void testUnsupportedTransactionType() {
        TransactionRequest request = createWithdrawalRequest();
        request.setTransactionType(TransactionType.DEPOSIT);

        TransactionResponse response = transactionService.process(request);

        assertFalse(response.isApproved());
        assertEquals("57", response.getResponseCode()); // Transaction not permitted
    }

    @Test
    @DisplayName("Should generate transaction ID if not provided")
    void testGenerateTransactionId() {
        TransactionRequest request = createWithdrawalRequest();
        request.setTransactionId(null);

        TransactionResponse response = transactionService.process(request);

        assertNotNull(response.getTransactionId());
        assertEquals(16, response.getTransactionId().length());
    }

    @Test
    @DisplayName("Should handle empty processor list")
    void testEmptyProcessorList() {
        TransactionService emptyService = new TransactionServiceImpl(Collections.emptyList());

        TransactionRequest request = createWithdrawalRequest();
        TransactionResponse response = emptyService.process(request);

        assertFalse(response.isApproved());
        assertEquals("57", response.getResponseCode()); // Transaction not permitted
    }

    @Test
    @DisplayName("Should process multiple transactions sequentially")
    void testMultipleTransactions() {
        TransactionRequest withdrawal = createWithdrawalRequest();
        TransactionRequest transfer = createTransferRequest();
        TransactionRequest inquiry = createBalanceInquiryRequest();

        TransactionResponse withdrawalResponse = transactionService.process(withdrawal);
        TransactionResponse transferResponse = transactionService.process(transfer);
        TransactionResponse inquiryResponse = transactionService.process(inquiry);

        assertTrue(withdrawalResponse.isApproved());
        assertTrue(transferResponse.isApproved());
        assertTrue(inquiryResponse.isApproved());
    }

    private TransactionRequest createWithdrawalRequest() {
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

    private TransactionRequest createTransferRequest() {
        return TransactionRequest.builder()
                .transactionId("TXN002")
                .transactionType(TransactionType.TRANSFER)
                .processingCode("401010")
                .pan("4111111111111111")
                .amount(new BigDecimal("10000"))
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .sourceAccountType(AccountType.SAVINGS)
                .destinationAccount("98765432109876")
                .destinationAccountType(AccountType.SAVINGS)
                .destinationBankCode("012")
                .pinBlock("1234567890ABCDEF")
                .terminalId("ATM00001")
                .merchantId("MERCHANT001")
                .acquiringBankCode("004")
                .stan("000002")
                .rrn("123456789013")
                .channel("ATM")
                .build();
    }

    private TransactionRequest createBalanceInquiryRequest() {
        return TransactionRequest.builder()
                .transactionId("TXN003")
                .transactionType(TransactionType.BALANCE_INQUIRY)
                .processingCode("311000")
                .pan("4111111111111111")
                .amount(BigDecimal.ZERO)
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
