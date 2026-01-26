package com.fep.common.message;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * FEP 內部統一訊息格式
 *
 * <p>這是 FEP 系統內部處理的標準訊息格式，所有外部電文（ATM、FISC、CBS 等）
 * 都會先轉換為此格式，業務邏輯只操作此格式，輸出時再轉換為目標通道的格式。
 *
 * <p>架構設計：
 * <pre>
 * 外部電文 (ATM/FISC/CBS)
 *     ↓ [MessageTransformer.toInternal]
 * InternalMessage (統一格式)
 *     ↓ [業務邏輯處理]
 * InternalMessage
 *     ↓ [MessageTransformer.toExternal]
 * 外部電文 (目標通道格式)
 * </pre>
 *
 * <p>好處：
 * <ul>
 *   <li>業務邏輯與電文格式解耦</li>
 *   <li>新增通道只需定義 Schema 和 Field Mapping</li>
 *   <li>日誌和監控使用統一的欄位名稱</li>
 *   <li>可獨立測試轉換邏輯和業務邏輯</li>
 * </ul>
 */
@Slf4j
@Getter
@Setter
@ToString(exclude = {"rawData", "sensitiveFields"})
public class InternalMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 訊息識別 ====================

    /**
     * 訊息類型
     */
    private MessageType messageType;

    /**
     * 交易類型代碼 (對應 Processing Code)
     */
    private String transactionCode;

    /**
     * 系統追蹤號 (STAN)
     */
    private String traceNumber;

    /**
     * 參考號 (RRN)
     */
    private String referenceNumber;

    /**
     * 業務鍵 (用於 BPMN 流程)
     */
    private String businessKey;

    // ==================== 來源/目標通道 ====================

    /**
     * 來源通道 ID
     */
    private String sourceChannelId;

    /**
     * 來源客戶端 ID (如 ATM 的 IP:Port)
     */
    private String sourceClientId;

    /**
     * 目標通道 ID
     */
    private String targetChannelId;

    // ==================== 卡片/帳戶資訊 ====================

    /**
     * 卡號 (PAN)
     */
    private String cardNumber;

    /**
     * 來源帳號
     */
    private String sourceAccount;

    /**
     * 目的帳號
     */
    private String destinationAccount;

    /**
     * 來源銀行代碼
     */
    private String sourceBankCode;

    /**
     * 目標銀行代碼
     */
    private String destinationBankCode;

    // ==================== 交易資訊 ====================

    /**
     * 交易金額 (最小單位，如分)
     */
    private Long transactionAmount;

    /**
     * 幣別代碼
     */
    private String currencyCode;

    /**
     * 交易時間
     */
    private LocalDateTime transactionTime;

    // ==================== 終端資訊 ====================

    /**
     * 終端機代碼
     */
    private String terminalId;

    /**
     * 商戶代碼
     */
    private String merchantId;

    /**
     * 商戶名稱
     */
    private String merchantName;

    // ==================== 回應資訊 ====================

    /**
     * 回應碼
     */
    private String responseCode;

    /**
     * 回應訊息
     */
    private String responseMessage;

    /**
     * 授權碼
     */
    private String authorizationCode;

    // ==================== 擴充欄位 ====================

    /**
     * 擴充欄位 (用於非標準欄位)
     */
    private final Map<String, Object> extendedFields = new HashMap<>();

    /**
     * 敏感欄位標記 (不會出現在日誌中)
     */
    private final Map<String, Boolean> sensitiveFields = new HashMap<>();

    // ==================== 原始資料 ====================

    /**
     * 原始電文資料 (用於組裝回應或除錯)
     */
    private byte[] rawData;

    /**
     * 原始訊息來源 Schema 名稱
     */
    private String sourceSchemaName;

    // ==================== 追蹤資訊 ====================

    /**
     * 訊息建立時間
     */
    private final LocalDateTime createdAt;

    /**
     * 追蹤 ID (用於日誌關聯)
     */
    private String traceId;

    /**
     * BPMN 流程 Key
     */
    private String processKey;

    // ==================== 建構子 ====================

    public InternalMessage() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public InternalMessage(
            MessageType messageType,
            String transactionCode,
            String traceNumber,
            String referenceNumber,
            String businessKey,
            String sourceChannelId,
            String sourceClientId,
            String targetChannelId,
            String cardNumber,
            String sourceAccount,
            String destinationAccount,
            String sourceBankCode,
            String destinationBankCode,
            Long transactionAmount,
            String currencyCode,
            LocalDateTime transactionTime,
            String terminalId,
            String merchantId,
            String merchantName,
            String responseCode,
            String responseMessage,
            String authorizationCode,
            byte[] rawData,
            String sourceSchemaName,
            String traceId,
            String processKey) {
        this.createdAt = LocalDateTime.now();
        this.messageType = messageType;
        this.transactionCode = transactionCode;
        this.traceNumber = traceNumber;
        this.referenceNumber = referenceNumber;
        this.businessKey = businessKey;
        this.sourceChannelId = sourceChannelId;
        this.sourceClientId = sourceClientId;
        this.targetChannelId = targetChannelId;
        this.cardNumber = cardNumber;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.sourceBankCode = sourceBankCode;
        this.destinationBankCode = destinationBankCode;
        this.transactionAmount = transactionAmount;
        this.currencyCode = currencyCode;
        this.transactionTime = transactionTime;
        this.terminalId = terminalId;
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.authorizationCode = authorizationCode;
        this.rawData = rawData;
        this.sourceSchemaName = sourceSchemaName;
        this.traceId = traceId;
        this.processKey = processKey;
    }

    // ==================== 擴充欄位操作 ====================

    /**
     * 設定擴充欄位
     */
    public void setExtendedField(String key, Object value) {
        extendedFields.put(key, value);
    }

    /**
     * 設定擴充欄位（標記為敏感）
     */
    public void setExtendedField(String key, Object value, boolean sensitive) {
        extendedFields.put(key, value);
        if (sensitive) {
            sensitiveFields.put(key, true);
        }
    }

    /**
     * 取得擴充欄位
     */
    public Object getExtendedField(String key) {
        return extendedFields.get(key);
    }

    /**
     * 取得擴充欄位（字串）
     */
    public String getExtendedFieldAsString(String key) {
        Object value = extendedFields.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 是否有擴充欄位
     */
    public boolean hasExtendedField(String key) {
        return extendedFields.containsKey(key);
    }

    // ==================== 工具方法 ====================

    /**
     * 取得遮罩後的卡號 (用於日誌)
     */
    public String getMaskedCardNumber() {
        return maskSensitive(cardNumber);
    }

    /**
     * 取得遮罩後的來源帳號 (用於日誌)
     */
    public String getMaskedSourceAccount() {
        return maskSensitive(sourceAccount);
    }

    /**
     * 取得遮罩後的目的帳號 (用於日誌)
     */
    public String getMaskedDestinationAccount() {
        return maskSensitive(destinationAccount);
    }

    /**
     * 遮罩敏感資料
     */
    private String maskSensitive(String value) {
        if (value == null || value.length() < 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    /**
     * 判斷是否為請求訊息
     */
    public boolean isRequest() {
        return messageType != null && messageType.isRequest();
    }

    /**
     * 判斷是否為回應訊息
     */
    public boolean isResponse() {
        return messageType != null && messageType.isResponse();
    }

    /**
     * 判斷交易是否成功
     */
    public boolean isSuccess() {
        return "00".equals(responseCode) || "000".equals(responseCode);
    }

    /**
     * 建立回應訊息
     */
    public InternalMessage createResponse(String responseCode, String responseMessage) {
        InternalMessage response = InternalMessage.builder()
                .messageType(this.messageType != null ? this.messageType.toResponse() : MessageType.FINANCIAL_RESPONSE)
                .transactionCode(this.transactionCode)
                .traceNumber(this.traceNumber)
                .referenceNumber(this.referenceNumber)
                .businessKey(this.businessKey)
                .sourceChannelId(this.sourceChannelId)
                .sourceClientId(this.sourceClientId)
                .cardNumber(this.cardNumber)
                .sourceAccount(this.sourceAccount)
                .destinationAccount(this.destinationAccount)
                .transactionAmount(this.transactionAmount)
                .currencyCode(this.currencyCode)
                .terminalId(this.terminalId)
                .merchantId(this.merchantId)
                .responseCode(responseCode)
                .responseMessage(responseMessage)
                .traceId(this.traceId)
                .build();

        // 複製擴充欄位
        response.extendedFields.putAll(this.extendedFields);

        return response;
    }

    /**
     * 複製訊息（淺複製）
     */
    public InternalMessage copy() {
        InternalMessage copy = InternalMessage.builder()
                .messageType(this.messageType)
                .transactionCode(this.transactionCode)
                .traceNumber(this.traceNumber)
                .referenceNumber(this.referenceNumber)
                .businessKey(this.businessKey)
                .sourceChannelId(this.sourceChannelId)
                .sourceClientId(this.sourceClientId)
                .targetChannelId(this.targetChannelId)
                .cardNumber(this.cardNumber)
                .sourceAccount(this.sourceAccount)
                .destinationAccount(this.destinationAccount)
                .sourceBankCode(this.sourceBankCode)
                .destinationBankCode(this.destinationBankCode)
                .transactionAmount(this.transactionAmount)
                .currencyCode(this.currencyCode)
                .transactionTime(this.transactionTime)
                .terminalId(this.terminalId)
                .merchantId(this.merchantId)
                .merchantName(this.merchantName)
                .responseCode(this.responseCode)
                .responseMessage(this.responseMessage)
                .authorizationCode(this.authorizationCode)
                .rawData(this.rawData)
                .sourceSchemaName(this.sourceSchemaName)
                .traceId(this.traceId)
                .processKey(this.processKey)
                .build();

        copy.extendedFields.putAll(this.extendedFields);
        copy.sensitiveFields.putAll(this.sensitiveFields);

        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InternalMessage that = (InternalMessage) o;
        return Objects.equals(traceNumber, that.traceNumber) &&
               Objects.equals(sourceChannelId, that.sourceChannelId) &&
               Objects.equals(sourceClientId, that.sourceClientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceNumber, sourceChannelId, sourceClientId);
    }

    /**
     * 訊息類型枚舉
     */
    public enum MessageType {
        // 金融交易
        FINANCIAL_REQUEST("0200", true),
        FINANCIAL_RESPONSE("0210", false),

        // 沖正交易
        REVERSAL_REQUEST("0400", true),
        REVERSAL_RESPONSE("0410", false),

        // 網路管理
        NETWORK_REQUEST("0800", true),
        NETWORK_RESPONSE("0810", false),

        // 授權
        AUTHORIZATION_REQUEST("0100", true),
        AUTHORIZATION_RESPONSE("0110", false);

        private final String mtiCode;
        private final boolean request;

        MessageType(String mtiCode, boolean request) {
            this.mtiCode = mtiCode;
            this.request = request;
        }

        public String getMtiCode() {
            return mtiCode;
        }

        public boolean isRequest() {
            return request;
        }

        public boolean isResponse() {
            return !request;
        }

        /**
         * 轉換為對應的回應類型
         */
        public MessageType toResponse() {
            return switch (this) {
                case FINANCIAL_REQUEST -> FINANCIAL_RESPONSE;
                case REVERSAL_REQUEST -> REVERSAL_RESPONSE;
                case NETWORK_REQUEST -> NETWORK_RESPONSE;
                case AUTHORIZATION_REQUEST -> AUTHORIZATION_RESPONSE;
                default -> this;
            };
        }

        /**
         * 從 MTI 代碼解析
         */
        public static MessageType fromMti(String mti) {
            if (mti == null) return null;
            for (MessageType type : values()) {
                if (type.mtiCode.equals(mti)) {
                    return type;
                }
            }
            // 嘗試模糊匹配
            return switch (mti.substring(0, 2)) {
                case "01" -> mti.endsWith("0") ? AUTHORIZATION_RESPONSE : AUTHORIZATION_REQUEST;
                case "02" -> mti.endsWith("0") ? FINANCIAL_RESPONSE : FINANCIAL_REQUEST;
                case "04" -> mti.endsWith("0") ? REVERSAL_RESPONSE : REVERSAL_REQUEST;
                case "08" -> mti.endsWith("0") ? NETWORK_RESPONSE : NETWORK_REQUEST;
                default -> null;
            };
        }
    }
}
