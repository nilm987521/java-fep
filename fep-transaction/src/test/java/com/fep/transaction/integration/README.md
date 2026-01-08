# FEP Transaction Integration Tests

整合測試和端對端測試套件，用於驗證交易處理模組的完整功能。

## 測試結構

```
integration/
├── IntegrationTestSuite.java              # 測試套件（統一執行入口）
├── IntegrationTestConfig.java             # 測試配置
├── TransactionApiIntegrationTest.java     # API 整合測試
├── EndToEndTransactionTest.java          # 端對端測試
└── MessageTransactionIntegrationTest.java # 跨模組整合測試
```

## 測試類型

### 1. API 整合測試 (TransactionApiIntegrationTest)

測試交易處理 API 的完整功能：

- **提款交易測試**
  - 成功處理跨行提款
  - 拒絕無效金額
  - 拒絕超過限額的提款

- **轉帳交易測試**
  - 成功處理跨行轉帳
  - 拒絕轉帳至相同帳戶
  - 處理跨行轉帳（不同銀行）

- **餘額查詢測試**
  - 成功處理餘額查詢
  - 驗證不扣款行為

- **重複交易偵測**
  - 偵測並拒絕重複交易

- **並行處理測試**
  - 多執行緒並行處理交易

- **錯誤處理測試**
  - 處理空請求
  - 處理缺少必要欄位
  - 處理不支援的交易類型

- **效能測試**
  - 交易處理效能基準測試

### 2. 端對端測試 (EndToEndTransactionTest)

測試從 ISO 8583 電文到回應的完整流程：

**流程**: ISO 8583 Request → Domain Request → Process → Domain Response → ISO 8583 Response

- **提款流程測試**
  - 完整的跨行提款流程
  - 提款金額不足流程

- **轉帳流程測試**
  - 完整的跨行轉帳流程
  - 跨行轉帳至不同銀行

- **餘額查詢流程測試**
  - 完整的餘額查詢流程

- **沖正流程測試**
  - 完整的交易沖正流程

- **錯誤處理流程**
  - 處理格式錯誤的電文
  - 處理重複交易

- **欄位驗證測試**
  - 驗證所有必要欄位正確傳遞

- **效能測試**
  - 完整流程效能基準測試

### 3. 跨模組整合測試 (MessageTransactionIntegrationTest)

測試 fep-message 和 fep-transaction 模組之間的整合：

- **電文解析測試**
  - ISO 8583 電文解析為交易請求
  - 交易回應轉換為 ISO 8583 電文
  - 處理碼映射到交易類型

- **欄位轉換測試**
  - 所有必要欄位正確轉換
  - 回應欄位正確傳遞
  - 金額格式轉換
  - 日期時間格式轉換

- **錯誤處理測試**
  - 處理缺少必要欄位
  - 處理無效金額格式

- **多種交易類型測試**
  - 提款、轉帳、餘額查詢

- **往返轉換測試**
  - 完整往返轉換的完整性驗證

- **效能測試**
  - 轉換效能基準測試

## 執行測試

### 執行所有整合測試

```bash
# 執行整個測試套件
mvn test -Dtest=IntegrationTestSuite

# 或執行整個 integration 套件
mvn test -Dtest="com.fep.transaction.integration.*"
```

### 執行特定測試類別

```bash
# API 整合測試
mvn test -Dtest=TransactionApiIntegrationTest

# 端對端測試
mvn test -Dtest=EndToEndTransactionTest

# 跨模組整合測試
mvn test -Dtest=MessageTransactionIntegrationTest
```

### 執行特定測試方法

```bash
# 執行特定測試方法
mvn test -Dtest=TransactionApiIntegrationTest#testSuccessfulWithdrawalTransaction
```

### 在 IDE 中執行

#### IntelliJ IDEA
1. 右鍵點擊測試類別或套件
2. 選擇 "Run Tests"

#### Eclipse
1. 右鍵點擊測試類別或套件
2. 選擇 "Run As" → "JUnit Test"

## 測試覆蓋範圍

### 交易類型覆蓋
- ✅ 跨行提款 (Withdrawal)
- ✅ 跨行轉帳 (Transfer)
- ✅ 餘額查詢 (Balance Inquiry)
- ✅ 交易沖正 (Reversal)

### 功能覆蓋
- ✅ 電文解析與組裝
- ✅ 交易處理流程
- ✅ 重複交易偵測
- ✅ 欄位驗證
- ✅ 錯誤處理
- ✅ 並行處理
- ✅ 效能測試

### ISO 8583 欄位覆蓋
- ✅ Field 2: PAN (Primary Account Number)
- ✅ Field 3: Processing Code
- ✅ Field 4: Transaction Amount
- ✅ Field 7: Transmission Date/Time
- ✅ Field 11: STAN
- ✅ Field 12: Local Time
- ✅ Field 13: Local Date
- ✅ Field 32: Acquiring Institution ID
- ✅ Field 33: Forwarding Institution ID
- ✅ Field 37: RRN
- ✅ Field 39: Response Code
- ✅ Field 41: Terminal ID
- ✅ Field 42: Merchant ID
- ✅ Field 49: Currency Code
- ✅ Field 102/103: Account Numbers

## 測試報告

測試完成後，報告會產生在：
```
fep-transaction/target/surefire-reports/
```

### 查看測試報告
```bash
# 產生 HTML 報告
mvn surefire-report:report

# 報告位置
open target/site/surefire-report.html
```

## 效能基準

預期的效能指標：

| 測試項目 | 目標時間 |
|---------|---------|
| 單筆交易處理 | < 50 ms |
| 電文轉換 | < 5 ms |
| 完整 E2E 流程 | < 100 ms |
| 並行處理 (10 threads) | < 30 seconds |

## 常見問題

### Q: 測試失敗怎麼辦？

A: 檢查以下項目：
1. 確認所有依賴模組已編譯 (`mvn clean install`)
2. 檢查測試日誌輸出
3. 確認測試環境配置正確

### Q: 如何增加測試覆蓋率？

A: 可以在現有測試類別中添加更多測試案例，或建立新的測試類別。

### Q: 測試執行很慢？

A: 可以使用平行執行：
```bash
mvn test -DforkCount=4 -DreuseForks=true
```

## 持續整合

這些測試應該在 CI/CD pipeline 中自動執行：

```yaml
# .gitlab-ci.yml 範例
test:
  stage: test
  script:
    - mvn clean test
  artifacts:
    reports:
      junit:
        - target/surefire-reports/TEST-*.xml
```

## 維護指引

### 新增測試

1. 在對應的測試類別中添加新的 `@Test` 方法
2. 使用 `@Order` 註解控制執行順序
3. 使用 `@DisplayName` 提供清晰的中文描述
4. 更新此 README 文件

### 測試命名規範

- 測試方法名稱: `test{功能描述}`
- DisplayName: 使用中文描述測試目的
- 測試類別: `{功能}IntegrationTest` 或 `{流程}E2ETest`

## 相關文件

- [Transaction Module Documentation](../../main/java/com/fep/transaction/README.md)
- [ISO 8583 Message Format](../../../message/README.md)
- [FEP System Overview](/README.md)
