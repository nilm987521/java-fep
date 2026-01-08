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
 * Unit tests for TransferProcessor.
 */
class TransferProcessorTest {

    private TransferProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransferProcessor();
    }

    @Test
    @DisplayName("Should return TRANSFER as supported type")
    void testSupportedType() {
        assertEquals(TransactionType.TRANSFER, processor.getSupportedType());
    }

    @Test
    @DisplayName("Should process valid transfer request successfully")
    void testProcessValidTransfer() {
        TransactionRequest request = createValidTransferRequest();

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
        assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        assertNotNull(response.getAuthorizationCode());
        assertEquals(new BigDecimal("10000"), response.getAmount());
    }

    @Test
    @DisplayName("Should reject transfer without source account")
    void testTransferWithoutSourceAccount() {
        TransactionRequest request = createValidTransferRequest();
        request.setSourceAccount(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject transfer without destination account")
    void testTransferWithoutDestinationAccount() {
        TransactionRequest request = createValidTransferRequest();
        request.setDestinationAccount(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject transfer without destination bank code")
    void testTransferWithoutDestinationBankCode() {
        TransactionRequest request = createValidTransferRequest();
        request.setDestinationBankCode(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject transfer with invalid bank code format")
    void testTransferInvalidBankCodeFormat() {
        TransactionRequest request = createValidTransferRequest();
        request.setDestinationBankCode("12"); // Should be 3 digits

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject transfer to same account")
    void testTransferToSameAccount() {
        TransactionRequest request = createValidTransferRequest();
        request.setDestinationAccount(request.getSourceAccount());

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject transfer without PIN block")
    void testTransferWithoutPinBlock() {
        TransactionRequest request = createValidTransferRequest();
        request.setPinBlock(null);

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
    }

    @Test
    @DisplayName("Should reject transfer exceeding limit")
    void testTransferExceedsLimit() {
        TransactionRequest request = createValidTransferRequest();
        request.setAmount(new BigDecimal("3000000")); // Exceeds 2M limit

        TransactionResponse response = processor.process(request);

        assertFalse(response.isApproved());
        assertEquals("61", response.getResponseCode()); // Exceeds limit
    }

    @Test
    @DisplayName("Should process transfer with different destination bank codes")
    void testTransferToDifferentBanks() {
        String[] bankCodes = {"004", "005", "008", "012", "013"};

        for (String bankCode : bankCodes) {
            TransactionRequest request = createValidTransferRequest();
            request.setDestinationBankCode(bankCode);

            TransactionResponse response = processor.process(request);

            assertTrue(response.isApproved(), "Transfer to bank " + bankCode + " should succeed");
        }
    }

    private TransactionRequest createValidTransferRequest() {
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
}
