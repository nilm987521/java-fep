package com.fep.transaction.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FEP 交易 Entity
 *
 * <p>對應 FEP_TRANSACTION 表，用於持久化交易記錄。
 * 純 DB 架構中，此 Entity 取代 Redis 作為交易上下文的儲存。
 *
 * <p>狀態流轉：
 * <pre>
 * PENDING → SENT → COMPLETED (成功)
 *                → FAILED    (失敗)
 *                → TIMEOUT   (超時)
 *                → REVERSED  (已沖正)
 * </pre>
 */
@Entity
@Table(name = "FEP_TRANSACTION", indexes = {
    @Index(name = "IDX_FEP_TXN_STAN", columnList = "stan"),
    @Index(name = "IDX_FEP_TXN_STATUS_EXPIRE", columnList = "status, expireTime"),
    @Index(name = "IDX_FEP_TXN_TXN_DATE", columnList = "transactionDate")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FepTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fep_txn_seq")
    @SequenceGenerator(name = "fep_txn_seq", sequenceName = "SEQ_FEP_TRANSACTION", allocationSize = 50)
    private Long id;

    /**
     * 交易唯一識別碼 (UUID)
     */
    @Column(name = "transaction_id", nullable = false, unique = true, length = 64)
    private String transactionId;

    /**
     * STAN (System Trace Audit Number)
     */
    @Column(name = "stan", nullable = false, length = 6)
    private String stan;

    /**
     * MTI (Message Type Indicator)
     */
    @Column(name = "mti", nullable = false, length = 4)
    private String mti;

    /**
     * Processing Code
     */
    @Column(name = "processing_code", length = 6)
    private String processingCode;

    /**
     * 來源帳號
     */
    @Column(name = "source_account", length = 32)
    private String sourceAccount;

    /**
     * 目標帳號
     */
    @Column(name = "target_account", length = 32)
    private String targetAccount;

    /**
     * 交易金額
     */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * 來源銀行代碼
     */
    @Column(name = "source_bank_code", length = 10)
    private String sourceBankCode;

    /**
     * 目標銀行代碼
     */
    @Column(name = "target_bank_code", length = 10)
    private String targetBankCode;

    /**
     * 交易狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    /**
     * FISC 回應碼
     */
    @Column(name = "response_code", length = 4)
    private String responseCode;

    /**
     * BPMN Process ID
     */
    @Column(name = "process_id", length = 64)
    private String processId;

    /**
     * 通道 ID
     */
    @Column(name = "channel_id", length = 32)
    private String channelId;

    /**
     * Callback Key (用於關聯回應)
     */
    @Column(name = "callback_key", length = 64)
    private String callbackKey;

    /**
     * 請求時間
     */
    @Column(name = "request_time", nullable = false)
    private LocalDateTime requestTime;

    /**
     * 發送至 FISC 時間
     */
    @Column(name = "sent_to_fisc_time")
    private LocalDateTime sentToFiscTime;

    /**
     * FISC 回應時間
     */
    @Column(name = "fisc_response_time")
    private LocalDateTime fiscResponseTime;

    /**
     * 過期時間 (用於 Timeout 掃描)
     */
    @Column(name = "expire_time", nullable = false)
    private LocalDateTime expireTime;

    /**
     * 原始請求電文 (Base64)
     */
    @Lob
    @Column(name = "raw_request")
    private String rawRequest;

    /**
     * 原始回應電文 (Base64)
     */
    @Lob
    @Column(name = "raw_response")
    private String rawResponse;

    /**
     * 沖正狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reversal_status", length = 20)
    private ReversalStatus reversalStatus;

    /**
     * 交易日期 (yyyyMMdd 格式，用於分區)
     */
    @Column(name = "transaction_date", nullable = false, length = 10)
    private String transactionDate;

    /**
     * 建立時間
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 更新時間
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 版本號 (樂觀鎖)
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * 交易狀態枚舉
     */
    public enum TransactionStatus {
        /**
         * 已建立，尚未發送
         */
        PENDING,

        /**
         * 已發送至 FISC，等待回應
         */
        SENT,

        /**
         * 交易完成（成功）
         */
        COMPLETED,

        /**
         * 交易失敗
         */
        FAILED,

        /**
         * 超時
         */
        TIMEOUT,

        /**
         * 已沖正
         */
        REVERSED
    }

    /**
     * 沖正狀態枚舉
     */
    public enum ReversalStatus {
        /**
         * 不需要沖正
         */
        NONE,

        /**
         * 沖正進行中
         */
        IN_PROGRESS,

        /**
         * 沖正成功
         */
        SUCCESS,

        /**
         * 沖正失敗
         */
        FAILED,

        /**
         * 待人工調帳
         */
        PENDING_ADJUSTMENT
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
        if (reversalStatus == null) {
            reversalStatus = ReversalStatus.NONE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
