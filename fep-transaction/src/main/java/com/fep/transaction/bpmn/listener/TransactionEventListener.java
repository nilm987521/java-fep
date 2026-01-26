package com.fep.transaction.bpmn.listener;

import com.fep.common.event.FiscResponseEvent;
import com.fep.common.event.TransactionRequestEvent;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.transaction.bpmn.service.TransferProcessService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 交易事件監聽器
 *
 * <p>監聽來自 fep-communication 模組的交易相關事件，
 * 並觸發對應的 BPMN 流程處理。
 *
 * <p>職責：
 * <ul>
 *   <li>監聽 {@link TransactionRequestEvent}，啟動對應 BPMN 流程</li>
 *   <li>監聽 {@link FiscResponseEvent}，觸發 BPMN 訊息關聯（傳統模式）</li>
 *   <li>管理 STAN → ProcessId 對應關係</li>
 *   <li>管理 STAN → ResponseCallback 對應關係</li>
 * </ul>
 *
 * <h3>傳統模式流程圖：</h3>
 * <pre>
 * TransactionRequestEvent ──► TransactionEventListener
 *                                     │
 *                                     ▼
 *                             startTransferProcess()
 *                                     │
 *                                     ▼
 *                             BPMN Process Started
 *                                     │
 *                        (... BPMN 執行中，等待回應 ...)
 *                                     │
 * FiscResponseEvent ────────────► correlateMessage()
 *                                     │
 *                                     ▼
 *                             BPMN Message Catch Event
 *                                     │
 *                        (... BPMN 繼續執行 ...)
 *                                     │
 *                                     ▼
 *                             sendResponseToClient()
 * </pre>
 *
 * <h3>高 TPS 模式流程圖：</h3>
 * <pre>
 * TransactionRequestEvent ──► TransactionEventListener
 *                                     │
 *                                     ▼
 *                             startTransferProcess()
 *                                     │
 *                                     ▼
 *                             Request BPMN (立即結束)
 *                                     │
 *                                     ▼
 *                             SavePendingTransaction (Redis)
 *                                     │
 *                                     ▼
 *                             SendToFISC (Fire & Forget)
 *                                     │
 *                                     ▼
 *                                   END
 *
 * FiscResponseEvent ────────────► FiscResponseHandler (不經此監聽器)
 *                                     │
 *                                     ▼
 *                             Response BPMN Started
 *                                     │
 *                                     ▼
 *                             sendResponseToClient()
 * </pre>
 *
 * @see com.fep.transaction.bpmn.handler.FiscResponseHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final TransferProcessService processService;
    private final Iso8583MessageFactory messageFactory;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMdd");

    /**
     * 是否使用高 TPS 架構 (兩個 BPMN 流程模式)
     *
     * <p>高 TPS 模式特點：
     * <ul>
     *   <li>Request BPMN 發送至 FISC 後立即結束，不等待回應</li>
     *   <li>FISC 回應由 FiscResponseHandler 啟動 Response BPMN 處理</li>
     *   <li>此監聽器的 handleFiscResponse 方法在高 TPS 模式下不執行 correlateMessage</li>
     * </ul>
     */
    @Value("${fep.bpmn.high-tps-mode:true}")
    private boolean highTpsMode;

    /**
     * CallbackKey → ProcessId 映射
     * <p>CallbackKey 格式: channelId:clientId:atmStan
     */
    private final Map<String, String> callbackKeyToProcessMap = new ConcurrentHashMap<>();

    /**
     * CallbackKey → Response Callback 映射
     * <p>CallbackKey 格式: channelId:clientId:atmStan
     */
    private final Map<String, Consumer<byte[]>> callbackKeyToCallbackMap = new ConcurrentHashMap<>();

    /**
     * ProcessId → CallbackKey 反向映射 (用於流程結束時查找)
     */
    private final Map<String, String> processToCallbackKeyMap = new ConcurrentHashMap<>();

    /**
     * FISC STAN → CallbackKey 映射
     * <p>由於 ATM 發送的 STAN 與 FEP 發給 FISC 的 STAN 不同，
     * 需要此映射來關聯 FISC 回應與原始 ATM callback
     * <p>使用 CallbackKey 可支援多台 ATM 使用相同 STAN 的情況
     */
    private final Map<String, String> fiscStanToCallbackKeyMap = new ConcurrentHashMap<>();

    /**
     * CallbackKey → 原始 ATM 請求上下文 映射
     * <p>用於在高 TPS 模式下，基於原始請求建立正確的 ATM 回應
     */
    private final Map<String, OriginalRequestContext> callbackKeyToRequestContextMap = new ConcurrentHashMap<>();

    /**
     * 原始 ATM 請求上下文
     * <p>儲存建立 ATM 回應所需的欄位
     */
    @Getter
    public static class OriginalRequestContext {
        private final String mti;
        private final String stan;
        private final String processingCode;
        private final String pan;
        private final String amount;
        private final String terminalId;
        private final String merchantId;
        private final String rrn;
        private final String targetAccount;
        private final String sourceAccount;
        private final byte[] rawMessage;
        private final long createdTime;

        public OriginalRequestContext(TransactionRequestEvent event) {
            this.mti = event.getMti();
            this.stan = event.getStan();
            this.processingCode = event.getProcessingCode();
            this.pan = event.getPan();
            this.amount = event.getAmountAsString();  // 使用字串格式
            this.targetAccount = event.getTargetAccount();
            this.sourceAccount = event.getSourceAccount() != null ? event.getSourceAccount() : event.getPan();
            this.rawMessage = event.getRawMessage();
            this.createdTime = System.currentTimeMillis();

            // 直接從事件中取得欄位值
            this.terminalId = event.getTerminalId();
            this.merchantId = event.getMerchantId();
            this.rrn = event.getRrn();
        }
    }

    /**
     * 處理交易請求事件
     *
     * <p>收到 ATM/POS 的交易請求後，啟動 BPMN 流程
     *
     * @param event 交易請求事件
     */
    @Async("transactionExecutor")
    @EventListener
    public void handleTransactionRequest(TransactionRequestEvent event) {
        String stan = event.getStan();
        String channelId = event.getChannelId();
        String clientId = event.getClientId();
        String processKey = event.getProcessKey();

        // 產生唯一的 callback key: channelId:clientId:atmStan
        String callbackKey = generateCallbackKey(channelId, clientId, stan);

        if (log.isDebugEnabled()) {
            log.debug("[{}] 收到交易請求事件: STAN={}, callbackKey={}, type={}, MTI={}, processKey={}",
                    channelId, stan, callbackKey, event.getTransactionType(), event.getMti(), processKey);
        }

        try {
            // 重要：先註冊 callback 和原始請求上下文，因為 Camunda 流程是同步執行的
            // 流程可能在 startBpmnProcess() 返回之前就執行完畢並嘗試發送回應
            registerCallback(callbackKey, event.getResponseCallback());

            // 儲存原始請求上下文（用於高 TPS 模式建立 ATM 回應）
            callbackKeyToRequestContextMap.put(callbackKey, new OriginalRequestContext(event));

            // 啟動 BPMN 流程（傳入 callbackKey）
            String processId = startBpmnProcess(event, callbackKey);

            // 補充註冊 processId 映射（用於 FISC 回應關聯）
            registerProcessMapping(callbackKey, processId);

            if (log.isDebugEnabled()) {
                log.debug("[{}] BPMN 流程已啟動: STAN={}, callbackKey={}, processId={}, processKey={}",
                        channelId, stan, callbackKey, processId, processKey);
            }

        } catch (Exception e) {
            log.error("[{}] 啟動 BPMN 流程失敗: STAN={}, callbackKey={}, processKey={}, error={}",
                    channelId, stan, callbackKey, processKey, e.getMessage(), e);

            // 清理已註冊的 callback 和請求上下文
            callbackKeyToCallbackMap.remove(callbackKey);
            callbackKeyToRequestContextMap.remove(callbackKey);

            // 發送錯誤回應
            sendErrorResponse(event, "96"); // System malfunction
        }
    }

    /**
     * 處理 FISC 回應事件
     *
     * <p>收到 FISC 回應後，觸發 BPMN 訊息關聯
     *
     * <p>高 TPS 模式：此方法不執行，改由 FiscResponseHandler 啟動 Response BPMN
     *
     * @param event FISC 回應事件
     */
    @Async("bpmnExecutor")
    @EventListener
    public void handleFiscResponse(FiscResponseEvent event) {
        String fiscStan = event.getStan();

        // 高 TPS 模式：基於原始 ATM 請求建立回應並發送給 ATM
        if (highTpsMode) {
            // 查找 CallbackKey（FISC STAN 與 ATM callback 的映射）
            String callbackKey = fiscStanToCallbackKeyMap.get(fiscStan);
            if (callbackKey == null) {
                log.warn("找不到 FISC STAN 對應的 callbackKey: fiscStan={}", fiscStan);
                return;
            }

            log.info("高 TPS 模式：建立 ATM 回應: fiscStan={}, callbackKey={}, RC={}",
                    fiscStan, callbackKey, event.getResponseCode());

            try {
                // 取得原始 ATM 請求上下文
                OriginalRequestContext requestContext = callbackKeyToRequestContextMap.get(callbackKey);
                if (requestContext == null) {
                    log.error("找不到原始請求上下文: callbackKey={}", callbackKey);
                    return;
                }

                // 基於原始請求建立 ATM 回應
                byte[] responseData = buildAtmResponse(requestContext, event);

                if (responseData != null && responseData.length > 0) {
                    boolean sent = sendResponseToClientByCallbackKey(callbackKey, responseData);
                    if (sent) {
                        log.info("已發送 ATM 回應: callbackKey={}, RC={}", callbackKey, event.getResponseCode());
                        // 清理映射
                        fiscStanToCallbackKeyMap.remove(fiscStan);
                        callbackKeyToRequestContextMap.remove(callbackKey);
                    } else {
                        log.warn("發送 ATM 回應失敗（找不到 callback）: fiscStan={}, callbackKey={}", fiscStan, callbackKey);
                    }
                } else {
                    log.error("建立 ATM 回應失敗: fiscStan={}", fiscStan);
                }
            } catch (Exception e) {
                log.error("處理 FISC 回應失敗: fiscStan={}, callbackKey={}, error={}",
                        fiscStan, callbackKey, e.getMessage(), e);
            }
            return;
        }

        // 傳統模式：使用 Message Correlation
        String callbackKey = fiscStanToCallbackKeyMap.get(fiscStan);
        if (callbackKey == null) {
            log.warn("收到無法匹配的 FISC 回應: fiscStan={}, RC={}", fiscStan, event.getResponseCode());
            return;
        }

        String processId = callbackKeyToProcessMap.get(callbackKey);
        if (processId == null) {
            log.warn("收到無法匹配的 FISC 回應: fiscStan={}, callbackKey={}, RC={}",
                    fiscStan, callbackKey, event.getResponseCode());
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("處理 FISC 回應 (傳統模式): fiscStan={}, callbackKey={}, RC={}, processId={}, type={}",
                    fiscStan, callbackKey, event.getResponseCode(), processId, event.getResponseType());
        }

        try {
            // 根據回應類型決定訊息名稱
            String messageName = determineMessageName(event);

            // 準備流程變數
            Map<String, Object> variables = buildResponseVariables(event);

            // 發送訊息至流程 (觸發 Message Catch Event)
            processService.correlateMessage(processId, messageName, variables);

            if (log.isDebugEnabled()) {
                log.debug("已通知流程回應: processId={}, message={}, RC={}",
                        processId, messageName, event.getResponseCode());
            }

            // 清理 FISC STAN 映射
            fiscStanToCallbackKeyMap.remove(fiscStan);

        } catch (Exception e) {
            log.error("通知流程失敗: fiscStan={}, callbackKey={}, processId={}, error={}",
                    fiscStan, callbackKey, processId, e.getMessage(), e);
        }
    }

    /**
     * 發送回應給客戶端
     *
     * <p>供 BPMN Delegate 呼叫，在流程結束時發送回應
     *
     * @param processId 流程實例 ID
     * @param responseData 回應資料 (序列化的 Iso8583Message)
     * @return true 如果成功發送
     */
    public boolean sendResponseToClient(String processId, byte[] responseData) {
        String callbackKey = processToCallbackKeyMap.get(processId);
        if (callbackKey == null) {
            log.warn("找不到流程對應的 callbackKey: processId={}", processId);
            return false;
        }

        Consumer<byte[]> callback = callbackKeyToCallbackMap.get(callbackKey);
        if (callback == null) {
            log.warn("找不到回應 callback: callbackKey={}, processId={}", callbackKey, processId);
            return false;
        }

        try {
            callback.accept(responseData);
            if (log.isDebugEnabled()) {
                log.debug("已發送回應給客戶端: callbackKey={}, processId={}", callbackKey, processId);
            }
            return true;
        } catch (Exception e) {
            log.error("發送回應失敗: callbackKey={}, processId={}, error={}",
                    callbackKey, processId, e.getMessage(), e);
            return false;
        } finally {
            // 清理映射
            cleanupMappings(callbackKey, processId);
        }
    }

    /**
     * 透過 CallbackKey 發送回應給客戶端
     *
     * @param callbackKey 回調鍵 (channelId:clientId:atmStan)
     * @param responseData 回應資料
     * @return true 如果成功發送
     */
    public boolean sendResponseToClientByCallbackKey(String callbackKey, byte[] responseData) {
        Consumer<byte[]> callback = callbackKeyToCallbackMap.get(callbackKey);
        if (callback == null) {
            log.warn("找不到回應 callback: callbackKey={}", callbackKey);
            return false;
        }

        String processId = callbackKeyToProcessMap.get(callbackKey);

        try {
            callback.accept(responseData);
            if (log.isDebugEnabled()) {
                log.debug("已發送回應給客戶端: callbackKey={}", callbackKey);
            }
            return true;
        } catch (Exception e) {
            log.error("發送回應失敗: callbackKey={}, error={}", callbackKey, e.getMessage(), e);
            return false;
        } finally {
            // 清理映射
            if (processId != null) {
                cleanupMappings(callbackKey, processId);
            } else {
                callbackKeyToCallbackMap.remove(callbackKey);
            }
        }
    }

    /**
     * 透過 STAN 發送回應給客戶端（向下相容方法）
     *
     * <p>此方法透過 processId 查找 callbackKey，然後發送回應。
     * 若找不到對應的 callbackKey，會嘗試直接使用 STAN 作為 key（舊行為）。
     *
     * @param stan ATM STAN
     * @param responseData 回應資料
     * @return true 如果成功發送
     * @deprecated 使用 {@link #sendResponseToClientByCallbackKey(String, byte[])} 代替
     */
    @Deprecated
    public boolean sendResponseToClientByStan(String stan, byte[] responseData) {
        // 嘗試透過任一 callbackKey 找到匹配的（向下相容）
        // 由於舊程式可能沒有設置 callbackKey，嘗試遍歷找到以 stan 結尾的 key
        for (String key : callbackKeyToCallbackMap.keySet()) {
            if (key.endsWith(":" + stan)) {
                return sendResponseToClientByCallbackKey(key, responseData);
            }
        }

        log.warn("sendResponseToClientByStan: 找不到匹配的 callbackKey: STAN={}", stan);
        return false;
    }

    /**
     * 取得流程 ID
     *
     * @param callbackKey 回調鍵
     * @return 流程 ID，若不存在則返回 null
     */
    public String getProcessId(String callbackKey) {
        return callbackKeyToProcessMap.get(callbackKey);
    }

    /**
     * 取得 CallbackKey
     *
     * @param processId 流程 ID
     * @return CallbackKey，若不存在則返回 null
     */
    public String getCallbackKey(String processId) {
        return processToCallbackKeyMap.get(processId);
    }

    /**
     * 取得待處理數量 (監控用)
     */
    public int getPendingCount() {
        return callbackKeyToProcessMap.size();
    }

    /**
     * 註冊 FISC STAN 與 CallbackKey 的映射
     *
     * <p>由於 ATM 發送的 STAN (如 000001) 與 FEP 發給 FISC 的 STAN (如 000003) 不同，
     * 當 FISC 回應時，需要透過此映射找到原始的 ATM callback。
     * <p>使用 CallbackKey 可支援多台 ATM 使用相同 STAN 的情況。
     *
     * @param fiscStan FEP 發給 FISC 的 STAN
     * @param callbackKey 回調鍵 (channelId:clientId:atmStan)
     */
    public void registerFiscStanMapping(String fiscStan, String callbackKey) {
        if (fiscStan != null && callbackKey != null) {
            fiscStanToCallbackKeyMap.put(fiscStan, callbackKey);
            log.debug("已註冊 FISC STAN 映射: fiscStan={} -> callbackKey={}", fiscStan, callbackKey);
        }
    }

    /**
     * 根據 FISC STAN 取得 CallbackKey
     *
     * @param fiscStan FISC STAN
     * @return CallbackKey，若不存在則返回 null
     */
    public String getCallbackKeyByFiscStan(String fiscStan) {
        return fiscStanToCallbackKeyMap.get(fiscStan);
    }

    /**
     * 產生唯一的 callback key
     *
     * @param channelId 通道 ID
     * @param clientId 客戶端 ID (包含 IP:Port)
     * @param stan ATM STAN
     * @return 複合的 callback key
     */
    public static String generateCallbackKey(String channelId, String clientId, String stan) {
        return String.format("%s:%s:%s",
                channelId != null ? channelId : "UNKNOWN",
                clientId != null ? clientId : "UNKNOWN",
                stan != null ? stan : "000000");
    }

    // ==================== Private Methods ====================

    /**
     * 啟動 BPMN 流程
     *
     * <p>使用事件中預先解析好的 processKey 啟動對應的 BPMN 流程。
     * processKey 已由 BpmnServerMessageHandler 透過 ProcessRouterService 解析。
     *
     * @param event 交易請求事件
     * @param callbackKey 回調鍵 (channelId:clientId:atmStan)
     */
    private String startBpmnProcess(TransactionRequestEvent event, String callbackKey) {
        // 建立轉帳請求
        TransferProcessService.TransferRequest request = buildTransferRequest(event);

        // 使用事件中的 processKey 啟動流程
        String processKey = event.getProcessKey();
        if (processKey != null && !processKey.isEmpty()) {
            return processService.startProcessWithKey(
                    request,
                    processKey,
                    event.getChannelId(),
                    event.getClientId(),
                    event.getMti(),
                    event.getProcessingCode(),
                    callbackKey
            );
        }

        // 向下相容：若 processKey 為空，使用舊的路由邏輯
        return processService.startTransferProcess(
                request,
                event.getChannelId(),
                event.getClientId(),
                event.getMti(),
                event.getProcessingCode(),
                callbackKey
        );
    }

    /**
     * 建立轉帳請求
     *
     * <p>使用 InternalMessage 中的欄位建立轉帳請求。
     * sourceAccount 優先使用 InternalMessage.sourceAccount，若為空則使用 cardNumber (PAN)。
     */
    private TransferProcessService.TransferRequest buildTransferRequest(TransactionRequestEvent event) {
        // 優先使用 sourceAccount，若為空則使用 PAN
        String sourceAccount = event.getSourceAccount();
        if (sourceAccount == null || sourceAccount.isBlank()) {
            sourceAccount = event.getPan();
        }
        String targetAccount = event.getTargetAccount();

        log.debug("Building transfer request: sourceAccount='{}' (length={}), targetAccount='{}' (length={}), PAN='{}'",
                sourceAccount != null ? maskAccount(sourceAccount) : "null",
                sourceAccount != null ? sourceAccount.length() : 0,
                targetAccount != null ? maskAccount(targetAccount) : "null",
                targetAccount != null ? targetAccount.length() : 0,
                event.getPan() != null ? maskAccount(event.getPan()) : "null");

        return TransferProcessService.TransferRequest.builder()
                .businessKey(generateBusinessKey(event))
                .stan(event.getStan())
                .sourceAccount(sourceAccount)
                .targetAccount(targetAccount)
                .amount(event.getAmount() != null ? event.getAmount() : 0L)
                .sourceBankCode(event.getSourceBankCode())
                .targetBankCode(event.getTargetBankCode())
                .designated(false) // 預設非約定，後續可從 DB 查詢
                .channel(event.getChannelId())
                .rawMessage(event.getRawMessage()) // 原始電文 (用於組裝回應)
                .build();
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 8) return "****";
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }

    /**
     * 產生業務鍵
     */
    private String generateBusinessKey(TransactionRequestEvent event) {
        return String.format("%s_%s_%s",
                event.getChannelId(),
                event.getStan(),
                System.currentTimeMillis());
    }

    /**
     * 解析金額
     */
    private long parseAmount(String amount) {
        if (amount == null || amount.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(amount.trim());
        } catch (NumberFormatException e) {
            log.warn("無法解析金額: {}", amount);
            return 0;
        }
    }

    /**
     * 註冊 callback（在啟動流程之前）
     *
     * <p>重要：必須在 startBpmnProcess() 之前調用，
     * 因為 Camunda 流程是同步執行的，可能在返回之前就嘗試發送回應。
     *
     * @param callbackKey 回調鍵 (channelId:clientId:atmStan)
     * @param callback 回調函數
     */
    private void registerCallback(String callbackKey, Consumer<byte[]> callback) {
        if (callback != null && callbackKey != null) {
            callbackKeyToCallbackMap.put(callbackKey, callback);
            log.debug("已註冊 callback: callbackKey={}", callbackKey);
        }
    }

    /**
     * 註冊 processId 映射（在流程啟動之後）
     *
     * <p>用於 FISC 回應關聯時查找對應的流程。
     *
     * @param callbackKey 回調鍵 (channelId:clientId:atmStan)
     * @param processId 流程實例 ID
     */
    private void registerProcessMapping(String callbackKey, String processId) {
        if (callbackKey != null && processId != null) {
            callbackKeyToProcessMap.put(callbackKey, processId);
            processToCallbackKeyMap.put(processId, callbackKey);
            log.debug("已註冊流程映射: callbackKey={} <-> processId={}", callbackKey, processId);
        }
    }

    /**
     * 清理映射關係
     *
     * @param callbackKey 回調鍵
     * @param processId 流程實例 ID
     */
    private void cleanupMappings(String callbackKey, String processId) {
        callbackKeyToProcessMap.remove(callbackKey);
        callbackKeyToCallbackMap.remove(callbackKey);
        processToCallbackKeyMap.remove(processId);
        callbackKeyToRequestContextMap.remove(callbackKey);

        log.debug("已清理映射: callbackKey={}, processId={}", callbackKey, processId);
    }

    /**
     * 決定 BPMN 訊息名稱
     */
    private String determineMessageName(FiscResponseEvent event) {
        return switch (event.getResponseType()) {
            case FINANCIAL_RESPONSE -> "FiscResponse";       // 對應 BPMN 中的 Message_FiscResponse
            case REVERSAL_RESPONSE -> "ReversalResponse";    // 對應 BPMN 中的 Message_ReversalResponse
            default -> "FiscResponse";
        };
    }

    /**
     * 建立回應變數
     */
    private Map<String, Object> buildResponseVariables(FiscResponseEvent event) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("responseCode", event.getResponseCode());
        variables.put("fiscResponseReceived", true);
        variables.put("fiscResponseTime", event.getResponseTime());

        if (event.getAuthCode() != null) {
            variables.put("authCode", event.getAuthCode());
        }

        // 沖正回應特殊處理
        if (event.getResponseType() == FiscResponseEvent.ResponseType.REVERSAL_RESPONSE) {
            variables.put("reversalResult", event.isSuccess() ? "OK" : "FAIL");
            variables.put("reversalResponseCode", event.getResponseCode());
        }

        return variables;
    }

    /**
     * 發送錯誤回應
     */
    private void sendErrorResponse(TransactionRequestEvent event, String errorCode) {
        Consumer<byte[]> callback = event.getResponseCallback();
        if (callback == null) {
            log.warn("無法發送錯誤回應，callback 為 null: STAN={}", event.getStan());
            return;
        }

        // 建立錯誤回應 (這裡需要與 BpmnServerMessageHandler 協調)
        // 由於我們在 fep-transaction，無法直接建立 Iso8583Message，
        // 所以這裡透過特殊格式傳遞錯誤碼，由 BpmnServerMessageHandler 處理
        String errorMarker = "ERROR:" + errorCode;
        callback.accept(errorMarker.getBytes());

        log.warn("已發送錯誤回應: STAN={}, errorCode={}", event.getStan(), errorCode);
    }

    /**
     * 基於原始 ATM 請求建立回應
     *
     * <p>在高 TPS 模式下，FISC 回應格式與 ATM 預期格式不同，
     * 因此需要基於原始 ATM 請求來建立正確格式的回應。
     *
     * @param requestContext 原始 ATM 請求上下文
     * @param fiscResponse FISC 回應事件
     * @return 組裝好的 ATM 回應位元組
     */
    private byte[] buildAtmResponse(OriginalRequestContext requestContext, FiscResponseEvent fiscResponse) {
        try {
            Iso8583Message response = new Iso8583Message();

            // 1. 設定回應 MTI (0200 → 0210, 0400 → 0410)
            String responseMti = calculateResponseMti(requestContext.getMti());
            response.setMti(responseMti);

            // 2. 從原始請求複製必要欄位
            if (requestContext.getPan() != null) {
                response.setField(2, requestContext.getPan());
            }
            if (requestContext.getProcessingCode() != null) {
                response.setField(3, requestContext.getProcessingCode());
            }
            if (requestContext.getAmount() != null) {
                response.setField(4, requestContext.getAmount());
            }
            if (requestContext.getStan() != null) {
                response.setField(11, requestContext.getStan());  // 使用原始 ATM STAN
            }
            if (requestContext.getTargetAccount() != null) {
                response.setField(103, requestContext.getTargetAccount());
            }

            // 3. 設定 terminalId, merchantId, rrn（直接從上下文取得）
            if (requestContext.getTerminalId() != null) {
                response.setField(41, requestContext.getTerminalId());
            }
            if (requestContext.getMerchantId() != null) {
                response.setField(42, requestContext.getMerchantId());
            }
            if (requestContext.getRrn() != null) {
                response.setField(37, requestContext.getRrn());
            }
            if (requestContext.getSourceAccount() != null) {
                response.setField(102, requestContext.getSourceAccount());
            }

            // 4. 設定回應碼
            response.setField(39, fiscResponse.getResponseCode());

            // 5. 設定授權碼（如果有）
            if (fiscResponse.getAuthCode() != null && !fiscResponse.getAuthCode().isEmpty()) {
                response.setField(38, fiscResponse.getAuthCode());
            }

            // 6. 設定時間
            LocalDateTime now = LocalDateTime.now();
            response.setField(12, now.format(TIME_FORMAT));
            response.setField(13, now.format(DATE_FORMAT));

            // 7. 序列化
            return messageFactory.assemble(response);

        } catch (Exception e) {
            log.error("建立 ATM 回應失敗: {}", e.getMessage(), e);
            return null;
        }
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
     * 複製欄位（如果存在）
     */
    private void copyFieldIfPresent(Iso8583Message source, Iso8583Message target, int fieldNum) {
        Object value = source.getField(fieldNum);
        if (value != null) {
            target.setField(fieldNum, value);
        }
    }
}
