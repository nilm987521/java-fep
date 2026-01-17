# FEP 系統 JMeter 測試計劃

## 1. 測試計劃概述

### 1.1 測試目標

| 目標 | 說明 | 成功標準 |
|------|------|---------|
| 功能驗證 | 驗證所有交易類型正確處理 | 100% 交易成功率 |
| 效能測試 | 驗證系統達到 >2000 TPS | P99 < 500ms |
| 穩定性測試 | 長時間運行無異常 | 24 小時無錯誤 |
| 壓力測試 | 找出系統極限 | 確定最大 TPS |

### 1.2 測試架構 - 端對端完整流程

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          完整端對端測試架構                                   │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐     │
│  │                        JMeter Load Generator                        │     │
│  │  ┌───────────────────┐  ┌───────────────────┐  ┌─────────────────┐  │     │
│  │  │ ATM Simulator     │  │ POS Simulator     │  │ Mobile Simulator│  │     │
│  │  │ (AtmSimulator     │  │ (TemplateSampler) │  │ (TemplateSampler│  │     │
│  │  │  Sampler)         │  │                   │  │                 │  │     │
│  │  └─────────┬─────────┘  └─────────┬─────────┘  └────────┬────────┘  │     │
│  └────────────┼──────────────────────┼─────────────────────┼───────────┘     │
│               │                      │                     │                 │
│               │     ISO 8583 / Custom Protocol             │                 │
│               └──────────────────────┼─────────────────────┘                 │
│                                      ▼                                       │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │                      FEP Server (本體)                                │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │   │
│  │  │ 通道接入    │  │ 電文轉換    │  │ 交易路由    │  │ 安全模組    │   │   │
│  │  │ (Channel)   │  │ (ChannelMsg │  │ (Router)    │  │ (HSM/MAC)   │   │   │
│  │  │             │  │  Service)   │  │             │  │             │   │   │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └─────────────┘   │   │
│  │         │                │                │                           │   │
│  │         └────────────────┼────────────────┘                           │   │
│  │                          ▼                                            │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │   │
│  │  │              FiscDualChannelClient (財金連線)                   │  │   │
│  │  │  Send Channel (Port 9001)  ←→  Receive Channel (Port 9002)      │  │   │
│  │  └──────────────────────────────┬──────────────────────────────────┘  │   │
│  └─────────────────────────────────┼─────────────────────────────────────┘   │
│                                    │                                         │
│                        Dual-Channel TCP/IP                                   │
│                                    ▼                                         │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │                    JMeter Simulators (模擬外部系統)                   │   │
│  │                                                                       │   │
│  │  ┌─────────────────────────────┐   ┌─────────────────────────────┐    │   │
│  │  │   FISC Simulator            │   │   Bank Core Simulator       │    │   │
│  │  │   (FiscDualChannelServer    │   │   (BankCoreServerSampler)   │    │   │
│  │  │    Sampler)                 │   │                             │    │   │
│  │  │   • Receive Port: 9001      │   │   • Receive Port: 9003      │    │   │
│  │  │   • Send Port: 9002         │   │   • Send Port: 9004         │    │   │
│  │  │   • Response Rules          │   │   • Balance Simulation      │    │   │
│  │  │   • MTI Routing             │   │   • Account Validation      │    │   │
│  │  └─────────────────────────────┘   └─────────────────────────────┘    │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘

交易流程：
1. JMeter ATM/POS Simulator 發送交易請求到 FEP
2. FEP 接收並解析電文 (使用 ChannelMessageService)
3. FEP 進行交易路由、安全驗證
4. FEP 透過 FiscDualChannelClient 發送到 FISC Simulator
5. FISC Simulator 回應 (或轉發到 Bank Core Simulator)
6. FEP 接收回應，轉換後回傳給 ATM/POS Simulator
7. JMeter Assertion 驗證回應正確性
```

### 1.3 系統組件角色

| 組件 | 角色 | 說明 |
|------|------|------|
| **FEP Server (本體)** | 待測系統 (SUT) | Spring Boot 應用程式，處理所有交易 |
| **JMeter ATM/POS Sampler** | 負載產生器 | 模擬 ATM/POS 終端發送交易 |
| **FISC Simulator** | 外部系統模擬 | 模擬財金公司回應 |
| **Bank Core Simulator** | 外部系統模擬 | 模擬銀行核心系統 |
| **GenericMessageAssertion** | 驗證器 | 驗證交易回應正確性 |

### 1.4 可用的 JMeter 組件

| 組件類型 | 組件名稱 | 用途 |
|---------|---------|------|
| **Sampler** | AtmSimulatorSampler | 發送 ATM 交易（支援自定義 Schema） |
| **Sampler** | TemplateSampler | 基於模板發送交易 |
| **Sampler** | FiscSampler | 傳統 ISO 8583 交易 |
| **Sampler** | FiscDualChannelServerSampler | 模擬 FISC 伺服器 |
| **Sampler** | BankCoreServerSampler | 模擬銀行核心系統 |
| **Config** | SchemaConfigElement | 配置電文 Schema |
| **Config** | TransactionTemplateConfig | 配置交易模板 |
| **Assertion** | GenericMessageAssertion | 驗證回應電文 |

---

## 2. FEP Server (本體) 設置

### 2.1 FEP Server 編譯與啟動

```bash
# 1. 編譯整個專案
cd /Users/daniel/Documents/Personal/java-fep
mvn clean package -DskipTests

# 2. 啟動 FEP Server (本體)
cd fep-application
java -jar target/fep-application-*.jar --spring.profiles.active=test

# 或使用 Maven 啟動
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

### 2.2 FEP Server 測試環境配置

**測試用 application-test.yml:**
```yaml
server:
  port: 8080

fep:
  # 通訊設定
  communication:
    fisc:
      enabled: true
      host: localhost
      send-port: 9001      # 連接到 FISC Simulator 的接收端口
      receive-port: 9002   # 連接到 FISC Simulator 的發送端口
      connect-timeout: 5000
      read-timeout: 30000
      reconnect-interval: 3000
      max-reconnect-attempts: 10

  # Channel Schema 設定
  channel:
    config-file: classpath:channel-config.json
    hot-reload: true
    schema-location: classpath:schemas/

  # 安全設定 (測試環境可簡化)
  security:
    hsm:
      enabled: false      # 測試環境不啟用 HSM
    mac-validation: false # 測試環境不驗證 MAC

  # 限流設定
  rate-limit:
    enabled: true
    max-tps: 3000        # 測試環境限制

# 日誌設定
logging:
  level:
    com.fep: DEBUG
    com.fep.communication: TRACE
```

### 2.3 FEP Server 健康檢查

```bash
# 檢查 FEP Server 是否啟動成功
curl http://localhost:8080/actuator/health

# 預期回應:
# {"status":"UP","components":{"fisc":{"status":"UP"},"db":{"status":"UP"}}}

# 檢查 FISC 連接狀態
curl http://localhost:8080/actuator/fisc

# 查看即時指標
curl http://localhost:8080/actuator/metrics/fep.transactions.count
```

### 2.4 完整測試環境啟動順序

```
啟動順序 (重要！):
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  Step 1: 啟動 JMeter Simulators (模擬外部系統)                       │
│  ┌─────────────────────────────────────────────────────────────┐     │
│  │  • FISC Simulator     - 監聽 Port 9001 (接收), 9002 (發送)  │     │
│  │  • Bank Core Simulator - 監聽 Port 9003 (接收), 9004 (發送) │     │
│  └─────────────────────────────────────────────────────────────┘     │
│                              │                                       │
│                              ▼                                       │
│  Step 2: 啟動 FEP Server (本體)                                      │
│  ┌─────────────────────────────────────────────────────────────┐     │
│  │  java -jar fep-application.jar --spring.profiles.active=test│     │
│  │  • 等待 "Started FepApplication" 訊息                       │     │
│  │  • 確認 FISC 連接成功: "FISC connection established"        │     │
│  └─────────────────────────────────────────────────────────────┘     │
│                              │                                       │
│                              ▼                                       │
│  Step 3: 啟動 JMeter Load Generator (發送測試交易)                   │
│  ┌─────────────────────────────────────────────────────────────┐     │
│  │  jmeter -n -t performance-test.jmx -l results.jtl           │     │
│  │  • ATM/POS Simulators 開始發送交易到 FEP                    │     │
│  └─────────────────────────────────────────────────────────────┘     │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.5 FEP Server 監控 (測試期間)

```
監控面板:
┌─────────────────────────────────────────────────────────────────────────┐
│                     FEP Server 即時監控                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  交易統計 (每 5 秒更新)                                                  │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  TPS: 2,134  │  Success: 99.8%  │  Avg RT: 187ms  │  P99: 412ms │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  連接狀態                                                               │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  FISC Send:    ● Connected   │  Active Requests: 127             │    │
│  │  FISC Receive: ● Connected   │  Pending Responses: 89            │    │
│  │  Bank Core:    ● Connected   │  Queue Size: 45                   │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  資源使用                                                               │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  CPU: ████████░░░░░░ 54%  │  Heap: ██████████░░ 78%              │    │
│  │  GC:  12ms/min            │  Threads: 234 active                 │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**監控端點:**
```bash
# 即時 TPS 統計
watch -n 1 "curl -s localhost:8080/actuator/metrics/fep.transactions.count | jq"

# 回應時間統計
curl localhost:8080/actuator/metrics/fep.transactions.latency

# 活躍連接數
curl localhost:8080/actuator/metrics/fep.connections.active

# 錯誤統計
curl localhost:8080/actuator/metrics/fep.transactions.errors

# 完整健康狀態
curl localhost:8080/actuator/health | jq
```

**日誌監控:**
```bash
# 即時監控 FEP 日誌
tail -f logs/fep-application.log | grep -E "(ERROR|WARN|TPS|latency)"

# 監控交易流水
tail -f logs/transaction.log
```

---

## 3. JMeter 環境設置

### 3.1 環境配置

```yaml
# 開發/測試環境
development:
  fep_server:
    host: localhost
    port: 8080
  fisc_simulator:
    receive_port: 9001
    send_port: 9002
  bank_core_simulator:
    receive_port: 9003
    send_port: 9004

# 效能測試環境
performance:
  fep_server:
    host: fep-perf.internal
    port: 8080
    instances: 3
  load_generators:
    count: 3
    jmeter_heap: 4g
```

### 3.2 JMeter 啟動參數

```bash
# 效能測試建議配置
export JVM_ARGS="-Xms4g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# 啟動 JMeter (GUI 模式 - 開發用)
jmeter -t test-plan.jmx

# 啟動 JMeter (CLI 模式 - 效能測試)
jmeter -n -t test-plan.jmx -l results.jtl -e -o report/
```

### 3.3 Schema 和模板文件

```
fep-jmeter-plugin/src/main/resources/
├── schemas/
│   ├── AtmMessages.json          # FISC ATM Schema
│   └── ncr-ndc-v1.json           # NCR NDC Schema
└── templates/
    └── atm-transaction-templates.json  # 13 種交易模板
```

---

## 4. 測試場景設計

### 4.1 Phase 1: 功能測試

#### 場景 1.1: 基礎連接測試

```
測試目標: 驗證 FEP 與 FISC/CBS 連接正常
執行時間: 5 分鐘
並發用戶: 1

測試步驟:
1. 啟動 FISC Simulator (Passive 模式)
2. 啟動 Bank Core Simulator
3. FEP 簽入 (Sign-On 0800)
4. Echo Test (0800)
5. FEP 簽出 (Sign-Off 0800)
```

**JMeter 測試計劃結構:**
```
Test Plan
├── FISC Simulator (FiscDualChannelServerSampler)
│   ├── Receive Port: 9001
│   ├── Send Port: 9002
│   └── Operation Mode: PASSIVE
├── Bank Core Simulator (BankCoreServerSampler)
│   ├── Receive Port: 9003
│   └── Send Port: 9004
├── Thread Group (1 user, 1 loop)
│   ├── Sign-On Request (TemplateSampler)
│   │   └── Template: 網路簽到
│   ├── Echo Test (TemplateSampler)
│   │   └── Template: Echo測試
│   └── Sign-Off Request (TemplateSampler)
│       └── Template: 網路簽退
└── Listeners
    ├── View Results Tree
    └── Summary Report
```

#### 場景 1.2: ATM 交易功能測試

```
測試目標: 驗證所有 ATM 交易類型
執行時間: 10 分鐘
並發用戶: 1

交易類型:
1. 跨行提款 (0200) - Withdrawal
2. 餘額查詢 (0200) - Balance Inquiry
3. 跨行轉帳 (0200) - Transfer
4. 繳費 (0200) - Bill Payment
5. 交易沖正 (0400) - Reversal
```

**JMeter 測試計劃結構:**
```
Test Plan
├── Schema Config (SchemaConfigElement)
│   └── Schema File: schemas/AtmMessages.json
├── Simulators Setup Group
│   ├── FISC Simulator
│   └── Bank Core Simulator
├── ATM Transaction Thread Group
│   ├── Transaction Controller
│   │   ├── Withdrawal Test
│   │   │   ├── AtmSimulatorSampler (MTI: 0200)
│   │   │   │   └── Field Values: {"processingCode": "010000", ...}
│   │   │   └── GenericMessageAssertion
│   │   │       └── Expected: {"responseCode": "$eq:00"}
│   │   ├── Balance Inquiry Test
│   │   │   ├── AtmSimulatorSampler (MTI: 0200)
│   │   │   │   └── Field Values: {"processingCode": "310000", ...}
│   │   │   └── GenericMessageAssertion
│   │   ├── Transfer Test
│   │   │   └── ...
│   │   ├── Bill Payment Test
│   │   │   └── ...
│   │   └── Reversal Test (MTI: 0400)
│   │       └── ...
└── Listeners
```

#### 場景 1.3: 錯誤處理測試

```
測試目標: 驗證各種錯誤情況的處理
執行時間: 10 分鐘

錯誤場景:
1. 餘額不足 (Response Code: 51)
2. 卡片無效 (Response Code: 14)
3. 超過限額 (Response Code: 61)
4. 網路逾時 (Connection Timeout)
5. 交易重複 (Response Code: 94)
```

**FISC Simulator 回應規則配置:**
```json
{
  "rules": [
    {
      "name": "insufficient-balance",
      "mti": "0200",
      "conditions": [
        {"field": "processingCode", "operator": "eq", "value": "010000"},
        {"field": "amount", "operator": "gt", "value": "10000000"}
      ],
      "response": {
        "responseCode": "51"
      }
    },
    {
      "name": "invalid-card",
      "mti": "0200",
      "conditions": [
        {"field": "pan", "operator": "startsWith", "value": "9999"}
      ],
      "response": {
        "responseCode": "14"
      }
    }
  ]
}
```

---

### 4.2 Phase 2: 效能測試

#### 場景 2.1: 基準效能測試 (Baseline)

```
測試目標: 建立效能基準線
執行時間: 30 分鐘
並發用戶: 10 → 50 → 100
Ramp-up: 5 分鐘

交易組合:
- 提款: 40%
- 餘額查詢: 30%
- 轉帳: 20%
- 繳費: 10%
```

**JMeter 配置:**
```
Test Plan
├── Thread Group
│   ├── Number of Threads: 100
│   ├── Ramp-up Period: 300 sec
│   └── Loop Count: Forever
│   ├── Runtime Controller (30 min)
│   │   └── Throughput Controller
│   │       ├── Withdrawal (40%)
│   │       ├── Balance Inquiry (30%)
│   │       ├── Transfer (20%)
│   │       └── Bill Payment (10%)
├── Constant Throughput Timer
│   └── Target Throughput: 2000/min (calculated)
└── Listeners
    ├── Aggregate Report
    ├── Response Times Over Time
    └── Transactions per Second
```

#### 場景 2.2: 負載測試 (Load Test)

```
測試目標: 驗證目標 TPS (2000+)
執行時間: 60 分鐘
目標 TPS: 2000

階段:
1. 暖機階段 (5 min): 500 TPS
2. 爬升階段 (10 min): 500 → 2000 TPS
3. 穩定階段 (40 min): 2000 TPS
4. 降溫階段 (5 min): 2000 → 0 TPS
```

**效能指標監控:**
```
關鍵指標:
├── 回應時間
│   ├── Average < 200ms
│   ├── P90 < 400ms
│   ├── P99 < 500ms
│   └── Max < 1000ms
├── 吞吐量
│   └── TPS >= 2000
├── 錯誤率
│   └── Error Rate < 0.1%
└── 資源使用
    ├── CPU < 80%
    ├── Memory < 85%
    └── Network I/O
```

#### 場景 2.3: 壓力測試 (Stress Test)

```
測試目標: 找出系統極限
執行時間: 2 小時
並發增長: 每 10 分鐘增加 500 TPS

監控重點:
- 系統何時開始降級
- 錯誤率超過 1% 的臨界點
- 回應時間超過 SLA 的臨界點
```

---

### 4.3 Phase 3: 穩定性測試

#### 場景 3.1: 耐久測試 (Endurance Test)

```
測試目標: 驗證長時間運行穩定性
執行時間: 24 小時
並發用戶: 目標 TPS 的 70% (1400 TPS)

監控項目:
- 記憶體洩漏
- 連接池耗盡
- 錯誤累積
- 效能降級趨勢
```

#### 場景 3.2: 故障恢復測試

```
測試目標: 驗證故障恢復能力
執行時間: 2 小時

故障場景:
1. FISC 連接斷線 (模擬網路故障)
2. Bank Core 無回應 (模擬超時)
3. FEP 重啟
4. 資料庫連接中斷
```

---

## 5. 測試案例詳細設計

### 5.1 ATM 提款交易測試

```json
{
  "testCase": "TC-ATM-001",
  "name": "ATM 跨行提款 - 正常流程",
  "description": "驗證正常提款交易流程",
  "preconditions": [
    "FEP 已簽入 FISC",
    "帳戶餘額充足"
  ],
  "request": {
    "mti": "0200",
    "fields": {
      "pan": "4111111111111111",
      "processingCode": "010000",
      "amount": "000000100000",
      "stan": "${__Random(100000,999999)}",
      "localTime": "${__time(HHmmss)}",
      "localDate": "${__time(MMdd)}",
      "terminalId": "ATM12345",
      "acquiringBankCode": "822"
    }
  },
  "expectedResponse": {
    "mti": "0210",
    "responseCode": "00",
    "authCode": "$regex:[A-Z0-9]{6}"
  },
  "assertions": [
    {"field": "responseCode", "operator": "$eq", "value": "00"},
    {"field": "mti", "operator": "$eq", "value": "0210"}
  ]
}
```

### 5.2 交易驗證 Assertion 配置

```json
{
  "assertions": {
    "withdrawal_success": {
      "responseCode": "$eq:00",
      "mti": "$eq:0210",
      "authCode": "$regex:[A-Z0-9]{6}",
      "stan": "$eq:${REQUEST_STAN}"
    },
    "balance_inquiry_success": {
      "responseCode": "$eq:00",
      "mti": "$eq:0210",
      "availableBalance": "$regex:\\d{12}"
    },
    "insufficient_balance": {
      "responseCode": "$eq:51",
      "mti": "$eq:0210"
    }
  }
}
```

---

## 6. JMeter 測試計劃範例

### 6.1 完整測試計劃結構

```
FEP_Performance_Test.jmx
│
├── Test Plan
│   ├── User Defined Variables
│   │   ├── FEP_HOST = localhost
│   │   ├── FEP_PORT = 8080
│   │   ├── FISC_RECEIVE_PORT = 9001
│   │   ├── FISC_SEND_PORT = 9002
│   │   └── TARGET_TPS = 2000
│   │
│   ├── Schema Config (SchemaConfigElement)
│   │   └── Schema File: schemas/AtmMessages.json
│   │
│   ├── setUp Thread Group (Simulators)
│   │   ├── FISC Dual Channel Server (FiscDualChannelServerSampler)
│   │   │   ├── Receive Port: ${FISC_RECEIVE_PORT}
│   │   │   ├── Send Port: ${FISC_SEND_PORT}
│   │   │   ├── Operation Mode: PASSIVE
│   │   │   ├── Default Response Code: 00
│   │   │   └── MTI Response Rules: {...}
│   │   └── Bank Core Server (BankCoreServerSampler)
│   │
│   ├── setUp Thread Group (Sign-On)
│   │   ├── Sign-On Request (TemplateSampler)
│   │   └── Response Assertion
│   │
│   ├── Main Transaction Thread Group
│   │   ├── Number of Threads: 100
│   │   ├── Ramp-up: 300 sec
│   │   ├── Loop: Forever
│   │   │
│   │   ├── Runtime Controller (1800 sec)
│   │   │   ├── Throughput Controller (40% - Withdrawal)
│   │   │   │   ├── AtmSimulatorSampler
│   │   │   │   │   ├── Host: ${FEP_HOST}
│   │   │   │   │   ├── Port: ${FEP_PORT}
│   │   │   │   │   └── Field Values: {...}
│   │   │   │   └── GenericMessageAssertion
│   │   │   │
│   │   │   ├── Throughput Controller (30% - Balance Inquiry)
│   │   │   │   └── ...
│   │   │   │
│   │   │   ├── Throughput Controller (20% - Transfer)
│   │   │   │   └── ...
│   │   │   │
│   │   │   └── Throughput Controller (10% - Bill Payment)
│   │   │       └── ...
│   │   │
│   │   └── Constant Throughput Timer
│   │       └── Target: ${__jexl3(${TARGET_TPS} * 60 / ${__threadNum})}
│   │
│   ├── tearDown Thread Group (Sign-Off)
│   │   └── Sign-Off Request
│   │
│   └── Listeners
│       ├── Aggregate Report
│       ├── Response Times Over Time
│       ├── Transactions per Second
│       ├── Summary Report
│       └── Backend Listener (InfluxDB - optional)
```

---

## 7. 執行計劃

### 7.1 執行時程

| 階段 | 活動 | 時間 | 前置條件 |
|------|------|------|---------|
| Week 1 | 環境準備和測試計劃建立 | 3 天 | JMeter Plugin 編譯完成 |
| Week 1 | 功能測試執行 | 2 天 | 環境就緒 |
| Week 2 | 基準效能測試 | 2 天 | 功能測試通過 |
| Week 2 | 負載測試執行 | 3 天 | 基準測試完成 |
| Week 3 | 壓力測試執行 | 2 天 | 負載測試通過 |
| Week 3 | 穩定性測試 | 3 天 | 壓力測試完成 |
| Week 4 | 結果分析和報告 | 2 天 | 所有測試完成 |

### 7.2 執行命令

```bash
# 1. 編譯 JMeter Plugin
cd /Users/daniel/Documents/Personal/java-fep
mvn clean package -pl fep-jmeter-plugin -DskipTests

# 2. 安裝 Plugin 到 JMeter
cp fep-jmeter-plugin/target/fep-jmeter-plugin-*.jar $JMETER_HOME/lib/ext/
cp fep-jmeter-plugin/target/dependency/*.jar $JMETER_HOME/lib/ext/

# 3. 執行功能測試 (GUI 模式)
jmeter -t tests/functional-test.jmx

# 4. 執行效能測試 (CLI 模式)
jmeter -n \
  -t tests/performance-test.jmx \
  -l results/perf-$(date +%Y%m%d).jtl \
  -e -o report/perf-$(date +%Y%m%d)/ \
  -Jthreads=100 \
  -Jrampup=300 \
  -Jduration=1800

# 5. 分散式執行 (多台 Load Generator)
jmeter -n \
  -t tests/performance-test.jmx \
  -R load1.internal,load2.internal,load3.internal \
  -l results/distributed.jtl
```

---

## 8. 驗收標準

### 8.1 功能測試驗收

| 項目 | 標準 |
|------|------|
| 所有交易類型 | 100% 通過 |
| 錯誤處理 | 正確回應錯誤碼 |
| 連接管理 | 自動重連成功 |

### 8.2 效能測試驗收

| 指標 | 基準 | 目標 |
|------|------|------|
| TPS | 1000 | 2000+ |
| Avg Response Time | < 300ms | < 200ms |
| P90 Response Time | < 500ms | < 400ms |
| P99 Response Time | < 800ms | < 500ms |
| Error Rate | < 0.5% | < 0.1% |

### 8.3 穩定性測試驗收

| 項目 | 標準 |
|------|------|
| 24 小時運行 | 無錯誤累積 |
| 記憶體使用 | 無洩漏 (穩定) |
| 連接池 | 無耗盡 |
| 故障恢復 | < 30 秒內恢復 |

---

## 9. 報告模板

### 9.1 測試報告大綱

```
1. 執行摘要
   - 測試範圍
   - 執行時間
   - 關鍵結果

2. 測試環境
   - 硬體配置
   - 軟體版本
   - 網路拓撲

3. 測試結果
   - 功能測試結果
   - 效能測試結果
   - 穩定性測試結果

4. 效能分析
   - TPS 趨勢圖
   - 回應時間分布
   - 錯誤分析

5. 問題和建議
   - 發現的問題
   - 改進建議

6. 結論
   - 是否達到目標
   - 後續行動
```

---

## 附錄

### A. 測試資料準備

```sql
-- 測試帳戶資料 (範例)
INSERT INTO test_accounts (pan, balance, status) VALUES
('4111111111111111', 1000000, 'ACTIVE'),
('4222222222222222', 500000, 'ACTIVE'),
('4333333333333333', 0, 'ACTIVE'),         -- 餘額不足測試
('9999999999999999', 1000000, 'BLOCKED');  -- 無效卡測試
```

### B. JMeter 變數參考

| 變數名 | 說明 | 範例 |
|--------|------|------|
| `${__Random(min,max)}` | 隨機數 | `${__Random(100000,999999)}` |
| `${__time(format)}` | 時間格式 | `${__time(HHmmss)}` |
| `${__UUID}` | 唯一識別碼 | |
| `${__threadNum}` | 執行緒編號 | |
| `${__jexl3(expr)}` | 表達式計算 | |

### C. 常用 MTI 對照

| MTI | 說明 |
|-----|------|
| 0100 | Authorization Request |
| 0110 | Authorization Response |
| 0200 | Financial Request |
| 0210 | Financial Response |
| 0400 | Reversal Request |
| 0410 | Reversal Response |
| 0800 | Network Management Request |
| 0810 | Network Management Response |
