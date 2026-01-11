# FEP JMeter Plugin

JMeter 外掛，用於測試 FISC ISO 8583 財金交易。

## 功能特色

- **FISC Connection Config** - 設定 FISC 連線參數
- **FISC ISO 8583 Sampler** - 發送 ISO 8583 交易電文
- **FISC Dual-Channel Server Simulator** - 模擬財金雙連線伺服器（用於測試 FEP 客戶端）

### 財金雙連線架構

符合財金公司實際連線架構：
```
FEP Client                    FISC Simulator
----------                    --------------
Send Channel ──request──►     Send Port (9000)
                              │
                              ▼ (queue + handler)
                              │
Recv Channel ◄──response──    Receive Port (9001)
```

- **Send Port**: 接收客戶端的交易請求
- **Receive Port**: 發送交易回應給客戶端
- **非同步處理**: Request/Response 走不同 TCP 連線

## 支援的交易類型

| 交易類型 | MTI | 說明 |
|---------|-----|------|
| ECHO_TEST | 0800 | 網路測試 |
| SIGN_ON | 0800 | 簽入 |
| SIGN_OFF | 0800 | 簽退 |
| WITHDRAWAL | 0200 | 跨行提款 |
| TRANSFER | 0200 | 跨行轉帳 |
| BALANCE_INQUIRY | 0200 | 餘額查詢 |
| BILL_PAYMENT | 0200 | 代收代付 |

## 安裝方式

1. 建置外掛：
   ```bash
   cd fep-jmeter-plugin
   mvn clean package -DskipTests
   ```

2. 複製 JAR 檔案到 JMeter：
   ```bash
   cp target/fep-jmeter-plugin-1.0.0-SNAPSHOT-plugin.jar $JMETER_HOME/lib/ext/
   ```

3. 重新啟動 JMeter

## 使用方式

### 1. 新增 FISC Connection Config

在 Test Plan 或 Thread Group 下新增：
- **右鍵 > Add > Config Element > FISC Connection Config**

設定參數：
- **Configuration Name**: 設定名稱（用於識別）
- **Primary Host**: FISC 主機位址
- **Primary Port**: FISC 主機埠號
- **Institution ID**: 銀行代碼

### 2. 新增 FISC ISO 8583 Sampler

在 Thread Group 下新增：
- **右鍵 > Add > Sampler > FISC ISO 8583 Sampler**

設定參數：
- **Transaction Type**: 交易類型
- **PAN**: 卡號
- **Amount**: 金額（單位：分）
- **Custom Fields**: 自訂欄位（格式：field:value;field:value）

### 3. 範例設定

#### Echo Test
```
Transaction Type: ECHO_TEST
```

#### 跨行提款
```
Transaction Type: WITHDRAWAL
PAN: 4111111111111111
Amount: 10000 (NT$100.00)
Terminal ID: ATM00001
```

#### 跨行轉帳
```
Transaction Type: TRANSFER
PAN: 4111111111111111
Amount: 50000 (NT$500.00)
Custom Fields: 103:00000012345678901234  (轉入帳號)
```

### 4. 新增 FISC Dual-Channel Server Simulator

在 Thread Group 下新增：
- **右鍵 > Add > Sampler > FISC Dual-Channel Server Simulator**

這是模擬財金雙連線伺服器的 Sampler，可用於：
- 測試 FEP 客戶端雙連線功能
- 模擬各種回應碼場景
- 壓力測試和效能驗證
- 驗證電文欄位格式

#### 設定參數

**Server Settings:**
| 參數 | 說明 | 預設值 |
|------|------|--------|
| Send Port | 接收請求的埠號 | 9000 |
| Receive Port | 發送回應的埠號 | 9001 |
| Sample Interval | 統計取樣間隔（毫秒） | 1000 |

**Response Settings:**
| 參數 | 說明 | 預設值 |
|------|------|--------|
| Default Response Code | 預設回應碼 | 00 |
| Response Delay | 模擬處理延遲（毫秒） | 0 |
| Balance Amount | 餘額查詢回傳金額（分） | - |

**Validation Settings:**
| 參數 | 說明 | 預設值 |
|------|------|--------|
| Enable Validation | 啟用電文驗證 | true |
| Validation Error Code | 驗證失敗回傳碼 | 30 |
| Validation Rules | 驗證規則（見下方說明） | - |

**Routing Settings:**
| 參數 | 說明 | 預設值 |
|------|------|--------|
| Enable Bank ID Routing | 依銀行代碼路由 | true |
| Bank ID Field | 銀行代碼欄位 | 32 |

#### 驗證規則語法

```
REQUIRED:2,3,4,11,41              # 必填欄位
FORMAT:2=N(13-19);3=N(6)          # 欄位格式：N=數字，長度範圍
VALUE:3=010000|400000|310000      # 允許的值
LENGTH:4=12;11=6                  # 固定長度
PATTERN:37=^[A-Z0-9]{12}$         # 正規表達式
```

#### 回應規則語法

依 Processing Code (F3) 設定不同回應碼：
```
010000:00;400000:51;310000:00
```
- `010000:00` - 提款成功
- `400000:51` - 轉帳餘額不足
- `310000:00` - 餘額查詢成功

#### 範例設定

##### 基本伺服器（全部成功）
```
Send Port: 9000
Receive Port: 9001
Default Response Code: 00
Response Delay: 50
```

##### 模擬餘額查詢
```
Send Port: 9000
Receive Port: 9001
Default Response Code: 00
Balance Amount: 1000000 (NT$10,000.00)
```

##### 模擬轉帳餘額不足
```
Send Port: 9000
Receive Port: 9001
Default Response Code: 00
Response Rules: 010000:00;400000:51;310000:00
```

##### 自訂回應欄位
```
Custom Response Fields: 43:Test Merchant;49:901
```

### 5. 新增 Bank Core Dual-Channel Server Simulator

在 Thread Group 下新增：
- **右鍵 > Add > Sampler > Bank Core Dual-Channel Server Simulator**

這是模擬銀行核心系統雙連線伺服器的 Sampler，可用於：
- 測試 FEP 與核心系統整合
- 模擬各種回應碼場景
- 測試 FEP ID 路由功能
- 驗證電文欄位格式

#### 設定參數

**Server Settings:**
| 參數 | 說明 | 預設值 |
|------|------|--------|
| Send Port | 接收 FEP 請求的埠號 | 9100 |
| Receive Port | 發送回應給 FEP 的埠號 | 9101 |
| Sample Interval | 統計取樣間隔（毫秒） | 1000 |

**Response Settings:**
| 參數 | 說明 | 預設值 |
|------|------|--------|
| Default Response Code | 預設回應碼 | 00 |
| Response Delay | 模擬處理延遲（毫秒） | 0 |

**Balance Settings:**
| 參數 | 說明 | 預設值 |
|------|------|--------|
| Available Balance | 可用餘額（分） | 100000 |
| Ledger Balance | 帳面餘額（分） | 150000 |

**Validation Settings:**
| 參數 | 說明 | 預設值 |
|------|------|--------|
| Enable Validation | 啟用電文驗證 | false |
| Validation Error Code | 驗證失敗回傳碼 | 30 |
| Validation Rules | 驗證規則 | - |

**Routing Settings:**
| 參數 | 說明 | 預設值 |
|------|------|--------|
| Enable FEP ID Routing | 依 FEP ID 路由回應 | true |
| FEP ID Field | FEP ID 欄位 | 32 |

**Advanced Settings:**
| 參數 | 說明 |
|------|------|
| Response Rules (JSON) | JSON 格式的 MTI 回應規則 |
| Custom Response Fields | 自訂回應欄位 |

#### JSON 回應規則範例

```json
{
  "defaultResponseCode": "12",
  "handlers": [
    {
      "mti": "0200",
      "rules": [
        {"condition": {"field": 3, "value": "010000"}, "response": {"39": "00", "38": "AUTH01"}},
        {"condition": {"field": 3, "value": "400000"}, "response": {"39": "51"}},
        {"condition": "DEFAULT", "response": {"39": "00"}}
      ]
    },
    {
      "mti": "0800",
      "rules": [
        {"condition": {"field": 70, "value": "001"}, "response": {"39": "00"}},
        {"condition": "DEFAULT", "response": {"39": "00"}}
      ]
    }
  ]
}
```

### 6. Server Simulator 使用場景

#### 場景 A：作為獨立模擬伺服器
1. 建立一個 Thread Group，設定執行緒數為 1
2. 新增 FISC Dual-Channel Server Simulator
3. 設定 Loop Count 為 Forever（持續運行）
4. 啟動測試，伺服器即開始監聽雙 Port

#### 場景 B：整合測試
1. 建立 Thread Group 1：運行 FISC Dual-Channel Server Simulator
2. FEP 客戶端連線到：
   - Send Channel → localhost:9000
   - Receive Channel → localhost:9001
3. 進行端對端測試

## JMeter 變數

### FISC Sampler 變數
執行後，以下變數會被設定：
- `FISC_MTI` - 回應 MTI
- `FISC_RESPONSE_CODE` - 回應碼
- `FISC_STAN` - 交易序號
- `FISC_RRN` - 調閱參考號碼
- `FISC_BALANCE` - 餘額（如有）

### FISC Dual-Channel Server Simulator 變數
- `FISC_SEND_PORT` - Send Port 實際埠號
- `FISC_RECEIVE_PORT` - Receive Port 實際埠號
- `FISC_SEND_CLIENTS` - Send Channel 連線數
- `FISC_RECEIVE_CLIENTS` - Receive Channel 連線數
- `FISC_MESSAGES_RECEIVED` - 已接收訊息數
- `FISC_MESSAGES_SENT` - 已發送訊息數
- `FISC_VALIDATION_ERRORS` - 驗證失敗數
- `FISC_LAST_REQUEST_MTI` - 最後一筆請求的 MTI
- `FISC_LAST_REQUEST_STAN` - 最後一筆請求的 STAN
- `FISC_LAST_VALIDATION_RESULT` - 最後一筆驗證結果

## 回應碼說明

| 回應碼 | 說明 |
|--------|------|
| 00 | 交易成功 |
| 05 | 不予承作 |
| 12 | 無效交易 |
| 14 | 無效卡號 |
| 51 | 餘額不足 |
| 55 | 密碼錯誤 |
| 91 | 發卡行無法連線 |

## 開發者資訊

### 模組結構
```
fep-jmeter-plugin/
├── src/main/java/com/fep/jmeter/
│   ├── sampler/
│   │   ├── FiscSampler.java              # FISC 客戶端 Sampler
│   │   ├── FiscSamplerBeanInfo.java      # 客戶端 GUI 設定
│   │   ├── FiscServerSampler.java        # FISC 伺服器模擬 Sampler
│   │   └── FiscServerSamplerBeanInfo.java # 伺服器 GUI 設定
│   ├── config/
│   │   ├── FiscConfigElement.java        # 連線設定元件
│   │   └── FiscConfigElementBeanInfo.java
│   └── gui/
│       └── FiscSamplerGui.java           # 自訂 GUI
└── pom.xml
```

### 依賴模組
- `fep-common` - 共用工具
- `fep-message` - ISO 8583 電文處理
- `fep-communication` - TCP/IP 連線管理

## 範例檔案

在 `examples/` 目錄下提供以下範例：

### FISC 財金模擬器

| 檔案 | 說明 |
|------|------|
| `FISC_DualChannel_Server_Simulator.jmx` | 雙連線伺服器模擬基本範本 |
| `FISC_DualChannel_Test_Scenarios.jmx` | 多場景測試（成功/餘額不足/逾時/驗證） |
| `FISC_MTI_Response_Rules_Demo.jmx` | MTI 回應規則引擎示範 |
| `FISC_Test_Plan.jmx` | 完整測試計畫，包含各類交易和壓測 |
| `FISC_Parameterized_Test.jmx` | 使用 CSV 參數化的測試計畫 |
| `test_cards.csv` | 測試卡號資料檔案 |

### BankCore 銀行核心系統模擬器

| 檔案 | 說明 |
|------|------|
| `BankCore_Server_Simulator.jmx` | 銀行核心系統雙連線伺服器基本範本 |
| `BankCore_Test_Scenarios.jmx` | 多場景測試（基本、驗證、路由、規則、餘額不足） |

### ATM 模擬器

| 檔案 | 說明 |
|------|------|
| `ATM_Simulator_Test.jmx` | ATM 模擬器測試（提款、轉帳、餘額查詢、自訂範本） |

### 交易範本 (Transaction Template)

| 檔案 | 說明 |
|------|------|
| `Transaction_Template_Test.jmx` | 交易範本功能示範（COMMON/INLINE/FILE 三種來源） |

### 雙連線模擬範例

#### 場景 1: 全部成功
- 所有交易回傳 `00`
- 適用於正向測試

#### 場景 2: 轉帳餘額不足
- 提款回傳 `00`
- 轉帳回傳 `51` (餘額不足)
- 適用於錯誤處理測試

#### 場景 3: 高延遲回應
- 回應延遲 3 秒
- 適用於逾時處理測試

#### 場景 4: 系統故障
- 所有交易回傳 `96` (系統故障)
- 適用於異常處理測試

#### 場景 5: 嚴格欄位驗證
- 完整欄位格式驗證
- 適用於電文正確性測試

### 使用範例

1. 複製範例檔案到 JMeter bin 目錄或指定完整路徑
2. 開啟 JMeter GUI
3. 載入 `.jmx` 測試計畫
4. 修改連線參數（FISC_HOST, FISC_PORT 等）
5. 執行測試

### 命令列執行

```bash
# 執行雙連線伺服器模擬
jmeter -n -t FISC_DualChannel_Server_Simulator.jmx -l results.jtl

# 執行多場景測試
jmeter -n -t FISC_DualChannel_Test_Scenarios.jmx -l results.jtl

# 執行基本測試
jmeter -n -t FISC_Test_Plan.jmx -l results.jtl

# 產生 HTML 報告
jmeter -n -t FISC_Test_Plan.jmx -l results.jtl -e -o report/
```

## 交易範本 (Transaction Template)

TransactionTemplate 提供彈性的 ISO 8583 電文配置機制，允許自訂交易範本。

### 功能特色

- **任意欄位配置** - 支援 ISO 8583 所有欄位 (1-128)
- **變數替換** - 使用 `${variable}` 語法替換變數
- **自動欄位生成** - 自動產生 STAN、時間戳記
- **多種來源** - COMMON (預設)、FILE (JSON檔案)、INLINE (內嵌JSON)
- **JMeter 整合** - 支援 JMeter 變數

### 預設範本 (CommonTemplates)

| 範本名稱 | MTI | 說明 |
|---------|-----|------|
| Withdrawal | 0200 | 提款交易 |
| Balance Inquiry | 0200 | 餘額查詢 |
| Fund Transfer | 0200 | 轉帳交易 |
| Bill Payment | 0200 | 繳費交易 |
| Sign On | 0800 | 簽到 |
| Sign Off | 0800 | 簽退 |
| Echo Test | 0800 | 連線測試 |
| Reversal | 0400 | 沖正交易 |

### JSON 範本格式

```json
{
  "name": "Custom Withdrawal",
  "mti": "0200",
  "description": "自訂提款交易",
  "fields": {
    "3": "010000",
    "4": "${amount}",
    "11": "${stan}",
    "12": "${time}",
    "13": "${date}",
    "22": "051",
    "41": "${terminalId}"
  },
  "validationRules": "REQUIRED:2,3,4,11,41",
  "enabled": true,
  "priority": 10
}
```

### 支援的變數

| 變數名 | 說明 | 來源 |
|--------|------|------|
| `${stan}` | System Trace Audit Number | 自動生成 |
| `${time}` | 時間 (HHmmss) | 自動生成 |
| `${date}` | 日期 (MMdd) | 自動生成 |
| `${datetime}` | 日期時間 (MMddHHmmss) | 自動生成 |
| `${amount}` | 交易金額 | Sampler 屬性 |
| `${cardNumber}` | 卡號 | Sampler 屬性 |
| `${terminalId}` | 終端機 ID | Sampler 屬性 |
| `${bankCode}` | 銀行代碼 | Sampler 屬性 |
| `${sourceAccount}` | 來源帳號 | Sampler 屬性 |
| `${destAccount}` | 目的帳號 | Sampler 屬性 |

### 使用方式

#### 1. 新增 Transaction Template Config

在 Test Plan 或 Thread Group 下新增：
- **右鍵 > Add > Config Element > Transaction Template Config**

設定參數：
- **Template Source**: COMMON / FILE / INLINE
- **Template File**: JSON 檔案路徑 (FILE 模式)
- **Inline Templates**: 內嵌 JSON (INLINE 模式)
- **Include Common Templates**: 是否包含預設範本
- **Auto-generate STAN**: 自動產生 STAN
- **Auto-generate Timestamp**: 自動產生時間戳記

#### 2. 新增 Template Sampler

在 Thread Group 下新增：
- **右鍵 > Add > Sampler > Template Sampler**

設定參數：
- **Target Host/Port**: 目標伺服器位址
- **Template Name**: 範本名稱
- **MTI Override**: 覆寫 MTI (選填)
- **Amount/Card Number/Terminal ID/Bank Code**: 變數值
- **Custom Fields**: 額外欄位 (格式: field:value;field:value)

#### 3. 範例

##### 使用 COMMON 範本
```
Template Source: COMMON
Template Name: Withdrawal
Amount: 100000
Card Number: 4111111111111111
Terminal ID: ATM00001
```

##### 使用 INLINE 自訂範本
```
Template Source: INLINE
Inline Templates: [{"name": "Custom", "mti": "0100", "fields": {"3": "000000"}}]
Template Name: Custom
```

##### 使用 FILE 外部範本
```
Template Source: FILE
Template File: /path/to/templates.json
Template Name: MyTemplate
```

### Template Sampler 變數

執行後，以下變數會被設定：
- `TPL_TEMPLATE` - 使用的範本名稱
- `TPL_MTI` - 回應 MTI
- `TPL_RESPONSE_CODE` - 回應碼
- `TPL_STAN` - 交易序號
- `TPL_RRN` - 調閱參考號碼
- `TPL_AUTH_CODE` - 授權碼
- `TPL_BALANCE` - 餘額（如有）
