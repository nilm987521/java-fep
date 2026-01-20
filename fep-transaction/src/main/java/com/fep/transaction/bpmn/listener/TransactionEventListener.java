package com.fep.transaction.bpmn.listener;

import com.fep.common.event.FiscResponseEvent;
import com.fep.common.event.TransactionRequestEvent;
import com.fep.transaction.bpmn.service.TransferProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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
 *   <li>監聽 {@link FiscResponseEvent}，觸發 BPMN 訊息關聯</li>
 *   <li>管理 STAN → ProcessId 對應關係</li>
 *   <li>管理 STAN → ResponseCallback 對應關係</li>
 * </ul>
 *
 * <p>流程圖：
 * <pre>
 * TransactionRequestEvent ──► TransactionEventListener
 *                                     │
 *                                     ▼
 *                             startTransferProcess()
 *                                     │
 *                                     ▼
 *                             BPMN Process Started
 *                                     │
 *                        (... BPMN 執行中 ...)
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final TransferProcessService processService;

    /**
     * STAN → ProcessId 映射
     */
    private final Map<String, String> stanToProcessMap = new ConcurrentHashMap<>();

    /**
     * STAN → Response Callback 映射
     */
    private final Map<String, Consumer<byte[]>> stanToCallbackMap = new ConcurrentHashMap<>();

    /**
     * ProcessId → STAN 反向映射 (用於流程結束時查找)
     */
    private final Map<String, String> processToStanMap = new ConcurrentHashMap<>();

    /**
     * 處理交易請求事件
     *
     * <p>收到 ATM/POS 的交易請求後，啟動 BPMN 流程
     *
     * @param event 交易請求事件
     */
    @Async
    @EventListener
    public void handleTransactionRequest(TransactionRequestEvent event) {
        String stan = event.getStan();
        String channelId = event.getChannelId();
        String processKey = event.getProcessKey();

        log.info("[{}] 收到交易請求事件: STAN={}, type={}, MTI={}, processKey={}",
                channelId, stan, event.getTransactionType(), event.getMti(), processKey);

        try {
            // 重要：先註冊 callback，因為 Camunda 流程是同步執行的
            // 流程可能在 startBpmnProcess() 返回之前就執行完畢並嘗試發送回應
            registerCallback(stan, event.getResponseCallback());

            // 啟動 BPMN 流程
            String processId = startBpmnProcess(event);

            // 補充註冊 processId 映射（用於 FISC 回應關聯）
            registerProcessMapping(stan, processId);

            log.info("[{}] BPMN 流程已啟動: STAN={}, processId={}, processKey={}",
                    channelId, stan, processId, processKey);

        } catch (Exception e) {
            log.error("[{}] 啟動 BPMN 流程失敗: STAN={}, processKey={}, error={}",
                    channelId, stan, processKey, e.getMessage(), e);

            // 清理已註冊的 callback
            stanToCallbackMap.remove(stan);

            // 發送錯誤回應
            sendErrorResponse(event, "96"); // System malfunction
        }
    }

    /**
     * 處理 FISC 回應事件
     *
     * <p>收到 FISC 回應後，觸發 BPMN 訊息關聯
     *
     * @param event FISC 回應事件
     */
    @Async
    @EventListener
    public void handleFiscResponse(FiscResponseEvent event) {
        String stan = event.getStan();
        String processId = stanToProcessMap.get(stan);

        if (processId == null) {
            log.warn("收到無法匹配的 FISC 回應: STAN={}, RC={}", stan, event.getResponseCode());
            return;
        }

        log.info("處理 FISC 回應: STAN={}, RC={}, processId={}, type={}",
                stan, event.getResponseCode(), processId, event.getResponseType());

        try {
            // 根據回應類型決定訊息名稱
            String messageName = determineMessageName(event);

            // 準備流程變數
            Map<String, Object> variables = buildResponseVariables(event);

            // 發送訊息至流程 (觸發 Message Catch Event)
            processService.correlateMessage(processId, messageName, variables);

            log.info("已通知流程回應: processId={}, message={}, RC={}",
                    processId, messageName, event.getResponseCode());

        } catch (Exception e) {
            log.error("通知流程失敗: STAN={}, processId={}, error={}",
                    stan, processId, e.getMessage(), e);
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
        String stan = processToStanMap.get(processId);
        if (stan == null) {
            log.warn("找不到流程對應的 STAN: processId={}", processId);
            return false;
        }

        Consumer<byte[]> callback = stanToCallbackMap.get(stan);
        if (callback == null) {
            log.warn("找不到回應 callback: STAN={}, processId={}", stan, processId);
            return false;
        }

        try {
            callback.accept(responseData);
            log.info("已發送回應給客戶端: STAN={}, processId={}", stan, processId);
            return true;
        } catch (Exception e) {
            log.error("發送回應失敗: STAN={}, processId={}, error={}",
                    stan, processId, e.getMessage(), e);
            return false;
        } finally {
            // 清理映射
            cleanupMappings(stan, processId);
        }
    }

    /**
     * 透過 STAN 發送回應給客戶端
     *
     * @param stan 交易追蹤號
     * @param responseData 回應資料
     * @return true 如果成功發送
     */
    public boolean sendResponseToClientByStan(String stan, byte[] responseData) {
        Consumer<byte[]> callback = stanToCallbackMap.get(stan);
        if (callback == null) {
            log.warn("找不到回應 callback: STAN={}", stan);
            return false;
        }

        String processId = stanToProcessMap.get(stan);

        try {
            callback.accept(responseData);
            log.info("已發送回應給客戶端: STAN={}", stan);
            return true;
        } catch (Exception e) {
            log.error("發送回應失敗: STAN={}, error={}", stan, e.getMessage(), e);
            return false;
        } finally {
            // 清理映射
            if (processId != null) {
                cleanupMappings(stan, processId);
            } else {
                stanToCallbackMap.remove(stan);
            }
        }
    }

    /**
     * 取得流程 ID
     *
     * @param stan STAN
     * @return 流程 ID，若不存在則返回 null
     */
    public String getProcessId(String stan) {
        return stanToProcessMap.get(stan);
    }

    /**
     * 取得 STAN
     *
     * @param processId 流程 ID
     * @return STAN，若不存在則返回 null
     */
    public String getStan(String processId) {
        return processToStanMap.get(processId);
    }

    /**
     * 取得待處理數量 (監控用)
     */
    public int getPendingCount() {
        return stanToProcessMap.size();
    }

    // ==================== Private Methods ====================

    /**
     * 啟動 BPMN 流程
     *
     * <p>使用事件中預先解析好的 processKey 啟動對應的 BPMN 流程。
     * processKey 已由 BpmnServerMessageHandler 透過 ProcessRouterService 解析。
     */
    private String startBpmnProcess(TransactionRequestEvent event) {
        // 建立轉帳請求
        TransferProcessService.TransferRequest request = buildTransferRequest(event);

        // 使用事件中的 processKey 啟動流程
        String processKey = event.getProcessKey();
        if (processKey != null && !processKey.isEmpty()) {
            return processService.startProcessWithKey(
                    request,
                    processKey,
                    event.getChannelId(),
                    event.getMti(),
                    event.getProcessingCode()
            );
        }

        // 向下相容：若 processKey 為空，使用舊的路由邏輯
        return processService.startTransferProcess(
                request,
                event.getChannelId(),
                event.getMti(),
                event.getProcessingCode()
        );
    }

    /**
     * 建立轉帳請求
     */
    private TransferProcessService.TransferRequest buildTransferRequest(TransactionRequestEvent event) {
        return TransferProcessService.TransferRequest.builder()
                .businessKey(generateBusinessKey(event))
                .stan(event.getStan())
                .sourceAccount(event.getPan())
                .targetAccount(event.getTargetAccount())
                .amount(parseAmount(event.getAmount()))
                .sourceBankCode(event.getSourceBankCode())
                .targetBankCode(event.getTargetBankCode())
                .designated(false) // 預設非約定，後續可從 DB 查詢
                .channel(event.getChannelId())
                .rawMessage(event.getRawMessage()) // 原始電文 (用於組裝回應)
                .build();
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
     */
    private void registerCallback(String stan, Consumer<byte[]> callback) {
        if (callback != null && stan != null) {
            stanToCallbackMap.put(stan, callback);
            log.debug("已註冊 callback: STAN={}", stan);
        }
    }

    /**
     * 註冊 processId 映射（在流程啟動之後）
     *
     * <p>用於 FISC 回應關聯時查找對應的流程。
     */
    private void registerProcessMapping(String stan, String processId) {
        if (stan != null && processId != null) {
            stanToProcessMap.put(stan, processId);
            processToStanMap.put(processId, stan);
            log.debug("已註冊流程映射: STAN={} <-> processId={}", stan, processId);
        }
    }

    /**
     * 清理映射關係
     */
    private void cleanupMappings(String stan, String processId) {
        stanToProcessMap.remove(stan);
        stanToCallbackMap.remove(stan);
        processToStanMap.remove(processId);

        log.debug("已清理映射: STAN={}, processId={}", stan, processId);
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
}
