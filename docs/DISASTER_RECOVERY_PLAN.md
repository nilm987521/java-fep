# FEP System 災難復原計畫 (DRP)

> 版本: 1.0.0
> 建立日期: 2026-01-08
> 文件狀態: 正式版
> 分類等級: 機密

---

## 1. 文件目的

本文件定義 FEP (Front-End Processor) 系統在遭遇災難事件時的復原程序，確保業務持續運作並將損失降至最低。

---

## 2. 復原目標

### 2.1 RTO/RPO 定義

| 指標 | 目標值 | 說明 |
|-----|--------|------|
| **RTO** (Recovery Time Objective) | 15 分鐘 | 系統復原所需最大時間 |
| **RPO** (Recovery Point Objective) | 0 分鐘 | 可接受的最大資料遺失時間 |
| **MTTR** (Mean Time To Repair) | 30 分鐘 | 平均修復時間 |

### 2.2 服務等級
| 等級 | 說明 | RTO |
|-----|------|-----|
| P1 | 完全服務中斷 | 15 分鐘 |
| P2 | 部分服務降級 | 30 分鐘 |
| P3 | 非關鍵功能異常 | 4 小時 |

---

## 3. 災難類型與應對

### 3.1 災難類型分類

| 類型 | 說明 | 發生機率 | 影響程度 |
|-----|------|---------|---------|
| 硬體故障 | 伺服器、儲存設備故障 | 中 | 高 |
| 網路中斷 | 網路設備故障、線路中斷 | 中 | 高 |
| 軟體異常 | 應用程式當機、記憶體洩漏 | 高 | 中 |
| 資料庫故障 | 資料庫當機、資料毀損 | 低 | 極高 |
| 資安事件 | 駭客攻擊、惡意程式 | 中 | 極高 |
| 自然災害 | 地震、水災、火災 | 低 | 極高 |
| 人為錯誤 | 誤操作、設定錯誤 | 高 | 中 |

### 3.2 高可用架構

```
┌─────────────────────────────────────────────────────────────────────┐
│                        主資料中心 (台北)                             │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐          │
│  │  FEP Node 1   │  │  FEP Node 2   │  │  FEP Node 3   │          │
│  │  (Active)     │  │  (Active)     │  │  (Active)     │          │
│  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘          │
│          └──────────────────┼──────────────────┘                   │
│                             │                                       │
│                    ┌────────▼────────┐                             │
│                    │  Oracle RAC     │                             │
│                    │  (Primary)      │                             │
│                    └────────┬────────┘                             │
└─────────────────────────────┼───────────────────────────────────────┘
                              │ Data Guard (同步複寫)
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        備援資料中心 (高雄)                           │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐          │
│  │  FEP Node 4   │  │  FEP Node 5   │  │  FEP Node 6   │          │
│  │  (Standby)    │  │  (Standby)    │  │  (Standby)    │          │
│  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘          │
│          └──────────────────┼──────────────────┘                   │
│                             │                                       │
│                    ┌────────▼────────┐                             │
│                    │  Oracle RAC     │                             │
│                    │  (Standby)      │                             │
│                    └─────────────────┘                             │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. 備份策略

### 4.1 資料庫備份

| 備份類型 | 頻率 | 保留期間 | 儲存位置 |
|---------|------|---------|---------|
| 完整備份 | 每日 | 30 天 | 本地 + 異地 |
| 增量備份 | 每小時 | 7 天 | 本地 |
| Archive Log | 即時 | 7 天 | 本地 + 異地 |
| RMAN 備份 | 每日 | 30 天 | 磁帶 |

### 4.2 應用程式備份

| 備份項目 | 頻率 | 保留期間 |
|---------|------|---------|
| 設定檔 | 變更時 | 永久 (Git) |
| Docker 映像 | 每次建置 | 90 天 |
| Kubernetes 配置 | 變更時 | 永久 (Git) |
| 日誌檔案 | 每日 | 90 天 |

### 4.3 備份驗證

```bash
# 每週執行備份還原測試
#!/bin/bash
# backup-verification.sh

echo "開始備份驗證..."

# 1. 還原資料庫到測試環境
rman target / <<EOF
run {
  restore database from tag 'DAILY_BACKUP';
  recover database;
}
EOF

# 2. 驗證資料完整性
sqlplus -s system/password@testdb <<EOF
SELECT COUNT(*) FROM fep_transactions WHERE trx_date = TRUNC(SYSDATE-1);
EOF

# 3. 執行應用程式健康檢查
curl -f http://test-fep-service/actuator/health

echo "備份驗證完成"
```

---

## 5. 災難復原程序

### 5.1 應用程式節點故障

**徵兆**: Pod 狀態異常、健康檢查失敗

**復原步驟**:
```bash
# 1. 確認故障節點
kubectl get pods -n fep-prod -o wide

# 2. 檢查 Pod 日誌
kubectl logs <pod-name> -n fep-prod --tail=100

# 3. 刪除故障 Pod (Kubernetes 自動重建)
kubectl delete pod <pod-name> -n fep-prod

# 4. 確認新 Pod 啟動成功
kubectl get pods -n fep-prod -w

# 5. 驗證服務狀態
curl http://fep-service/actuator/health
```

**預估時間**: 2-5 分鐘

### 5.2 資料庫故障

**徵兆**: 資料庫連線失敗、ORA 錯誤

**復原步驟**:
```bash
# 1. 確認資料庫狀態
sqlplus / as sysdba <<EOF
SELECT INSTANCE_NAME, STATUS FROM V\$INSTANCE;
EOF

# 2. 如果主資料庫無法恢復，切換到備援
# 在 Standby 端執行
dgmgrl sys/password@standby <<EOF
switchover to standby_db;
EOF

# 3. 更新應用程式連線字串
kubectl set env deployment/fep-transaction \
  SPRING_DATASOURCE_URL=jdbc:oracle:thin:@standby-db:1521/FEPDB \
  -n fep-prod

# 4. 重啟應用程式
kubectl rollout restart deployment/fep-transaction -n fep-prod

# 5. 驗證連線
curl http://fep-service/actuator/health
```

**預估時間**: 10-15 分鐘

### 5.3 財金連線中斷

**徵兆**: 財金交易逾時、連線錯誤

**復原步驟**:
```bash
# 1. 檢查網路連線
telnet fisc.gateway.tw 8583

# 2. 檢查防火牆規則
iptables -L -n | grep 8583

# 3. 重新建立連線
curl -X POST http://fep-service/api/v1/admin/fisc/reconnect

# 4. 執行 Echo Test
curl -X POST http://fep-service/api/v1/test/echo

# 5. 如果主線路無法恢復，切換備援線路
curl -X POST http://fep-service/api/v1/admin/fisc/switch-backup
```

**預估時間**: 5-10 分鐘

### 5.4 完整資料中心故障

**徵兆**: 主資料中心完全無法存取

**復原步驟**:
```bash
# 1. 宣布進入 DR 模式
# 通知所有相關人員

# 2. 在備援資料中心啟動服務
kubectl config use-context dr-cluster
kubectl scale deployment --all --replicas=3 -n fep-prod

# 3. 切換資料庫到備援
dgmgrl sys/password@dr-standby <<EOF
failover to dr_standby_db;
EOF

# 4. 更新 DNS 指向備援資料中心
# 聯繫網路管理員執行 DNS 切換

# 5. 通知財金公司切換連線
# 聯繫財金公司聯絡窗口

# 6. 驗證服務狀態
curl http://dr-fep-service/actuator/health

# 7. 執行測試交易
curl -X POST http://dr-fep-service/api/v1/test/echo
```

**預估時間**: 15-30 分鐘

### 5.5 資安事件

**徵兆**: 異常存取、系統遭入侵

**復原步驟**:
```bash
# 1. 立即隔離受影響系統
kubectl cordon <node-name>
kubectl drain <node-name> --ignore-daemonsets

# 2. 保留證據
kubectl logs <pod-name> -n fep-prod > /evidence/pod-logs-$(date +%Y%m%d%H%M%S).txt
kubectl describe pod <pod-name> -n fep-prod > /evidence/pod-describe.txt

# 3. 停止可疑服務
kubectl scale deployment/<compromised-deployment> --replicas=0 -n fep-prod

# 4. 通報資安單位
# 聯繫資安主管

# 5. 從乾淨的映像重新部署
kubectl set image deployment/fep-transaction \
  fep-transaction=harbor.bank.local/fep/fep-system:1.0.0-verified \
  -n fep-prod

# 6. 變更所有憑證與金鑰
# 執行金鑰輪換程序
```

**預估時間**: 依事件嚴重程度而定

---

## 6. 通訊計畫

### 6.1 通報流程

```
災難發生
    │
    ▼
值班人員發現
    │
    ├─────────────────────────────┐
    ▼                             ▼
P1/P2 事件                    P3 事件
    │                             │
    ▼                             ▼
立即通報主管                  記錄 Ticket
    │                             │
    ▼                             ▼
啟動 DR 程序                  排程處理
    │
    ▼
通知相關單位
(財金/營運/客服)
    │
    ▼
執行復原程序
    │
    ▼
驗證服務恢復
    │
    ▼
發送恢復通知
```

### 6.2 通報名單

| 順序 | 角色 | 通報時機 | 通報方式 |
|-----|------|---------|---------|
| 1 | 值班主管 | 立即 | 電話 + LINE |
| 2 | 技術主管 | 5 分鐘內 | 電話 + LINE |
| 3 | 專案經理 | 10 分鐘內 | 電話 + Email |
| 4 | 財金聯絡人 | P1 事件時 | 電話 |
| 5 | 營運單位 | 服務影響時 | Email |
| 6 | 高階主管 | P1 事件超過 30 分鐘 | 電話 |

### 6.3 通報範本

**事件通報**:
```
【FEP 系統事件通報】

事件等級: P1/P2/P3
發生時間: YYYY-MM-DD HH:MM:SS
影響範圍: [描述]
目前狀態: 處理中/已解決
預估恢復時間: [時間]
處理人員: [姓名]

事件描述:
[詳細說明]

目前採取措施:
1. [措施1]
2. [措施2]
```

**恢復通報**:
```
【FEP 系統恢復通報】

事件等級: P1/P2/P3
發生時間: YYYY-MM-DD HH:MM:SS
恢復時間: YYYY-MM-DD HH:MM:SS
影響時間: X 分鐘

根本原因:
[描述]

採取措施:
[描述]

後續改善:
[描述]
```

---

## 7. 演練計畫

### 7.1 演練類型

| 演練類型 | 頻率 | 參與人員 |
|---------|------|---------|
| 桌面演練 | 每季 | 全體 |
| 功能測試 | 每月 | 技術團隊 |
| 完整切換演練 | 每年 | 全體 + 財金 |

### 7.2 演練腳本

**場景: 主資料庫故障切換演練**

| 步驟 | 時間 | 動作 | 負責人 |
|-----|------|------|--------|
| 1 | T+0 | 模擬主資料庫故障 | DBA |
| 2 | T+2m | 監控告警觸發 | NOC |
| 3 | T+5m | 確認故障並通報 | 值班人員 |
| 4 | T+8m | 啟動 DR 程序 | 技術主管 |
| 5 | T+10m | 執行資料庫切換 | DBA |
| 6 | T+12m | 更新應用程式連線 | DevOps |
| 7 | T+15m | 驗證服務恢復 | QA |
| 8 | T+20m | 通報恢復完成 | 專案經理 |

### 7.3 演練記錄

| 日期 | 演練類型 | 結果 | 發現問題 | 改善措施 |
|-----|---------|------|---------|---------|
| - | - | - | - | - |

---

## 8. 維護與更新

### 8.1 文件維護
- 本文件每季審查一次
- 重大系統變更後立即更新
- 演練後檢討並更新

### 8.2 版本紀錄

| 版本 | 日期 | 變更內容 | 作者 |
|-----|------|---------|------|
| 1.0 | 2026-01-08 | 初版建立 | FEP Team |

---

## 附錄 A: 緊急聯絡清單

| 角色 | 姓名 | 電話 | Email | LINE |
|-----|------|------|-------|------|
| 值班主管 | - | - | - | - |
| 技術主管 | - | - | - | - |
| DBA | - | - | - | - |
| 網路管理員 | - | - | - | - |
| 財金聯絡人 | - | - | - | - |
| 資安主管 | - | - | - | - |
| HSM 廠商 | - | - | - | - |

## 附錄 B: 重要系統資訊

| 項目 | 值 |
|-----|-----|
| 主資料庫 IP | 10.x.x.x |
| 備援資料庫 IP | 10.x.x.x |
| 財金連線 IP | 10.x.x.x |
| HSM IP | 10.x.x.x |
| Kubernetes Master | 10.x.x.x |
| Harbor Registry | harbor.bank.local |

## 附錄 C: 復原檢查清單

### 應用程式復原檢查
- [ ] 所有 Pod 狀態為 Running
- [ ] 健康檢查端點回應正常
- [ ] 日誌無異常錯誤
- [ ] 指標監控正常

### 資料庫復原檢查
- [ ] 資料庫實例運行中
- [ ] 連線池正常
- [ ] 複寫狀態同步
- [ ] 無資料遺失

### 網路復原檢查
- [ ] 財金連線正常
- [ ] 主機連線正常
- [ ] 負載均衡正常
- [ ] DNS 解析正確

### 安全檢查
- [ ] HSM 連線正常
- [ ] 憑證有效
- [ ] 金鑰狀態正常
- [ ] 無可疑存取記錄
