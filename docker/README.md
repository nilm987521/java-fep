# FEP Oracle Docker 測試環境

## 快速啟動

```bash
cd docker
docker-compose up -d
```

## 連線資訊

| 項目 | 值 |
|-----|-----|
| Host | localhost |
| Port | 1521 |
| Service Name | FREEPDB1 |
| SYS Password | fep_password |
| App User | fep_user |
| App Password | fep_password |

## JDBC URL

```
jdbc:oracle:thin:@localhost:1521/FREEPDB1
```

## 常用指令

### 啟動容器
```bash
docker-compose up -d
```

### 查看日誌
```bash
docker-compose logs -f oracle
```

### 停止容器
```bash
docker-compose down
```

### 停止並刪除資料
```bash
docker-compose down -v
```

### 進入 SQL*Plus
```bash
docker exec -it fep-oracle sqlplus fep_user/fep_password@//localhost:1521/FREEPDB1
```

### 以 SYS 身份連線
```bash
docker exec -it fep-oracle sqlplus sys/fep_password@//localhost:1521/FREEPDB1 as sysdba
```

## 啟動 FEP 應用程式 (Oracle 模式)

```bash
# 設定環境變數
export ORACLE_HOST=localhost
export ORACLE_PORT=1521
export ORACLE_SERVICE=FREEPDB1
export ORACLE_USER=fep_user
export ORACLE_PASSWORD=fep_password

# 啟動應用程式
mvn spring-boot:run -pl fep-application -Dspring.profiles.active=oracle
```

## 注意事項

1. **首次啟動**：Oracle 容器首次啟動需要 2-3 分鐘初始化
2. **資料持久化**：資料存放在 Docker volume `oracle-data`
3. **記憶體需求**：建議至少 2GB RAM 給 Docker
4. **初始化腳本**：`init-scripts/` 目錄下的 SQL 會在首次啟動時自動執行

## 初始化腳本

| 檔案 | 說明 |
|------|------|
| `00_setup.sql` | 設定 fep_user 權限 |
| `01_create_fep_transaction.sql` | 建立 FEP_TRANSACTION 表 |
| `02_create_fep_scheduled_transfer.sql` | 建立 FEP_SCHEDULED_TRANSFER 表 |
| `03_create_fep_settlement_file.sql` | 建立 FEP_SETTLEMENT_FILE 表 |
| `04_create_fep_settlement_record.sql` | 建立 FEP_SETTLEMENT_RECORD 表 |
| `05_create_fep_discrepancy.sql` | 建立 FEP_DISCREPANCY 表 |
| `06_create_fep_clearing_record.sql` | 建立 FEP_CLEARING_RECORD 表 |

共建立 6 個表格和 27 個索引。

## 驗證連線

```bash
# 測試資料庫連線
docker exec -it fep-oracle sqlplus -s fep_user/fep_password@//localhost:1521/FREEPDB1 <<EOF
SELECT 'Connection OK' AS STATUS FROM DUAL;
SELECT table_name FROM user_tables ORDER BY table_name;
SELECT COUNT(*) AS index_count FROM user_indexes WHERE index_name LIKE 'IDX_%';
EOF
```

預期結果：6 個表格、27 個索引。
