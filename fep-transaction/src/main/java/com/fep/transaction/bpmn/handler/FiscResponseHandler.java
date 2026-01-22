package com.fep.transaction.bpmn.handler;

import com.fep.transaction.bpmn.service.TransferProcessService;
import com.fep.transaction.db.service.PendingTransactionDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FISC 回應處理器
 *
 * <p>接收 FiscDualChannelClient 的回應，並關聯至對應的 BPMN 流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FiscResponseHandler {

    private final TransferProcessService processService;
    private final RuntimeService runtimeService;
    private final PendingTransactionDbService dbService;

    /**
     * Response BPMN Process Key
     */
    private static final String RESPONSE_PROCESS_KEY = "Process_TransferResponse";

    /**
     * Timeout BPMN Process Key
     */
    private static final String TIMEOUT_PROCESS_KEY = "Process_TransferTimeout";

    /**
     * 是否使用高 TPS 架構 (兩個 BPMN 流程模式)
     */
    @Value("${fep.bpmn.high-tps-mode:true}")
    private boolean highTpsMode;

    /**
     * STAN 到 ProcessId 的映射
     * 用於將 FISC 回應關聯到正確的流程實例
     */
    private final Map<String, String> stanToProcessMap = new ConcurrentHashMap<>();

    /**
     * STAN 到 TransactionId 的映射 (高 TPS 模式使用)
     */
    private final Map<String, String> stanToTransactionIdMap = new ConcurrentHashMap<>();

    /**
     * 註冊 STAN 與流程的關聯
     *
     * @param stan STAN
     * @param processId 流程實例 ID
     */
    public void registerStan(String stan, String processId) {
        log.debug("註冊 STAN 映射: STAN={} -> processId={}", stan, processId);
        stanToProcessMap.put(stan, processId);
    }

    /**
     * 移除 STAN 映射
     *
     * @param stan STAN
     */
    public void unregisterStan(String stan) {
        stanToProcessMap.remove(stan);
    }

    /**
     * BPMN 訊息名稱常數
     *
     * <p>這些名稱必須與 interbank-transfer.bpmn 中定義的 Message name 一致：
     * <ul>
     *   <li>Message_FiscResponse: name="FiscResponse"</li>
     *   <li>Message_ReversalResponse: name="ReversalResponse"</li>
     * </ul>
     */
    public static final String MESSAGE_FISC_RESPONSE = "FiscResponse";
    public static final String MESSAGE_REVERSAL_RESPONSE = "ReversalResponse";

    /**
     * 處理 FISC 0210 回應
     *
     * <p>此方法由 ReceiveChannelHandler 或 TransactionEventListener 呼叫
     *
     * <p>高 TPS 模式：啟動 Response BPMN 流程
     * <p>傳統模式：使用 Message Correlation
     *
     * @param stan STAN
     * @param responseCode 回應碼
     * @param rawMessage 原始電文 (供後續處理)
     */
    public void handleTransferResponse(String stan, String responseCode, byte[] rawMessage) {
        if (highTpsMode) {
            handleTransferResponseHighTps(stan, responseCode, rawMessage);
        } else {
            handleTransferResponseLegacy(stan, responseCode, rawMessage);
        }
    }

    /**
     * 高 TPS 模式：啟動 Response BPMN 流程
     */
    private void handleTransferResponseHighTps(String stan, String responseCode, byte[] rawMessage) {
        // 從 DB 取得 transactionId
        String transactionId = dbService.getTransactionIdByStan(stan);

        if (transactionId == null) {
            log.warn("收到無法匹配的 FISC 回應 (高 TPS 模式): STAN={}, RC={}", stan, responseCode);
            return;
        }

        log.info("處理 FISC 回應 (高 TPS 模式): STAN={}, RC={}, txnId={}", stan, responseCode, transactionId);

        try {
            // 準備流程變數
            Map<String, Object> variables = new HashMap<>();
            variables.put("transactionId", transactionId);
            variables.put("stan", stan);
            variables.put("responseCode", responseCode);
            variables.put("fiscResponseReceived", true);
            variables.put("fiscResponseTime", System.currentTimeMillis());

            if (rawMessage != null) {
                variables.put("rawResponseMessage", rawMessage);
            }

            // 啟動 Response BPMN 流程
            String businessKey = "RESP-" + transactionId;
            ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                    RESPONSE_PROCESS_KEY,
                    businessKey,
                    variables
            );

            log.info("Response BPMN 流程已啟動: processId={}, txnId={}, RC={}",
                    instance.getId(), transactionId, responseCode);

        } catch (Exception e) {
            log.error("啟動 Response BPMN 流程失敗: STAN={}, txnId={}, error={}",
                    stan, transactionId, e.getMessage(), e);
        }
    }

    /**
     * 傳統模式：使用 Message Correlation
     */
    private void handleTransferResponseLegacy(String stan, String responseCode, byte[] rawMessage) {
        String processId = stanToProcessMap.get(stan);

        if (processId == null) {
            log.warn("收到無法匹配的 FISC 回應 (傳統模式): STAN={}, RC={}", stan, responseCode);
            return;
        }

        log.info("處理 FISC 回應 (傳統模式): STAN={}, RC={}, processId={}", stan, responseCode, processId);

        try {
            // 準備流程變數
            Map<String, Object> variables = new HashMap<>();
            variables.put("responseCode", responseCode);
            variables.put("fiscResponseReceived", true);
            variables.put("fiscResponseTime", System.currentTimeMillis());

            // 發送訊息至流程 (觸發 Message Catch Event)
            processService.correlateMessage(
                processId,
                MESSAGE_FISC_RESPONSE,
                variables
            );

            log.info("已通知流程回應: processId={}, message={}, RC={}",
                    processId, MESSAGE_FISC_RESPONSE, responseCode);

        } catch (Exception e) {
            log.error("通知流程失敗: STAN={}, error={}", stan, e.getMessage(), e);
        } finally {
            // 清理映射
            unregisterStan(stan);
        }
    }

    /**
     * 處理 FISC 0410 沖正回應
     *
     * @param stan STAN
     * @param responseCode 回應碼
     */
    public void handleReversalResponse(String stan, String responseCode) {
        String processId = stanToProcessMap.get(stan);

        if (processId == null) {
            log.warn("收到無法匹配的沖正回應: STAN={}, RC={}", stan, responseCode);
            return;
        }

        log.info("處理沖正回應: STAN={}, RC={}, processId={}", stan, responseCode, processId);

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("reversalResult", "00".equals(responseCode) ? "OK" : "FAIL");
            variables.put("reversalResponseCode", responseCode);

            // 使用與 BPMN 定義一致的訊息名稱: "ReversalResponse"
            processService.correlateMessage(
                processId,
                MESSAGE_REVERSAL_RESPONSE,
                variables
            );

            log.info("已通知流程沖正回應: processId={}, message={}, RC={}",
                    processId, MESSAGE_REVERSAL_RESPONSE, responseCode);

        } catch (Exception e) {
            log.error("通知沖正回應失敗: STAN={}, error={}", stan, e.getMessage(), e);
        } finally {
            unregisterStan(stan);
        }
    }

    /**
     * 取得待處理的 STAN 數量 (監控用)
     */
    public int getPendingCount() {
        return stanToProcessMap.size();
    }
}
