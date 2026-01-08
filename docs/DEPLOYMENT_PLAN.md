# FEP System 上線部署計畫

> 版本: 1.0.0
> 建立日期: 2026-01-08
> 文件狀態: 正式版

---

## 1. 專案概述

### 1.1 系統簡介
FEP (Front-End Processor) 系統是一套完整的金融交易前端處理器，用於處理與財金公司 (FISC) 的跨行交易。

### 1.2 系統架構
```
┌─────────────────────────────────────────────────────────────────────┐
│                          外部通道層                                   │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐   │
│  │   ATM   │  │   POS   │  │  網路銀行 │  │ 行動銀行 │  │  第三方  │   │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘   │
└───────┼────────────┼────────────┼────────────┼────────────┼────────┘
        └────────────┴────────────┼────────────┴────────────┘
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        FEP 核心處理層                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │  通道接入模組  │  │  交易路由引擎  │  │  電文轉換引擎  │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │  安全加密模組  │  │  交易控制模組  │  │  日誌稽核模組  │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.3 模組清單
| 模組名稱 | 功能說明 | 版本 |
|---------|---------|------|
| fep-common | 共用模組 | 1.0.0 |
| fep-message | ISO 8583 電文處理 | 1.0.0 |
| fep-communication | 財金連線管理 | 1.0.0 |
| fep-transaction | 交易處理核心 | 1.0.0 |
| fep-security | 安全加密模組 | 1.0.0 |
| fep-settlement | 對帳清算模組 | 1.0.0 |
| fep-integration | 核心系統整合 | 1.0.0 |
| fep-monitor | 監控管理前端 | 1.0.0 |

---

## 2. 上線前準備

### 2.1 環境需求

#### 硬體需求
| 環境 | CPU | 記憶體 | 儲存空間 | 數量 |
|-----|-----|--------|---------|------|
| 應用伺服器 | 8 Core | 16 GB | 100 GB SSD | 4 |
| 資料庫伺服器 | 16 Core | 64 GB | 1 TB SSD | 2 (RAC) |
| Redis 叢集 | 4 Core | 16 GB | 50 GB SSD | 6 |
| Kafka 叢集 | 4 Core | 8 GB | 200 GB SSD | 3 |

#### 軟體需求
| 軟體 | 版本 | 用途 |
|-----|------|------|
| JDK | 21+ | 應用程式執行環境 |
| Oracle Database | 19c+ | 主資料庫 |
| Redis | 7.0+ | 快取/Session |
| Apache Kafka | 3.6+ | 訊息佇列 |
| Kubernetes | 1.28+ | 容器編排 |
| Nginx | 1.24+ | 負載均衡 |

### 2.2 網路需求
| 連線 | 來源 | 目的 | Port | 協定 |
|-----|------|------|------|------|
| 財金公司 | FEP | FISC | 8583 | TCP/IP |
| 主機系統 | FEP | Mainframe | 1414 | IBM MQ |
| 資料庫 | FEP | Oracle | 1521 | Oracle Net |
| Redis | FEP | Redis Cluster | 6379 | Redis |
| Kafka | FEP | Kafka Cluster | 9092 | Kafka |

### 2.3 憑證與金鑰
- [ ] SSL/TLS 憑證 (HTTPS)
- [ ] 財金公司連線憑證
- [ ] HSM 金鑰注入
- [ ] IBM MQ 憑證
- [ ] 資料庫連線憑證

---

## 3. 部署步驟

### 3.1 Phase 1: 基礎設施準備 (D-14)

#### 3.1.1 Kubernetes 叢集設定
```bash
# 建立命名空間
kubectl create namespace fep-prod

# 設定資源配額
kubectl apply -f k8s/resource-quota.yaml

# 設定網路政策
kubectl apply -f k8s/network-policy.yaml
```

#### 3.1.2 資料庫初始化
```sql
-- 建立 Schema
CREATE USER fep_owner IDENTIFIED BY <password>;
GRANT CONNECT, RESOURCE, DBA TO fep_owner;

-- 執行 DDL 腳本
@scripts/ddl/create_tables.sql
@scripts/ddl/create_indexes.sql
@scripts/ddl/create_sequences.sql
```

#### 3.1.3 Redis 叢集設定
```bash
# 建立 Redis 叢集
redis-cli --cluster create \
  redis-1:6379 redis-2:6379 redis-3:6379 \
  redis-4:6379 redis-5:6379 redis-6:6379 \
  --cluster-replicas 1
```

### 3.2 Phase 2: 應用程式部署 (D-7)

#### 3.2.1 建置 Docker 映像
```bash
# 建置所有模組
mvn clean package -DskipTests

# 建置 Docker 映像
docker build -t fep-system:1.0.0 .

# 推送至 Harbor
docker push harbor.bank.local/fep/fep-system:1.0.0
```

#### 3.2.2 部署至 Kubernetes
```bash
# 部署 ConfigMap
kubectl apply -f k8s/configmap.yaml -n fep-prod

# 部署 Secret
kubectl apply -f k8s/secrets.yaml -n fep-prod

# 部署應用程式
kubectl apply -f k8s/deployment.yaml -n fep-prod

# 部署服務
kubectl apply -f k8s/service.yaml -n fep-prod

# 設定 HPA
kubectl apply -f k8s/hpa.yaml -n fep-prod
```

#### 3.2.3 Kubernetes 部署配置範例
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fep-transaction
  namespace: fep-prod
spec:
  replicas: 6
  selector:
    matchLabels:
      app: fep-transaction
  template:
    metadata:
      labels:
        app: fep-transaction
    spec:
      containers:
      - name: fep-transaction
        image: harbor.bank.local/fep/fep-system:1.0.0
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: JAVA_OPTS
          value: "-Xms2g -Xmx4g -XX:+UseG1GC"
```

### 3.3 Phase 3: 整合測試 (D-5)

#### 3.3.1 連線測試
```bash
# 財金公司連線測試
curl -X POST http://fep-service/api/v1/test/fisc-connection

# 主機系統連線測試
curl -X POST http://fep-service/api/v1/test/mainframe-connection

# HSM 連線測試
curl -X POST http://fep-service/api/v1/test/hsm-connection
```

#### 3.3.2 交易測試
```bash
# Echo Test (0800)
curl -X POST http://fep-service/api/v1/test/echo

# 模擬提款交易
curl -X POST http://fep-service/api/v1/test/withdrawal \
  -H "Content-Type: application/json" \
  -d '{"amount": 1000, "pan": "4111111111111111"}'
```

### 3.4 Phase 4: 正式上線 (D-Day)

#### 3.4.1 上線流程
1. **T-2H**: 通知相關單位進入上線準備
2. **T-1H**: 執行最終備份
3. **T-30M**: 停止舊系統連線
4. **T-15M**: 切換 DNS/VIP
5. **T-0**: 啟動新系統
6. **T+15M**: 執行健康檢查
7. **T+30M**: 開放測試交易
8. **T+1H**: 全面開放營運

#### 3.4.2 健康檢查清單
- [ ] 所有 Pod 狀態為 Running
- [ ] 財金連線狀態正常
- [ ] 主機系統連線正常
- [ ] HSM 連線正常
- [ ] 資料庫連線池正常
- [ ] Redis 快取正常
- [ ] Kafka 訊息佇列正常
- [ ] 監控 Dashboard 顯示正常

---

## 4. 效能指標

### 4.1 目標效能
| 指標 | 目標值 | 測試結果 |
|-----|--------|---------|
| TPS | >2,000 | 23,337 ✅ |
| P95 延遲 | <100ms | 5ms ✅ |
| P99 延遲 | <200ms | 8ms ✅ |
| 可用性 | 99.99% | - |
| 成功率 | >99.9% | 100% ✅ |

### 4.2 資源使用
| 資源 | 警告閾值 | 告警閾值 |
|-----|---------|---------|
| CPU | 70% | 85% |
| 記憶體 | 75% | 90% |
| 磁碟 | 80% | 90% |
| 連線數 | 80% | 95% |

---

## 5. 監控與告警

### 5.1 監控項目
- 交易量 (TPS)
- 交易成功率
- 回應時間
- 系統資源使用率
- 連線狀態
- 錯誤率

### 5.2 告警設定
| 告警類型 | 條件 | 嚴重度 | 通知方式 |
|---------|------|--------|---------|
| 交易失敗率 | >1% | CRITICAL | SMS + Email + LINE |
| 回應時間 | >500ms | HIGH | Email + LINE |
| 連線斷開 | 任何 | CRITICAL | SMS + Email + LINE |
| CPU 使用率 | >85% | HIGH | Email |
| 記憶體使用率 | >90% | HIGH | Email |

### 5.3 監控工具
- Prometheus: 指標收集
- Grafana: 視覺化儀表板
- ELK Stack: 日誌分析
- fep-monitor: 業務監控

---

## 6. 回滾計畫

### 6.1 回滾條件
- 交易成功率低於 95%
- 核心功能無法運作
- 資料異常或遺失
- 安全漏洞發現

### 6.2 回滾步驟
```bash
# 1. 宣布進入回滾程序
kubectl annotate deployment fep-transaction rollback=true -n fep-prod

# 2. 回滾到前一版本
kubectl rollout undo deployment/fep-transaction -n fep-prod

# 3. 確認回滾狀態
kubectl rollout status deployment/fep-transaction -n fep-prod

# 4. 驗證服務狀態
curl http://fep-service/actuator/health
```

### 6.3 回滾時間目標
- RTO (Recovery Time Objective): 15 分鐘
- RPO (Recovery Point Objective): 0 (無資料遺失)

---

## 7. 聯絡人清單

| 角色 | 姓名 | 電話 | Email |
|-----|------|------|-------|
| 專案經理 | - | - | - |
| 技術主管 | - | - | - |
| DBA | - | - | - |
| 網路管理員 | - | - | - |
| 財金聯絡人 | - | - | - |
| 資安主管 | - | - | - |

---

## 8. 附錄

### 8.1 相關文件
- [系統架構設計書](./ARCHITECTURE.md)
- [API 規格書](./API_SPEC.md)
- [資料庫設計書](./DATABASE_DESIGN.md)
- [災難復原計畫](./DISASTER_RECOVERY_PLAN.md)

### 8.2 變更紀錄
| 版本 | 日期 | 變更內容 | 作者 |
|-----|------|---------|------|
| 1.0 | 2026-01-08 | 初版建立 | FEP Team |
