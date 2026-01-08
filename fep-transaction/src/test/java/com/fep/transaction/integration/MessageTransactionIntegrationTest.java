package com.fep.transaction.integration;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.iso8583.MessageType;
import com.fep.transaction.converter.MessageToRequestConverter;
import com.fep.transaction.converter.ResponseToMessageConverter;
import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.service.TransactionService;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Message and Transaction modules.
 * Tests the integration between fep-message and fep-transaction modules.
 */
@DisplayName("Message-Transaction Module Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MessageTransactionIntegrationTest {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMdd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MMddHHmmss");

    private TransactionService transactionService;
    private MessageToRequestConverter requestConverter;
    private ResponseToMessageConverter responseConverter;
    private Iso8583MessageFactory messageFactory;

    @BeforeEach
    void setUp() {
        transactionService = IntegrationTestConfig.createTestTransactionService();
        requestConverter = new MessageToRequestConverter();
        responseConverter = new ResponseToMessageConverter();
        messageFactory = new Iso8583MessageFactory();
    }

    // ========== Message Parsing Tests ==========

    @Test
    @Order(1)
    @DisplayName("整合 - ISO 8583 電文解析為交易請求")
    void testMessageParsingToTransactionRequest() {
        // Arrange
        Iso8583Message message = createSampleMessage(MessageType.FINANCIAL_REQUEST, "010000", "1000");

        // Act
        TransactionRequest request = requestConverter.convert(message);

        // Assert
        assertNotNull(request, "轉換後的請求不應為 null");
        assertEquals(TransactionType.WITHDRAWAL, request.getTransactionType());
        assertNotNull(request.getPan(), "PAN 應被解析");
        assertNotNull(request.getAmount(), "金額應被解析");
        assertNotNull(request.getStan(), "STAN 應被解析");
        assertNotNull(request.getRrn(), "RRN 應被解析");
        assertNotNull(request.getTerminalId(), "終端機 ID 應被解析");
    }

    @Test
    @Order(2)
    @DisplayName("整合 - 交易回應轉換為 ISO 8583 電文")
    void testTransactionResponseToMessage() {
        // Arrange
        Iso8583Message requestMessage = createSampleMessage(MessageType.FINANCIAL_REQUEST, "010000", "1000");
        TransactionRequest request = requestConverter.convert(requestMessage);
        TransactionResponse response = transactionService.process(request);

        // Act
        Iso8583Message responseMessage = responseConverter.convert(request, response, requestMessage);

        // Assert
        assertNotNull(responseMessage, "回應電文不應為 null");
        assertEquals(MessageType.FINANCIAL_RESPONSE.getCode(), responseMessage.getMti());
        assertTrue(responseMessage.hasField(39), "應有回應碼");
        assertTrue(responseMessage.hasField(11), "應有 STAN");
        assertTrue(responseMessage.hasField(37), "應有 RRN");
    }

    @Test
    @Order(3)
    @DisplayName("整合 - 處理碼正確映射到交易類型")
    void testProcessingCodeMapping() {
        // Test different processing codes
        String[][] testCases = {
                {"010000", "WITHDRAWAL"},      // Withdrawal from savings
                {"400000", "TRANSFER"},        // Transfer
                {"310000", "BALANCE_INQUIRY"}, // Balance inquiry
        };

        for (String[] testCase : testCases) {
            String processingCode = testCase[0];
            String expectedType = testCase[1];

            // Arrange
            Iso8583Message message = createSampleMessage(MessageType.FINANCIAL_REQUEST, processingCode, "1000");

            // Act
            TransactionRequest request = requestConverter.convert(message);

            // Assert
            assertNotNull(request.getTransactionType(),
                    "處理碼 " + processingCode + " 應映射到交易類型");
            assertEquals(expectedType, request.getTransactionType().name(),
                    "處理碼 " + processingCode + " 應映射到 " + expectedType);
        }
    }

    // ========== Field Conversion Tests ==========

    @Test
    @Order(10)
    @DisplayName("整合 - 所有必要欄位正確轉換")
    void testAllRequiredFieldsConversion() {
        // Arrange
        Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        LocalDateTime now = LocalDateTime.now();

        message.setField(2, "4111111111111111");
        message.setField(3, "010000");
        message.setField(4, "000000002000");
        message.setField(7, now.format(DATETIME_FORMAT));
        message.setField(11, "000123");
        message.setField(12, now.format(TIME_FORMAT));
        message.setField(13, now.format(DATE_FORMAT));
        message.setField(14, "2512");
        message.setField(18, "6011");
        message.setField(22, "051");
        message.setField(25, "00");
        message.setField(32, "004");
        message.setField(33, "004");
        message.setField(37, "123456789012");
        message.setField(41, "ATM00001");
        message.setField(42, "MERCHANT001");
        message.setField(43, "Test Merchant      Taipei     TW");
        message.setField(49, "901");
        message.setField(102, "1234567890");

        // Act
        TransactionRequest request = requestConverter.convert(message);

        // Assert - Verify all fields are converted
        assertEquals("4111111111111111", request.getPan());
        assertNotNull(request.getAmount());
        assertEquals("000123", request.getStan());
        assertEquals("123456789012", request.getRrn());
        assertEquals("ATM00001", request.getTerminalId());
        assertEquals("MERCHANT001", request.getMerchantId());
        assertEquals("004", request.getAcquiringBankCode());
        assertEquals("1234567890", request.getSourceAccount());
        assertEquals("2512", request.getExpirationDate());
    }

    @Test
    @Order(11)
    @DisplayName("整合 - 回應欄位正確傳遞")
    void testResponseFieldPropagation() {
        // Arrange
        Iso8583Message requestMessage = createSampleMessage(MessageType.FINANCIAL_REQUEST, "010000", "1000");
        TransactionRequest request = requestConverter.convert(requestMessage);

        // Act
        TransactionResponse response = transactionService.process(request);
        Iso8583Message responseMessage = responseConverter.convert(request, response, requestMessage);

        // Assert - Verify common fields are propagated
        assertEquals(requestMessage.getFieldAsString(2), responseMessage.getFieldAsString(2), "PAN 應一致");
        assertEquals(requestMessage.getFieldAsString(3), responseMessage.getFieldAsString(3), "處理碼應一致");
        assertEquals(requestMessage.getFieldAsString(4), responseMessage.getFieldAsString(4), "金額應一致");
        assertEquals(requestMessage.getFieldAsString(11), responseMessage.getFieldAsString(11), "STAN 應一致");
        assertEquals(requestMessage.getFieldAsString(37), responseMessage.getFieldAsString(37), "RRN 應一致");
        assertEquals(requestMessage.getFieldAsString(41), responseMessage.getFieldAsString(41), "終端機 ID 應一致");
    }

    // ========== Amount Conversion Tests ==========

    @Test
    @Order(20)
    @DisplayName("整合 - 金額格式正確轉換")
    void testAmountFormatConversion() {
        // Test different amount formats (ISO 8583 field 4 is in minor units)
        String[][] testCases = {
                {"000000001000", "10.00"},    // 1000 minor units = 10.00 TWD
                {"000000010000", "100.00"},   // 10000 minor units = 100.00 TWD
                {"000000100000", "1000.00"},  // 100000 minor units = 1000.00 TWD
                {"000001000000", "10000.00"}, // 1000000 minor units = 10000.00 TWD
        };

        for (String[] testCase : testCases) {
            String isoAmount = testCase[0];
            String expectedAmount = testCase[1];

            // Arrange - Create message with raw ISO amount directly
            Iso8583Message message = createMessageWithRawAmount(MessageType.FINANCIAL_REQUEST, "010000", isoAmount);

            // Act
            TransactionRequest request = requestConverter.convert(message);

            // Assert
            assertNotNull(request.getAmount());
            assertEquals(expectedAmount, request.getAmount().toString(),
                    "ISO 金額 " + isoAmount + " 應轉換為 " + expectedAmount);
        }
    }

    // ========== Date/Time Conversion Tests ==========

    @Test
    @Order(30)
    @DisplayName("整合 - 日期時間格式正確轉換")
    void testDateTimeConversion() {
        // Arrange
        Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        LocalDateTime now = LocalDateTime.now();

        message.setField(2, "4111111111111111");
        message.setField(3, "010000");
        message.setField(4, "000000001000");
        message.setField(7, now.format(DATETIME_FORMAT));
        message.setField(11, "000001");
        message.setField(12, now.format(TIME_FORMAT));
        message.setField(13, now.format(DATE_FORMAT));
        message.setField(37, "000000000001");
        message.setField(41, "ATM00001");

        // Act
        TransactionRequest request = requestConverter.convert(message);

        // Assert
        assertNotNull(request.getTransactionDateTime());
        // Date/time should be parsed correctly
        assertEquals(now.getHour(), request.getTransactionDateTime().getHour());
        assertEquals(now.getMinute(), request.getTransactionDateTime().getMinute());
    }

    // ========== Error Handling Tests ==========

    @Test
    @Order(40)
    @DisplayName("整合 - 處理缺少必要欄位的電文")
    void testMissingRequiredFieldsHandling() {
        // Arrange - Message with only MTI and STAN
        Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        message.setField(11, "000001");

        // Act
        TransactionRequest request = requestConverter.convert(message);
        TransactionResponse response = transactionService.process(request);

        // Assert
        assertNotNull(response);
        assertFalse(response.isApproved(), "缺少必要欄位應被拒絕");
    }

    @Test
    @Order(41)
    @DisplayName("整合 - 處理無效的金額格式")
    void testInvalidAmountFormatHandling() {
        // Arrange - Create message with invalid amount format directly
        Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        LocalDateTime now = LocalDateTime.now();

        message.setField(2, "4111111111111111");
        message.setField(3, "010000");
        message.setField(4, "INVALID_AMOUNT"); // Invalid format
        message.setField(7, now.format(DATETIME_FORMAT));
        message.setField(11, String.format("%06d", System.currentTimeMillis() % 1000000));
        message.setField(12, now.format(TIME_FORMAT));
        message.setField(13, now.format(DATE_FORMAT));
        message.setField(32, "004");
        message.setField(37, String.format("%012d", System.currentTimeMillis() % 1000000000000L));
        message.setField(41, "ATM00001");
        message.setField(42, "MERCHANT001");
        message.setField(49, "901");
        message.setField(102, "1234567890");

        // Act & Assert
        // Converter should handle invalid format gracefully
        try {
            TransactionRequest request = requestConverter.convert(message);
            TransactionResponse response = transactionService.process(request);
            assertNotNull(response);
            assertFalse(response.isApproved(), "無效金額格式應被拒絕");
        } catch (Exception e) {
            // NumberFormatException is acceptable for invalid amount format
            assertTrue(e instanceof NumberFormatException ||
                    e.getMessage().contains("amount") ||
                    e.getMessage().contains("invalid") ||
                    e.getMessage().contains("number"),
                    "例外訊息應提及金額、無效或數字格式錯誤: " + e.getMessage());
        }
    }

    // ========== Multiple Transaction Types Tests ==========

    @Test
    @Order(50)
    @DisplayName("整合 - 處理多種交易類型")
    void testMultipleTransactionTypes() {
        // Test withdrawal - use unique identifiers to avoid duplicate detection
        Iso8583Message withdrawalMsg = createSampleMessage(MessageType.FINANCIAL_REQUEST, "010000", "1000");
        withdrawalMsg.setField(11, "000050"); // Unique STAN for this test
        withdrawalMsg.setField(37, String.format("MTT%09d", System.nanoTime() % 1000000000)); // Unique RRN
        TransactionRequest withdrawalReq = requestConverter.convert(withdrawalMsg);
        TransactionResponse withdrawalResp = transactionService.process(withdrawalReq);
        assertTrue(withdrawalResp.isApproved(), "提款應成功: " + withdrawalResp.getResponseDescription());

        // Test balance inquiry - Note: Balance inquiry still requires a valid amount for pipeline validation
        // Use amount=100 (100 TWD) which is valid but will be ignored by BalanceInquiryProcessor
        Iso8583Message balanceMsg = createSampleMessage(MessageType.AUTH_REQUEST, "310000", "100");
        balanceMsg.setField(11, "000051"); // Unique STAN for this test
        balanceMsg.setField(37, String.format("MTT%09d", System.nanoTime() % 1000000000 + 1)); // Unique RRN
        TransactionRequest balanceReq = requestConverter.convert(balanceMsg);
        TransactionResponse balanceResp = transactionService.process(balanceReq);
        assertTrue(balanceResp.isApproved(), "餘額查詢應成功: " + balanceResp.getResponseDescription());

        // Test transfer - use unique identifiers
        Iso8583Message transferMsg = createSampleMessage(MessageType.FINANCIAL_REQUEST, "400000", "5000");
        transferMsg.setField(11, "000052"); // Unique STAN for this test
        transferMsg.setField(37, String.format("MTT%09d", System.nanoTime() % 1000000000 + 2)); // Unique RRN
        transferMsg.setField(103, "98765432109876"); // Destination account (14 digits)
        transferMsg.setField(100, "012"); // Receiving Institution ID
        TransactionRequest transferReq = requestConverter.convert(transferMsg);
        TransactionResponse transferResp = transactionService.process(transferReq);
        assertTrue(transferResp.isApproved(), "轉帳應成功: " + transferResp.getResponseDescription());
    }

    // ========== Round-Trip Tests ==========

    @Test
    @Order(60)
    @DisplayName("整合 - 完整往返轉換測試")
    void testCompleteRoundTrip() {
        // Arrange
        Iso8583Message originalMessage = createCompleteMessage();

        // Act - Request flow
        TransactionRequest request = requestConverter.convert(originalMessage);
        TransactionResponse response = transactionService.process(request);
        Iso8583Message responseMessage = responseConverter.convert(request, response, originalMessage);

        // Assert - Verify round-trip integrity
        assertNotNull(responseMessage);
        assertEquals(MessageType.FINANCIAL_RESPONSE.getCode(), responseMessage.getMti());

        // Verify key fields maintained integrity
        assertEquals(originalMessage.getFieldAsString(2), responseMessage.getFieldAsString(2));
        assertEquals(originalMessage.getFieldAsString(11), responseMessage.getFieldAsString(11));
        assertEquals(originalMessage.getFieldAsString(37), responseMessage.getFieldAsString(37));

        System.out.println("=== Round-Trip Test ===");
        System.out.println("Original Fields: " + originalMessage.getFieldNumbers().size());
        System.out.println("Response Fields: " + responseMessage.getFieldNumbers().size());
        System.out.println("Response Code: " + responseMessage.getFieldAsString(39));
    }

    // ========== Performance Tests ==========

    @Test
    @Order(70)
    @DisplayName("整合 - 轉換效能測試")
    void testConversionPerformance() {
        int iterations = 100;
        long totalConversionTime = 0;
        long totalProcessingTime = 0;

        for (int i = 0; i < iterations; i++) {
            Iso8583Message message = createSampleMessage(MessageType.FINANCIAL_REQUEST, "010000", "1000");
            message.setField(11, String.format("%06d", i));

            // Measure conversion time
            long conversionStart = System.nanoTime();
            TransactionRequest request = requestConverter.convert(message);
            long conversionEnd = System.nanoTime();
            totalConversionTime += (conversionEnd - conversionStart);

            // Measure processing time
            long processingStart = System.nanoTime();
            TransactionResponse response = transactionService.process(request);
            long processingEnd = System.nanoTime();
            totalProcessingTime += (processingEnd - processingStart);

            // Convert response
            responseConverter.convert(request, response, message);
        }

        double avgConversionMs = (totalConversionTime / iterations) / 1_000_000.0;
        double avgProcessingMs = (totalProcessingTime / iterations) / 1_000_000.0;

        System.out.printf("=== Conversion Performance ===%n");
        System.out.printf("Iterations: %d%n", iterations);
        System.out.printf("Avg Conversion Time: %.3f ms%n", avgConversionMs);
        System.out.printf("Avg Processing Time: %.3f ms%n", avgProcessingMs);

        assertTrue(avgConversionMs < 5, "平均轉換時間應小於 5ms");
        assertTrue(avgProcessingMs < 50, "平均處理時間應小於 50ms");
    }

    // ========== Helper Methods ==========

    private Iso8583Message createSampleMessage(MessageType messageType, String processingCode, String amount) {
        Iso8583Message message = new Iso8583Message(messageType);
        LocalDateTime now = LocalDateTime.now();

        message.setField(2, "4111111111111111");
        message.setField(3, processingCode);
        // Amount is in major units (TWD), convert to minor units (* 100) for ISO 8583 field 4
        message.setField(4, String.format("%012d", Long.parseLong(amount) * 100));
        message.setField(7, now.format(DATETIME_FORMAT));
        message.setField(11, String.format("%06d", System.currentTimeMillis() % 1000000));
        message.setField(12, now.format(TIME_FORMAT));
        message.setField(13, now.format(DATE_FORMAT));
        message.setField(32, "004");
        message.setField(37, String.format("%012d", System.currentTimeMillis() % 1000000000000L));
        message.setField(41, "ATM00001");
        message.setField(42, "MERCHANT001");
        message.setField(49, "901");
        message.setField(52, "1234567890ABCDEF");
        message.setField(102, "12345678901234");

        return message;
    }

    /**
     * Creates a message with raw ISO 8583 amount (minor units).
     * Use this when you want to test specific ISO amount formats without conversion.
     */
    private Iso8583Message createMessageWithRawAmount(MessageType messageType, String processingCode, String rawIsoAmount) {
        Iso8583Message message = new Iso8583Message(messageType);
        LocalDateTime now = LocalDateTime.now();

        message.setField(2, "4111111111111111");
        message.setField(3, processingCode);
        message.setField(4, rawIsoAmount);  // Use raw ISO amount directly
        message.setField(7, now.format(DATETIME_FORMAT));
        message.setField(11, String.format("%06d", System.currentTimeMillis() % 1000000));
        message.setField(12, now.format(TIME_FORMAT));
        message.setField(13, now.format(DATE_FORMAT));
        message.setField(32, "004");
        message.setField(37, String.format("%012d", System.currentTimeMillis() % 1000000000000L));
        message.setField(41, "ATM00001");
        message.setField(42, "MERCHANT001");
        message.setField(49, "901");
        message.setField(52, "1234567890ABCDEF");
        message.setField(102, "12345678901234");

        return message;
    }

    private Iso8583Message createCompleteMessage() {
        Iso8583Message message = createSampleMessage(MessageType.FINANCIAL_REQUEST, "010000", "1000");

        // Add additional fields
        message.setField(14, "2512");
        message.setField(18, "6011");
        message.setField(22, "051");
        message.setField(25, "00");
        message.setField(26, "12");
        message.setField(33, "004");
        message.setField(43, "Test Merchant      Taipei     TW");
        message.setField(48, "Additional data");

        return message;
    }
}
