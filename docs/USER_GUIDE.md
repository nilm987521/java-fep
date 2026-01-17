# FEP 系統使用說明

## 目錄

1. [系統概述](#1-系統概述)
2. [環境需求](#2-環境需求)
3. [快速開始](#3-快速開始)
4. [專案結構](#4-專案結構)
5. [建置與安裝](#5-建置與安裝)
6. [系統配置](#6-系統配置)
7. [啟動與運行](#7-啟動與運行)
8. [核心功能模組](#8-核心功能模組)
9. [JMeter 測試工具](#9-jmeter-測試工具)
10. [API 參考](#10-api-參考)
11. [資料庫設定](#11-資料庫設定)
12. [監控與維運](#12-監控與維運)
13. [常見問題](#13-常見問題)

---

## 1. 系統概述

### 1.1 什麼是 FEP？

FEP (Front-End Processor) 是銀行與財金公司 (FISC) 之間的前端處理器，負責處理跨行交易的電文轉換、路由、安全驗證等功能。

### 1.2 系統架構

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              外部通道                                    │
│  ┌─────────┐  ┌─────────┐  ┌───────────┐  ┌──────────┐  ┌─────────┐     │
│  │   ATM   │  │   POS   │  │  網路銀行 │  │ 行動銀行 │  │  第三方 │     │
│  └────┬────┘  └────┬────┘  └────┬──────┘  └────┬─────┘  └────┬────┘     │
└───────┼────────────┼────────────┼──────────────┼─────────────┼──────────┘
        │            │            │              │             │
        └────────────┴────────────┼──────────────┴─────────────┘
                                  ▼
┌───────────────────────────────────────────────────────────────┐
│                        FEP 核心處理層                         │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐   │
│  │  通道接入模組  │  │  交易路由引擎  │  │  電文轉換引擎  │   │
│  │ (fep-comm)     │  │ (fep-trans)    │  │ (fep-message)  │   │
│  └────────────────┘  └────────────────┘  └────────────────┘   │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐   │
│  │  安全加密模組  │  │  對帳清算模組  │  │  整合介面模組  │   │
│  │ (fep-security) │  │ (fep-settle)   │  │ (fep-integr)   │   │
│  └────────────────┘  └────────────────┘  └────────────────┘   │
└───────────────────────────────────────────────────────────────┘
        │                    │                    │
        ▼                    ▼                    ▼
┌──────────────┐    ┌────────────────┐    ┌──────────────┐
│   財金公司   │    │  核心銀行系統  │    │     HSM      │
│   (FISC)     │    │    (CBS)       │    │   安全模組   │
└──────────────┘    └────────────────┘    └──────────────┘
```

### 1.3 主要特性

| 特性 | 說明 |
|------|------|
| 高效能 | 目標 TPS > 2000，回應時間 < 500ms |
| ISO 8583 | 完整支援 ISO 8583 電文格式 |
| 雙通道連線 | 支援 FISC 雙通道 (Send/Receive) 架構 |
| 動態 Schema | 支援熱更新的 Channel-Schema 映射 |
| HSM 整合 | 支援 Thales、Utimaco、SafeNet HSM |
| 容器化部署 | 支援 Kubernetes 部署 |

---

## 2. 環境需求

### 2.1 必要軟體

| 軟體 | 最低版本 | 建議版本 | 說明 |
|------|---------|---------|------|
| Java | 21 | 21 (LTS) | Eclipse Adoptium 或 Oracle JDK |
| Maven | 3.9.0 | 3.9.10 | 建置工具 |
| Git | 2.0 | 最新版 | 版本控制 |
| Docker | 20.0 | 最新版 | 容器化 (開發環境) |

### 2.2 檢查環境

```bash
# 檢查 Java 版本
java -version
# 應顯示: openjdk version "21.x.x"

# 檢查 Maven 版本
mvn -v
# 應顯示: Apache Maven 3.9.x

# 檢查 Docker
docker -v
# 應顯示: Docker version 20.x+
```

### 2.3 硬體建議

| 環境 | CPU | 記憶體 | 硬碟 |
|------|-----|--------|------|
| 開發環境 | 4 核心 | 8 GB | 50 GB SSD |
| 測試環境 | 8 核心 | 16 GB | 100 GB SSD |
| 正式環境 | 16+ 核心 | 32+ GB | 500+ GB SSD |

---

## 3. 快速開始

### 3.1 五分鐘快速啟動

```bash
# 1. 取得原始碼
git clone <repository-url>
cd java-fep

# 2. 建置專案 (跳過測試以加速)
mvn clean install -DskipTests

# 3. 啟動 Oracle 資料庫 (Docker)
cd docker
docker-compose up -d
cd ..

# 4. 等待資料庫就緒 (約 60 秒)
sleep 60

# 5. 啟動 FEP 應用程式
mvn spring-boot:run -pl fep-application -Dspring-boot.run.profiles=oracle

# 6. 驗證啟動成功
curl http://localhost:8080/api/actuator/health
```

### 3.2 預期輸出

```
啟動成功後應看到:
========================================
  FEP System Started Successfully!
  API Documentation: http://localhost:8080/api/swagger-ui.html
========================================
```

### 3.3 健康檢查

```bash
# 檢查系統狀態
curl http://localhost:8080/api/actuator/health

# 預期回應
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

## 4. 專案結構

### 4.1 模組總覽

```
java-fep/
├── pom.xml                      # 父 POM (專案管理)
├── config/                      # 設定檔目錄
│   └── channel-schema-mapping.json
├── schemas/                     # Schema 定義檔
│   └── atm-schemas.json
├── docker/                      # Docker 相關檔案
│   ├── docker-compose.yml
│   └── init-scripts/
├── docs/                        # 文件
│
├── fep-common/                  # 共用模組
├── fep-common-db/               # 資料庫實體與 Repository
├── fep-message/                 # ISO 8583 電文處理
├── fep-communication/           # FISC 連線管理
├── fep-transaction/             # 交易處理邏輯
├── fep-security/                # 安全加密模組
├── fep-settlement/              # 對帳清算模組
├── fep-integration/             # 外部系統整合
├── fep-application/             # Spring Boot 主程式
└── fep-jmeter-plugin/           # JMeter 測試插件
```

### 4.2 模組功能說明

| 模組 | 說明 | 主要功能 |
|------|------|---------|
| **fep-common** | 共用工具 | 常數定義、工具類別、例外處理 |
| **fep-common-db** | 資料存取 | JPA 實體、Repository、資料庫操作 |
| **fep-message** | 電文處理 | ISO 8583 解析/組裝、Schema 管理 |
| **fep-communication** | 通訊管理 | FISC 連線、心跳檢測、重連機制 |
| **fep-transaction** | 交易處理 | 交易路由、限額控制、重複檢測 |
| **fep-security** | 安全模組 | HSM 整合、PIN Block、MAC 驗證 |
| **fep-settlement** | 對帳清算 | 日終對帳、清算檔案、差異帳處理 |
| **fep-integration** | 系統整合 | IBM MQ、REST API、核心系統介接 |
| **fep-application** | 主應用程式 | Spring Boot 入口、API 控制器 |
| **fep-jmeter-plugin** | 測試工具 | JMeter 插件、模擬器、壓力測試 |

### 4.3 模組依賴關係

```
fep-application (主程式)
├── fep-common
├── fep-common-db
├── fep-message
│   └── fep-common
├── fep-communication
│   ├── fep-common
│   └── fep-message
├── fep-transaction
│   ├── fep-common
│   ├── fep-message
│   └── fep-communication
├── fep-security
│   └── fep-common
├── fep-settlement
│   ├── fep-common
│   └── fep-common-db
└── fep-integration
    ├── fep-common
    └── fep-message
```

---

## 5. 建置與安裝

### 5.1 完整建置

```bash
# 建置所有模組 (含測試)
mvn clean install

# 建置所有模組 (跳過測試)
mvn clean install -DskipTests

# 建置並產生測試覆蓋率報告
mvn clean install
# 報告位置: target/site/jacoco/index.html
```

### 5.2 單一模組建置

```bash
# 建置特定模組
mvn clean install -pl fep-message

# 建置模組及其依賴
mvn clean install -pl fep-transaction -am

# 模組名稱:
# fep-common, fep-common-db, fep-message, fep-communication,
# fep-transaction, fep-security, fep-settlement, fep-integration,
# fep-application, fep-jmeter-plugin
```

### 5.3 建置產出物

```
各模組建置後產生:
├── fep-application/target/
│   └── fep-application-1.0.0-SNAPSHOT.jar     # 可執行 JAR
├── fep-jmeter-plugin/target/
│   └── fep-jmeter-plugin-1.0.0-SNAPSHOT-plugin.jar  # JMeter 插件
└── 各模組/target/
    └── *.jar                                   # 模組 JAR
```

### 5.4 常見建置問題

**問題 1: Maven 下載失敗**
```bash
# 清除本地快取
mvn dependency:purge-local-repository

# 強制更新依賴
mvn clean install -U
```

**問題 2: Java 版本不符**
```bash
# 確認 JAVA_HOME 設定
echo $JAVA_HOME

# macOS 設定
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Linux 設定
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
```

**問題 3: 測試失敗**
```bash
# 檢視測試報告
cat target/surefire-reports/*.txt

# 執行特定測試
mvn test -Dtest=ClassName#methodName
```

---

## 6. 系統配置

### 6.1 主要設定檔

| 檔案 | 位置 | 用途 |
|------|------|------|
| `application.yml` | `fep-application/src/main/resources/` | 主配置 |
| `application-dev.yml` | 同上 | 開發環境 |
| `application-prod.yml` | 同上 | 正式環境 |
| `application-oracle.yml` | 同上 | Oracle 資料庫 |
| `channel-schema-mapping.json` | `config/` | 通道 Schema 映射 |
| `atm-schemas.json` | `schemas/` | ATM 電文 Schema |

### 6.2 基本配置 (application.yml)

```yaml
server:
  port: 8080
  servlet:
    context-path: /api

spring:
  application:
    name: fep-system
  profiles:
    active: dev      # 可切換: dev, prod, oracle
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Taipei
```

### 6.3 FISC 連線配置

```yaml
fep:
  fisc:
    # 單通道模式
    host: localhost
    port: 9000
    connection-timeout: 10000    # 連線逾時 (ms)
    read-timeout: 30000          # 讀取逾時 (ms)
    heartbeat-interval: 30000    # 心跳間隔 (ms)
    max-connections: 5           # 最大連線數
    auto-reconnect: true         # 自動重連

    # 雙通道模式 (建議正式環境使用)
    dual-channel:
      enabled: true
      send-host: 192.168.1.100
      send-port: 9001            # 發送通道
      receive-host: 192.168.1.100
      receive-port: 9002         # 接收通道
      health-check-interval: 10000
```

### 6.4 Channel Schema 配置

```yaml
fep:
  channel:
    enabled: true
    config-file: config/channel-schema-mapping.json
    hot-reload: true             # 支援熱更新
    fail-on-missing-config: true # 缺少設定時失敗
```

### 6.5 安全模組配置

```yaml
fep:
  security:
    pin-block-format: FORMAT_0   # PIN Block 格式
    mac-algorithm: ANSI_X9_19    # MAC 演算法

  hsm:
    type: SOFTWARE               # 開發: SOFTWARE, 正式: THALES
    host: localhost
    port: 1500
    # 正式環境設定
    # type: THALES
    # host: ${HSM_HOST}
    # port: ${HSM_PORT}
```

### 6.6 交易處理配置

```yaml
fep:
  transaction:
    default-timeout: 30000       # 預設逾時 (ms)
    max-retry-count: 3           # 最大重試次數
    retry-interval: 1000         # 重試間隔 (ms)

  rate-limit:
    enabled: true
    max-tps: 2000                # 最大 TPS
    algorithm: TOKEN_BUCKET      # 限流演算法
```

### 6.7 環境變數覆蓋

```bash
# 使用環境變數覆蓋設定
export FISC_HOST=192.168.1.100
export FISC_PORT=9000
export FISC_DUAL_CHANNEL_ENABLED=true
export FISC_SEND_PORT=9001
export FISC_RECV_PORT=9002
export HSM_HOST=192.168.1.200
export HSM_PORT=1500

# 啟動應用程式
java -jar fep-application.jar
```

---

## 7. 啟動與運行

### 7.1 開發模式啟動

```bash
# 方式 1: Maven 啟動
mvn spring-boot:run -pl fep-application

# 方式 2: Maven 啟動 (指定 Profile)
mvn spring-boot:run -pl fep-application \
  -Dspring-boot.run.profiles=dev

# 方式 3: JAR 啟動
java -jar fep-application/target/fep-application-1.0.0-SNAPSHOT.jar
```

### 7.2 正式環境啟動

```bash
# 設定環境變數
export SPRING_PROFILES_ACTIVE=prod
export FISC_HOST=fisc.production.local
export FISC_PORT=9000
export FISC_DUAL_CHANNEL_ENABLED=true
export FISC_SEND_HOST=fisc.production.local
export FISC_SEND_PORT=9001
export FISC_RECV_HOST=fisc.production.local
export FISC_RECV_PORT=9002
export ORACLE_HOST=db.production.local
export ORACLE_PORT=1521
export ORACLE_SERVICE=FEPPROD
export ORACLE_USER=fep_user
export ORACLE_PASSWORD=<密碼>
export HSM_HOST=hsm.production.local
export HSM_PORT=1500

# 啟動 (建議使用 JVM 調優參數)
java -Xms4g -Xmx8g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -jar fep-application.jar
```

### 7.3 啟動參數說明

| 參數 | 說明 | 預設值 |
|------|------|--------|
| `-Xms` | 初始堆積記憶體 | 256m |
| `-Xmx` | 最大堆積記憶體 | 512m |
| `-XX:+UseG1GC` | 使用 G1 垃圾收集器 | - |
| `--server.port` | 服務埠號 | 8080 |
| `--spring.profiles.active` | 啟用的 Profile | dev |

### 7.4 啟動順序 (完整測試環境)

```
完整測試環境啟動順序:

1. 啟動 Oracle 資料庫
   cd docker && docker-compose up -d

2. 啟動 JMeter FISC 模擬器 (如需要)
   jmeter -n -t FISC_Simulator.jmx

3. 啟動 FEP 應用程式
   mvn spring-boot:run -pl fep-application

4. 啟動 JMeter 負載測試 (如需要)
   jmeter -n -t Performance_Test.jmx
```

### 7.5 停止應用程式

```bash
# 方式 1: Ctrl+C (前景執行時)

# 方式 2: 發送 SIGTERM
kill -15 <PID>

# 方式 3: 呼叫 Actuator shutdown 端點 (需啟用)
curl -X POST http://localhost:8080/api/actuator/shutdown
```

---

## 8. 核心功能模組

### 8.1 電文處理模組 (fep-message)

#### 8.1.1 支援的 MTI 類型

| MTI | 類型 | 說明 |
|-----|------|------|
| 0100 | Authorization Request | 授權請求 |
| 0110 | Authorization Response | 授權回應 |
| 0200 | Financial Request | 金融交易請求 |
| 0210 | Financial Response | 金融交易回應 |
| 0400 | Reversal Request | 沖正請求 |
| 0410 | Reversal Response | 沖正回應 |
| 0800 | Network Management | 網路管理 |
| 0810 | Network Management Response | 網路管理回應 |

#### 8.1.2 使用 ChannelMessageService

```java
@Autowired
private ChannelMessageService messageService;

// 解析電文
public void handleIncoming(String channelId, byte[] data) {
    String mti = extractMti(data);  // 取得 MTI

    // 使用 channelId + mti 自動選擇 Schema 並解析
    GenericMessage message = messageService.parseMessage(channelId, mti, data);

    // 取得欄位值
    String pan = message.getFieldAsString("pan");
    String amount = message.getFieldAsString("amount");

    // 取得通路屬性
    boolean macRequired = messageService.isMacRequired(channelId);
}

// 組裝電文
public byte[] buildResponse(String channelId, String mti) {
    GenericMessage message = messageService.createResponseMessage(channelId, mti);

    message.setField("mti", "0210");
    message.setField("responseCode", "00");
    message.setField("authCode", "ABC123");

    return messageService.assembleMessage(channelId, mti, message);
}
```

#### 8.1.3 Channel Schema 映射

```json
// config/channel-schema-mapping.json
{
  "$schema": "fep-channel-schema-mapping-v1",
  "version": "1.0.0",
  "channels": [
    {
      "id": "ATM_FISC_V1",
      "name": "FISC ATM Channel",
      "type": "ATM",
      "vendor": "FISC",
      "active": true,
      "defaultRequestSchema": "FISC ATM Schema",
      "defaultResponseSchema": "FISC ATM Schema",
      "properties": {
        "encoding": "ASCII",
        "macRequired": "true",
        "institutionId": "822"
      }
    }
  ]
}
```

### 8.2 通訊管理模組 (fep-communication)

#### 8.2.1 FISC 雙通道架構

```
FEP 系統                                FISC 系統
┌─────────────────┐                    ┌─────────────────┐
│                 │  Send Channel      │                 │
│  FiscDualChannel├───────────────────►│  Receive Port   │
│  Client         │  (Port 9001)       │  (接收請求)     │
│                 │                    │                 │
│                 │  Receive Channel   │                 │
│                 │◄───────────────────┤  Send Port      │
│                 │  (Port 9002)       │  (發送回應)     │
└─────────────────┘                    └─────────────────┘
```

#### 8.2.2 連線管理功能

| 功能 | 說明 |
|------|------|
| 連線池管理 | 可配置連線數量、自動重連 |
| 心跳檢測 | 定時發送 0800 電文 |
| 負載均衡 | 支援多條線路分流 |
| 故障恢復 | 自動切換備援連線 |

### 8.3 交易處理模組 (fep-transaction)

#### 8.3.1 支援的交易類型

| 類型 | Processing Code | 說明 |
|------|-----------------|------|
| 跨行提款 | 010000 | Interbank Withdrawal |
| 餘額查詢 | 310000 | Balance Inquiry |
| 跨行轉帳 | 400000 | Fund Transfer |
| 代收代付 | 500000 | Bill Payment |
| 沖正交易 | - | Reversal (MTI 0400) |

#### 8.3.2 交易處理流程

```
1. 接收請求 → 2. 電文解析 → 3. 交易驗證 → 4. 限額檢查
      ↓
5. 路由決策 → 6. 發送到 FISC/CBS → 7. 接收回應 → 8. 組裝回應
      ↓
9. 記錄日誌 → 10. 回傳結果
```

### 8.4 安全模組 (fep-security)

#### 8.4.1 HSM 支援

| 廠牌 | 類型設定 | 說明 |
|------|---------|------|
| Thales | THALES | 正式環境 |
| Utimaco | UTIMACO | 正式環境 |
| SafeNet | SAFENET | 正式環境 |
| 軟體模擬 | SOFTWARE | 開發環境 |

#### 8.4.2 金鑰層級

```
Master Key (MK) ← 儲存於 HSM
├── Zone Master Key (ZMK) ← 與財金交換用
├── Terminal Master Key (TMK) ← ATM/POS 用
└── Working Keys
    ├── PIN Encryption Key (PEK)
    ├── MAC Key (MAK)
    └── Data Encryption Key (DEK)
```

#### 8.4.3 安全功能

| 功能 | 說明 |
|------|------|
| PIN Block 加解密 | 支援 Format 0, Format 3 |
| PIN 轉換 | Format 0 ↔ Format 3 |
| MAC 計算 | ANSI X9.19 |
| MAC 驗證 | 驗證電文完整性 |

---

## 9. JMeter 測試工具

### 9.1 安裝 JMeter 插件

```bash
# 1. 建置插件
cd fep-jmeter-plugin
mvn clean package -DskipTests

# 2. 複製到 JMeter
cp target/fep-jmeter-plugin-1.0.0-SNAPSHOT-plugin.jar $JMETER_HOME/lib/ext/

# 3. 重新啟動 JMeter
```

### 9.2 可用的 JMeter 組件

#### 9.2.1 Sampler 組件

| 組件 | 用途 |
|------|------|
| **AtmSimulatorSampler** | 模擬 ATM 發送交易 |
| **TemplateSampler** | 基於模板發送交易 |
| **FiscSampler** | 傳統 ISO 8583 交易 |
| **FiscDualChannelServerSampler** | 模擬 FISC 伺服器 |
| **BankCoreServerSampler** | 模擬銀行核心系統 |

#### 9.2.2 Config 組件

| 組件 | 用途 |
|------|------|
| **SchemaConfigElement** | 配置電文 Schema |
| **TransactionTemplateConfig** | 配置交易模板 |
| **FiscConnectionConfig** | 配置 FISC 連線參數 |

#### 9.2.3 Assertion 組件

| 組件 | 用途 |
|------|------|
| **GenericMessageAssertion** | 驗證回應電文欄位 |

### 9.3 FISC 模擬器使用

#### 9.3.1 基本設定

```
Test Plan
└── Thread Group
    └── FISC Dual-Channel Server Sampler
        ├── Send Port: 9001 (接收請求)
        ├── Receive Port: 9002 (發送回應)
        ├── Operation Mode: PASSIVE
        └── Default Response Code: 00
```

#### 9.3.2 回應規則配置

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

### 9.4 交易模板使用

#### 9.4.1 內建模板

| 模板名稱 | MTI | 說明 |
|---------|-----|------|
| Withdrawal | 0200 | 跨行提款 |
| Balance Inquiry | 0200 | 餘額查詢 |
| Fund Transfer | 0200 | 跨行轉帳 |
| Bill Payment | 0200 | 繳費 |
| Sign On | 0800 | 簽入 |
| Sign Off | 0800 | 簽出 |
| Echo Test | 0800 | 連線測試 |
| Reversal | 0400 | 沖正 |

#### 9.4.2 模板變數

| 變數 | 說明 |
|------|------|
| `${stan}` | 自動產生交易序號 |
| `${time}` | 時間 (HHmmss) |
| `${date}` | 日期 (MMdd) |
| `${datetime}` | 日期時間 (MMddHHmmss) |
| `${amount}` | 交易金額 |
| `${cardNumber}` | 卡號 |
| `${terminalId}` | 終端機代號 |

### 9.5 執行測試

```bash
# GUI 模式 (開發用)
jmeter -t tests/functional-test.jmx

# CLI 模式 (效能測試)
jmeter -n \
  -t tests/performance-test.jmx \
  -l results/result.jtl \
  -e -o report/

# 分散式測試
jmeter -n \
  -t tests/performance-test.jmx \
  -R load1.internal,load2.internal \
  -l results/distributed.jtl
```

---

## 10. API 參考

### 10.1 健康檢查端點

```bash
# 基本健康檢查
GET /api/actuator/health

# 詳細健康資訊
GET /api/actuator/health/liveness
GET /api/actuator/health/readiness

# 應用程式資訊
GET /api/actuator/info
```

### 10.2 指標端點

```bash
# 所有指標
GET /api/actuator/metrics

# 特定指標
GET /api/actuator/metrics/jvm.memory.used
GET /api/actuator/metrics/http.server.requests
GET /api/actuator/metrics/fep.transactions.count

# Prometheus 格式
GET /api/actuator/prometheus
```

### 10.3 API 文件

```
Swagger UI: http://localhost:8080/api/swagger-ui.html
OpenAPI JSON: http://localhost:8080/api/v3/api-docs
```

### 10.4 回應碼對照表

| 代碼 | 說明 |
|------|------|
| 00 | 成功/核准 |
| 05 | 拒絕交易 |
| 12 | 無效交易 |
| 14 | 無效卡號 |
| 51 | 餘額不足 |
| 54 | 卡片過期 |
| 55 | PIN 錯誤 |
| 57 | 交易不允許 |
| 91 | 發卡行無法連線 |
| 96 | 系統異常 |

---

## 11. 資料庫設定

### 11.1 Docker Oracle 環境

```bash
# 啟動 Oracle
cd docker
docker-compose up -d

# 連線資訊
Host: localhost
Port: 1521
Service: FREEPDB1
User: fep_user
Password: fep_password

# 查看日誌
docker-compose logs -f oracle

# 停止並清除資料
docker-compose down -v
```

### 11.2 資料表結構

| 資料表 | 說明 |
|--------|------|
| `fep_transaction` | 交易記錄 |
| `fep_scheduled_transfer` | 預約轉帳 |
| `fep_settlement_file` | 清算檔案 |
| `fep_settlement_record` | 清算記錄 |
| `fep_discrepancy` | 差異帳 |
| `fep_clearing_record` | 清分記錄 |

### 11.3 連線池配置

```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 20           # 最小閒置連線
      maximum-pool-size: 50      # 最大連線數
      connection-timeout: 10000  # 連線逾時
      idle-timeout: 300000       # 閒置逾時
      max-lifetime: 1200000      # 連線最大存活時間
```

---

## 12. 監控與維運

### 12.1 日誌配置

```yaml
logging:
  level:
    root: INFO
    com.fep: DEBUG
    com.fep.communication: TRACE  # 詳細通訊日誌
  file:
    name: logs/fep-application.log
    max-size: 100MB
    max-history: 30
```

### 12.2 日誌檔案位置

```
logs/
├── fep-application.log      # 主日誌
├── fep-application.log.1    # 歷史日誌
├── transaction.log          # 交易日誌
└── audit.log                # 稽核日誌
```

### 12.3 即時監控命令

```bash
# 監控應用程式日誌
tail -f logs/fep-application.log

# 過濾錯誤訊息
tail -f logs/fep-application.log | grep -E "(ERROR|WARN)"

# 監控交易量
watch -n 5 "curl -s localhost:8080/api/actuator/metrics/fep.transactions.count | jq"

# 監控記憶體使用
watch -n 5 "curl -s localhost:8080/api/actuator/metrics/jvm.memory.used | jq"
```

### 12.4 效能指標監控

| 指標 | 端點 | 說明 |
|------|------|------|
| TPS | `/actuator/metrics/fep.transactions.count` | 每秒交易數 |
| 回應時間 | `/actuator/metrics/fep.transactions.latency` | 交易延遲 |
| 錯誤率 | `/actuator/metrics/fep.transactions.errors` | 錯誤數量 |
| 連線數 | `/actuator/metrics/fep.connections.active` | 活躍連線 |

---

## 13. 常見問題

### 13.1 啟動問題

**Q: 應用程式無法啟動，顯示 Port 8080 已被使用**
```bash
# 找出佔用 8080 的程序
lsof -i :8080

# 終止該程序或更換埠號
java -jar fep-application.jar --server.port=8081
```

**Q: 資料庫連線失敗**
```bash
# 確認 Docker Oracle 是否正在運行
docker-compose ps

# 確認連線資訊
docker exec -it oracle-free sqlplus fep_user/fep_password@FREEPDB1
```

### 13.2 連線問題

**Q: 無法連線到 FISC**
- 檢查網路連通性: `telnet <host> <port>`
- 確認防火牆設定
- 檢查設定檔中的 host/port 是否正確
- 查看日誌: `grep "FISC" logs/fep-application.log`

**Q: 心跳檢測失敗**
- 檢查心跳間隔設定
- 確認 FISC 伺服器回應正常
- 查看連線狀態: `curl localhost:8080/api/actuator/health`

### 13.3 效能問題

**Q: TPS 無法達到目標**
- 檢查連線池設定是否足夠
- 確認資料庫連線數量
- 監控 CPU/記憶體使用率
- 檢查是否有慢查詢

**Q: 記憶體使用過高**
- 調整 JVM 堆積大小
- 檢查是否有記憶體洩漏
- 使用 `jmap` 分析堆積使用

### 13.4 測試問題

**Q: JMeter 插件無法載入**
- 確認 JAR 檔案已複製到 `$JMETER_HOME/lib/ext/`
- 檢查 JMeter 版本相容性 (需 5.6+)
- 重新啟動 JMeter

**Q: 模擬器無法啟動**
- 確認端口未被佔用
- 檢查防火牆設定
- 查看 JMeter 日誌

---

## 附錄

### A. 環境變數清單

| 變數名稱 | 說明 | 預設值 |
|---------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 啟用的 Profile | dev |
| `FISC_HOST` | FISC 主機 | localhost |
| `FISC_PORT` | FISC 埠號 | 9000 |
| `FISC_DUAL_CHANNEL_ENABLED` | 啟用雙通道 | false |
| `FISC_SEND_HOST` | 發送通道主機 | - |
| `FISC_SEND_PORT` | 發送通道埠號 | 9001 |
| `FISC_RECV_HOST` | 接收通道主機 | - |
| `FISC_RECV_PORT` | 接收通道埠號 | 9002 |
| `ORACLE_HOST` | Oracle 主機 | localhost |
| `ORACLE_PORT` | Oracle 埠號 | 1521 |
| `ORACLE_SERVICE` | Oracle 服務名稱 | FREEPDB1 |
| `ORACLE_USER` | Oracle 使用者 | fep_user |
| `ORACLE_PASSWORD` | Oracle 密碼 | - |
| `HSM_HOST` | HSM 主機 | localhost |
| `HSM_PORT` | HSM 埠號 | 1500 |

### B. Maven 命令速查

```bash
# 建置
mvn clean install                    # 完整建置
mvn clean install -DskipTests        # 跳過測試
mvn clean install -pl <module>       # 建置特定模組

# 測試
mvn test                             # 執行測試
mvn test -Dtest=ClassName            # 執行特定測試類別
mvn test -Dtest=ClassName#method     # 執行特定測試方法

# 執行
mvn spring-boot:run -pl fep-application
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 相依性
mvn dependency:tree                  # 顯示相依性樹
mvn dependency:analyze               # 分析相依性

# 報告
mvn site                             # 產生專案網站
mvn surefire-report:report           # 產生測試報告
```

### C. 版本資訊

| 項目 | 版本 |
|------|------|
| FEP System | 1.0.0-SNAPSHOT |
| Java | 21 |
| Spring Boot | 3.2.1 |
| Netty | 4.1.104.Final |
| Oracle JDBC | 23.3.0.23.09 |
| JMeter | 5.6.3 |
| JaCoCo | 0.8.11 |

---

*文件版本: 1.0.0*
*最後更新: 2026-01-17*
