package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for P2PTransferProcessor.
 */
class P2PTransferProcessorTest {

    private P2PTransferProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new P2PTransferProcessor();
    }

    @Test
    void testSupportedType() {
        assertEquals(TransactionType.P2P_TRANSFER, processor.getSupportedType());
    }

    @Test
    void testSuccessfulTransferByAccount() {
        TransactionRequest request = createRequest();
        request.setDestinationAccount("12345678901234");

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertTrue(response.isApproved());
        assertNotNull(response.getAuthorizationCode());
    }

    @Test
    void testSuccessfulTransferByMobile() {
        TransactionRequest request = createRequest();
        request.setAdditionalData("beneficiaryMobile=0912345678");

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertTrue(response.isApproved());
    }

    @Test
    void testSuccessfulTransferByEmail() {
        TransactionRequest request = createRequest();
        request.setAdditionalData("beneficiaryEmail=test@example.com");

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertTrue(response.isApproved());
    }

    @Test
    void testNullAmountReturnsError() {
        TransactionRequest request = createRequest();
        request.setAmount(null);
        request.setDestinationAccount("12345678901234");

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertFalse(response.isApproved());
    }

    @Test
    void testZeroAmountReturnsError() {
        TransactionRequest request = createRequest();
        request.setAmount(BigDecimal.ZERO);
        request.setDestinationAccount("12345678901234");

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertFalse(response.isApproved());
    }

    @Test
    void testExceedsMaximumAmountReturnsError() {
        TransactionRequest request = createRequest();
        request.setAmount(new BigDecimal("60000")); // Max is 50,000
        request.setDestinationAccount("12345678901234");

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertFalse(response.isApproved());
    }

    @Test
    void testMissingDestinationReturnsError() {
        TransactionRequest request = createRequest();
        // No destination set

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertFalse(response.isApproved());
    }

    @Test
    void testMultipleDestinationsReturnsError() {
        TransactionRequest request = createRequest();
        request.setDestinationAccount("12345678901234");
        request.setAdditionalData("beneficiaryMobile=0912345678");

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertFalse(response.isApproved());
    }

    @Test
    void testInvalidMobileFormatReturnsError() {
        TransactionRequest request = createRequest();
        request.setAdditionalData("beneficiaryMobile=1234567890"); // Not starting with 09

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertFalse(response.isApproved());
    }

    @Test
    void testInvalidEmailFormatReturnsError() {
        TransactionRequest request = createRequest();
        request.setAdditionalData("beneficiaryEmail=not-an-email");

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertFalse(response.isApproved());
    }

    @Test
    void testMissingSourceAccountReturnsError() {
        TransactionRequest request = createRequest();
        request.setSourceAccount(null);
        request.setDestinationAccount("12345678901234");

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertFalse(response.isApproved());
    }

    @Test
    void testInterbankTransferWithSupportedBank() {
        TransactionRequest request = createRequest();
        request.setDestinationAccount("12345678901234");
        request.setAcquiringBankCode("004");
        request.setDestinationBankCode("005");

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
    }

    @Test
    void testInterbankTransferWithUnsupportedBankReturnsError() {
        TransactionRequest request = createRequest();
        request.setDestinationAccount("12345678901234");
        request.setAcquiringBankCode("004");
        request.setDestinationBankCode("999"); // Unsupported bank

        TransactionResponse response = processor.process(request);

        assertNotNull(response);
        assertFalse(response.isApproved());
    }

    @Test
    void testMaximumAmountAllowed() {
        TransactionRequest request = createRequest();
        request.setAmount(new BigDecimal("50000")); // Exactly at max
        request.setDestinationAccount("12345678901234");

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
    }

    @Test
    void testMinimumAmountAllowed() {
        TransactionRequest request = createRequest();
        request.setAmount(BigDecimal.ONE); // Exactly at min
        request.setDestinationAccount("12345678901234");

        TransactionResponse response = processor.process(request);

        assertTrue(response.isApproved());
    }

    private TransactionRequest createRequest() {
        return TransactionRequest.builder()
                .transactionId("TXN-" + System.currentTimeMillis())
                .transactionType(TransactionType.P2P_TRANSFER)
                .pan("4111111111111111")
                .amount(new BigDecimal("5000"))
                .sourceAccount("98765432109876")
                .build();
    }
}
