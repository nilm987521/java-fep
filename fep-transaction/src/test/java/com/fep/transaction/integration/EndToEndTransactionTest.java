package com.fep.transaction.integration;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.iso8583.MessageType;
import com.fep.transaction.config.TransactionModuleConfig;
import com.fep.transaction.converter.MessageToRequestConverter;
import com.fep.transaction.converter.ResponseToMessageConverter;
import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.service.TransactionService;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Transaction Tests.
 * Tests complete transaction flow from ISO 8583 message parsing to response assembly.
 *
 * Flow: ISO 8583 Request → Domain Request → Process → Domain Response → ISO 8583 Response
 */
@DisplayName("End-to-End Transaction Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndTransactionTest {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMdd");

    private TransactionService transactionService;
    private TransactionRepository repository;
    private MessageToRequestConverter requestConverter;
    private ResponseToMessageConverter responseConverter;
    private Iso8583MessageFactory messageFactory;

    @BeforeEach
    void setUp() {
        repository = TransactionModuleConfig.createRepository();
        transactionService = TransactionModuleConfig.createTransactionService(repository);
        requestConverter = new MessageToRequestConverter();
        responseConverter = new ResponseToMessageConverter();
        messageFactory = new Iso8583MessageFactory();
    }

    // ========== Withdrawal End-to-End Tests ==========

    @Test
    @Order(1)
    @DisplayName("E2E - 完整的跨行提款流程")
    void testCompleteWithdrawalFlow() {
        // Arrange - Create ISO 8583 request message
        Iso8583Message requestMessage = createWithdrawalRequestMessage();

        // Act - Step 1: Parse ISO message to domain request
        TransactionRequest domainRequest = requestConverter.convert(requestMessage);
        assertNotNull(domainRequest, "Domain request 應成功轉換");

        // Act - Step 2: Process transaction
        TransactionResponse domainResponse = transactionService.process(domainRequest);
        assertNotNull(domainResponse, "Domain response 不應為 null");

        // Act - Step 3: Convert domain response to ISO message
        Iso8583Message responseMessage = responseConverter.convert(domainRequest, domainResponse, requestMessage);
        assertNotNull(responseMessage, "Response message 應成功轉換");

        // Assert - Verify request message
        assertEquals(MessageType.FINANCIAL_REQUEST.getCode(), requestMessage.getMti());
        assertTrue(requestMessage.hasField(2), "應有 PAN 欄位");
        assertTrue(requestMessage.hasField(4), "應有金額欄位");

        // Assert - Verify domain processing
        assertTrue(domainResponse.isApproved(), "交易應被核准");
        assertEquals(ResponseCode.APPROVED.getCode(), domainResponse.getResponseCode());

        // Assert - Verify response message
        assertEquals(MessageType.FINANCIAL_RESPONSE.getCode(), responseMessage.getMti());
        assertTrue(responseMessage.hasField(39), "應有回應碼欄位");
        assertEquals("00", responseMessage.getFieldAsString(39), "回應碼應為 00 (核准)");
        assertTrue(responseMessage.hasField(38), "應有授權碼欄位");

        // Assert - Verify field propagation
        assertEquals(requestMessage.getFieldAsString(2), responseMessage.getFieldAsString(2), "PAN 應一致");
        assertEquals(requestMessage.getFieldAsString(11), responseMessage.getFieldAsString(11), "STAN 應一致");
        assertEquals(requestMessage.getFieldAsString(37), responseMessage.getFieldAsString(37), "RRN 應一致");

        System.out.println("=== E2E Withdrawal Flow ===");
        System.out.println("Request MTI: " + requestMessage.getMti());
        System.out.println("Response MTI: " + responseMessage.getMti());
        System.out.println("Response Code: " + responseMessage.getFieldAsString(39));
        System.out.println("Auth Code: " + responseMessage.getFieldAsString(38));
    }

    @Test
    @Order(2)
    @DisplayName("E2E - 提款金額不足的完整流程")
    void testWithdrawalInsufficientFundsFlow() {
        // Arrange
        Iso8583Message requestMessage = createWithdrawalRequestMessage();
        // Set very large amount
        requestMessage.setField(4, "000005000000"); // 5,000,000 minor units = 50,000 TWD (超過限額)

        // Act
        TransactionRequest domainRequest = requestConverter.convert(requestMessage);
        TransactionResponse domainResponse = transactionService.process(domainRequest);
        Iso8583Message responseMessage = responseConverter.convert(domainRequest, domainResponse, requestMessage);

        // Assert
        assertNotNull(responseMessage);
        assertFalse(domainResponse.isApproved(), "交易應被拒絕");
        String responseCode = responseMessage.getFieldAsString(39);
        assertNotNull(responseCode);
        assertNotEquals("00", responseCode, "回應碼不應為核准");

        System.out.println("=== E2E Insufficient Funds ===");
        System.out.println("Response Code: " + responseCode);
        System.out.println("Response Description: " + domainResponse.getResponseDescription());
    }

    // ========== Transfer End-to-End Tests ==========

    @Test
    @Order(10)
    @DisplayName("E2E - 完整的跨行轉帳流程")
    void testCompleteTransferFlow() {
        // Arrange
        Iso8583Message requestMessage = createTransferRequestMessage();

        // Act
        TransactionRequest domainRequest = requestConverter.convert(requestMessage);
        TransactionResponse domainResponse = transactionService.process(domainRequest);
        Iso8583Message responseMessage = responseConverter.convert(domainRequest, domainResponse, requestMessage);

        // Assert
        assertNotNull(domainRequest);
        assertNotNull(domainResponse);
        assertNotNull(responseMessage);

        assertTrue(domainResponse.isApproved(), "轉帳交易應被核准");
        assertEquals("00", responseMessage.getFieldAsString(39));

        // Verify transfer-specific fields
        assertNotNull(domainRequest.getSourceAccount(), "來源帳戶應存在");
        assertNotNull(domainRequest.getDestinationAccount(), "目的帳戶應存在");

        System.out.println("=== E2E Transfer Flow ===");
        System.out.println("From Account: " + domainRequest.getSourceAccount());
        System.out.println("To Account: " + domainRequest.getDestinationAccount());
        System.out.println("Amount: " + domainRequest.getAmount());
        System.out.println("Response Code: " + responseMessage.getFieldAsString(39));
    }

    @Test
    @Order(11)
    @DisplayName("E2E - 跨行轉帳至不同銀行")
    void testInterbankTransferFlow() {
        // Arrange
        Iso8583Message requestMessage = createTransferRequestMessage();
        // Set different acquiring institution
        requestMessage.setField(33, "822"); // 中國信託銀行

        // Act
        TransactionRequest domainRequest = requestConverter.convert(requestMessage);
        TransactionResponse domainResponse = transactionService.process(domainRequest);
        Iso8583Message responseMessage = responseConverter.convert(domainRequest, domainResponse, requestMessage);

        // Assert
        assertNotNull(responseMessage);
        assertTrue(domainResponse.isApproved(), "跨行轉帳應被核准");
        assertEquals("00", responseMessage.getFieldAsString(39));

        System.out.println("=== E2E Interbank Transfer ===");
        System.out.println("Destination Bank: " + requestMessage.getFieldAsString(33));
        System.out.println("Response: " + responseMessage.getFieldAsString(39));
    }

    // ========== Balance Inquiry End-to-End Tests ==========

    @Test
    @Order(20)
    @DisplayName("E2E - 完整的餘額查詢流程")
    void testCompleteBalanceInquiryFlow() {
        // Arrange
        Iso8583Message requestMessage = createBalanceInquiryRequestMessage();

        // Act
        TransactionRequest domainRequest = requestConverter.convert(requestMessage);
        TransactionResponse domainResponse = transactionService.process(domainRequest);
        Iso8583Message responseMessage = responseConverter.convert(domainRequest, domainResponse, requestMessage);

        // Assert
        assertNotNull(responseMessage);
        assertTrue(domainResponse.isApproved(), "餘額查詢應成功");
        assertEquals("00", responseMessage.getFieldAsString(39));

        // Balance inquiry should return balance information
        assertNotNull(domainResponse.getAvailableBalance(), "可用餘額應有值");
        assertNotNull(domainResponse.getLedgerBalance(), "帳面餘額應有值");

        // Check if balance is returned in Field 54 (Additional Amounts)
        if (responseMessage.hasField(54)) {
            System.out.println("Balance Info (Field 54): " + responseMessage.getFieldAsString(54));
        }

        System.out.println("=== E2E Balance Inquiry ===");
        System.out.println("Available Balance: " + domainResponse.getAvailableBalance());
        System.out.println("Ledger Balance: " + domainResponse.getLedgerBalance());
    }

    // ========== Reversal End-to-End Tests ==========

    @Test
    @Order(30)
    @DisplayName("E2E - 完整的交易沖正流程")
    void testCompleteReversalFlow() {
        // Arrange - First, create a successful withdrawal
        Iso8583Message originalRequest = createWithdrawalRequestMessage();
        TransactionRequest originalDomainRequest = requestConverter.convert(originalRequest);
        TransactionResponse originalResponse = transactionService.process(originalDomainRequest);

        assertTrue(originalResponse.isApproved(), "原始交易應成功");

        // Create reversal message
        Iso8583Message reversalRequest = createReversalRequestMessage(originalRequest);

        // Act - Process reversal
        TransactionRequest reversalDomainRequest = requestConverter.convert(reversalRequest);
        TransactionResponse reversalResponse = transactionService.process(reversalDomainRequest);
        Iso8583Message reversalResponseMessage = responseConverter.convert(reversalDomainRequest, reversalResponse, reversalRequest);

        // Assert
        assertNotNull(reversalResponseMessage);
        assertTrue(reversalResponse.isApproved(), "沖正交易應成功");
        assertEquals("00", reversalResponseMessage.getFieldAsString(39));

        System.out.println("=== E2E Reversal Flow ===");
        System.out.println("Original Transaction ID: " + originalDomainRequest.getTransactionId());
        System.out.println("Reversal Response: " + reversalResponseMessage.getFieldAsString(39));
    }

    // ========== Error Handling End-to-End Tests ==========

    @Test
    @Order(40)
    @DisplayName("E2E - 處理格式錯誤的電文")
    void testMalformedMessageHandling() {
        // Arrange - Create message with missing required fields
        Iso8583Message requestMessage = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        requestMessage.setField(11, "000001"); // Only STAN, missing other required fields

        // Act
        TransactionRequest domainRequest = requestConverter.convert(requestMessage);
        TransactionResponse domainResponse = transactionService.process(domainRequest);
        Iso8583Message responseMessage = responseConverter.convert(domainRequest, domainResponse, requestMessage);

        // Assert
        assertNotNull(responseMessage);
        assertFalse(domainResponse.isApproved(), "格式錯誤的交易應被拒絕");
        assertNotEquals("00", responseMessage.getFieldAsString(39));

        System.out.println("=== E2E Malformed Message ===");
        System.out.println("Response Code: " + responseMessage.getFieldAsString(39));
    }

    @Test
    @Order(41)
    @DisplayName("E2E - 處理重複交易")
    void testDuplicateTransactionHandling() {
        // Arrange
        Iso8583Message requestMessage = createWithdrawalRequestMessage();

        // Act - First transaction
        TransactionRequest domainRequest1 = requestConverter.convert(requestMessage);
        TransactionResponse domainResponse1 = transactionService.process(domainRequest1);

        // Act - Duplicate transaction (same STAN and RRN)
        TransactionRequest domainRequest2 = requestConverter.convert(requestMessage);
        TransactionResponse domainResponse2 = transactionService.process(domainRequest2);
        Iso8583Message responseMessage2 = responseConverter.convert(domainRequest2, domainResponse2, requestMessage);

        // Assert
        assertTrue(domainResponse1.isApproved(), "第一筆交易應成功");
        assertFalse(domainResponse2.isApproved(), "重複交易應被拒絕");
        assertEquals(ResponseCode.DUPLICATE_TRANSACTION.getCode(), domainResponse2.getResponseCode());

        System.out.println("=== E2E Duplicate Detection ===");
        System.out.println("First Response: " + domainResponse1.getResponseCode());
        System.out.println("Duplicate Response: " + domainResponse2.getResponseCode());
    }

    // ========== Message Field Validation Tests ==========

    @Test
    @Order(50)
    @DisplayName("E2E - 驗證所有必要欄位正確傳遞")
    void testAllRequiredFieldsPropagation() {
        // Arrange
        Iso8583Message requestMessage = createCompleteRequestMessage();

        // Act
        TransactionRequest domainRequest = requestConverter.convert(requestMessage);
        TransactionResponse domainResponse = transactionService.process(domainRequest);
        Iso8583Message responseMessage = responseConverter.convert(domainRequest, domainResponse, requestMessage);

        // Assert - Verify all required fields are present in response
        assertTrue(responseMessage.hasField(2), "應有 PAN (F2)");
        assertTrue(responseMessage.hasField(3), "應有 Processing Code (F3)");
        assertTrue(responseMessage.hasField(4), "應有 Amount (F4)");
        assertTrue(responseMessage.hasField(7), "應有 Transmission Date/Time (F7)");
        assertTrue(responseMessage.hasField(11), "應有 STAN (F11)");
        assertTrue(responseMessage.hasField(12), "應有 Local Time (F12)");
        assertTrue(responseMessage.hasField(13), "應有 Local Date (F13)");
        assertTrue(responseMessage.hasField(37), "應有 RRN (F37)");
        assertTrue(responseMessage.hasField(39), "應有 Response Code (F39)");
        assertTrue(responseMessage.hasField(41), "應有 Terminal ID (F41)");

        System.out.println("=== E2E Field Propagation ===");
        System.out.println("Request Fields: " + requestMessage.getFieldNumbers().size());
        System.out.println("Response Fields: " + responseMessage.getFieldNumbers().size());
    }

    // ========== Performance End-to-End Tests ==========

    @Test
    @Order(60)
    @DisplayName("E2E - 完整流程效能測試")
    void testCompleteFlowPerformance() {
        int iterations = 50;
        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            // Create unique message for each iteration
            Iso8583Message requestMessage = createWithdrawalRequestMessage();
            requestMessage.setField(11, String.format("%06d", i)); // Unique STAN

            long startTime = System.nanoTime();

            // Complete flow
            TransactionRequest domainRequest = requestConverter.convert(requestMessage);
            TransactionResponse domainResponse = transactionService.process(domainRequest);
            Iso8583Message responseMessage = responseConverter.convert(domainRequest, domainResponse, requestMessage);

            long endTime = System.nanoTime();
            totalTime += (endTime - startTime);

            assertNotNull(responseMessage);
        }

        double avgTimeMs = (totalTime / iterations) / 1_000_000.0;

        System.out.printf("=== E2E Performance ===%n");
        System.out.printf("Iterations: %d%n", iterations);
        System.out.printf("Average Time: %.2f ms%n", avgTimeMs);
        System.out.printf("Total Time: %.2f ms%n", totalTime / 1_000_000.0);

        assertTrue(avgTimeMs < 50, "平均完整流程處理時間應小於 50ms");
    }

    // ========== Helper Methods ==========

    private Iso8583Message createWithdrawalRequestMessage() {
        Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);

        LocalDateTime now = LocalDateTime.now();

        message.setField(2, "4111111111111111");           // PAN
        message.setField(3, "010000");                     // Processing Code (Withdrawal from savings)
        message.setField(4, "000000100000");               // Amount: 1000 TWD (100000 minor units / 100 = 1000.00)
        message.setField(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss"))); // Transmission date/time
        message.setField(11, String.format("%06d", System.currentTimeMillis() % 1000000)); // STAN
        message.setField(12, now.format(TIME_FORMAT));     // Local time
        message.setField(13, now.format(DATE_FORMAT));     // Local date
        message.setField(18, "6011");                      // Merchant type (ATM)
        message.setField(22, "051");                       // POS entry mode
        message.setField(25, "00");                        // POS condition code
        message.setField(32, "004");                       // Acquiring institution (Taiwan Bank)
        message.setField(37, generateRRN());               // RRN
        message.setField(41, "ATM00001");                  // Terminal ID
        message.setField(42, "MERCHANT00001");             // Merchant ID
        message.setField(43, "Taiwan Bank ATM    Taipei  TW"); // Card acceptor name/location
        message.setField(49, "901");                       // Currency code (TWD)
        message.setField(52, "1234567890ABCDEF");          // PIN Block (encrypted)
        message.setField(102, "12345678901234");           // Source account

        return message;
    }

    private Iso8583Message createTransferRequestMessage() {
        Iso8583Message message = new Iso8583Message(MessageType.FINANCIAL_REQUEST);

        LocalDateTime now = LocalDateTime.now();

        message.setField(2, "4111111111111111");
        message.setField(3, "400000");                     // Processing Code (Transfer)
        message.setField(4, "000000500000");               // Amount: 5000 TWD (500000 minor units / 100 = 5000.00)
        message.setField(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        message.setField(11, String.format("%06d", System.currentTimeMillis() % 1000000));
        message.setField(12, now.format(TIME_FORMAT));
        message.setField(13, now.format(DATE_FORMAT));
        message.setField(32, "004");
        message.setField(33, "004");                       // Forwarding institution
        message.setField(37, generateRRN());
        message.setField(41, "ATM00001");
        message.setField(42, "MERCHANT00001");
        message.setField(49, "901");
        message.setField(52, "1234567890ABCDEF");          // PIN Block (encrypted)
        message.setField(100, "012");                      // Receiving Institution ID
        message.setField(102, "12345678901234");           // Source account
        message.setField(103, "98765432109876");           // Destination account

        return message;
    }

    private Iso8583Message createBalanceInquiryRequestMessage() {
        Iso8583Message message = new Iso8583Message(MessageType.AUTH_REQUEST);

        LocalDateTime now = LocalDateTime.now();

        message.setField(2, "4111111111111111");
        message.setField(3, "310000");                     // Processing Code (Balance Inquiry)
        message.setField(4, "000000000000");               // Amount: 0 (balance inquiry doesn't need amount)
        message.setField(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        message.setField(11, String.format("%06d", System.currentTimeMillis() % 1000000));
        message.setField(12, now.format(TIME_FORMAT));
        message.setField(13, now.format(DATE_FORMAT));
        message.setField(32, "004");
        message.setField(37, generateRRN());
        message.setField(41, "ATM00001");
        message.setField(42, "MERCHANT00001");
        message.setField(49, "901");
        message.setField(52, "1234567890ABCDEF");          // PIN Block (encrypted)
        message.setField(102, "12345678901234");           // Account number

        return message;
    }

    private Iso8583Message createReversalRequestMessage(Iso8583Message originalMessage) {
        Iso8583Message message = new Iso8583Message(MessageType.REVERSAL_REQUEST);

        LocalDateTime now = LocalDateTime.now();
        String originalStan = originalMessage.getFieldAsString(11);
        String originalRrn = originalMessage.getFieldAsString(37);
        String originalDateTime = originalMessage.getFieldAsString(7);

        // Copy original fields
        message.setField(2, originalMessage.getFieldAsString(2));
        message.setField(3, originalMessage.getFieldAsString(3));
        message.setField(4, originalMessage.getFieldAsString(4));
        message.setField(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        // Use NEW STAN for reversal (not original STAN) to avoid duplicate detection
        message.setField(11, String.format("%06d", (System.currentTimeMillis() % 1000000 + 900000) % 1000000));
        message.setField(12, now.format(TIME_FORMAT));
        message.setField(13, now.format(DATE_FORMAT));
        message.setField(32, originalMessage.getFieldAsString(32)); // Acquiring bank code
        // Use NEW RRN for reversal to avoid duplicate detection
        message.setField(37, "REV" + generateRRN().substring(3));
        message.setField(41, originalMessage.getFieldAsString(41)); // Terminal ID
        message.setField(42, originalMessage.getFieldAsString(42)); // Merchant ID
        message.setField(49, originalMessage.getFieldAsString(49)); // Currency code
        message.setField(52, originalMessage.getFieldAsString(52)); // PIN Block (required for withdrawal reversal)
        message.setField(102, originalMessage.getFieldAsString(102)); // Source account (required for withdrawal reversal)

        // Field 90: Original data elements (MTI + STAN + DateTime + Acquiring Institution + Forwarding Institution)
        // Format: Original MTI (4) + Original STAN (6) + Original DateTime (10) + Original Acquiring (11) + Original Forwarding (11)
        // This field contains the original transaction reference info, not duplicate checker info
        String originalData = "0200" + originalStan + originalDateTime + "00000000000" + "00000000000";
        message.setField(90, originalData);

        return message;
    }

    private Iso8583Message createCompleteRequestMessage() {
        Iso8583Message message = createWithdrawalRequestMessage();

        // Add additional optional fields
        message.setField(14, "2512");                      // Expiration date
        message.setField(23, "001");                       // Card sequence number
        message.setField(26, "12");                        // Card acceptor business code
        message.setField(35, "4111111111111111=25121011234567890"); // Track 2 data
        message.setField(48, "Additional data");           // Additional data
        message.setField(52, "1234567890ABCDEF");          // PIN data (masked)
        message.setField(55, "EMV data");                  // EMV data

        return message;
    }

    private String generateRRN() {
        long timestamp = System.currentTimeMillis();
        return String.format("%012d", timestamp % 1000000000000L);
    }
}
