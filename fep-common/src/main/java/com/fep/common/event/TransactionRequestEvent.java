package com.fep.common.event;

import com.fep.common.message.InternalMessage;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.util.function.Consumer;

/**
 * 交易請求事件
 *
 * <p>當 FEP Server 收到來自 ATM/POS 的交易請求時發布此事件，
 * 用於觸發 BPMN 流程處理。
 *
 * <p>此事件包含 InternalMessage（FEP 內部統一格式），
 * 業務邏輯只需操作 InternalMessage，不需關心外部電文格式。
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
 *                        ▼ [MessageTransformer.toInternal]
 *               InternalMessage
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
@ToString(exclude = "responseCallback")
public class TransactionRequestEvent extends ApplicationEvent {

    /**
     * 內部訊息（FEP 統一格式）
     */
    private final InternalMessage message;

    /**
     * 交易類型
     */
    private final TransactionType transactionType;

    /**
     * BPMN 流程 Key - 由 ProcessRouterService 解析後傳入
     */
    private final String processKey;

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
            InternalMessage message,
            TransactionType transactionType,
            String processKey,
            Consumer<byte[]> responseCallback) {

        super(source);
        this.message = message;
        this.transactionType = transactionType;
        this.processKey = processKey;
        this.responseCallback = responseCallback;
    }

    // ==================== 便捷存取方法（委派給 InternalMessage）====================

    /**
     * 取得 MTI
     */
    public String getMti() {
        if (message == null) return null;
        if (message.getMessageType() != null) {
            return message.getMessageType().getMtiCode();
        }
        // Fallback: 檢查擴充欄位中的原始 MTI（用於未知 MTI）
        return message.getExtendedFieldAsString("originalMti");
    }

    /**
     * 取得 STAN
     */
    public String getStan() {
        return message != null ? message.getTraceNumber() : null;
    }

    /**
     * 取得通道 ID
     */
    public String getChannelId() {
        return message != null ? message.getSourceChannelId() : null;
    }

    /**
     * 取得客戶端 ID
     */
    public String getClientId() {
        return message != null ? message.getSourceClientId() : null;
    }

    /**
     * 取得 Processing Code
     */
    public String getProcessingCode() {
        return message != null ? message.getTransactionCode() : null;
    }

    /**
     * 取得交易金額
     */
    public Long getAmount() {
        return message != null ? message.getTransactionAmount() : null;
    }

    /**
     * 取得交易金額（字串格式）
     */
    public String getAmountAsString() {
        Long amount = getAmount();
        return amount != null ? String.valueOf(amount) : null;
    }

    /**
     * 取得卡號
     */
    public String getPan() {
        return message != null ? message.getCardNumber() : null;
    }

    /**
     * 取得來源帳號
     */
    public String getSourceAccount() {
        return message != null ? message.getSourceAccount() : null;
    }

    /**
     * 取得目標帳號
     */
    public String getTargetAccount() {
        return message != null ? message.getDestinationAccount() : null;
    }

    /**
     * 取得來源銀行代碼
     */
    public String getSourceBankCode() {
        return message != null ? message.getSourceBankCode() : null;
    }

    /**
     * 取得目標銀行代碼
     */
    public String getTargetBankCode() {
        return message != null ? message.getDestinationBankCode() : null;
    }

    /**
     * 取得終端機 ID
     */
    public String getTerminalId() {
        return message != null ? message.getTerminalId() : null;
    }

    /**
     * 取得商戶 ID
     */
    public String getMerchantId() {
        return message != null ? message.getMerchantId() : null;
    }

    /**
     * 取得 RRN
     */
    public String getRrn() {
        return message != null ? message.getReferenceNumber() : null;
    }

    /**
     * 取得原始電文
     */
    public byte[] getRawMessage() {
        return message != null ? message.getRawData() : null;
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
         * 網路管理 (MTI: 0800)
         */
        NETWORK_MANAGEMENT,

        /**
         * 未知交易類型
         */
        UNKNOWN;

        /**
         * 從 MTI 和 Processing Code 判斷交易類型
         */
        public static TransactionType fromMtiAndProcessingCode(String mti, String processingCode) {
            // 沖正交易
            if (mti != null && (mti.startsWith("04"))) {
                return REVERSAL;
            }

            // 網路管理
            if (mti != null && mti.startsWith("08")) {
                return NETWORK_MANAGEMENT;
            }

            // 根據 Processing Code 判斷
            if (processingCode == null || processingCode.length() < 2) {
                return UNKNOWN;
            }

            String typeCode = processingCode.substring(0, 2);
            return switch (typeCode) {
                case "40" -> TRANSFER;
                case "01" -> WITHDRAWAL;
                case "31", "30" -> BALANCE_INQUIRY;
                case "50" -> BILL_PAYMENT;
                default -> UNKNOWN;
            };
        }
    }
}
