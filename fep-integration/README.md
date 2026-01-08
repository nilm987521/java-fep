# FEP Integration Module

核心系統整合模組，負責與主機系統（透過 IBM MQ）和開放系統（透過 REST API）進行通訊。

## 功能概述

### 1. 主機系統整合（Mainframe Adapter）
- 透過 IBM MQ 與大型主機核心系統通訊
- 支援 Request-Reply 模式
- 電文格式轉換：ISO 8583 ↔ COBOL Copybook
- 連線池管理與自動重連
- 完整的錯誤處理與重試機制

### 2. 開放系統整合（Open System Adapter）
- 透過 REST API 與開放系統核心銀行通訊
- 支援非同步呼叫（WebFlux）
- JSON 格式資料交換
- 連線逾時與重試機制

### 3. 電文格式轉換
- ISO 8583 → Mainframe COBOL 格式
- Mainframe COBOL 格式 → ISO 8583
- 支援 Big5 編碼（台灣主機系統標準）
- 固定長度欄位處理

## 架構設計

```
┌─────────────────────────────────────────────────────────────┐
│                   FEP Integration Layer                      │
│  ┌──────────────────────┐      ┌──────────────────────┐    │
│  │  MainframeAdapter    │      │  OpenSystemAdapter   │    │
│  │  (IBM MQ)            │      │  (REST API)          │    │
│  └──────────┬───────────┘      └──────────┬───────────┘    │
│             │                              │                 │
│  ┌──────────▼───────────┐      ┌──────────▼───────────┐    │
│  │  MqTemplate          │      │  WebClient           │    │
│  │  - sendAndReceive()  │      │  - Async calls       │    │
│  │  - Connection Pool   │      │  - Timeout handling  │    │
│  └──────────────────────┘      └──────────────────────┘    │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         Message Converters                            │   │
│  │  - Iso8583ToMainframeConverter                       │   │
│  │  - MainframeToIso8583Converter                       │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
           │                              │
           ▼                              ▼
┌────────────────────┐          ┌────────────────────┐
│   IBM MQ           │          │  Open System API   │
│   Queue Manager    │          │  (Core Banking)    │
└────────────────────┘          └────────────────────┘
```

## 依賴項目

### IBM MQ
```xml
<dependency>
    <groupId>com.ibm.mq</groupId>
    <artifactId>com.ibm.mq.allclient</artifactId>
    <version>9.3.4.1</version>
</dependency>
```

### Spring WebFlux (用於 Open System API)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

## 配置說明

### application-integration.yml

```yaml
fep:
  mq:
    # IBM MQ 連線設定
    queue-manager: QM1
    host: localhost
    port: 1414
    channel: DEV.APP.SVRCONN
    
    # 認證（可選）
    username: mquser
    password: mqpass
    
    # 佇列名稱
    request-queue: CBS.REQUEST.QUEUE
    reply-queue: CBS.REPLY.QUEUE
    
    # 逾時設定
    connection-timeout: 30000
    request-timeout: 10000
    
    # 連線池
    max-connections: 10
    min-connections: 2
    
    # 編碼設定
    encoding: Big5
    transactional: true

  open-system:
    base-url: http://localhost:8080/api/v1/cbs
    timeout: 10000
```

## 使用範例

### 1. 使用 MainframeAdapter

```java
@Service
@RequiredArgsConstructor
public class TransactionService {
    
    private final MainframeAdapter mainframeAdapter;
    
    public TransactionResponse processWithdrawal(TransactionRequest request) {
        return mainframeAdapter.process(request);
    }
}
```

### 2. 使用 MqTemplate 直接發送訊息

```java
@Service
@RequiredArgsConstructor
public class MqService {
    
    private final MqTemplate mqTemplate;
    
    public String sendToMainframe(String message) throws MqException {
        return mqTemplate.sendAndReceive(message);
    }
    
    public CompletableFuture<String> sendAsync(String message) {
        return mqTemplate.sendAsync(message);
    }
}
```

### 3. 電文格式轉換

```java
@Service
@RequiredArgsConstructor
public class MessageService {
    
    private final Iso8583ToMainframeConverter toMainframeConverter;
    private final MainframeToIso8583Converter toIso8583Converter;
    
    public MainframeRequest convertToMainframe(Iso8583Message iso8583) {
        return toMainframeConverter.convert(iso8583);
    }
    
    public Iso8583Message convertFromMainframe(MainframeResponse response) {
        return toIso8583Converter.convert(response);
    }
}
```

## COBOL Copybook 格式範例

主機系統電文格式（固定長度）：

```
位置    長度   欄位名稱           說明
-----   ----   -------------     ----------------
0-3     4      TRAN-CODE         交易代碼
4-23    20     TRAN-ID           交易識別碼
24-37   14     DATETIME          日期時間
38-56   19     CARD-NUM          卡號
57-72   16     ACCT-NUM          帳號
73-84   12     AMOUNT            金額
85-87   3      CURRENCY          幣別
88-95   8      TERMINAL-ID       終端機代碼
96-110  15     MERCHANT-ID       特店代碼
111-140 30     FIELD1            額外欄位1
141-170 30     FIELD2            額外欄位2
171-270 100    FIELD3            額外欄位3
271-320 50     RESERVED          保留欄位
```

## 交易代碼對應表

| ISO 8583 MTI | Mainframe Code | 說明 |
|--------------|----------------|------|
| 0200         | 3001           | 提款 |
| 0220         | 3003           | 轉帳 |
| 0100         | 3002           | 授權/查詢 |
| 0400         | 9001           | 沖正 |
| 0800         | 9999           | 網路管理 |

## 回應碼對應表

| Mainframe Code | ISO 8583 Code | 說明 |
|----------------|---------------|------|
| 00 / 0000      | 00            | 核准 |
| 51             | 51            | 餘額不足 |
| 54             | 54            | 卡片過期 |
| 55             | 55            | PIN 錯誤 |
| 57             | 57            | 不允許交易 |
| 91             | 91            | 發卡行無法連線 |
| 96             | 96            | 系統故障 |

## 測試

### 單元測試

```bash
mvn test -pl fep-integration
```

### 整合測試（需要 IBM MQ 環境）

```bash
# 使用 Docker 啟動 IBM MQ
docker run -d \
  --name ibm-mq \
  -p 1414:1414 \
  -p 9443:9443 \
  -e LICENSE=accept \
  -e MQ_QMGR_NAME=QM1 \
  ibmcom/mq:latest

# 執行整合測試
mvn verify -pl fep-integration
```

## 監控與維運

### 健康檢查

```java
// 檢查主機連線狀態
boolean isHealthy = mainframeAdapter.healthCheck();

// 檢查開放系統連線狀態
boolean isHealthy = openSystemAdapter.healthCheck();
```

### 日誌級別

```yaml
logging:
  level:
    com.fep.integration: DEBUG
    com.ibm.mq: WARN
    org.springframework.jms: DEBUG
```

## 常見問題

### Q1: MQ 連線逾時？
A: 檢查防火牆設定，確認 port 1414 可連線。調整 `connection-timeout` 參數。

### Q2: Big5 編碼問題？
A: 確認 MQ CCSID 設定為 950（Big5），並在程式中正確設定編碼。

### Q3: 如何處理長時間執行的交易？
A: 調整 `request-timeout` 參數，或改用非同步模式。

## 效能調校

### 連線池設定
- `max-connections`: 根據 TPS 調整，建議 10-50
- `min-connections`: 保持最小連線數，建議 2-5

### 逾時設定
- 提款/轉帳：10-15 秒
- 查詢交易：5 秒
- 代收代付：30 秒

## 版本歷史

- v1.0.0 (2026-01-07): 初始版本
  - IBM MQ 整合
  - Mainframe Adapter
  - Open System Adapter
  - 電文格式轉換器

## 授權

本專案為內部專案，版權所有。
