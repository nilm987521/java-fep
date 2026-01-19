package com.fep.common.event;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.util.function.Consumer;

/**
 * 交易請求事件
 *
 * <p>當 FEP Server 收到來自 ATM/POS 的交易請求 (0200/0400) 時發布此事件，
 * 用於觸發 BPMN 流程處理。
 *
 * <p>事件驅動架構的好處：
 * <ul>
 *   <li>避免模組循環依賴（fep-communication 不依賴 fep-transaction）</li>
 *   <li>支援非同步處理</li>
 *   <li>易於測試與擴展</li>
 * </ul>
 *
 * <p>流程：
 * <pre>
 * ATM ──0200──► BpmnServerMessageHandler
 *                        │
 *                        ▼ (publish event)
 *               TransactionRequestEvent
 *                        │
 *                        ▼
 *               TransactionEventListener (fep-transaction)
 *                        │
 *                        ▼
 *               BPMN Process Started
 * </pre>
 */
@Getter
@ToString
public class TransactionRequestEvent extends ApplicationEvent {

    /**
     * 交易類型
     */
    private final TransactionType transactionType;

    /**
     * 原始電文內容 (序列化後的 byte 陣列)
     */
    private final byte[] rawMessage;

    /**
     * MTI (Message Type Indicator)
     */
    private final String mti;

    /**
     * STAN (System Trace Audit Number)
     */
    private final String stan;

    /**
     * 通道 ID (e.g., "ATM_FISC_V1")
     */
    private final String channelId;

    /**
     * 客戶端 ID (e.g., "127.0.0.1:12345")
     */
    private final String clientId;

    /**
     * Processing Code (欄位 3)
     */
    private final String processingCode;

    /**
     * 交易金額 (欄位 4)
     */
    private final String amount;

    /**
     * 主帳號 PAN (欄位 2)
     */
    private final String pan;

    /**
     * 目標帳號 (欄位 103, 用於轉帳)
     */
    private final String targetAccount;

    /**
     * 來源銀行代碼 (欄位 32)
     */
    private final String sourceBankCode;

    /**
     * 目標銀行代碼 (欄位 100)
     */
    private final String targetBankCode;

    /**
     * 回應 callback - 用於在 BPMN 流程完成後發送回應給客戶端
     */
    private final Consumer<byte[]> responseCallback;

    /**
     * 事件建構器
     */
    @Builder
    public TransactionRequestEvent(
            Object source,
            TransactionType transactionType,
            byte[] rawMessage,
            String mti,
            String stan,
            String channelId,
            String clientId,
            String processingCode,
            String amount,
            String pan,
            String targetAccount,
            String sourceBankCode,
            String targetBankCode,
            Consumer<byte[]> responseCallback) {

        super(source);
        this.transactionType = transactionType;
        this.rawMessage = rawMessage;
        this.mti = mti;
        this.stan = stan;
        this.channelId = channelId;
        this.clientId = clientId;
        this.processingCode = processingCode;
        this.amount = amount;
        this.pan = pan;
        this.targetAccount = targetAccount;
        this.sourceBankCode = sourceBankCode;
        this.targetBankCode = targetBankCode;
        this.responseCallback = responseCallback;
    }

    /**
     * 交易類型枚舉
     */
    public enum TransactionType {
        /**
         * 跨行轉帳 (Processing Code: 40xxxx)
         */
        TRANSFER,

        /**
         * 跨行提款 (Processing Code: 01xxxx)
         */
        WITHDRAWAL,

        /**
         * 餘額查詢 (Processing Code: 31xxxx)
         */
        BALANCE_INQUIRY,

        /**
         * 繳費 (Processing Code: 50xxxx)
         */
        BILL_PAYMENT,

        /**
         * 沖正 (MTI: 0400)
         */
        REVERSAL,

        /**
         * 未知交易類型
         */
        UNKNOWN
    }
}
