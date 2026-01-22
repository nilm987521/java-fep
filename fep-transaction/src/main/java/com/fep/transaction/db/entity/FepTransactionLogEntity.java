package com.fep.transaction.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FEP 交易日誌 Entity
 *
 * <p>記錄交易處理過程的詳細日誌，用於稽核追蹤和問題分析。
 * 每筆交易可能有多筆日誌記錄，記錄從請求到回應的完整處理過程。
 */
@Entity
@Table(name = "FEP_TRANSACTION_LOG", indexes = {
    @Index(name = "IDX_FEP_TXN_LOG_TXN_ID", columnList = "transactionId"),
    @Index(name = "IDX_FEP_TXN_LOG_STAN", columnList = "stan"),
    @Index(name = "IDX_FEP_TXN_LOG_TIME", columnList = "logTime"),
    @Index(name = "IDX_FEP_TXN_LOG_DATE", columnList = "logDate")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FepTransactionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fep_txn_log_seq")
    @SequenceGenerator(name = "fep_txn_log_seq", sequenceName = "SEQ_FEP_TRANSACTION_LOG", allocationSize = 50)
    private Long id;

    /**
     * 關聯的交易 ID
     */
    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    /**
     * STAN (System Trace Audit Number)
     */
    @Column(name = "stan", length = 6)
    private String stan;

    /**
     * 日誌階段
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "log_stage", nullable = false, length = 30)
    private LogStage logStage;

    /**
     * 日誌等級
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "log_level", nullable = false, length = 10)
    private LogLevel logLevel;

    /**
     * 日誌訊息
     */
    @Column(name = "message", length = 500)
    private String message;

    /**
     * 詳細資訊 (JSON 格式)
     */
    @Lob
    @Column(name = "details")
    private String details;

    /**
     * 原始電文 (Base64)
     */
    @Lob
    @Column(name = "raw_message")
    private String rawMessage;

    /**
     * 處理元件名稱 (如 Delegate 名稱)
     */
    @Column(name = "component", length = 100)
    private String component;

    /**
     * 處理耗時 (毫秒)
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * 錯誤代碼
     */
    @Column(name = "error_code", length = 20)
    private String errorCode;

    /**
     * 錯誤訊息
     */
    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    /**
     * 伺服器實例 ID
     */
    @Column(name = "server_instance", length = 50)
    private String serverInstance;

    /**
     * 日誌時間
     */
    @Column(name = "log_time", nullable = false)
    private LocalDateTime logTime;

    /**
     * 日誌日期 (yyyyMMdd 格式，用於分區)
     */
    @Column(name = "log_date", nullable = false, length = 10)
    private String logDate;

    /**
     * 日誌階段枚舉
     */
    public enum LogStage {
        /**
         * 請求接收
         */
        REQUEST_RECEIVED,

        /**
         * 請求驗證
         */
        VALIDATION,

        /**
         * 限額檢查
         */
        LIMIT_CHECK,

        /**
         * 凍結金額
         */
        FREEZE_AMOUNT,

        /**
         * 電文組裝
         */
        MESSAGE_ASSEMBLY,

        /**
         * 發送至 FISC
         */
        SEND_TO_FISC,

        /**
         * 等待回應
         */
        WAITING_RESPONSE,

        /**
         * 收到回應
         */
        RESPONSE_RECEIVED,

        /**
         * 回應處理
         */
        RESPONSE_PROCESSING,

        /**
         * 確認扣款
         */
        CONFIRM_DEBIT,

        /**
         * 解凍金額
         */
        UNFREEZE_AMOUNT,

        /**
         * 發送沖正
         */
        SEND_REVERSAL,

        /**
         * 沖正回應
         */
        REVERSAL_RESPONSE,

        /**
         * 回覆客戶端
         */
        REPLY_TO_CLIENT,

        /**
         * 交易完成
         */
        COMPLETED,

        /**
         * 錯誤處理
         */
        ERROR
    }

    /**
     * 日誌等級枚舉
     */
    public enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    @PrePersist
    protected void onCreate() {
        if (logTime == null) {
            logTime = LocalDateTime.now();
        }
        if (logDate == null) {
            logDate = logTime.toLocalDate().toString().replace("-", "");
        }
    }
}
