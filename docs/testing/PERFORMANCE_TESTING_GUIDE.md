# 效能壓力測試執行指南

## 快速開始

### 執行所有效能測試
```bash
cd /Users/daniel/Documents/Personal/java-fep/fep-transaction
mvn test -Dgroups=performance
```

### 執行個別測試

#### 1. TPS 壓力測試（目標 2000+ TPS）
```bash
mvn test -Dtest=PerformanceStressTest#testTpsStress_2000Plus
```

預期結果：
- TPS >= 2000
- 成功率 >= 95%
- 執行時間 < 30 秒

#### 2. 延遲分析測試
```bash
mvn test -Dtest=PerformanceStressTest#testLatencyAnalysis
```

預期結果：
- P50 延遲 < 100ms
- P95 延遲 < 500ms  ← 系統目標
- P99 延遲 < 1000ms

#### 3. 持續負載測試（30秒）
```bash
mvn test -Dtest=PerformanceStressTest#testSustainedLoad
```

預期結果：
- 平均 TPS >= 1500
- 成功率 >= 90%
- 穩定執行 30 秒

#### 4. 尖峰負載測試
```bash
mvn test -Dtest=PerformanceStressTest#testBurstLoad
```

預期結果：
- 尖峰 TPS >= 1000
- 成功率 >= 85%
- P95 延遲 < 1000ms

#### 5. 綜合效能測試
```bash
mvn test -Dtest=PerformanceStressTest#testMixedWorkload
```

預期結果：
- 混合負載 TPS >= 1800
- 成功率 >= 92%
- 模擬真實交易分佈（查詢 40%, 提款 30%, 轉帳 20%, 存款 10%）

## 測試參數調整

### JVM 記憶體配置
```bash
export MAVEN_OPTS="-Xms4g -Xmx4g -XX:+UseG1GC"
mvn test -Dgroups=performance
```

### 增加執行緒數
修改 `PerformanceStressTest.java` 中的：
- `numberOfThreads` - 並發執行緒數
- `transactionsPerThread` - 每執行緒交易數
- `testDurationSeconds` - 測試持續時間

## 疑難排解

### OutOfMemoryError
```bash
# 增加 JVM 記憶體
export MAVEN_OPTS="-Xms8g -Xmx8g"
```

### 測試逾時
```bash
# 增加測試逾時時間
mvn test -Dtest=PerformanceStressTest -Dsurefire.timeout=600
```

### 查看詳細日誌
```bash
# 啟用 DEBUG 日誌
mvn test -Dtest=PerformanceStressTest -X
```

## 效能基準值

基於測試環境（MBP M1, 16GB RAM）：

| 測試項目 | 目標值 | 實測值 |
|---------|--------|--------|
| TPS 壓力測試 | >= 2000 TPS | ~2347 TPS |
| P95 延遲 | < 500ms | ~3ms |
| P99 延遲 | < 1000ms | ~7ms |
| 持續負載 TPS | >= 1500 | 待測 |
| 尖峰負載 TPS | >= 1000 | 待測 |

## CI/CD 整合

### GitLab CI 範例
```yaml
performance-test:
  stage: test
  script:
    - mvn test -Dgroups=performance
  artifacts:
    reports:
      junit: fep-transaction/target/surefire-reports/*.xml
  only:
    - main
    - develop
  when: manual
```

### Jenkins Pipeline 範例
```groovy
stage('Performance Test') {
    steps {
        sh 'mvn test -Dgroups=performance'
    }
    post {
        always {
            junit 'fep-transaction/target/surefire-reports/*.xml'
        }
    }
}
```

## 注意事項

1. **資源消耗**：測試會使用大量 CPU 和記憶體
2. **網路連線**：確保測試環境網路穩定
3. **並發限制**：注意作業系統的檔案描述符限制
4. **結果變動**：效能結果可能因硬體環境而異
5. **測試隔離**：使用 @Tag("performance") 避免干擾一般測試
