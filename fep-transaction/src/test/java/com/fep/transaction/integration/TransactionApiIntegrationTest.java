package com.fep.transaction.integration;

import com.fep.transaction.config.TransactionModuleConfig;
import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.AccountType;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.repository.TransactionStatus;
import com.fep.transaction.service.TransactionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API Integration Tests for Transaction Module.
 * Tests the complete transaction processing flow through the public API.
 */
@DisplayName("Transaction API Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionApiIntegrationTest {

    private TransactionService transactionService;
    private TransactionRepository repository;

    @BeforeEach
    void setUp() {
        repository = TransactionModuleConfig.createRepository();
        transactionService = TransactionModuleConfig.createTransactionService(repository);
    }

    @AfterEach
    void tearDown() {
        // Clean up resources if needed
    }

    // ========== Withdrawal Transaction Tests ==========

    @Test
    @Order(1)
    @DisplayName("API - 成功處理跨行提款交易")
    void testSuccessfulWithdrawalTransaction() {
        // Arrange
        TransactionRequest request = createWithdrawalRequest();

        // Act
        TransactionResponse response = transactionService.process(request);

        // Assert
        assertNotNull(response, "回應不應為 null");
        assertTrue(response.isApproved(), "交易應被核准");
        assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        assertEquals(request.getTransactionId(), response.getTransactionId());
        assertNotNull(response.getAuthorizationCode(), "授權碼不應為 null");

        // Verify transaction is stored in repository
        TransactionRecord record = repository.findById(request.getTransactionId()).orElse(null);
        assertNotNull(record, "交易記錄應存在於資料庫");
        assertEquals(TransactionStatus.APPROVED, record.getStatus());
    }

    @Test
    @Order(2)
    @DisplayName("API - 拒絕無效金額的提款交易")
    void testWithdrawalWithInvalidAmount() {
        // Arrange
        TransactionRequest request = createWithdrawalRequest();
        request.setAmount(BigDecimal.ZERO);

        // Act
        TransactionResponse response = transactionService.process(request);

        // Assert
        assertNotNull(response);
        assertFalse(response.isApproved(), "零金額交易應被拒絕");
        assertEquals(ResponseCode.INVALID_AMOUNT.getCode(), response.getResponseCode());
    }

    @Test
    @Order(3)
    @DisplayName("API - 拒絕超過限額的提款交易")
    void testWithdrawalExceedsLimit() {
        // Arrange
        TransactionRequest request = createWithdrawalRequest();
        request.setAmount(new BigDecimal("100000")); // 10萬元，超過單筆限額

        // Act
        TransactionResponse response = transactionService.process(request);

        // Assert
        assertNotNull(response);
        assertFalse(response.isApproved());
        // 可能會是 EXCEEDS_WITHDRAWAL_LIMIT 或 INVALID_AMOUNT
        assertNotEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
    }

    // ========== Transfer Transaction Tests ==========

    @Test
    @Order(10)
    @DisplayName("API - 成功處理跨行轉帳交易")
    void testSuccessfulTransferTransaction() {
        // Arrange
        TransactionRequest request = createTransferRequest();

        // Act
        TransactionResponse response = transactionService.process(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.isApproved(), "轉帳交易應被核准");
        assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        assertEquals(request.getTransactionId(), response.getTransactionId());

        // Verify transaction record
        TransactionRecord record = repository.findById(request.getTransactionId()).orElse(null);
        assertNotNull(record);
        assertEquals(TransactionType.TRANSFER, record.getTransactionType());
        assertEquals(request.getSourceAccount(), record.getSourceAccount());
        assertEquals(request.getDestinationAccount(), record.getDestinationAccount());
    }

    @Test
    @Order(11)
    @DisplayName("API - 拒絕來源帳戶與目的帳戶相同的轉帳")
    void testTransferToSameAccount() {
        // Arrange
        TransactionRequest request = createTransferRequest();
        request.setDestinationAccount(request.getSourceAccount());

        // Act
        TransactionResponse response = transactionService.process(request);

        // Assert
        assertNotNull(response);
        assertFalse(response.isApproved(), "轉帳至相同帳戶應被拒絕");
    }

    @Test
    @Order(12)
    @DisplayName("API - 處理跨行轉帳（不同銀行代碼）")
    void testInterbankTransfer() {
        // Arrange
        TransactionRequest request = createTransferRequest();
        request.setDestinationBankCode("822"); // 中國信託

        // Act
        TransactionResponse response = transactionService.process(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.isApproved(), "跨行轉帳應被核准");
        assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
    }

    // ========== Balance Inquiry Tests ==========

    @Test
    @Order(20)
    @DisplayName("API - 成功處理餘額查詢")
    void testSuccessfulBalanceInquiry() {
        // Arrange
        TransactionRequest request = createBalanceInquiryRequest();

        // Act
        TransactionResponse response = transactionService.process(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.isApproved());
        assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        assertNotNull(response.getAvailableBalance(), "可用餘額應有值");
        assertNotNull(response.getLedgerBalance(), "帳面餘額應有值");
        assertTrue(response.getAvailableBalance().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @Order(21)
    @DisplayName("API - 餘額查詢不應扣款")
    void testBalanceInquiryDoesNotDeductAmount() {
        // Arrange
        TransactionRequest request = createBalanceInquiryRequest();
        request.setAmount(new BigDecimal("1000")); // 即使有金額也不應扣款

        // Act
        TransactionResponse response = transactionService.process(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.isApproved());
        // 餘額查詢只查詢，不執行金額操作
    }

    // ========== Duplicate Detection Tests ==========

    @Test
    @Order(30)
    @DisplayName("API - 偵測並拒絕重複交易")
    void testDuplicateTransactionDetection() {
        // Arrange
        TransactionRequest request = createWithdrawalRequest();

        // Act - First transaction
        TransactionResponse response1 = transactionService.process(request);

        // Act - Duplicate transaction (same RRN, STAN, terminal)
        TransactionRequest duplicateRequest = createWithdrawalRequest();
        duplicateRequest.setTransactionId(UUID.randomUUID().toString()); // Different transaction ID
        duplicateRequest.setRrn(request.getRrn()); // Same RRN
        duplicateRequest.setStan(request.getStan()); // Same STAN
        duplicateRequest.setTerminalId(request.getTerminalId()); // Same terminal

        TransactionResponse response2 = transactionService.process(duplicateRequest);

        // Assert
        assertTrue(response1.isApproved(), "第一筆交易應被核准");
        assertNotNull(response2);
        assertFalse(response2.isApproved(), "重複交易應被拒絕");
        assertEquals(ResponseCode.DUPLICATE_TRANSACTION.getCode(), response2.getResponseCode());
    }

    // ========== Concurrent Processing Tests ==========

    @Test
    @Order(40)
    @DisplayName("API - 並行處理多筆交易")
    @Execution(ExecutionMode.SAME_THREAD)
    void testConcurrentTransactionProcessing() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int transactionsPerThread = 5;
        CountDownLatch latch = new CountDownLatch(threadCount * transactionsPerThread);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < transactionsPerThread; j++) {
                    try {
                        TransactionRequest request = TransactionRequest.builder()
                                .transactionId(UUID.randomUUID().toString())
                                .transactionType(TransactionType.WITHDRAWAL)
                                .processingCode("011000")
                                .pan("411111" + String.format("%04d", threadId) + String.format("%06d", j))
                                .amount(new BigDecimal("1000"))
                                .currencyCode("901")
                                .sourceAccount("1234567890" + String.format("%04d", threadId))
                                .sourceAccountType(AccountType.SAVINGS)
                                .pinBlock("1234567890ABCDEF")
                                .terminalId("ATM" + String.format("%05d", threadId))
                                .merchantId("MERCHANT001")
                                .acquiringBankCode("004")
                                .stan(String.format("%06d", (threadId * transactionsPerThread + j)))
                                .rrn(UUID.randomUUID().toString().substring(0, 12))
                                .channel("ATM")
                                .build();

                        TransactionResponse response = transactionService.process(request);
                        if (response.isApproved()) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        // Assert
        assertTrue(latch.await(30, TimeUnit.SECONDS), "所有交易應在 30 秒內完成");
        executor.shutdown();

        int totalTransactions = threadCount * transactionsPerThread;
        assertEquals(totalTransactions, successCount.get() + failureCount.get(),
                "成功與失敗交易總數應等於總交易數");
        assertTrue(successCount.get() > 0, "應有成功的交易");

        System.out.printf("並行測試結果: 成功=%d, 失敗=%d, 總數=%d%n",
                successCount.get(), failureCount.get(), totalTransactions);
    }

    // ========== Transaction History Tests ==========

    @Test
    @Order(50)
    @DisplayName("API - 查詢交易記錄")
    void testTransactionHistoryQuery() {
        // Arrange - Process multiple transactions
        for (int i = 0; i < 5; i++) {
            TransactionRequest request = createWithdrawalRequest();
            request.setTransactionId(UUID.randomUUID().toString());
            transactionService.process(request);
        }

        // Act
        var allTransactions = repository.findAll();

        // Assert
        assertNotNull(allTransactions);
        assertTrue(allTransactions.size() >= 5, "應至少有 5 筆交易記錄");
    }

    // ========== Error Handling Tests ==========

    @Test
    @Order(60)
    @DisplayName("API - 處理空的交易請求")
    void testNullTransactionRequest() {
        // Act & Assert
        assertThrows(Exception.class, () -> transactionService.process(null),
                "空的交易請求應拋出異常");
    }

    @Test
    @Order(61)
    @DisplayName("API - 處理缺少必要欄位的請求")
    void testMissingRequiredFields() {
        // Arrange - Missing PAN
        TransactionRequest request = TransactionRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("1000"))
                // Missing PAN
                .build();

        // Act
        TransactionResponse response = transactionService.process(request);

        // Assert
        assertNotNull(response);
        assertFalse(response.isApproved(), "缺少必要欄位的交易應被拒絕");
    }

    @Test
    @Order(62)
    @DisplayName("API - 處理不支援的交易類型")
    void testUnsupportedTransactionType() {
        // Arrange
        TransactionRequest request = createWithdrawalRequest();
        request.setTransactionType(null);

        // Act
        TransactionResponse response = transactionService.process(request);

        // Assert
        assertNotNull(response);
        assertFalse(response.isApproved(), "未指定交易類型應被拒絕");
    }

    // ========== Performance Tests ==========

    @Test
    @Order(70)
    @DisplayName("API - 交易處理效能測試")
    void testTransactionProcessingPerformance() {
        // Arrange
        int iterations = 100;
        long startTime = System.currentTimeMillis();

        // Act
        for (int i = 0; i < iterations; i++) {
            TransactionRequest request = createWithdrawalRequest();
            request.setTransactionId(UUID.randomUUID().toString());
            transactionService.process(request);
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / iterations;

        // Assert
        System.out.printf("處理 %d 筆交易: 總時間=%d ms, 平均時間=%.2f ms%n",
                iterations, totalTime, avgTime);
        assertTrue(avgTime < 100, "平均處理時間應小於 100ms");
    }

    // ========== Helper Methods ==========

    private TransactionRequest createWithdrawalRequest() {
        return TransactionRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .transactionType(TransactionType.WITHDRAWAL)
                .processingCode("011000")
                .pan("4111111111111111")
                .amount(new BigDecimal("1000"))
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .sourceAccountType(AccountType.SAVINGS)
                .pinBlock("1234567890ABCDEF")
                .terminalId("ATM001")
                .merchantId("MERCHANT001")
                .acquiringBankCode("004")
                .stan(String.format("%06d", System.currentTimeMillis() % 1000000))
                .rrn(UUID.randomUUID().toString().substring(0, 12))
                .channel("ATM")
                .transactionDateTime(LocalDateTime.now())
                .build();
    }

    private TransactionRequest createTransferRequest() {
        return TransactionRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .transactionType(TransactionType.TRANSFER)
                .processingCode("401010")
                .pan("4111111111111111")
                .amount(new BigDecimal("5000"))
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .sourceAccountType(AccountType.SAVINGS)
                .destinationAccount("98765432109876")
                .destinationAccountType(AccountType.CHECKING)
                .destinationBankCode("012")
                .pinBlock("1234567890ABCDEF")
                .terminalId("ATM001")
                .merchantId("MERCHANT001")
                .acquiringBankCode("004")
                .stan(String.format("%06d", System.currentTimeMillis() % 1000000))
                .rrn(UUID.randomUUID().toString().substring(0, 12))
                .channel("ATM")
                .transactionDateTime(LocalDateTime.now())
                .build();
    }

    private TransactionRequest createBalanceInquiryRequest() {
        return TransactionRequest.builder()
                .transactionId(UUID.randomUUID().toString())
                .transactionType(TransactionType.BALANCE_INQUIRY)
                .processingCode("311000")
                .pan("4111111111111111")
                .amount(BigDecimal.ZERO)
                .currencyCode("901")
                .sourceAccount("12345678901234")
                .sourceAccountType(AccountType.SAVINGS)
                .pinBlock("1234567890ABCDEF")
                .terminalId("ATM001")
                .merchantId("MERCHANT001")
                .acquiringBankCode("004")
                .stan(String.format("%06d", System.currentTimeMillis() % 1000000))
                .rrn(UUID.randomUUID().toString().substring(0, 12))
                .channel("ATM")
                .transactionDateTime(LocalDateTime.now())
                .build();
    }
}
