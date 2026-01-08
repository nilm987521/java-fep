package com.fep.transaction.converter;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.MessageType;
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
 * Unit tests for message converters.
 */
class MessageConverterTest {

    private MessageToRequestConverter requestConverter;
    private ResponseToMessageConverter responseConverter;

    @BeforeEach
    void setUp() {
        requestConverter = new MessageToRequestConverter();
        responseConverter = new ResponseToMessageConverter();
    }

    @Test
    @DisplayName("Should convert ISO 8583 message to TransactionRequest")
    void testMessageToRequest() {
        Iso8583Message message = createWithdrawalMessage();

        TransactionRequest request = requestConverter.convert(message);

        assertNotNull(request.getTransactionId());
        assertEquals("4111111111111111", request.getPan());
        assertEquals("011000", request.getProcessingCode());
        assertEquals(TransactionType.WITHDRAWAL, request.getTransactionType());
        assertEquals(new BigDecimal("100.00"), request.getAmount());
        assertEquals("000001", request.getStan());
        assertEquals("123456789012", request.getRrn());
        assertEquals("ATM00001", request.getTerminalId());
    }

    @Test
    @DisplayName("Should parse amount correctly from ISO format")
    void testAmountParsing() {
        Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        message.setField(3, "011000");
        message.setField(4, "000000012345"); // 123.45 in minor units

        TransactionRequest request = requestConverter.convert(message);

        assertEquals(new BigDecimal("123.45"), request.getAmount());
    }

    @Test
    @DisplayName("Should determine channel from terminal ID")
    void testChannelDetermination() {
        Iso8583Message atmMessage = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        atmMessage.setField(3, "011000");
        atmMessage.setField(41, "ATM00001");

        TransactionRequest atmRequest = requestConverter.convert(atmMessage);
        assertEquals("ATM", atmRequest.getChannel());

        Iso8583Message posMessage = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        posMessage.setField(3, "011000");
        posMessage.setField(41, "POS12345");

        TransactionRequest posRequest = requestConverter.convert(posMessage);
        assertEquals("POS", posRequest.getChannel());
    }

    @Test
    @DisplayName("Should convert TransactionResponse to ISO 8583 message")
    void testResponseToMessage() {
        TransactionRequest request = createTestRequest();
        TransactionResponse response = createApprovedResponse();

        Iso8583Message message = responseConverter.convert(request, response, null);

        assertEquals(MessageType.FINANCIAL_RESPONSE.getCode(), message.getMti());
        assertEquals("00", message.getFieldAsString(39)); // Response code
        assertEquals("123456", message.getFieldAsString(38)); // Auth code
    }

    @Test
    @DisplayName("Should include balance info in response for inquiry")
    void testBalanceInquiryResponse() {
        TransactionRequest request = createBalanceInquiryRequest();
        TransactionResponse response = TransactionResponse.builder()
                .transactionId("TXN003")
                .responseCode("00")
                .approved(true)
                .availableBalance(new BigDecimal("10000.00"))
                .ledgerBalance(new BigDecimal("10500.00"))
                .currencyCode("901")
                .build();

        Iso8583Message message = responseConverter.convert(request, response, null);

        String additionalAmounts = message.getFieldAsString(54);
        assertNotNull(additionalAmounts);
        assertTrue(additionalAmounts.length() > 0);
    }

    @Test
    @DisplayName("Should create response from original message")
    void testResponseFromOriginalMessage() {
        Iso8583Message original = createWithdrawalMessage();
        TransactionRequest request = requestConverter.convert(original);
        TransactionResponse response = createApprovedResponse();

        Iso8583Message responseMessage = responseConverter.convert(request, response, original);

        // Should copy fields from original
        assertEquals(original.getFieldAsString(2), responseMessage.getFieldAsString(2));
        assertEquals(original.getFieldAsString(3), responseMessage.getFieldAsString(3));
        assertEquals(original.getFieldAsString(11), responseMessage.getFieldAsString(11));
        assertEquals("0210", responseMessage.getMti()); // Response MTI
    }

    @Test
    @DisplayName("Should handle null original message")
    void testNullOriginalMessage() {
        TransactionRequest request = createTestRequest();
        TransactionResponse response = createApprovedResponse();

        Iso8583Message message = responseConverter.convert(request, response, null);

        assertNotNull(message);
        assertEquals("00", message.getFieldAsString(39));
    }

    private Iso8583Message createWithdrawalMessage() {
        Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        message.setField(2, "4111111111111111");
        message.setField(3, "011000");
        message.setField(4, "000000010000"); // 100.00
        message.setField(11, "000001");
        message.setField(12, "120000");
        message.setField(13, "0106");
        message.setField(32, "004");
        message.setField(37, "123456789012");
        message.setField(41, "ATM00001");
        message.setField(42, "MERCHANT001   ");
        message.setField(49, "901");
        message.setField(102, "12345678901234");
        return message;
    }

    private TransactionRequest createTestRequest() {
        return TransactionRequest.builder()
                .transactionId("TXN001")
                .transactionType(TransactionType.WITHDRAWAL)
                .processingCode("011000")
                .pan("4111111111111111")
                .amount(new BigDecimal("5000"))
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .terminalId("ATM00001")
                .merchantId("MERCHANT001")
                .acquiringBankCode("004")
                .stan("000001")
                .rrn("123456789012")
                .build();
    }

    private TransactionRequest createBalanceInquiryRequest() {
        return TransactionRequest.builder()
                .transactionId("TXN003")
                .transactionType(TransactionType.BALANCE_INQUIRY)
                .processingCode("311000")
                .pan("4111111111111111")
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .terminalId("ATM00001")
                .stan("000003")
                .rrn("123456789014")
                .build();
    }

    private TransactionResponse createApprovedResponse() {
        return TransactionResponse.builder()
                .transactionId("TXN001")
                .responseCode("00")
                .responseCodeEnum(ResponseCode.APPROVED)
                .approved(true)
                .authorizationCode("123456")
                .amount(new BigDecimal("5000"))
                .currencyCode("901")
                .build();
    }
}
