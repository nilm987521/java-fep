package com.fep.transaction.redis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Pending Transaction DTO
 *
 * <p>用於 Redis HASH 儲存的交易資料傳輸物件。
 * 包含完整的交易上下文，供 Response/Timeout BPMN 流程載入使用。
 *
 * <p>Redis 結構：
 * <pre>
 * Key: fep:txn:{transactionId}
 * Fields:
 *   stan, mti, processingCode, amount
 *   sourceAccount, targetAccount
 *   status, rawMessage (base64)
 *   callbackKey, createdAt, expireAt
 * TTL: 完成後 5 分鐘
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingTransactionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交易唯一識別碼 (UUID)
     */
    private String transactionId;

    /**
     * STAN (System Trace Audit Number)
     */
    private String stan;

    /**
     * MTI (Message Type Indicator)
     */
    private String mti;

    /**
     * Processing Code
     */
    private String processingCode;

    /**
     * 來源帳號
     */
    private String sourceAccount;

    /**
     * 目標帳號
     */
    private String targetAccount;

    /**
     * 交易金額 (以分為單位)
     */
    private BigDecimal amount;

    /**
     * 來源銀行代碼
     */
    private String sourceBankCode;

    /**
     * 目標銀行代碼
     */
    private String targetBankCode;

    /**
     * 交易狀態
     */
    private TransactionStatus status;

    /**
     * 原始請求電文 (Base64 編碼)
     */
    private String rawRequestBase64;

    /**
     * 原始回應電文 (Base64 編碼)
     */
    private String rawResponseBase64;

    /**
     * 回應碼
     */
    private String responseCode;

    /**
     * Callback Key (用於關聯回應)
     */
    private String callbackKey;

    /**
     * 通道 ID
     */
    private String channelId;

    /**
     * BPMN Process ID (Request 流程)
     */
    private String processId;

    /**
     * 建立時間 (epoch ms)
     */
    private Long createdAt;

    /**
     * 發送至 FISC 時間 (epoch ms)
     */
    private Long sentToFiscAt;

    /**
     * FISC 回應時間 (epoch ms)
     */
    private Long fiscResponseAt;

    /**
     * 過期時間 (epoch ms)
     */
    private Long expireAt;

    /**
     * 沖正狀態
     */
    private ReversalStatus reversalStatus;

    /**
     * 交易日期 (yyyyMMdd 格式，用於 DB 分區)
     */
    private String transactionDate;

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
         * 沖正失敗，待調帳
         */
        FAILED,

        /**
         * 待人工調帳
         */
        PENDING_ADJUSTMENT
    }
}
