# FEP JMeter Plugin

JMeter 外掛，用於測試 FISC ISO 8583 財金交易。

## 功能特色

- **FISC Connection Config** - 設定 FISC 連線參數
- **FISC ISO 8583 Sampler** - 發送 ISO 8583 交易電文

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

## JMeter 變數

執行後，以下變數會被設定：
- `FISC_MTI` - 回應 MTI
- `FISC_RESPONSE_CODE` - 回應碼
- `FISC_STAN` - 交易序號
- `FISC_RRN` - 調閱參考號碼
- `FISC_BALANCE` - 餘額（如有）

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
│   │   ├── FiscSampler.java          # 主要 Sampler
│   │   └── FiscSamplerBeanInfo.java  # GUI 設定
│   ├── config/
│   │   ├── FiscConfigElement.java    # 連線設定元件
│   │   └── FiscConfigElementBeanInfo.java
│   └── gui/
│       └── FiscSamplerGui.java       # 自訂 GUI
└── pom.xml
```

### 依賴模組
- `fep-common` - 共用工具
- `fep-message` - ISO 8583 電文處理
- `fep-communication` - TCP/IP 連線管理

## 範例檔案

在 `examples/` 目錄下提供以下範例：

| 檔案 | 說明 |
|------|------|
| `FISC_Test_Plan.jmx` | 完整測試計畫，包含各類交易和壓測 |
| `FISC_Parameterized_Test.jmx` | 使用 CSV 參數化的測試計畫 |
| `test_cards.csv` | 測試卡號資料檔案 |

### 使用範例

1. 複製範例檔案到 JMeter bin 目錄或指定完整路徑
2. 開啟 JMeter GUI
3. 載入 `.jmx` 測試計畫
4. 修改連線參數（FISC_HOST, FISC_PORT 等）
5. 執行測試

### 命令列執行

```bash
# 執行基本測試
jmeter -n -t FISC_Test_Plan.jmx -l results.jtl

# 執行參數化測試
jmeter -n -t FISC_Parameterized_Test.jmx -l results.jtl

# 產生 HTML 報告
jmeter -n -t FISC_Test_Plan.jmx -l results.jtl -e -o report/
```
