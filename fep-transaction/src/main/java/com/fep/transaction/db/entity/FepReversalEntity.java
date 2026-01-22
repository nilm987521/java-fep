package com.fep.transaction.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FEP 沖正記錄 Entity
 *
 * <p>記錄沖正交易的詳細資訊，包含原始交易的關聯、沖正原因、
 * 沖正結果等。支援多次沖正重試的追蹤。
 */
@Entity
@Table(name = "FEP_REVERSAL", indexes = {
    @Index(name = "IDX_FEP_REV_ORIG_TXN_ID", columnList = "originalTransactionId"),
    @Index(name = "IDX_FEP_REV_ORIG_STAN", columnList = "originalStan"),
    @Index(name = "IDX_FEP_REV_STATUS", columnList = "status"),
    @Index(name = "IDX_FEP_REV_DATE", columnList = "reversalDate")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FepReversalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fep_reversal_seq")
    @SequenceGenerator(name = "fep_reversal_seq", sequenceName = "SEQ_FEP_REVERSAL", allocationSize = 50)
    private Long id;

    /**
     * 沖正交易 ID (UUID)
     */
    @Column(name = "reversal_id", nullable = false, unique = true, length = 64)
    private String reversalId;

    /**
     * 原始交易 ID
     */
    @Column(name = "original_transaction_id", nullable = false, length = 64)
    private String originalTransactionId;

    /**
     * 原始 STAN
     */
    @Column(name = "original_stan", nullable = false, length = 6)
    private String originalStan;

    /**
     * 原始 MTI (通常是 0200)
     */
    @Column(name = "original_mti", nullable = false, length = 4)
    private String originalMti;

    /**
     * 沖正 STAN (0400 電文的 STAN)
     */
    @Column(name = "reversal_stan", nullable = false, length = 6)
    private String reversalStan;

    /**
     * 沖正金額
     */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * 沖正原因
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private ReversalReason reason;

    /**
     * 沖正狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReversalStatus status;

    /**
     * FISC 回應碼
     */
    @Column(name = "response_code", length = 4)
    private String responseCode;

    /**
     * 重試次數
     */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    /**
     * 最大重試次數
     */
    @Column(name = "max_retry", nullable = false)
    private Integer maxRetry;

    /**
     * 下次重試時間
     */
    @Column(name = "next_retry_time")
    private LocalDateTime nextRetryTime;

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
     * 通道 ID
     */
    @Column(name = "channel_id", length = 32)
    private String channelId;

    /**
     * 沖正請求電文 (Base64)
     */
    @Lob
    @Column(name = "raw_request")
    private String rawRequest;

    /**
     * 沖正回應電文 (Base64)
     */
    @Lob
    @Column(name = "raw_response")
    private String rawResponse;

    /**
     * 備註
     */
    @Column(name = "remark", length = 500)
    private String remark;

    /**
     * 人工處理標記
     */
    @Column(name = "manual_processing", nullable = false)
    private Boolean manualProcessing;

    /**
     * 人工處理人員
     */
    @Column(name = "processed_by", length = 50)
    private String processedBy;

    /**
     * 人工處理時間
     */
    @Column(name = "processed_time")
    private LocalDateTime processedTime;

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
     * 沖正日期 (yyyyMMdd 格式，用於分區)
     */
    @Column(name = "reversal_date", nullable = false, length = 10)
    private String reversalDate;

    /**
     * 版本號 (樂觀鎖)
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * 沖正原因枚舉
     */
    public enum ReversalReason {
        /**
         * 交易超時
         */
        TIMEOUT,

        /**
         * 通訊失敗
         */
        COMMUNICATION_FAILURE,

        /**
         * 系統錯誤
         */
        SYSTEM_ERROR,

        /**
         * 客戶取消
         */
        CUSTOMER_CANCEL,

        /**
         * 重複交易
         */
        DUPLICATE_TRANSACTION,

        /**
         * 驗證失敗後沖正
         */
        VALIDATION_FAILURE,

        /**
         * 人工沖正
         */
        MANUAL
    }

    /**
     * 沖正狀態枚舉
     */
    public enum ReversalStatus {
        /**
         * 待處理
         */
        PENDING,

        /**
         * 處理中
         */
        IN_PROGRESS,

        /**
         * 已發送
         */
        SENT,

        /**
         * 成功
         */
        SUCCESS,

        /**
         * 失敗
         */
        FAILED,

        /**
         * 待人工處理
         */
        PENDING_MANUAL,

        /**
         * 人工處理完成
         */
        MANUAL_COMPLETED
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ReversalStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetry == null) {
            maxRetry = 3;
        }
        if (manualProcessing == null) {
            manualProcessing = false;
        }
        if (reversalDate == null) {
            reversalDate = createdAt.toLocalDate().toString().replace("-", "");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
