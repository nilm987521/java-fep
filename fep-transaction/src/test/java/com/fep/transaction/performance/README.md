# FEP Transaction 效能壓力測試

## 概述

本模組包含針對 FEP Transaction 系統的效能壓力測試，目標是驗證系統是否能達到 **2000+ TPS** 的效能要求。

## 測試內容

### 1. TPS 壓力測試
- **目標**: 達到 2000+ TPS
- **方法**: 50 個並發執行緒，每個執行緒執行 400 筆交易
- **驗證標準**:
  - TPS >= 2000
  - 成功率 >= 95%
  - 完成時間 < 30 秒

### 2. 延遲測試
- **目標**: 分析 P50, P95, P99 延遲
- **方法**: 30 個並發執行緒，混合不同交易類型
- **驗證標準**:
  - P95 延遲 < 500ms
  - P99 延遲 < 1000ms

### 3. 持續負載測試
- **目標**: 驗證系統在持續負載下的穩定性
- **方法**: 40 個執行緒持續執行 30 秒
- **驗證標準**:
  - 平均 TPS >= 1500
  - 成功率 >= 90%

### 4. 尖峰負載測試
- **目標**: 模擬突發大量請求
- **方法**: 100 個並發執行緒同時發起請求
- **驗證標準**:
  - 尖峰 TPS >= 1000
  - 成功率 >= 85%
  - P95 延遲 < 1000ms

### 5. 綜合效能測試
- **目標**: 模擬真實場景的混合負載
- **方法**: 60 個執行緒，混合不同交易類型（查詢 40%, 提款 30%, 轉帳 20%, 存款 10%）
- **驗證標準**:
  - 平均 TPS >= 1800
  - 成功率 >= 92%

## 執行方式

### 執行所有效能測試
```bash
cd /Users/daniel/Documents/Personal/java-fep/fep-transaction
mvn test -Dgroups=performance
```

### 執行特定測試
```bash
# 只執行 TPS 壓力測試
mvn test -Dtest=PerformanceStressTest#testTpsStress_2000Plus

# 只執行延遲測試
mvn test -Dtest=PerformanceStressTest#testLatencyAnalysis

# 只執行持續負載測試
mvn test -Dtest=PerformanceStressTest#testSustainedLoad

# 只執行尖峰負載測試
mvn test -Dtest=PerformanceStressTest#testBurstLoad

# 只執行綜合效能測試
mvn test -Dtest=PerformanceStressTest#testMixedWorkload
```

### 在 IDE 中執行
1. 開啟 `PerformanceStressTest.java`
2. 右鍵點擊類別或特定測試方法
3. 選擇 "Run" 或 "Debug"

## 效能指標說明

### TPS (Transactions Per Second)
- 每秒處理的交易數量
- 計算公式: `(總交易數 * 1000) / 測試時長(毫秒)`

### 延遲指標
- **P50 (中位數)**: 50% 的交易延遲低於此值
- **P95**: 95% 的交易延遲低於此值
- **P99**: 99% 的交易延遲低於此值

### 成功率
- 計算公式: `(成功交易數 / 總交易數) * 100%`

## 測試環境建議

### 硬體配置
- **CPU**: 至少 4 核心（建議 8 核心以上）
- **記憶體**: 至少 8GB（建議 16GB 以上）
- **磁碟**: SSD（避免 I/O 瓶頸）

### JVM 參數建議
```bash
-Xms4g -Xmx4g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+ParallelRefProcEnabled
```

### Maven 測試參數
```bash
mvn test -Dgroups=performance \
  -DargLine="-Xms4g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

## 測試報告範例

```
========================================
TPS 壓力測試 - 目標 2000+ TPS
========================================
配置：
  - 並發執行緒數: 50
  - 每執行緒交易數: 400
  - 總交易數: 20000

效能統計報告：
  - 測試時長: 8.52 秒
  - 總交易數: 20000
  - 成功交易: 19876
  - 失敗交易: 124
  - 成功率: 99.38%
  - 平均 TPS: 2347.42

延遲統計：
  - 最小延遲: 0 ms
  - 平均延遲: 18.35 ms
  - P50 延遲: 15 ms
  - P95 延遲: 42 ms
  - P99 延遲: 68 ms
  - 最大延遲: 156 ms

效能驗證：
  目標 TPS: >= 2000
  實際 TPS: 2347.42 ✓ 通過
```

## 注意事項

1. **測試隔離**: 效能測試標記為 `@Tag("performance")`，不會在一般測試中執行
2. **資源消耗**: 效能測試會消耗大量 CPU 和記憶體資源
3. **執行時間**: 完整測試約需 2-3 分鐘
4. **結果變動**: 效能結果可能因硬體環境而有所差異
5. **生產環境**: 本測試使用記憶體儲存庫，實際生產環境效能可能不同

## 效能調校建議

### 如果 TPS 未達標
1. 增加 `batchParallelism` 參數（目前為 20）
2. 檢查是否有不必要的同步操作
3. 優化資料庫連線池配置
4. 使用物件池減少物件建立開銷

### 如果延遲過高
1. 檢查是否有阻塞操作
2. 使用非同步處理機制
3. 優化電文解析邏輯
4. 減少日誌輸出

### 如果成功率過低
1. 檢查錯誤日誌
2. 調整執行緒池大小
3. 增加逾時設定
4. 檢查資源限制（檔案描述符、連線數等）

## 持續改進

建議將效能測試整合到 CI/CD 流程中，定期執行以監控效能變化：

```yaml
# GitLab CI 範例
performance-test:
  stage: test
  script:
    - mvn test -Dgroups=performance
  only:
    - develop
    - main
  when: manual  # 手動觸發
```

## 參考資料

- [CLAUDE.md - 技術決策](/Users/daniel/Documents/Personal/java-fep/CLAUDE.md)
- [Spring Boot 效能調校指南](https://spring.io/guides)
- [JVM 效能調校](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/)
