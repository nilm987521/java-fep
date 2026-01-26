package com.fep.message.transform;

import com.fep.common.message.InternalMessage;
import com.fep.message.generic.message.GenericMessage;

/**
 * 訊息轉換器介面
 *
 * <p>負責外部訊息格式 (GenericMessage) 與內部訊息格式 (InternalMessage) 之間的轉換。
 *
 * <p>轉換流程：
 * <pre>
 * 接收訊息：
 *   外部電文 (ATM/FISC/CBS)
 *       ↓ [Decoder]
 *   GenericMessage (外部格式)
 *       ↓ [toInternal]
 *   InternalMessage (內部格式)
 *
 * 發送訊息：
 *   InternalMessage (內部格式)
 *       ↓ [toExternal]
 *   GenericMessage (外部格式)
 *       ↓ [Encoder]
 *   外部電文 (目標通道格式)
 * </pre>
 */
public interface MessageTransformer {

    /**
     * 將外部訊息轉換為內部格式
     *
     * @param external 外部訊息 (GenericMessage)
     * @param sourceChannelId 來源通道 ID
     * @return 內部訊息
     * @throws TransformException 轉換失敗時拋出
     */
    InternalMessage toInternal(GenericMessage external, String sourceChannelId) throws TransformException;

    /**
     * 將內部格式轉換為外部訊息
     *
     * @param internal 內部訊息
     * @param targetChannelId 目標通道 ID
     * @return 外部訊息 (GenericMessage)
     * @throws TransformException 轉換失敗時拋出
     */
    GenericMessage toExternal(InternalMessage internal, String targetChannelId) throws TransformException;

    /**
     * 檢查是否支援指定通道
     *
     * @param channelId 通道 ID
     * @return 是否支援
     */
    boolean supportsChannel(String channelId);

    /**
     * 轉換例外
     */
    class TransformException extends RuntimeException {
        private final String channelId;
        private final String fieldName;

        public TransformException(String message) {
            super(message);
            this.channelId = null;
            this.fieldName = null;
        }

        public TransformException(String message, Throwable cause) {
            super(message, cause);
            this.channelId = null;
            this.fieldName = null;
        }

        public TransformException(String channelId, String fieldName, String message) {
            super(String.format("[%s] Field '%s': %s", channelId, fieldName, message));
            this.channelId = channelId;
            this.fieldName = fieldName;
        }

        public TransformException(String channelId, String fieldName, String message, Throwable cause) {
            super(String.format("[%s] Field '%s': %s", channelId, fieldName, message), cause);
            this.channelId = channelId;
            this.fieldName = fieldName;
        }

        public String getChannelId() {
            return channelId;
        }

        public String getFieldName() {
            return fieldName;
        }
    }
}
