package com.fep.communication.handler;

import com.fep.common.event.TransactionRequestEvent;
import com.fep.common.event.TransactionRequestEvent.TransactionType;
import com.fep.common.message.InternalMessage;
import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.parser.GenericMessageAssembler;
import com.fep.message.generic.schema.MessageSchema;
import com.fep.message.transform.MessageTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * BPMN 整合的 ServerMessageHandler 實作
 *
 * <p>此 Handler 將收到的 GenericMessage 轉換為 InternalMessage，
 * 然後透過 Spring Application Events 發布給 fep-transaction 模組。
 *
 * <p>轉換流程：
 * <pre>
 * GenericMessage (外部格式，如 ATM/FISC)
 *         ↓ [MessageTransformer.toInternal]
 * InternalMessage (FEP 內部統一格式)
 *         ↓ [發布事件]
 * TransactionRequestEvent
 *         ↓
 * BPMN 流程處理
 *         ↓
 * InternalMessage (回應)
 *         ↓ [MessageTransformer.toExternal]
 * GenericMessage (外部格式)
 *         ↓ [Encoder]
 * byte[] (發送給客戶端)
 * </pre>
 */
@Slf4j
public class BpmnServerMessageHandler implements ServerMessageHandler {

    /**
     * Spring 事件發布器
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 流程路由解析器
     */
    private final ProcessKeyResolver processKeyResolver;

    /**
     * 訊息轉換器
     */
    private final MessageTransformer messageTransformer;

    /**
     * Schema 提供者（用於組裝回應）
     */
    private final Supplier<Map<String, MessageSchema>> schemaProvider;

    /**
     * 訊息組裝器
     */
    private final GenericMessageAssembler assembler;

    /**
     * STAN → Response Callback 映射
     */
    private final Map<String, ResponseCallbackInfo> pendingCallbacks = new ConcurrentHashMap<>();

    /**
     * Callback 過期時間 (毫秒)
     */
    private static final long CALLBACK_TTL_MS = 60000; // 60 秒

    /**
     * 流程 Key 解析器介面
     */
    @FunctionalInterface
    public interface ProcessKeyResolver {
        String resolve(String channelId, String mti, String processingCode);
    }

    /**
     * 建構函數
     */
    public BpmnServerMessageHandler(ApplicationEventPublisher eventPublisher,
                                     ProcessKeyResolver processKeyResolver,
                                     MessageTransformer messageTransformer) {
        this(eventPublisher, processKeyResolver, messageTransformer, null);
    }

    /**
     * 建構函數（含 Schema 提供者）
     */
    public BpmnServerMessageHandler(ApplicationEventPublisher eventPublisher,
                                     ProcessKeyResolver processKeyResolver,
                                     MessageTransformer messageTransformer,
                                     Supplier<Map<String, MessageSchema>> schemaProvider) {
        this.eventPublisher = eventPublisher;
        this.processKeyResolver = processKeyResolver;
        this.messageTransformer = messageTransformer;
        this.schemaProvider = schemaProvider;
        this.assembler = new GenericMessageAssembler();

        log.info("BpmnServerMessageHandler 初始化完成 (使用 InternalMessage 架構)");
    }

    @Override
    public void handleMessage(ServerMessageContext context) {
        // 取得 GenericMessage（由 FiscDualChannelServer 轉換）
        GenericMessage genericMessage = context.getGenericMessage();
        if (genericMessage == null) {
            log.error("[{}] 無法取得 GenericMessage", context.getChannelId());
            sendErrorResponse(context, "96");
            return;
        }

        String channelId = context.getChannelId();
        String clientId = context.getClientId();
        String mti = genericMessage.getFieldAsString("mti");
        String stan = genericMessage.getFieldAsString("stan");
        String processingCode = genericMessage.getFieldAsString("processingCode");

        log.debug("[{}] BPMN Handler 收到訊息: MTI={}, STAN={}, processingCode={}, client={}",
                channelId, mti, stan, processingCode, clientId);

        try {
            // 1. 轉換為內部格式
            InternalMessage internal = messageTransformer.toInternal(genericMessage, channelId);
            internal.setSourceClientId(clientId);
            internal.setTransactionTime(LocalDateTime.now());

            // 2. 解析流程 Key
            String processKey = processKeyResolver.resolve(channelId, mti, processingCode);
            internal.setProcessKey(processKey);
            log.debug("[{}] MTI={} 路由至流程: {}", channelId, mti, processKey);

            // 3. 判斷交易類型
            TransactionType transactionType = TransactionType.fromMtiAndProcessingCode(mti, processingCode);

            // 4. 建立 response callback
            Consumer<byte[]> responseCallback = createResponseCallback(context, internal);

            // 5. 註冊 callback
            registerCallback(stan, channelId, clientId, context, responseCallback, internal);

            // 6. 發布事件
            publishTransactionEvent(internal, transactionType, processKey, responseCallback);

        } catch (Exception e) {
            log.error("[{}] 處理訊息失敗: MTI={}, STAN={}, error={}",
                    channelId, mti, stan, e.getMessage(), e);
            sendErrorResponse(context, "96");
        }
    }

    /**
     * 建立回應 callback
     */
    private Consumer<byte[]> createResponseCallback(ServerMessageContext context,
                                                     InternalMessage requestInternal) {
        String channelId = context.getChannelId();
        String stan = requestInternal.getTraceNumber();

        return responseData -> {
            try {
                if (responseData != null && responseData.length > 0) {
                    // 回應資料是已組裝好的 byte[]，直接發送
                    context.sendRawResponse(responseData);
                    log.debug("[{}] 已發送回應: STAN={}, {} bytes",
                            channelId, stan, responseData.length);
                } else {
                    log.error("[{}] 回應資料為空: STAN={}", channelId, stan);
                }
            } catch (Exception e) {
                log.error("[{}] 發送回應失敗: STAN={}, error={}",
                        channelId, stan, e.getMessage(), e);
            } finally {
                removeCallback(stan, channelId, context.getClientId());
            }
        };
    }

    /**
     * 發布交易請求事件
     */
    private void publishTransactionEvent(InternalMessage internal,
                                          TransactionType transactionType,
                                          String processKey,
                                          Consumer<byte[]> responseCallback) {
        TransactionRequestEvent event = TransactionRequestEvent.builder()
                .source(this)
                .message(internal)
                .transactionType(transactionType)
                .processKey(processKey)
                .responseCallback(responseCallback)
                .build();

        log.debug("[{}] 發布 TransactionRequestEvent: STAN={}, processKey={}, type={}",
                internal.getSourceChannelId(), internal.getTraceNumber(),
                processKey, transactionType);

        eventPublisher.publishEvent(event);
    }

    /**
     * 將 InternalMessage 組裝為 byte[] 回應
     *
     * <p>轉換流程：
     * <pre>
     * InternalMessage → MessageTransformer.toExternal() → GenericMessage (含 Schema)
     *                → GenericMessageAssembler.assemble() → byte[]
     * </pre>
     *
     * @param internal 內部訊息
     * @param targetChannelId 目標通道 ID
     * @return 組裝好的 byte[]
     */
    public byte[] assembleResponse(InternalMessage internal, String targetChannelId) {
        try {
            // 轉換為外部格式 (toExternal 已經設定好 Schema)
            GenericMessage external = messageTransformer.toExternal(internal, targetChannelId);

            // 確保有 Schema
            if (external.getSchema() == null) {
                // Fallback: 從 schemaProvider 取得
                MessageSchema schema = getSchemaFromProvider(targetChannelId);
                if (schema == null) {
                    log.error("找不到 Schema: channelId={}", targetChannelId);
                    return new byte[0];
                }
                // 建立新的 GenericMessage 並複製欄位
                external = copyMessageWithSchema(external, schema);
            }

            // 組裝為 byte[]
            return assembler.assemble(external);

        } catch (Exception e) {
            log.error("組裝回應失敗: channelId={}, error={}", targetChannelId, e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * 從 Provider 取得 Schema
     */
    private MessageSchema getSchemaFromProvider(String channelId) {
        if (schemaProvider != null) {
            Map<String, MessageSchema> schemas = schemaProvider.get();
            if (schemas != null) {
                return schemas.get(channelId);
            }
        }
        return null;
    }

    /**
     * 複製訊息並設定新的 Schema
     */
    private GenericMessage copyMessageWithSchema(GenericMessage source, MessageSchema schema) {
        GenericMessage target = new GenericMessage(schema);
        for (Map.Entry<String, Object> entry : source.getAllFields().entrySet()) {
            target.setField(entry.getKey(), entry.getValue());
        }
        return target;
    }

    /**
     * 取得 Schema (向下相容方法)
     * @deprecated 使用 {@link #getSchemaFromProvider(String)} 代替
     */
    @Deprecated
    private MessageSchema getSchema(String channelId, GenericMessage message) {
        if (message.getSchema() != null) {
            return message.getSchema();
        }
        if (schemaProvider != null) {
            Map<String, MessageSchema> schemas = schemaProvider.get();
            if (schemas != null) {
                return schemas.get(channelId);
            }
        }
        return null;
    }

    /**
     * 註冊 callback
     */
    private void registerCallback(String stan, String channelId, String clientId,
                                   ServerMessageContext context,
                                   Consumer<byte[]> callback,
                                   InternalMessage internal) {
        String callbackKey = generateCallbackKey(channelId, clientId, stan);
        ResponseCallbackInfo info = new ResponseCallbackInfo(
                callback, context, internal, System.currentTimeMillis());
        pendingCallbacks.put(callbackKey, info);
        log.debug("註冊 callback: key={}, pending count={}", callbackKey, pendingCallbacks.size());

        cleanupExpiredCallbacks();
    }

    /**
     * 移除 callback
     */
    private void removeCallback(String stan, String channelId, String clientId) {
        String callbackKey = generateCallbackKey(channelId, clientId, stan);
        pendingCallbacks.remove(callbackKey);
        log.debug("移除 callback: key={}, pending count={}", callbackKey, pendingCallbacks.size());
    }

    /**
     * 產生 callback key
     */
    private String generateCallbackKey(String channelId, String clientId, String stan) {
        return String.format("%s:%s:%s",
                channelId != null ? channelId : "UNKNOWN",
                clientId != null ? clientId : "UNKNOWN",
                stan != null ? stan : "000000");
    }

    /**
     * 清理過期的 callbacks
     */
    private void cleanupExpiredCallbacks() {
        long now = System.currentTimeMillis();
        int removed = 0;

        var iterator = pendingCallbacks.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue().createdTime() > CALLBACK_TTL_MS) {
                iterator.remove();
                removed++;
                log.warn("清理過期 callback: key={}", entry.getKey());
            }
        }

        if (removed > 0) {
            log.info("已清理 {} 個過期 callbacks", removed);
        }
    }

    /**
     * 發送錯誤回應
     */
    private void sendErrorResponse(ServerMessageContext context, String responseCode) {
        GenericMessage request = context.getGenericMessage();
        if (request == null) {
            log.error("[{}] 無法發送錯誤回應：request 為 null", context.getChannelId());
            return;
        }

        try {
            // 建立錯誤回應的 InternalMessage
            InternalMessage errorInternal = InternalMessage.builder()
                    .messageType(InternalMessage.MessageType.FINANCIAL_RESPONSE)
                    .transactionCode(request.getFieldAsString("processingCode"))
                    .traceNumber(request.getFieldAsString("stan"))
                    .cardNumber(request.getFieldAsString("pan"))
                    .terminalId(request.getFieldAsString("terminalId"))
                    .merchantId(request.getFieldAsString("merchantId"))
                    .responseCode(responseCode)
                    .transactionTime(LocalDateTime.now())
                    .build();

            // 轉換金額
            String amount = request.getFieldAsString("amount");
            if (amount != null) {
                try {
                    errorInternal.setTransactionAmount(Long.parseLong(amount.trim()));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }

            // 組裝並發送
            byte[] responseBytes = assembleResponse(errorInternal, context.getChannelId());
            if (responseBytes.length > 0) {
                context.sendRawResponse(responseBytes);
            }

            log.warn("[{}] 發送錯誤回應: STAN={}, RC={}",
                    context.getChannelId(),
                    request.getFieldAsString("stan"),
                    responseCode);

        } catch (Exception e) {
            log.error("[{}] 發送錯誤回應失敗: {}", context.getChannelId(), e.getMessage(), e);
        }
    }

    /**
     * 取得待處理的 callback 數量
     */
    public int getPendingCallbackCount() {
        return pendingCallbacks.size();
    }

    /**
     * 透過 callback key 發送回應
     */
    public boolean sendResponseByCallbackKey(String callbackKey, byte[] responseData) {
        ResponseCallbackInfo info = pendingCallbacks.get(callbackKey);
        if (info == null) {
            log.warn("找不到 callback: key={}", callbackKey);
            return false;
        }

        try {
            info.callback().accept(responseData);
            return true;
        } catch (Exception e) {
            log.error("發送回應失敗: key={}, error={}", callbackKey, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 取得原始請求的 InternalMessage
     */
    public InternalMessage getRequestInternal(String callbackKey) {
        ResponseCallbackInfo info = pendingCallbacks.get(callbackKey);
        return info != null ? info.requestInternal() : null;
    }

    /**
     * 透過 STAN 發送回應 (向下相容方法)
     *
     * <p>此方法遍歷所有 pending callbacks，找到以指定 STAN 結尾的 key。
     * 不建議使用此方法，因為當有多個客戶端使用相同 STAN 時會產生歧義。
     *
     * @param stan 交易序號
     * @param response 回應訊息 (Iso8583Message)
     * @return true 如果發送成功
     * @deprecated 使用 {@link #sendResponseByCallbackKey(String, byte[])} 代替
     */
    @Deprecated
    public boolean sendResponseByStan(String stan, com.fep.message.iso8583.Iso8583Message response) {
        // 遍歷找到以 stan 結尾的 callback key
        for (String key : pendingCallbacks.keySet()) {
            if (key.endsWith(":" + stan)) {
                ResponseCallbackInfo info = pendingCallbacks.get(key);
                if (info != null) {
                    try {
                        // 組裝回應 byte[]
                        byte[] responseData = assembleResponse(
                                createInternalMessageFromIso(response),
                                info.context().getChannelId());
                        if (responseData.length > 0) {
                            info.callback().accept(responseData);
                            return true;
                        }
                    } catch (Exception e) {
                        log.error("發送回應失敗: stan={}, error={}", stan, e.getMessage(), e);
                    }
                }
            }
        }
        log.warn("找不到 STAN 對應的 callback: stan={}", stan);
        return false;
    }

    /**
     * 從 Iso8583Message 建立 InternalMessage (簡易轉換)
     */
    private InternalMessage createInternalMessageFromIso(com.fep.message.iso8583.Iso8583Message iso) {
        return InternalMessage.builder()
                .messageType(InternalMessage.MessageType.fromMti(iso.getMti()))
                .traceNumber(iso.getFieldAsString(11))
                .responseCode(iso.getFieldAsString(39))
                .cardNumber(iso.getFieldAsString(2))
                .transactionCode(iso.getFieldAsString(3))
                .build();
    }

    /**
     * Callback 資訊記錄
     */
    private record ResponseCallbackInfo(
            Consumer<byte[]> callback,
            ServerMessageContext context,
            InternalMessage requestInternal,
            long createdTime
    ) {}
}
