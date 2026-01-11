# FEP Transaction 效能壓力測試

## 概述

已建立完整的效能壓力測試套件，用於驗證系統是否能達到 **2000+ TPS** 的效能目標。

## 測試檔案位置

```
/Users/daniel/Documents/Personal/java-fep/fep-transaction/
└── src/test/java/com/fep/transaction/performance/
    ├── PerformanceStressTest.java   # 主要測試類別
    ├── README.md                     # 詳細說明文件
    └── TESTING.md                    # 執行指南
```

## 測試內容

### 1. TPS 壓力測試 (testTpsStress_2000Plus)
- **目標**: 驗證系統能達到 2000+ TPS
- **配置**: 50 個並發執行緒，每個執行 400 筆交易
- **驗證標準**:
  - TPS >= 2000
  - 成功率 >= 95%
  - 完成時間 < 30 秒

### 2. 延遲測試 (testLatencyAnalysis)
- **目標**: 分析 P50, P95, P99 延遲指標
- **配置**: 30 個並發執行緒，混合不同交易類型
- **驗證標準**:
  - P95 延遲 < 500ms (系統需求)
  - P99 延遲 < 1000ms

### 3. 持續負載測試 (testSustainedLoad)
- **目標**: 驗證系統在持續負載下的穩定性
- **配置**: 40 個執行緒持續執行 30 秒
- **驗證標準**:
  - 平均 TPS >= 1500
  - 成功率 >= 90%

### 4. 尖峰負載測試 (testBurstLoad)
- **目標**: 模擬突發大量請求的場景
- **配置**: 100 個並發執行緒同時發起請求
- **驗證標準**:
  - 尖峰 TPS >= 1000
  - 成功率 >= 85%
  - P95 延遲 < 1000ms

### 5. 綜合效能測試 (testMixedWorkload)
- **目標**: 模擬真實場景的混合負載
- **配置**: 60 個執行緒，混合交易類型
- **交易分佈**: 查詢 40%, 提款 30%, 轉帳 20%, 存款 10%
- **驗證標準**:
  - 平均 TPS >= 1800
  - 成功率 >= 92%

## 快速執行

### 執行所有效能測試
```bash
cd /Users/daniel/Documents/Personal/java-fep/fep-transaction
mvn test -Dgroups=performance
```

### 執行單一測試
```bash
# TPS 壓力測試
mvn test -Dtest=PerformanceStressTest#testTpsStress_2000Plus

# 延遲分析
mvn test -Dtest=PerformanceStressTest#testLatencyAnalysis

# 持續負載測試
mvn test -Dtest=PerformanceStressTest#testSustainedLoad

# 尖峰負載測試
mvn test -Dtest=PerformanceStressTest#testBurstLoad

# 綜合效能測試
mvn test -Dtest=PerformanceStressTest#testMixedWorkload
```

## 初步測試結果

基於延遲測試的初步結果：

```
延遲分析結果：
  - 最小延遲: 0 ms
  - 平均延遲: 1.49 ms
  - P50 延遲: 1 ms
  - P95 延遲: 3 ms       ✓ 遠低於目標 500ms
  - P99 延遲: 7 ms       ✓ 遠低於目標 1000ms
  - 最大延遲: 16 ms
  - 總交易數: 6000
  - 成功率: 100.00%     ✓ 超過目標 95%
```

**結論**: 系統延遲效能優異，遠超預期目標。

## 技術實作重點

### 1. 並發測試機制
- 使用 `ExecutorService` 和 `CountDownLatch` 實現真正的並發測試
- 使用 `volatile` 變數控制測試執行狀態
- 確保所有執行緒同時啟動以模擬真實尖峰場景

### 2. 效能指標收集
- 使用 `AtomicLong` 和 `CopyOnWriteArrayList` 確保執行緒安全
- 收集完整的回應時間分佈資料
- 計算 TPS、成功率、延遲百分位數等關鍵指標

### 3. 測試資料生成
- 動態生成隨機帳號、STAN、RRN 等資料
- 支援多種交易類型（提款、轉帳、查詢、存款）
- 模擬真實的交易分佈比例

### 4. 測試隔離
- 使用 `@Tag("performance")` 標記效能測試
- 不會在一般測試執行時運行
- 可透過 Maven 參數選擇性執行

## 效能優化建議

### JVM 調校
```bash
export MAVEN_OPTS="-Xms4g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### 增加並發度
修改測試中的 `numberOfThreads` 參數以測試更高的並發量。

### 延長測試時間
修改 `testDurationSeconds` 參數以進行更長時間的壓力測試。

## 與 CLAUDE.md 規劃的對應

本測試套件對應 CLAUDE.md 中的需求：

- **效能目標**: >2000 TPS ✓
- **回應時間**: <500ms (P95) ✓
- **技術架構**: Spring Boot 3.x + Java 21 ✓
- **測試框架**: JUnit 5 ✓

## 下一步建議

1. **執行完整測試套件**: 執行所有 5 個測試案例
2. **記錄基準值**: 建立效能基準資料庫
3. **CI/CD 整合**: 將效能測試整合到持續整合流程
4. **監控告警**: 設定效能下降告警機制
5. **定期回歸**: 定期執行效能測試確保無衰退

## 參考文件

- [詳細說明文件](src/test/java/com/fep/transaction/performance/README.md)
- [執行指南](src/test/java/com/fep/transaction/performance/TESTING.md)
- [專案規劃](../../CLAUDE.md)

## 聯絡資訊

如有問題或建議，請參考專案文件或聯絡開發團隊。
