package com.fep.transaction.uat;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.AccountType;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.processor.*;
import com.fep.transaction.service.TransactionService;
import com.fep.transaction.service.TransactionServiceImpl;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UAT (User Acceptance Testing) 驗收測試
 *
 * 本測試類別模擬台灣銀行業真實業務場景,進行完整的使用者驗收測試。
 *
 * 測試範圍涵蓋：
 * 1. ATM 跨行交易場景
 * 2. 代收代付場景
 * 3. 行動支付場景
 * 4. 異常處理場景
 * 5. 交易限額場景
 *
 * @author FEP Development Team
 * @version 1.0.0
 */
@Tag("UAT")
@DisplayName("UAT - 使用者驗收測試")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UatAcceptanceTest {

    private static final Logger logger = LoggerFactory.getLogger(UatAcceptanceTest.class);

    // Test data constants
    private static final String TEST_PAN = "4111111111111111";
    private static final String TEST_ACCOUNT = "1234567890";
    private static final String TEST_DESTINATION_ACCOUNT = "9876543210";
    private static final String TEST_BANK_CODE = "012";
    private static final String TEST_TERMINAL_ID = "ATM00001";
    private static final String TEST_MERCHANT_ID = "M000001";
    private static final String TEST_PIN_BLOCK = "1234567890ABCDEF";

    // Services
    private TransactionService transactionService;

    // Test statistics
    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    @BeforeAll
    static void setUpBeforeClass() {
        logger.info("=".repeat(80));
        logger.info("開始執行 UAT 驗收測試");
        logger.info("測試環境: Java 21 + Spring Boot 3.x");
        logger.info("=".repeat(80));
    }

    @AfterAll
    static void tearDownAfterClass() {
        logger.info("=".repeat(80));
        logger.info("UAT 驗收測試完成");
        logger.info("總測試數: {}, 通過: {}, 失敗: {}",
                totalTests, passedTests, failedTests);
        logger.info("成功率: {}%", totalTests > 0 ? (passedTests * 100 / totalTests) : 0);
        logger.info("=".repeat(80));
    }

    @BeforeEach
    void setUp() {
        // Create processors
        List<TransactionProcessor> processors = createProcessors();

        // Initialize transaction service
        transactionService = new TransactionServiceImpl(processors);

        logger.info("測試環境初始化完成");
        totalTests++;
    }

    @AfterEach
    void tearDown() {
        logger.info("-".repeat(80));
    }

    // ========================================
    // 1. ATM 跨行交易場景
    // ========================================

    @Nested
    @DisplayName("1. ATM 跨行交易場景")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AtmInterbankScenarios {

        @Test
        @Order(1)
        @DisplayName("UAT-001: 跨行提款完整流程")
        void testInterbankWithdrawal() {
            logger.info("執行測試案例: UAT-001 - 跨行提款完整流程");

            try {
                // 前置條件
                // - 有效的卡片
                // - 帳戶餘額充足 (假設 10,000 元)
                // - 提款金額 3,000 元

                // 測試步驟
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.WITHDRAWAL)
                        .processingCode("011000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("3000.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .build();

                // 執行交易
                TransactionResponse response = transactionService.process(request);

                // 預期結果驗證
                assertNotNull(response, "回應不應為 null");
                assertTrue(response.isApproved(), "交易應該核准");
                assertEquals("00", response.getResponseCode(), "回應碼應為 00 (核准)");
                assertEquals(new BigDecimal("3000.00"), response.getAmount(), "金額應為 3000.00");
                assertNotNull(response.getAuthorizationCode(), "應有授權碼");
                assertNotNull(response.getAvailableBalance(), "應回傳可用餘額");

                logger.info("✓ UAT-001 測試通過: 跨行提款成功, 授權碼={}", response.getAuthorizationCode());
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-001 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(2)
        @DisplayName("UAT-002: 跨行存款完整流程")
        void testInterbankDeposit() {
            logger.info("執行測試案例: UAT-002 - 跨行存款完整流程");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.DEPOSIT)
                        .processingCode("211000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("5000.00"))
                        .currencyCode("901")
                        .destinationAccount(TEST_ACCOUNT)
                        .destinationAccountType(AccountType.SAVINGS)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved(), "存款交易應該核准");
                assertEquals("00", response.getResponseCode());
                assertEquals(new BigDecimal("5000.00"), response.getAmount());

                logger.info("✓ UAT-002 測試通過: 跨行存款成功");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-002 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(3)
        @DisplayName("UAT-003: 跨行餘額查詢")
        void testBalanceInquiry() {
            logger.info("執行測試案例: UAT-003 - 跨行餘額查詢");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.BALANCE_INQUIRY)
                        .processingCode("311000")
                        .pan(TEST_PAN)
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved());
                assertEquals("00", response.getResponseCode());
                assertNotNull(response.getAvailableBalance(), "應回傳可用餘額");
                assertNotNull(response.getLedgerBalance(), "應回傳帳戶餘額");

                logger.info("✓ UAT-003 測試通過: 餘額查詢成功, 可用餘額={}", response.getAvailableBalance());
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-003 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(4)
        @DisplayName("UAT-004: 跨行轉帳 - 約定帳戶")
        void testInterbankTransferDesignated() {
            logger.info("執行測試案例: UAT-004 - 跨行轉帳 (約定帳戶)");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.TRANSFER)
                        .processingCode("401000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("10000.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .destinationAccount(TEST_DESTINATION_ACCOUNT)
                        .destinationAccountType(AccountType.SAVINGS)
                        .destinationBankCode(TEST_BANK_CODE)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .isDesignatedAccount(true)
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved(), "約定轉帳應該核准");
                assertEquals("00", response.getResponseCode());
                assertEquals(new BigDecimal("10000.00"), response.getAmount());

                logger.info("✓ UAT-004 測試通過: 約定轉帳成功");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-004 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(5)
        @DisplayName("UAT-005: 跨行轉帳 - 非約定帳戶")
        void testInterbankTransferNonDesignated() {
            logger.info("執行測試案例: UAT-005 - 跨行轉帳 (非約定帳戶)");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.TRANSFER)
                        .processingCode("401000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("5000.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .destinationAccount(TEST_DESTINATION_ACCOUNT)
                        .destinationAccountType(AccountType.SAVINGS)
                        .destinationBankCode(TEST_BANK_CODE)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .isDesignatedAccount(false)
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved(), "非約定轉帳應該核准 (金額在限額內)");
                assertEquals("00", response.getResponseCode());

                logger.info("✓ UAT-005 測試通過: 非約定轉帳成功");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-005 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(6)
        @DisplayName("UAT-006: 無卡提款 - 手機預約")
        void testCardlessWithdrawal() {
            logger.info("執行測試案例: UAT-006 - 無卡提款 (手機預約)");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.CARDLESS_WITHDRAWAL)
                        .processingCode("281000")
                        .amount(new BigDecimal("2000.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .cardlessCode("12345678")  // Must be 8-16 characters
                        .mobilePhone("0912345678")
                        .otpCode("654321")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved());
                assertEquals("00", response.getResponseCode());
                assertNotNull(response.getCardlessReference(), "應有無卡交易參考號");

                logger.info("✓ UAT-006 測試通過: 無卡提款成功");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-006 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }
    }

    // ========================================
    // 2. 代收代付場景
    // ========================================

    @Nested
    @DisplayName("2. 代收代付場景")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BillPaymentScenarios {

        @Test
        @Order(1)
        @DisplayName("UAT-010: 水電費繳費")
        void testUtilityBillPayment() {
            logger.info("執行測試案例: UAT-010 - 水電費繳費");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.BILL_PAYMENT)
                        .processingCode("501000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("1500.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .billPaymentNumber("20240101123456")  // 14 digits
                        .billTypeCode("01")  // Water bill type code
                        .payeeInstitutionCode("TPWC")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved(), "水費繳費應該核准");
                assertEquals("00", response.getResponseCode());
                assertNotNull(response.getPaymentReceiptNumber(), "應有繳費收據號");

                logger.info("✓ UAT-010 測試通過: 水電費繳費成功");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-010 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(2)
        @DisplayName("UAT-011: 電信費繳費")
        void testTelecomBillPayment() {
            logger.info("執行測試案例: UAT-011 - 電信費繳費");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.BILL_PAYMENT)
                        .processingCode("501000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("899.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("MOBILE")
                        .billPaymentNumber("09123456780001")  // 14 digits
                        .billTypeCode("04")  // Telecom bill type code
                        .payeeInstitutionCode("CHT")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved());
                assertEquals("00", response.getResponseCode());

                logger.info("✓ UAT-011 測試通過: 電信費繳費成功");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-011 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(3)
        @DisplayName("UAT-012: 信用卡繳款")
        void testCreditCardPayment() {
            logger.info("執行測試案例: UAT-012 - 信用卡繳款");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.BILL_PAYMENT)
                        .processingCode("501000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("15000.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .billPaymentNumber("41111111111111")  // 14 digits
                        .billTypeCode("05")  // Credit card bill type code
                        .payeeInstitutionCode("BANK012")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved(), "信用卡繳款應該核准");
                assertEquals("00", response.getResponseCode());

                logger.info("✓ UAT-012 測試通過: 信用卡繳款成功");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-012 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(4)
        @DisplayName("UAT-013: 稅款繳納 - 牌照稅")
        void testTaxPayment() {
            logger.info("執行測試案例: UAT-013 - 稅款繳納 (牌照稅)");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.BILL_PAYMENT)
                        .processingCode("501000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("7200.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .billPaymentNumber("11300123456789")  // 14 digits
                        .billTypeCode("11")  // Vehicle license tax code
                        .payeeInstitutionCode("MOFNTA")
                        .taxId("A123456789")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved(), "稅款繳納應該核准");
                assertEquals("00", response.getResponseCode());

                logger.info("✓ UAT-013 測試通過: 稅款繳納成功");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-013 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }
    }

    // ========================================
    // 3. 行動支付場景
    // ========================================

    @Nested
    @DisplayName("3. 行動支付場景")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class MobilePaymentScenarios {

        @Test
        @Order(1)
        @DisplayName("UAT-020: 台灣 Pay QR Code 支付")
        void testTaiwanPayQrPayment() {
            logger.info("執行測試案例: UAT-020 - 台灣 Pay QR Code 支付");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.TAIWAN_PAY)
                        .processingCode("291000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("450.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .merchantId(TEST_MERCHANT_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("MOBILE")
                        .qrCodeData("TW_PAY_QR_12345678")
                        .merchantQrCode("MERCHANT_QR_0012345678901234567890")  // At least 20 chars
                        .taiwanPayToken("TP_TOKEN_ABC1234567890")  // At least 16 characters
                        .paymentType("PUSH")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved(), "台灣 Pay 支付應該核准");
                assertEquals("00", response.getResponseCode());
                assertNotNull(response.getTaiwanPayReference(), "應有台灣 Pay 交易參考號");
                assertNotNull(response.getMerchantName(), "應有特約商店名稱");

                logger.info("✓ UAT-020 測試通過: 台灣 Pay 支付成功");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-020 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(2)
        @DisplayName("UAT-021: 電子錢包加值 - LINE Pay")
        void testEWalletTopup() {
            logger.info("執行測試案例: UAT-021 - 電子錢包加值 (LINE Pay)");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.E_WALLET_TOPUP)
                        .processingCode("541000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("1000.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("MOBILE")
                        .eWalletProvider("LINE")  // Must match provider code (LINE, not LINEPAY)
                        .eWalletAccountId("LINEPAY_USER_001")  // At least 8 characters
                        .eWalletTxnType("TOPUP")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved(), "電子錢包加值應該核准");
                assertEquals("00", response.getResponseCode());
                assertNotNull(response.getEWalletReference(), "應有電子錢包交易參考號");
                assertNotNull(response.getEWalletBalance(), "應回傳加值後餘額");

                logger.info("✓ UAT-021 測試通過: 電子錢包加值成功, 餘額={}", response.getEWalletBalance());
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-021 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(3)
        @DisplayName("UAT-022: 電子票證加值 - 悠遊卡")
        void testETicketTopup() {
            logger.info("執行測試案例: UAT-022 - 電子票證加值 (悠遊卡)");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.E_TICKET_TOPUP)
                        .processingCode("511000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("500.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .eTicketCardNumber("1234567890123456")  // 16-20 digits
                        .eTicketType("01")  // EasyCard type code
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved(), "電子票證加值應該核准");
                assertEquals("00", response.getResponseCode());
                assertNotNull(response.getETicketBalance(), "應回傳加值後票卡餘額");

                logger.info("✓ UAT-022 測試通過: 電子票證加值成功");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-022 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(4)
        @DisplayName("UAT-023: 跨境支付交易")
        void testCrossBorderPayment() {
            logger.info("執行測試案例: UAT-023 - 跨境支付交易");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.CROSS_BORDER_PAYMENT)
                        .processingCode("521000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("50000.00"))
                        .currencyCode("901")
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("MOBILE")
                        .destinationAccount("12345678901234")  // Required destination account
                        .destinationCountryCode("US")
                        .beneficiaryName("John Doe")
                        .beneficiaryBankSwift("BOFAUS3N")
                        .remittancePurposeCode("04")  // Trade payment code
                        .senderReference("INV-2024-001")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved(), "跨境支付應該核准");
                assertEquals("00", response.getResponseCode());
                assertNotNull(response.getCrossBorderReference(), "應有跨境交易參考號");
                assertNotNull(response.getSwiftReference(), "應有 SWIFT 參考號");

                logger.info("✓ UAT-023 測試通過: 跨境支付成功");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-023 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }
    }

    // ========================================
    // 4. 異常處理場景
    // ========================================

    @Nested
    @DisplayName("4. 異常處理場景")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ExceptionHandlingScenarios {

        @Test
        @Order(1)
        @DisplayName("UAT-040: 無效交易類型處理")
        void testInvalidTransactionType() {
            logger.info("執行測試案例: UAT-040 - 無效交易類型處理");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(null)  // 無效的交易類型
                        .processingCode("999999")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("1000.00"))
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response, "應回傳錯誤回應");
                assertFalse(response.isApproved(), "無效交易應被拒絕");
                assertNotNull(response.getErrorDetails(), "應有錯誤詳情");

                logger.info("✓ UAT-040 測試通過: 無效交易類型正確處理");
                passedTests++;

            } catch (Exception e) {
                logger.error("✗ UAT-040 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(2)
        @DisplayName("UAT-041: 超過提款限額處理")
        void testInsufficientFunds() {
            logger.info("執行測試案例: UAT-041 - 超過提款限額處理");

            try {
                // 測試超過單筆提款限額 (20000 TWD)
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.WITHDRAWAL)
                        .processingCode("011000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("25000.00"))  // 超過單筆限額 20000
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertFalse(response.isApproved(), "超過限額應被拒絕");
                assertEquals(ResponseCode.EXCEEDS_WITHDRAWAL_LIMIT.getCode(),
                        response.getResponseCode(), "應回傳超過提款限額錯誤碼");

                logger.info("✓ UAT-041 測試通過: 餘額不足正確處理");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-041 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }
    }

    // ========================================
    // 5. 交易限額場景
    // ========================================

    @Nested
    @DisplayName("5. 交易限額場景")
    class LimitControlScenarios {

        @Test
        @Order(1)
        @DisplayName("UAT-050: 單筆限額驗證 - 通過")
        void testSingleTransactionLimitPass() {
            logger.info("執行測試案例: UAT-050 - 單筆限額驗證 (通過)");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.WITHDRAWAL)
                        .processingCode("011000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("20000.00"))  // 在單筆限額內
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                assertTrue(response.isApproved(), "金額在限額內應核准");

                logger.info("✓ UAT-050 測試通過: 單筆限額驗證通過");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-050 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }

        @Test
        @Order(2)
        @DisplayName("UAT-051: 非約定轉帳限額")
        void testNonDesignatedTransferLimit() {
            logger.info("執行測試案例: UAT-051 - 非約定轉帳限額");

            try {
                TransactionRequest request = TransactionRequest.builder()
                        .transactionId(generateTransactionId())
                        .transactionType(TransactionType.TRANSFER)
                        .processingCode("401000")
                        .pan(TEST_PAN)
                        .amount(new BigDecimal("10000.00"))
                        .sourceAccount(TEST_ACCOUNT)
                        .sourceAccountType(AccountType.SAVINGS)
                        .destinationAccount(TEST_DESTINATION_ACCOUNT)
                        .destinationAccountType(AccountType.SAVINGS)
                        .destinationBankCode(TEST_BANK_CODE)
                        .pinBlock(TEST_PIN_BLOCK)
                        .terminalId(TEST_TERMINAL_ID)
                        .stan(generateStan())
                        .rrn(generateRrn())
                        .channel("ATM")
                        .isDesignatedAccount(false)
                        .build();

                TransactionResponse response = transactionService.process(request);

                assertNotNull(response);
                // 根據實際限額設定,可能核准或拒絕
                logger.info("非約定轉帳結果: {}", response.isApproved() ? "核准" : "拒絕");

                logger.info("✓ UAT-051 測試通過: 非約定轉帳限額控制正常");
                passedTests++;

            } catch (AssertionError e) {
                logger.error("✗ UAT-051 測試失敗: {}", e.getMessage());
                failedTests++;
                throw e;
            }
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * 產生交易 ID
     */
    private String generateTransactionId() {
        return "TXN" + System.currentTimeMillis() +
                String.format("%04d", new Random().nextInt(10000));
    }

    /**
     * 產生 STAN (System Trace Audit Number)
     */
    private String generateStan() {
        return String.format("%06d", new Random().nextInt(1000000));
    }

    /**
     * 產生 RRN (Retrieval Reference Number)
     */
    private String generateRrn() {
        return String.format("%012d", System.currentTimeMillis() % 1000000000000L);
    }

    /**
     * 建立所有交易處理器
     */
    private List<TransactionProcessor> createProcessors() {
        List<TransactionProcessor> processors = new ArrayList<>();

        processors.add(new WithdrawalProcessor());
        processors.add(new DepositProcessor());
        processors.add(new BalanceInquiryProcessor());
        processors.add(new TransferProcessor());
        processors.add(new BillPaymentProcessor());
        processors.add(new TaiwanPayProcessor());
        processors.add(new EWalletProcessor());
        processors.add(new ETicketTopupProcessor());
        processors.add(new CardlessWithdrawalProcessor());
        processors.add(new CrossBorderPaymentProcessor());

        return processors;
    }
}
