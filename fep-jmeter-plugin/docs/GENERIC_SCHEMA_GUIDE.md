# GENERIC_SCHEMA 模式使用教學

## 概述

GENERIC_SCHEMA 模式讓 ATM 模擬器可以發送任意格式的電文，不再受限於 ISO 8583。適用於：

- NCR NDC 協定
- Diebold 91x 協定
- Wincor DDC 協定
- 台灣財金 ATM 格式
- 任何自訂電文格式

## 快速開始

### 1. 選擇協定類型

在 JMeter GUI 中，將 **Protocol Type** 設為 `GENERIC_SCHEMA`。

### 2. 選擇 Schema 來源

| 來源 | 說明 | 使用場景 |
|------|------|----------|
| `PRESET` | 使用內建預設 Schema | 快速測試常見協定 |
| `FILE` | 從檔案載入 Schema | 團隊共用 Schema |
| `INLINE` | 直接在 UI 輸入 JSON | 開發測試、快速原型 |

### 3. 設定欄位值

使用 JSON 格式指定各欄位的值：

```json
{
  "commandCode": "11",
  "terminalId": "${ATM_ID}",
  "amount": "000000100000",
  "pinBlock": "0411123456789ABC"
}
```

---

## Schema 來源詳解

### PRESET 模式

選擇內建的預設 Schema：

| Preset | 協定 | 說明 |
|--------|------|------|
| `NCR_NDC` | NCR NDC | NCR ATM 標準協定 |
| `DIEBOLD_91X` | Diebold 91x | Diebold ATM 協定 |
| `WINCOR_DDC` | Wincor DDC | Wincor Nixdorf DDC 協定 |
| `FISC_ATM` | 財金 ATM | 台灣財金公司 ATM 格式 |
| `ISO8583_GENERIC` | ISO 8583 | 通用 ISO 8583 格式 |

**範例配置：**
```
Protocol Type: GENERIC_SCHEMA
Schema Source: PRESET
Preset Schema: NCR_NDC
```

### FILE 模式

從外部 JSON 檔案載入 Schema：

```
Protocol Type: GENERIC_SCHEMA
Schema Source: FILE
Schema File: /path/to/my-protocol.json
```

### INLINE 模式

直接在 UI 輸入完整 Schema JSON：

```
Protocol Type: GENERIC_SCHEMA
Schema Source: INLINE
Schema Content: { "name": "My Protocol", "fields": [...] }
```

---

## Schema JSON 格式規範

### 基本結構

```json
{
  "name": "Protocol Name",
  "version": "1.0",
  "vendor": "Vendor Name",
  "description": "Protocol description",

  "header": { ... },
  "fields": [ ... ],
  "trailer": { ... },

  "encoding": { ... },
  "validation": { ... }
}
```

### Header 定義

```json
{
  "header": {
    "includeLength": true,
    "lengthBytes": 2,
    "lengthEncoding": "BINARY",
    "lengthIncludesHeader": false,
    "fields": [
      {
        "id": "protocolId",
        "length": 2,
        "encoding": "HEX",
        "defaultValue": "6000"
      }
    ]
  }
}
```

| 屬性 | 說明 |
|------|------|
| `includeLength` | 是否包含長度前綴 |
| `lengthBytes` | 長度欄位的位元組數 |
| `lengthEncoding` | 長度編碼：BINARY / BCD / ASCII |
| `lengthIncludesHeader` | 長度是否包含 header 本身 |

### 欄位定義

```json
{
  "fields": [
    {
      "id": "commandCode",
      "name": "Command Code",
      "description": "Transaction command identifier",
      "type": "ALPHANUMERIC",
      "length": 2,
      "encoding": "ASCII",
      "required": true,
      "defaultValue": "11"
    },
    {
      "id": "amount",
      "name": "Amount",
      "type": "NUMERIC",
      "length": 12,
      "encoding": "BCD",
      "padding": {
        "char": "0",
        "direction": "left"
      }
    },
    {
      "id": "track2",
      "name": "Track 2 Data",
      "length": 37,
      "lengthType": "LLVAR",
      "encoding": "ASCII",
      "lengthEncoding": "ASCII",
      "sensitive": true
    }
  ]
}
```

### 欄位屬性說明

| 屬性 | 必填 | 說明 |
|------|------|------|
| `id` | Y | 欄位識別碼，用於 fieldValues 對應 |
| `name` | N | 顯示名稱 |
| `description` | N | 欄位說明 |
| `type` | N | 資料類型：ALPHANUMERIC / NUMERIC / BINARY / COMPOSITE |
| `length` | Y | 欄位長度（字元數或位元組數） |
| `encoding` | N | 編碼方式，預設 ASCII |
| `lengthType` | N | 變動長度類型：LLVAR / LLLVAR / LLLLVAR |
| `lengthEncoding` | N | 長度前綴編碼，預設同 encoding |
| `required` | N | 是否必填 |
| `defaultValue` | N | 預設值，支援變數替換 |
| `sensitive` | N | 是否敏感資料（log 時會遮罩） |
| `padding` | N | 填補設定 |

### 支援的編碼類型

| 編碼 | 說明 | 範例 |
|------|------|------|
| `ASCII` | 標準 ASCII | "ABC" → 0x41 0x42 0x43 |
| `BCD` | Binary Coded Decimal | "1234" → 0x12 0x34 |
| `HEX` | 十六進位 | "DEADBEEF" → 0xDE 0xAD 0xBE 0xEF |
| `BINARY` | 原始二進位 | 直接複製位元組 |
| `EBCDIC` | 大型主機字元集 | "ABC" → 0xC1 0xC2 0xC3 |
| `PACKED_DECIMAL` | 壓縮十進位（含符號） | "+123" → 0x01 0x23 0x0C |

### 變動長度類型

| 類型 | 長度前綴位數 | 最大長度 |
|------|-------------|----------|
| `LLVAR` | 2 位 | 99 |
| `LLLVAR` | 3 位 | 999 |
| `LLLLVAR` | 4 位 | 9999 |

### 巢狀欄位（Composite）

```json
{
  "id": "deviceStatus",
  "name": "Device Status",
  "type": "COMPOSITE",
  "fields": [
    { "id": "cardReader", "length": 1, "encoding": "ASCII" },
    { "id": "dispenser", "length": 1, "encoding": "ASCII" },
    { "id": "printer", "length": 1, "encoding": "ASCII" }
  ]
}
```

設定巢狀欄位值：
```json
{
  "deviceStatus.cardReader": "0",
  "deviceStatus.dispenser": "0",
  "deviceStatus.printer": "1"
}
```

---

## 欄位值設定

### 基本格式

```json
{
  "fieldId1": "value1",
  "fieldId2": "value2"
}
```

### 使用 JMeter 變數

支援 `${variableName}` 語法：

```json
{
  "terminalId": "${ATM_ID}",
  "cardNumber": "${CARD_NUMBER}",
  "amount": "${__Random(100000,999999)}"
}
```

### 內建變數

| 變數 | 說明 |
|------|------|
| `${stan}` | 自動遞增的交易序號 (STAN) |
| `${rrn}` | 自動產生的 RRN |
| `${date}` | 當前日期 (MMdd) |
| `${time}` | 當前時間 (HHmmss) |
| `${datetime}` | 當前日期時間 (MMddHHmmss) |

---

## 完整範例

### 範例 1：NCR NDC 提款交易

**Schema Source:** PRESET
**Preset Schema:** NCR_NDC

**Field Values:**
```json
{
  "commandCode": "11",
  "responseFlag": " ",
  "luno": "ATM00001",
  "statusInfo": "0",
  "track2": "4111111111111111=2512101123400001",
  "pinBlock": "0411123456789ABC",
  "amount": "000000100000",
  "transactionSerialNumber": "0001",
  "functionId": "001"
}
```

### 範例 2：財金 ATM 跨行提款

**Schema Source:** PRESET
**Preset Schema:** FISC_ATM

**Field Values:**
```json
{
  "mti": "0200",
  "pan": "4111111111111111",
  "processingCode": "010000",
  "amount": "000000100000",
  "stan": "${stan}",
  "localTime": "${time}",
  "localDate": "${date}",
  "terminalId": "ATM00001",
  "acquiringInstitution": "012",
  "rrn": "${rrn}",
  "currencyCode": "901",
  "pinBlock": "0411123456789ABC"
}
```

### 範例 3：自訂簡易協定

**Schema Source:** INLINE

**Schema Content:**
```json
{
  "name": "Simple ATM Protocol",
  "version": "1.0",
  "header": {
    "includeLength": true,
    "lengthBytes": 2,
    "lengthEncoding": "BINARY",
    "lengthIncludesHeader": false
  },
  "fields": [
    { "id": "msgType", "length": 4, "encoding": "ASCII", "required": true },
    { "id": "termId", "length": 8, "encoding": "ASCII" },
    { "id": "txnCode", "length": 2, "encoding": "ASCII" },
    { "id": "cardNo", "length": 16, "encoding": "BCD", "sensitive": true },
    { "id": "amount", "length": 12, "encoding": "BCD" },
    { "id": "pin", "length": 8, "encoding": "HEX", "sensitive": true }
  ]
}
```

**Field Values:**
```json
{
  "msgType": "0200",
  "termId": "ATM00001",
  "txnCode": "01",
  "cardNo": "4111111111111111",
  "amount": "000000100000",
  "pin": "0411123456789ABC"
}
```

---

## 疑難排解

### 常見錯誤

| 錯誤訊息 | 原因 | 解決方案 |
|----------|------|----------|
| `Schema not found` | PRESET/FILE 找不到 | 確認 preset 名稱或檔案路徑 |
| `Invalid JSON` | JSON 格式錯誤 | 使用 JSON 驗證工具檢查 |
| `Field 'xxx' is required` | 必填欄位未設定 | 在 fieldValues 中加入該欄位 |
| `Unknown encoding: XXX` | 不支援的編碼 | 使用支援的編碼類型 |

### 除錯技巧

1. **檢視組裝結果**：在 View Results Tree 中查看 Request Data (hex)
2. **驗證 Schema**：先用 INLINE 模式測試，確認後再存成檔案
3. **逐步測試**：先測試少量欄位，再逐步加入更多欄位

---

## 建立自訂 Schema 檔案

1. 建立 JSON 檔案，例如 `my-protocol.json`
2. 定義 header、fields、trailer
3. 在 JMeter 中設定：
   - Schema Source: `FILE`
   - Schema File: `/path/to/my-protocol.json`
4. 測試並調整

### Schema 檔案範本

```json
{
  "$schema": "fep-message-schema-v1",
  "name": "My Custom Protocol",
  "version": "1.0",
  "vendor": "My Company",
  "description": "Custom ATM protocol for testing",

  "header": {
    "includeLength": true,
    "lengthBytes": 2,
    "lengthEncoding": "BINARY",
    "lengthIncludesHeader": false
  },

  "fields": [
    {
      "id": "messageType",
      "name": "Message Type",
      "length": 4,
      "encoding": "ASCII",
      "required": true
    }
  ],

  "encoding": {
    "defaultCharset": "ASCII",
    "endianness": "BIG_ENDIAN"
  }
}
```

---

## 參考資源

- [NCR NDC Protocol Reference](https://www.ncr.com/)
- [ISO 8583 Standard](https://en.wikipedia.org/wiki/ISO_8583)
- [FISC 財金公司](https://www.fisc.com.tw/)

## 預設 Schema 檔案位置

內建 Schema 位於：
```
fep-jmeter-plugin/src/main/resources/schemas/
├── ncr-ndc-v1.json
├── fisc-atm-v1.json
├── diebold-91x-v1.json
├── wincor-ddc-v1.json
└── iso8583-generic.json
```
