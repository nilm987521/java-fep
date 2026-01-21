# BPMN 流程交易管理設計方案

## 概述

本文件說明在 Camunda BPMN 流程中實現資料庫交易管理的設計方案，特別針對需要等待外部系統回應（如 FISC 財金公司）的場景。

## 問題背景

### 為什麼不能整個 BPMN 流程使用單一 Transaction？

Camunda 預設在每個 **Wait State（等待狀態）** 時會 commit transaction：

```
START → [Task1] → [Task2] → [Message Catch Event] → [Task3] → END
         │                         │
         └─── Transaction 1 ───────┘
                                   │
                                   ▼ (commit)

                    (等待外部訊息...)

                                   │
         └─── Transaction 2 ───────┴──────────────────────┘
```

### Wait States 包括：
- `Message Catch Event`（等待訊息）
- `Timer Event`（計時器）
- `User Task`（人工任務）
- `Signal Catch Event`
- `External Task`

### 為什麼這是必要的設計？

| 問題 | 說明 |
|-----|------|
| 長時間鎖定 | 等待 FISC 回應可能 1ms ~ 30秒，不能鎖住 DB 這麼久 |
| 系統崩潰恢復 | 分段 commit 可從最後一個 wait state 恢復 |
| 併發處理 | 其他交易需要存取相同資料 |

---

## 解決方案：狀態驅動的交易管理

### 設計概念

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   TX1 (Request)              (No TX)           TX2 (Response)   │
│   ┌───────────┐          ┌───────────┐         ┌───────────┐    │
│   │ 扣款凍結  │ ────────►│ 等待FISC  │────────►│ 確認/解凍 │    │
│   │           │          │   回應    │         │           │    │
│   │ status=   │          │           │         │ status=   │    │
│   │ FROZEN    │          │ (30秒)    │         │ COMPLETED │    │
│   └───────────┘          └───────────┘         │ or FAILED │    │
│        │                                       └───────────┘    │
│        ▼                                             │          │
│   ┌─────────────────────────────────────────────────▼────────┐  │
│   │                    同一筆 Record                          │  │
│   │  pending_transaction                                     │  │
│   │  ├─ id: 12345                                            │  │
│   │  ├─ amount: 10000                                        │  │
│   │  ├─ status: FROZEN → COMPLETED / CANCELLED               │  │
│   │  └─ freeze_time: 2024-01-21 10:00:00                     │  │
│   └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 狀態流轉圖

```
     ┌──────────┐
     │  FROZEN  │  ← TX1 建立（請求階段）
     └────┬─────┘
          │
    ┌─────┴─────┐
    │           │
    ▼           ▼
┌────────┐  ┌──────────┐
│COMPLETED│  │CANCELLED │  ← TX2 更新（回應階段）
│ (成功)  │  │  (失敗)  │
└────────┘  └──────────┘
                │
                ▼
          ┌──────────┐
          │ TIMEOUT  │  ← 排程處理（逾時）
          └──────────┘
```

---

## 資料庫設計

### Table Schema

```sql
CREATE TABLE pending_transaction (
    id              NUMBER PRIMARY KEY,
    stan            VARCHAR2(12) NOT NULL,
    account         VARCHAR2(20) NOT NULL,
    target_account  VARCHAR2(20),
    amount          NUMBER(15) NOT NULL,
    status          VARCHAR2(20) NOT NULL,
    freeze_time     TIMESTAMP NOT NULL,
    complete_time   TIMESTAMP,
    response_code   VARCHAR2(4),
    process_id      VARCHAR2(64),
    channel_id      VARCHAR2(32),
    mti             VARCHAR2(4),
    error_message   VARCHAR2(500),

    CONSTRAINT chk_status CHECK (status IN ('FROZEN', 'COMPLETED', 'CANCELLED', 'TIMEOUT'))
);

-- 索引
CREATE INDEX idx_pending_tx_stan ON pending_transaction(stan);
CREATE INDEX idx_pending_tx_status ON pending_transaction(status);
CREATE INDEX idx_pending_tx_freeze_time ON pending_transaction(freeze_time);
CREATE INDEX idx_pending_tx_process_id ON pending_transaction(process_id);
```

### Entity Class

```java
@Entity
@Table(name = "pending_transaction")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, length = 12)
    private String stan;

    @Column(nullable = false, length = 20)
    private String account;

    @Column(length = 20)
    private String targetAccount;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false)
    private LocalDateTime freezeTime;

    private LocalDateTime completeTime;

    @Column(length = 4)
    private String responseCode;

    @Column(length = 64)
    private String processId;

    @Column(length = 32)
    private String channelId;

    @Column(length = 4)
    private String mti;

    @Column(length = 500)
    private String errorMessage;
}

public enum TransactionStatus {
    FROZEN,      // 凍結中（等待回應）
    COMPLETED,   // 已完成（成功）
    CANCELLED,   // 已取消（失敗）
    TIMEOUT      // 逾時
}
```

---

## 實作範例

### 1. 凍結交易 Delegate（TX1）

```java
@Slf4j
@Component("freezeAmountDelegate")
@RequiredArgsConstructor
public class FreezeAmountDelegate implements JavaDelegate {

    private final PendingTransactionRepository repository;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) throws Exception {
        String stan = (String) execution.getVariable("stan");
        Long amount = (Long) execution.getVariable("amount");
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        String targetAccount = (String) execution.getVariable("targetAccount");
        String channelId = (String) execution.getVariable("channelId");
        String mti = (String) execution.getVariable("mti");

        // 建立凍結記錄
        PendingTransaction tx = PendingTransaction.builder()
                .stan(stan)
                .account(sourceAccount)
                .targetAccount(targetAccount)
                .amount(amount)
                .status(TransactionStatus.FROZEN)
                .freezeTime(LocalDateTime.now())
                .processId(execution.getProcessInstanceId())
                .channelId(channelId)
                .mti(mti)
                .build();

        repository.save(tx);

        // 儲存 ID 供後續使用
        execution.setVariable("pendingTxId", tx.getId());
        execution.setVariable("freezeId", tx.getId().toString());

        log.info("[{}] 已凍結交易: id={}, stan={}, amount={}",
                execution.getProcessInstanceId(), tx.getId(), stan, amount);
    }
}
```

### 2. 確認扣款 Delegate（TX2 - 成功路徑）

```java
@Slf4j
@Component("confirmDebitDelegate")
@RequiredArgsConstructor
public class ConfirmDebitDelegate implements JavaDelegate {

    private final PendingTransactionRepository repository;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) throws Exception {
        Long txId = (Long) execution.getVariable("pendingTxId");
        String responseCode = (String) execution.getVariable("responseCode");

        // 查詢並更新同一筆記錄
        PendingTransaction tx = repository.findById(txId)
                .orElseThrow(() -> new RuntimeException("找不到凍結交易: " + txId));

        // 檢查狀態
        if (tx.getStatus() != TransactionStatus.FROZEN) {
            log.warn("[{}] 交易狀態不正確: id={}, status={}",
                    execution.getProcessInstanceId(), txId, tx.getStatus());
            return;
        }

        // 確認扣款
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setCompleteTime(LocalDateTime.now());
        tx.setResponseCode(responseCode != null ? responseCode : "00");

        repository.save(tx);

        execution.setVariable("debitStatus", "CONFIRMED");

        log.info("[{}] 已確認扣款: id={}, stan={}",
                execution.getProcessInstanceId(), txId, tx.getStan());
    }
}
```

### 3. 解凍/取消 Delegate（TX2 - 失敗路徑）

```java
@Slf4j
@Component("unfreezeAmountDelegate")
@RequiredArgsConstructor
public class UnfreezeAmountDelegate implements JavaDelegate {

    private final PendingTransactionRepository repository;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) throws Exception {
        Long txId = (Long) execution.getVariable("pendingTxId");
        String responseCode = (String) execution.getVariable("responseCode");
        String errorMessage = (String) execution.getVariable("errorMessage");

        // 查詢並更新同一筆記錄
        PendingTransaction tx = repository.findById(txId)
                .orElseThrow(() -> new RuntimeException("找不到凍結交易: " + txId));

        // 檢查狀態
        if (tx.getStatus() != TransactionStatus.FROZEN) {
            log.warn("[{}] 交易狀態不正確，無法解凍: id={}, status={}",
                    execution.getProcessInstanceId(), txId, tx.getStatus());
            return;
        }

        // 取消/解凍
        tx.setStatus(TransactionStatus.CANCELLED);
        tx.setCompleteTime(LocalDateTime.now());
        tx.setResponseCode(responseCode);
        tx.setErrorMessage(errorMessage);

        repository.save(tx);

        execution.setVariable("debitStatus", "CANCELLED");

        log.info("[{}] 已解凍交易: id={}, stan={}, rc={}",
                execution.getProcessInstanceId(), txId, tx.getStan(), responseCode);
    }
}
```

### 4. Repository 介面

```java
@Repository
public interface PendingTransactionRepository extends JpaRepository<PendingTransaction, Long> {

    Optional<PendingTransaction> findByStan(String stan);

    List<PendingTransaction> findByStatus(TransactionStatus status);

    List<PendingTransaction> findByStatusAndFreezeTimeBefore(
            TransactionStatus status, LocalDateTime threshold);

    @Query("SELECT p FROM PendingTransaction p WHERE p.status = :status " +
           "AND p.freezeTime < :threshold ORDER BY p.freezeTime")
    List<PendingTransaction> findExpiredFrozenTransactions(
            @Param("status") TransactionStatus status,
            @Param("threshold") LocalDateTime threshold);
}
```

---

## 逾時處理

### 排程任務

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class FrozenTransactionCleanupJob {

    private final PendingTransactionRepository repository;

    /**
     * 每分鐘檢查逾時的凍結交易
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredFrozenTransactions() {
        // 超過 5 分鐘未完成的凍結交易視為逾時
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);

        List<PendingTransaction> expiredList = repository
                .findByStatusAndFreezeTimeBefore(TransactionStatus.FROZEN, threshold);

        for (PendingTransaction tx : expiredList) {
            tx.setStatus(TransactionStatus.TIMEOUT);
            tx.setCompleteTime(LocalDateTime.now());
            tx.setResponseCode("68");  // Response timeout
            tx.setErrorMessage("Transaction timeout after 5 minutes");

            repository.save(tx);

            log.warn("凍結交易逾時: id={}, stan={}, freezeTime={}",
                    tx.getId(), tx.getStan(), tx.getFreezeTime());
        }

        if (!expiredList.isEmpty()) {
            log.info("已處理 {} 筆逾時凍結交易", expiredList.size());
        }
    }
}
```

---

## BPMN 流程設計

### 跨行轉帳流程（含交易管理）

```
START
  │
  ▼
┌─────────────────┐
│ Task_Validate   │  驗證請求
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Task_CheckLimit │  檢查限額
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Task_Freeze     │  ◄── TX1: 建立 FROZEN 記錄
│ Amount          │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Task_SendToFISC │  發送至 FISC
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Event_Wait      │  ◄── TX1 已 Commit，等待回應
│ FiscResponse    │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
 RC=00     RC!=00
    │         │
    ▼         ▼
┌────────┐ ┌────────┐
│Confirm │ │Unfreeze│  ◄── TX2: 更新為 COMPLETED 或 CANCELLED
│ Debit  │ │ Amount │
└────────┘ └────────┘
    │         │
    └────┬────┘
         │
         ▼
┌─────────────────┐
│ Task_SendResp   │  發送回應給客戶端
└────────┬────────┘
         │
         ▼
        END
```

---

## 優點總結

| 優點 | 說明 |
|-----|------|
| 無長時間鎖定 | TX1 commit 後立即釋放 DB lock |
| 可追蹤狀態 | 任何時候都可查詢交易狀態 |
| 支援逾時處理 | 排程掃描 FROZEN 超過 N 分鐘的記錄 |
| 易於調帳 | FROZEN/TIMEOUT 狀態的記錄可人工處理 |
| 系統重啟恢復 | FROZEN 記錄可在系統重啟後繼續處理 |
| 審計追蹤 | 完整記錄交易生命週期 |

---

## 注意事項

1. **冪等性**：確保 Delegate 操作是冪等的，避免重複處理

2. **狀態檢查**：更新前檢查當前狀態，避免不正確的狀態轉換

3. **逾時設定**：根據業務需求調整逾時時間（通常 FISC 回應時間 < 30 秒）

4. **監控告警**：對 FROZEN 和 TIMEOUT 狀態的記錄設置監控告警

5. **調帳機制**：建立人工調帳流程處理異常狀態的交易
