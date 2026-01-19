package com.fep.common.event;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

/**
 * FISC 回應事件
 *
 * <p>當 FEP 收到 FISC 的回應訊息 (0210/0410) 時發布此事件，
 * 用於觸發 BPMN 流程的訊息關聯 (Message Correlation)。
 *
 * <p>流程：
 * <pre>
 * FISC ──0210──► FiscDualChannelClient (ReceiveChannel)
 *                        │
 *                        ▼ (publish event)
 *                 FiscResponseEvent
 *                        │
 *                        ▼
 *                TransactionEventListener
 *                        │
 *                        ▼ (correlateMessage)
 *                BPMN Process (Message Catch Event)
 * </pre>
 */
@Getter
@ToString
public class FiscResponseEvent extends ApplicationEvent {

    /**
     * 回應類型
     */
    private final ResponseType responseType;

    /**
     * MTI (e.g., "0210", "0410")
     */
    private final String mti;

    /**
     * STAN - 用於匹配原始請求
     */
    private final String stan;

    /**
     * 回應碼 (欄位 39)
     */
    private final String responseCode;

    /**
     * 原始電文內容
     */
    private final byte[] rawMessage;

    /**
     * 通道 ID
     */
    private final String channelId;

    /**
     * 回應時間 (毫秒)
     */
    private final long responseTime;

    /**
     * 授權碼 (欄位 38, 可選)
     */
    private final String authCode;

    /**
     * 事件建構器
     */
    @Builder
    public FiscResponseEvent(
            Object source,
            ResponseType responseType,
            String mti,
            String stan,
            String responseCode,
            byte[] rawMessage,
            String channelId,
            long responseTime,
            String authCode) {

        super(source);
        this.responseType = responseType;
        this.mti = mti;
        this.stan = stan;
        this.responseCode = responseCode;
        this.rawMessage = rawMessage;
        this.channelId = channelId;
        this.responseTime = responseTime;
        this.authCode = authCode;
    }

    /**
     * 判斷回應是否成功
     *
     * @return true 如果回應碼為 "00"
     */
    public boolean isSuccess() {
        return "00".equals(responseCode);
    }

    /**
     * 回應類型枚舉
     */
    public enum ResponseType {
        /**
         * 金融交易回應 (0210)
         */
        FINANCIAL_RESPONSE,

        /**
         * 沖正回應 (0410)
         */
        REVERSAL_RESPONSE,

        /**
         * 網路管理回應 (0810)
         */
        NETWORK_RESPONSE,

        /**
         * 未知回應類型
         */
        UNKNOWN
    }
}
