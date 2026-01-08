# FEP Transaction Integration Tests - Quick Start Guide

## 快速開始

### 執行所有整合測試
```bash
cd /Users/daniel/Documents/Personal/java-fep
mvn test -pl fep-transaction -Dtest=IntegrationTestSuite
```

### 執行特定測試類別

```bash
# API 整合測試（15個測試）
mvn test -pl fep-transaction -Dtest=TransactionApiIntegrationTest

# 端對端測試（10個測試）
mvn test -pl fep-transaction -Dtest=EndToEndTransactionTest

# 跨模組整合測試（12個測試）
mvn test -pl fep-transaction -Dtest=MessageTransactionIntegrationTest
```

### 執行單個測試方法

```bash
# 測試成功的提款交易
mvn test -pl fep-transaction -Dtest=TransactionApiIntegrationTest#testSuccessfulWithdrawalTransaction

# 測試完整的 E2E 流程
mvn test -pl fep-transaction -Dtest=EndToEndTransactionTest#testCompleteWithdrawalFlow

# 測試電文轉換
mvn test -pl fep-transaction -Dtest=MessageTransactionIntegrationTest#testMessageParsingToTransactionRequest
```

## 測試結構

```
fep-transaction/src/test/java/com/fep/transaction/integration/
│
├── TransactionApiIntegrationTest.java      # API 整合測試
│   ├── 提款交易測試 (3個)
│   ├── 轉帳交易測試 (3個)
│   ├── 餘額查詢測試 (2個)
│   ├── 重複交易偵測 (1個)
│   ├── 並行處理測試 (1個)
│   ├── 交易歷史查詢 (1個)
│   ├── 錯誤處理測試 (3個)
│   └── 效能測試 (1個)
│
├── EndToEndTransactionTest.java           # 端對端測試
│   ├── 提款流程 (2個)
│   ├── 轉帳流程 (2個)
│   ├── 餘額查詢流程 (1個)
│   ├── 沖正流程 (1個)
│   ├── 錯誤處理 (2個)
│   ├── 欄位驗證 (1個)
│   └── 效能測試 (1個)
│
├── MessageTransactionIntegrationTest.java  # 跨模組整合測試
│   ├── 電文解析 (3個)
│   ├── 欄位轉換 (4個)
│   ├── 錯誤處理 (2個)
│   ├── 多交易類型 (1個)
│   ├── 往返轉換 (1個)
│   └── 效能測試 (1個)
│
├── IntegrationTestSuite.java              # 測試套件
├── IntegrationTestConfig.java             # 測試配置
└── README.md                              # 詳細說明
```

## 測試覆蓋的交易類型

### 1. 跨行提款 (Withdrawal)
- **處理碼**: 010000
- **MTI**: 0200 (Financial Request)
- **測試檔案**: TransactionApiIntegrationTest, EndToEndTransactionTest

**測試案例**:
```java
// 成功提款
TransactionRequest request = TransactionRequest.builder()
    .transactionType(TransactionType.WITHDRAWAL)
    .pan("4111111111111111")
    .amount(new BigDecimal("1000"))
    .sourceAccount("1234567890")
    .build();
```

### 2. 跨行轉帳 (Transfer)
- **處理碼**: 400000
- **MTI**: 0200 (Financial Request)
- **測試檔案**: TransactionApiIntegrationTest, EndToEndTransactionTest

**測試案例**:
```java
// 成功轉帳
TransactionRequest request = TransactionRequest.builder()
    .transactionType(TransactionType.TRANSFER)
    .amount(new BigDecimal("5000"))
    .sourceAccount("1234567890")
    .destinationAccount("9876543210")
    .destinationBankCode("004")
    .build();
```

### 3. 餘額查詢 (Balance Inquiry)
- **處理碼**: 310000
- **MTI**: 0100 (Authorization Request)
- **測試檔案**: TransactionApiIntegrationTest, EndToEndTransactionTest

**測試案例**:
```java
// 餘額查詢
TransactionRequest request = TransactionRequest.builder()
    .transactionType(TransactionType.BALANCE_INQUIRY)
    .sourceAccount("1234567890")
    .build();
```

### 4. 交易沖正 (Reversal)
- **MTI**: 0400 (Reversal Request)
- **測試檔案**: EndToEndTransactionTest

## 測試報告

### 查看測試報告
```bash
# 純文字報告
cat fep-transaction/target/surefire-reports/*.txt

# XML 報告（可用於 CI/CD）
ls fep-transaction/target/surefire-reports/TEST-*.xml
```

### 產生 HTML 報告
```bash
mvn surefire-report:report -pl fep-transaction
open fep-transaction/target/site/surefire-report.html
```

## 測試資料範例

### ISO 8583 電文欄位
```
F2  - PAN: 4111111111111111
F3  - Processing Code: 010000 (提款)
F4  - Amount: 000000001000 (1000 TWD)
F11 - STAN: 000001
F12 - Time: 120530
F13 - Date: 0108
F32 - Acquiring Bank: 004
F37 - RRN: 000000000001
F41 - Terminal ID: ATM00001
F49 - Currency: 901 (TWD)
```

### Transaction Request 物件
```java
TransactionRequest request = TransactionRequest.builder()
    .transactionId(UUID.randomUUID().toString())
    .transactionType(TransactionType.WITHDRAWAL)
    .pan("4111111111111111")
    .amount(new BigDecimal("1000"))
    .currencyCode("901")
    .sourceAccount("1234567890")
    .sourceAccountType(AccountType.SAVINGS)
    .terminalId("ATM00001")
    .merchantId("MERCHANT001")
    .acquiringBankCode("004")
    .stan("000001")
    .rrn("000000000001")
    .channel("ATM")
    .build();
```

## 常見問題

### Q: 測試失敗怎麼辦？
A:
1. 檢查測試日誌：`cat fep-transaction/target/surefire-reports/*.txt`
2. 確認所有依賴模組已編譯：`mvn clean install`
3. 查看具體錯誤訊息並根據業務邏輯調整

### Q: 如何調試特定測試？
A:
```bash
# 在 IntelliJ IDEA 中
1. 開啟測試類別
2. 在測試方法上右鍵 → Debug
3. 設置斷點進行調試

# 使用 Maven
mvn test -pl fep-transaction -Dtest=TestClass#testMethod -Dmaven.surefire.debug
```

### Q: 如何增加新的測試案例？
A:
1. 在對應的測試類別中添加新的 `@Test` 方法
2. 使用 `@Order` 控制執行順序
3. 使用 `@DisplayName` 提供中文描述
4. 參考現有測試案例的模式

### Q: 測試執行很慢？
A:
```bash
# 使用平行執行
mvn test -pl fep-transaction -DforkCount=4 -DreuseForks=true

# 只執行特定測試
mvn test -pl fep-transaction -Dtest=TransactionApiIntegrationTest
```

## 效能指標

| 測試項目 | 目標時間 | 實際測試 |
|---------|---------|---------|
| 單筆交易處理 | < 100 ms | TransactionApiIntegrationTest#testTransactionProcessingPerformance |
| 電文轉換 | < 5 ms | MessageTransactionIntegrationTest#testConversionPerformance |
| 完整 E2E 流程 | < 50 ms | EndToEndTransactionTest#testCompleteFlowPerformance |
| 並行處理 | < 30s | TransactionApiIntegrationTest#testConcurrentTransactionProcessing |

## CI/CD 整合

### GitLab CI 範例
```yaml
test:
  stage: test
  script:
    - mvn clean test -pl fep-transaction
  artifacts:
    reports:
      junit:
        - fep-transaction/target/surefire-reports/TEST-*.xml
```

### GitHub Actions 範例
```yaml
- name: Run Integration Tests
  run: mvn test -pl fep-transaction -Dtest=IntegrationTestSuite

- name: Publish Test Report
  uses: dorny/test-reporter@v1
  if: always()
  with:
    name: Integration Tests
    path: fep-transaction/target/surefire-reports/TEST-*.xml
    reporter: java-junit
```

## 相關連結

- [測試詳細說明](src/test/java/com/fep/transaction/integration/README.md)
- [專案總結](/INTEGRATION_TESTS_SUMMARY.md)
- [FEP 系統文件](/CLAUDE.md)

---

**更新日期**: 2026-01-08
**版本**: 1.0.0
