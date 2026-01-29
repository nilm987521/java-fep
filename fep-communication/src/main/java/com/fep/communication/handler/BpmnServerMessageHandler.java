package com.fep.communication.handler;

import com.fep.common.event.TransactionRequestEvent;
import com.fep.common.event.TransactionRequestEvent.TransactionType;
import com.fep.common.message.InternalMessage;
import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.parser.GenericMessageAssembler;
import com.fep.message.generic.schema.MessageSchema;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.transform.MessageTransformer;
import com.fep.communication.logging.ChannelMdcUtil;
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
     * ISO 8583 訊息工廠（用於解析 FISC 格式回應）
     */
    private final Iso8583MessageFactory messageFactory;

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
        this.messageFactory = new Iso8583MessageFactory();

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

        // 取得或生成 traceId
        String traceId = genericMessage.getTraceId();
        if (traceId == null || traceId.isEmpty()) {
            traceId = ChannelMdcUtil.getTraceId();  // 從 MDC 取得
            if (traceId == null || traceId.isEmpty()) {
                traceId = ChannelMdcUtil.generateTraceId();  // Fallback: 生成新的
            }
        }
        ChannelMdcUtil.setTraceId(traceId);

        log.debug("[{}] BPMN Handler 收到訊息: MTI={}, STAN={}, processingCode={}, client={}, traceId={}",
                channelId, mti, stan, processingCode, clientId, traceId);

        try {
            // 1. 轉換為內部格式
            InternalMessage internal = messageTransformer.toInternal(genericMessage, channelId);
            internal.setSourceClientId(clientId);
            internal.setTransactionTime(LocalDateTime.now());

            // 設定 traceId 到 InternalMessage
            internal.setTraceId(traceId);

            // 2. 解析流程 Key
            String processKey = processKeyResolver.resolve(channelId, mti, processingCode);
            internal.setProcessKey(processKey);
            log.debug("[{}] MTI={} 路由至流程: {}, traceId={}", channelId, mti, processKey, traceId);

            // 3. 判斷交易類型
            TransactionType transactionType = TransactionType.fromMtiAndProcessingCode(mti, processingCode);

            // 4. 建立 response callback
            Consumer<byte[]> responseCallback = createResponseCallback(context, internal);

            // 5. 註冊 callback
            registerCallback(stan, channelId, clientId, context, responseCallback, internal);

            // 6. 發布事件
            publishTransactionEvent(internal, transactionType, processKey, responseCallback);

        } catch (Exception e) {
            log.error("[{}] 處理訊息失敗: MTI={}, STAN={}, traceId={}, error={}",
                    channelId, mti, stan, traceId, e.getMessage(), e);
            sendErrorResponse(context, "96");
        }
    }

    /**
     * 建立回應 callback
     *
     * <p>此 callback 會將 BPMN 流程產生的回應（FISC 格式）轉換為 ATM 格式。
     * BPMN delegates 使用 Iso8583MessageFactory.assemble() 產生 FISC 格式：
     * - 2-byte BCD length prefix
     * - BCD encoded MTI (2 bytes)
     * - Binary bitmap
     * - BCD/ASCII encoded fields
     *
     * 但 ATM 客戶端期望 GenericMessage 格式：
     * - 4-byte ASCII length prefix
     * - ASCII encoded MTI (4 bytes)
     * - Binary bitmap
     * - ASCII encoded fields
     *
     * 此 callback 會完整轉換訊息格式。
     */
    private Consumer<byte[]> createResponseCallback(ServerMessageContext context,
                                                     InternalMessage requestInternal) {
        String channelId = context.getChannelId();
        String stan = requestInternal.getTraceNumber();
        String traceId = requestInternal.getTraceId();  // 保留 traceId 用於回應日誌

        // 取得原始請求的 schema（用於組裝回應）
        MessageSchema responseSchema = null;
        GenericMessage requestGeneric = context.getGenericMessage();
        if (requestGeneric != null) {
            responseSchema = requestGeneric.getSchema();
        }
        if (responseSchema == null) {
            responseSchema = getSchemaFromProvider(channelId);
        }
        final MessageSchema finalSchema = responseSchema;

        return responseData -> {
            // 設定 MDC 上下文，確保日誌包含追蹤資訊
            try (var ignored = ChannelMdcUtil.withChannel(channelId)) {
                ChannelMdcUtil.setTraceId(traceId);
                ChannelMdcUtil.setTransactionContext(stan,
                        requestInternal.getMessageType() != null
                                ? requestInternal.getMessageType().getMtiCode()
                                : null);

                try {
                    if (responseData != null && responseData.length > 0) {
                        // 將 FISC 格式轉換為 ATM 格式
                        byte[] atmFormatData = convertFiscToAtmFormat(responseData, channelId, finalSchema);

                        if (atmFormatData != null && atmFormatData.length > 0) {
                            context.sendRawResponse(atmFormatData);
                            log.debug("[{}] 已發送回應 (格式轉換): STAN={}, traceId={}, FISC {} bytes → ATM {} bytes",
                                    channelId, stan, traceId, responseData.length, atmFormatData.length);
                        } else {
                            // 如果轉換失敗，嘗試直接發送原始資料
                            context.sendRawResponse(responseData);
                            log.warn("[{}] 格式轉換失敗，直接發送原始資料: STAN={}, traceId={}, {} bytes",
                                    channelId, stan, traceId, responseData.length);
                        }
                    } else {
                        log.error("[{}] 回應資料為空: STAN={}, traceId={}", channelId, stan, traceId);
                    }
                } catch (Exception e) {
                    log.error("[{}] 發送回應失敗: STAN={}, traceId={}, error={}",
                            channelId, stan, traceId, e.getMessage(), e);
                } finally {
                    removeCallback(stan, channelId, context.getClientId());
                }
            }
        };
    }

    /**
     * 將 FISC 格式轉換為 ATM 格式
     *
     * <p>完整轉換流程：
     * <ol>
     *   <li>解析 FISC 格式的 byte[] 為 Iso8583Message</li>
     *   <li>轉換為 GenericMessage（使用 ATM schema）</li>
     *   <li>使用 GenericMessageAssembler 組裝為 ATM 格式</li>
     * </ol>
     *
     * @param fiscData FISC 格式的訊息
     * @param channelId 通道 ID（用於日誌）
     * @param schema ATM schema（用於組裝）
     * @return ATM 格式的訊息，或 null 如果轉換失敗
     */
    private byte[] convertFiscToAtmFormat(byte[] fiscData, String channelId, MessageSchema schema) {
        if (fiscData == null || fiscData.length < 2) {
            log.warn("[{}] 無法轉換：資料太短 ({} bytes)", channelId, fiscData != null ? fiscData.length : 0);
            return null;
        }

        try {
            // 1. 解析 FISC 格式為 Iso8583Message
            Iso8583Message isoMessage = messageFactory.parse(fiscData);
            if (isoMessage == null) {
                log.warn("[{}] 無法解析 FISC 格式訊息", channelId);
                return null;
            }

            log.debug("[{}] 解析 FISC 訊息: MTI={}, fields={}",
                    channelId, isoMessage.getMti(), isoMessage.getFieldNumbers());

            // 2. 如果沒有 schema，退回到舊的長度前綴轉換
            if (schema == null) {
                log.warn("[{}] 沒有可用的 schema，使用舊的轉換方式", channelId);
                return convertLengthPrefixOnly(fiscData, channelId);
            }

            // 3. 轉換為 GenericMessage
            GenericMessage genericMessage = convertIsoToGenericMessage(isoMessage, schema);

            // 4. 組裝為 ATM 格式
            byte[] atmData = assembler.assemble(genericMessage);

            log.debug("[{}] 轉換完成: FISC {} bytes → ATM {} bytes, MTI={}",
                    channelId, fiscData.length, atmData.length, isoMessage.getMti());

            return atmData;

        } catch (Exception e) {
            log.error("[{}] FISC→ATM 格式轉換失敗: {}", channelId, e.getMessage(), e);
            // 退回到舊的轉換方式
            return convertLengthPrefixOnly(fiscData, channelId);
        }
    }

    /**
     * 將 Iso8583Message 轉換為 GenericMessage
     *
     * @param isoMessage ISO 8583 訊息
     * @param schema ATM schema
     * @return GenericMessage
     */
    private GenericMessage convertIsoToGenericMessage(Iso8583Message isoMessage, MessageSchema schema) {
        GenericMessage generic = new GenericMessage(schema);

        // 設定 MTI
        generic.setField("mti", isoMessage.getMti());

        // 複製所有欄位
        for (int fieldNum : isoMessage.getFieldNumbers()) {
            Object value = isoMessage.getField(fieldNum);
            if (value != null) {
                String fieldName = mapFieldNumberToName(fieldNum);
                if (fieldName != null) {
                    generic.setField(fieldName, value.toString());
                }
            }
        }

        return generic;
    }

    /**
     * ISO 8583 欄位編號到 schema 欄位名稱的映射
     */
    private String mapFieldNumberToName(int fieldNum) {
        return switch (fieldNum) {
            case 2 -> "pan";
            case 3 -> "processingCode";
            case 4 -> "amount";
            case 11 -> "stan";
            case 12 -> "localTime";
            case 13 -> "localDate";
            case 14 -> "expiryDate";
            case 22 -> "posEntryMode";
            case 23 -> "cardSequence";
            case 24 -> "functionCode";
            case 25 -> "posConditionCode";
            case 32 -> "acquiringInstitution";
            case 35 -> "track2Data";
            case 37 -> "rrn";
            case 38 -> "authCode";
            case 39 -> "responseCode";
            case 41 -> "terminalId";
            case 42 -> "merchantId";
            case 43 -> "cardAcceptorName";
            case 48 -> "additionalData";
            case 49 -> "currencyCode";
            case 52 -> "pinBlock";
            case 54 -> "additionalAmounts";
            case 55 -> "emvData";
            case 70 -> "networkManagementCode";
            case 102 -> "sourceAccount";
            case 103 -> "destAccount";
            default -> null;
        };
    }

    /**
     * 舊的轉換方式：僅轉換長度前綴
     *
     * <p>當無法取得 schema 時使用此方法作為 fallback。
     * 注意：這會導致訊息內容仍為 FISC 格式（BCD），可能無法正確解析。
     */
    private byte[] convertLengthPrefixOnly(byte[] fiscData, String channelId) {
        try {
            // 解析 FISC BCD 長度（2 bytes）
            int fiscLength = decodeBcdLength(fiscData[0], fiscData[1]);

            // 驗證長度是否合理
            int bodyLength = fiscData.length - 2;
            if (fiscLength != bodyLength) {
                log.debug("[{}] 資料可能已不含長度前綴 (declared={}, actual={})",
                        channelId, fiscLength, fiscData.length);
                bodyLength = fiscData.length;
                return addAsciiLengthPrefix(fiscData, 0, bodyLength);
            }

            return addAsciiLengthPrefix(fiscData, 2, bodyLength);
        } catch (Exception e) {
            log.error("[{}] 長度前綴轉換失敗: {}", channelId, e.getMessage());
            return null;
        }
    }

    /**
     * 解碼 BCD 長度（2 bytes）
     */
    private int decodeBcdLength(byte b1, byte b2) {
        int d1 = (b1 >> 4) & 0x0F;
        int d2 = b1 & 0x0F;
        int d3 = (b2 >> 4) & 0x0F;
        int d4 = b2 & 0x0F;
        return d1 * 1000 + d2 * 100 + d3 * 10 + d4;
    }

    /**
     * 加上 4-byte ASCII 長度前綴
     *
     * @param data 原始資料
     * @param offset 訊息本體起始位置
     * @param bodyLength 訊息本體長度
     * @return 含 ASCII 長度前綴的訊息
     */
    private byte[] addAsciiLengthPrefix(byte[] data, int offset, int bodyLength) {
        // 產生 4-byte ASCII 長度（例如 "0105" 表示 105 bytes）
        String lengthStr = String.format("%04d", bodyLength);
        byte[] lengthPrefix = lengthStr.getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        // 組合：[4-byte ASCII length][message body]
        byte[] result = new byte[4 + bodyLength];
        System.arraycopy(lengthPrefix, 0, result, 0, 4);
        System.arraycopy(data, offset, result, 4, bodyLength);

        return result;
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
