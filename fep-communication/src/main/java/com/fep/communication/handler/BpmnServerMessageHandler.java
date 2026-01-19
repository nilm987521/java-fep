package com.fep.communication.handler;

import com.fep.common.event.TransactionRequestEvent;
import com.fep.common.event.TransactionRequestEvent.TransactionType;
import com.fep.message.iso8583.Iso8583Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * BPMN 整合的 ServerMessageHandler 實作
 *
 * <p>此 Handler 將收到的交易請求 (0200/0400) 透過 Spring Application Events
 * 發布給 fep-transaction 模組，觸發 BPMN 流程處理。
 *
 * <p>架構設計：
 * <ul>
 *   <li>0200/0400 交易：發布 {@link TransactionRequestEvent}，由 BPMN 流程處理</li>
 *   <li>0800 網路管理：委派給 {@link DefaultServerMessageHandler} 本地處理</li>
 * </ul>
 *
 * <p>流程：
 * <pre>
 * ATM ──0200──► BpmnServerMessageHandler
 *                    │
 *                    ├── 0200/0400 → publish TransactionRequestEvent
 *                    │                  └── BPMN 流程處理
 *                    │                        └── 完成後呼叫 responseCallback
 *                    │
 *                    └── 0800 → DefaultServerMessageHandler (本地處理)
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class BpmnServerMessageHandler implements ServerMessageHandler {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMdd");

    /**
     * Spring 事件發布器
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 預設 Handler (用於處理 0800 網路管理訊息)
     */
    private final DefaultServerMessageHandler defaultHandler;

    /**
     * STAN → Response Callback 映射
     * 用於在 BPMN 流程完成後發送回應
     */
    private final Map<String, ResponseCallbackInfo> pendingCallbacks = new ConcurrentHashMap<>();

    /**
     * Callback 過期時間 (毫秒)
     */
    private static final long CALLBACK_TTL_MS = 60000; // 60 秒

    @Override
    public void handleMessage(ServerMessageContext context) {
        Iso8583Message request = context.getMessage();
        String mti = request.getMti();
        String channelId = context.getChannelId();
        String clientId = context.getClientId();
        String stan = request.getFieldAsString(11);

        log.info("[{}] BPMN Handler 收到訊息: MTI={}, STAN={}, client={}",
                channelId, mti, stan, clientId);

        try {
            switch (mti) {
                case "0200" -> handleFinancialRequest(context);
                case "0400" -> handleReversalRequest(context);
                case "0800" -> defaultHandler.handleMessage(context); // 委派給預設 Handler
                default -> handleUnknownMti(context);
            }
        } catch (Exception e) {
            log.error("[{}] 處理訊息失敗: MTI={}, STAN={}, error={}",
                    channelId, mti, stan, e.getMessage(), e);
            sendErrorResponse(context, "96"); // System malfunction
        }
    }

    /**
     * 處理 0200 金融交易請求
     */
    private void handleFinancialRequest(ServerMessageContext context) {
        Iso8583Message request = context.getMessage();
        String stan = request.getFieldAsString(11);
        String processingCode = request.getFieldAsString(3);

        // 判斷交易類型
        TransactionType transactionType = determineTransactionType(processingCode);

        log.info("[{}] 金融交易請求: STAN={}, processingCode={}, type={}",
                context.getChannelId(), stan, processingCode, transactionType);

        // 建立 response callback
        Consumer<byte[]> responseCallback = createResponseCallback(context, stan);

        // 註冊 callback
        registerCallback(stan, context, responseCallback);

        // 發布事件
        publishTransactionEvent(context, transactionType, responseCallback);
    }

    /**
     * 處理 0400 沖正請求
     */
    private void handleReversalRequest(ServerMessageContext context) {
        Iso8583Message request = context.getMessage();
        String stan = request.getFieldAsString(11);

        log.info("[{}] 沖正請求: STAN={}", context.getChannelId(), stan);

        // 建立 response callback
        Consumer<byte[]> responseCallback = createResponseCallback(context, stan);

        // 註冊 callback
        registerCallback(stan, context, responseCallback);

        // 發布沖正事件
        publishTransactionEvent(context, TransactionType.REVERSAL, responseCallback);
    }

    /**
     * 處理未知 MTI
     */
    private void handleUnknownMti(ServerMessageContext context) {
        String mti = context.getMessage().getMti();
        log.warn("[{}] 未知 MTI: {}", context.getChannelId(), mti);
        sendErrorResponse(context, "12"); // Invalid transaction
    }

    /**
     * 根據 Processing Code 判斷交易類型
     */
    private TransactionType determineTransactionType(String processingCode) {
        if (processingCode == null || processingCode.length() < 2) {
            return TransactionType.UNKNOWN;
        }

        String typeCode = processingCode.substring(0, 2);
        return switch (typeCode) {
            case "40" -> TransactionType.TRANSFER;      // 轉帳
            case "01" -> TransactionType.WITHDRAWAL;    // 提款
            case "31" -> TransactionType.BALANCE_INQUIRY; // 餘額查詢
            case "50" -> TransactionType.BILL_PAYMENT;  // 繳費
            default -> TransactionType.UNKNOWN;
        };
    }

    /**
     * 建立回應 callback
     */
    private Consumer<byte[]> createResponseCallback(ServerMessageContext context, String stan) {
        return responseData -> {
            try {
                // 將 response data 轉換為 Iso8583Message 並發送
                Iso8583Message response = deserializeResponse(responseData);
                if (response != null) {
                    context.sendResponse(response);
                    log.info("[{}] 已發送 BPMN 回應: STAN={}, MTI={}, RC={}",
                            context.getChannelId(), stan,
                            response.getMti(), response.getFieldAsString(39));
                } else {
                    log.error("[{}] 無法反序列化回應: STAN={}", context.getChannelId(), stan);
                }
            } catch (Exception e) {
                log.error("[{}] 發送回應失敗: STAN={}, error={}",
                        context.getChannelId(), stan, e.getMessage(), e);
            } finally {
                // 清理 callback
                removeCallback(stan);
            }
        };
    }

    /**
     * 發布交易請求事件
     */
    private void publishTransactionEvent(ServerMessageContext context,
                                         TransactionType transactionType,
                                         Consumer<byte[]> responseCallback) {
        Iso8583Message request = context.getMessage();

        TransactionRequestEvent event = TransactionRequestEvent.builder()
                .source(this)
                .transactionType(transactionType)
                .rawMessage(serializeMessage(request))
                .mti(request.getMti())
                .stan(request.getFieldAsString(11))
                .channelId(context.getChannelId())
                .clientId(context.getClientId())
                .processingCode(request.getFieldAsString(3))
                .amount(request.getFieldAsString(4))
                .pan(request.getFieldAsString(2))
                .targetAccount(request.getFieldAsString(103))
                .sourceBankCode(request.getFieldAsString(32))
                .targetBankCode(request.getFieldAsString(100))
                .responseCallback(responseCallback)
                .build();

        log.debug("[{}] 發布 TransactionRequestEvent: STAN={}, type={}",
                context.getChannelId(), event.getStan(), transactionType);

        eventPublisher.publishEvent(event);
    }

    /**
     * 序列化 ISO 8583 訊息
     */
    private byte[] serializeMessage(Iso8583Message message) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(message);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("序列化訊息失敗: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * 反序列化回應訊息
     */
    private Iso8583Message deserializeResponse(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
             java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais)) {
            return (Iso8583Message) ois.readObject();
        } catch (Exception e) {
            log.error("反序列化回應失敗: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 註冊 callback
     */
    private void registerCallback(String stan, ServerMessageContext context,
                                   Consumer<byte[]> callback) {
        ResponseCallbackInfo info = new ResponseCallbackInfo(
                callback, context, System.currentTimeMillis());
        pendingCallbacks.put(stan, info);
        log.debug("註冊 callback: STAN={}, pending count={}", stan, pendingCallbacks.size());

        // 清理過期的 callbacks
        cleanupExpiredCallbacks();
    }

    /**
     * 移除 callback
     */
    private void removeCallback(String stan) {
        pendingCallbacks.remove(stan);
        log.debug("移除 callback: STAN={}, pending count={}", stan, pendingCallbacks.size());
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
                log.warn("清理過期 callback: STAN={}", entry.getKey());
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
        Iso8583Message request = context.getMessage();
        Iso8583Message response = new Iso8583Message();

        // 計算回應 MTI
        String responseMti = calculateResponseMti(request.getMti());
        response.setMti(responseMti);

        // 複製關鍵欄位
        copyField(request, response, 2);  // PAN
        copyField(request, response, 3);  // Processing Code
        copyField(request, response, 4);  // Amount
        copyField(request, response, 11); // STAN
        copyField(request, response, 41); // Terminal ID
        copyField(request, response, 42); // Merchant ID

        // 設定回應碼
        response.setField(39, responseCode);

        // 設定時間
        LocalDateTime now = LocalDateTime.now();
        response.setField(12, now.format(TIME_FORMAT));
        response.setField(13, now.format(DATE_FORMAT));

        context.sendResponse(response);
        log.warn("[{}] 發送錯誤回應: MTI={}, RC={}",
                context.getChannelId(), responseMti, responseCode);
    }

    /**
     * 計算回應 MTI
     */
    private String calculateResponseMti(String requestMti) {
        try {
            int mti = Integer.parseInt(requestMti);
            return String.format("%04d", mti + 10);
        } catch (NumberFormatException e) {
            return "0210";
        }
    }

    /**
     * 複製欄位
     */
    private void copyField(Iso8583Message source, Iso8583Message target, int fieldNum) {
        Object value = source.getField(fieldNum);
        if (value != null) {
            target.setField(fieldNum, value);
        }
    }

    /**
     * 取得待處理的 callback 數量 (監控用)
     */
    public int getPendingCallbackCount() {
        return pendingCallbacks.size();
    }

    /**
     * 透過 STAN 直接發送回應 (供 BPMN 流程使用)
     *
     * @param stan 交易追蹤號
     * @param response 回應訊息
     * @return true 如果成功發送
     */
    public boolean sendResponseByStan(String stan, Iso8583Message response) {
        ResponseCallbackInfo info = pendingCallbacks.get(stan);
        if (info == null) {
            log.warn("找不到 callback: STAN={}", stan);
            return false;
        }

        try {
            byte[] responseData = serializeMessage(response);
            info.callback().accept(responseData);
            return true;
        } catch (Exception e) {
            log.error("發送回應失敗: STAN={}, error={}", stan, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 取得 ServerMessageContext (供 BPMN 流程使用)
     */
    public ServerMessageContext getContext(String stan) {
        ResponseCallbackInfo info = pendingCallbacks.get(stan);
        return info != null ? info.context() : null;
    }

    /**
     * Callback 資訊記錄
     */
    private record ResponseCallbackInfo(
            Consumer<byte[]> callback,
            ServerMessageContext context,
            long createdTime
    ) {}
}
