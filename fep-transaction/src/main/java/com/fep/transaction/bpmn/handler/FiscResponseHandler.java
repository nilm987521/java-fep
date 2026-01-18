package com.fep.transaction.bpmn.handler;

import com.fep.transaction.bpmn.service.TransferProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * STAN 到 ProcessId 的映射
     * 用於將 FISC 回應關聯到正確的流程實例
     */
    private final Map<String, String> stanToProcessMap = new ConcurrentHashMap<>();

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
     * 處理 FISC 0210 回應
     *
     * <p>此方法由 ReceiveChannelHandler 呼叫
     *
     * @param stan STAN
     * @param responseCode 回應碼
     * @param rawMessage 原始電文 (供後續處理)
     */
    public void handleTransferResponse(String stan, String responseCode, byte[] rawMessage) {
        String processId = stanToProcessMap.get(stan);

        if (processId == null) {
            log.warn("收到無法匹配的 FISC 回應: STAN={}, RC={}", stan, responseCode);
            return;
        }

        log.info("處理 FISC 回應: STAN={}, RC={}, processId={}", stan, responseCode, processId);

        try {
            // 準備流程變數
            Map<String, Object> variables = new HashMap<>();
            variables.put("responseCode", responseCode);
            variables.put("fiscResponseReceived", true);
            variables.put("fiscResponseTime", System.currentTimeMillis());

            // 發送訊息至流程 (觸發 Message Catch Event)
            processService.correlateMessage(
                processId,
                "TransferResponse0210",  // 對應 BPMN 中的 Message name
                variables
            );

            log.info("已通知流程回應: processId={}, RC={}", processId, responseCode);

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

            processService.correlateMessage(
                processId,
                "ReversalResponse0410",
                variables
            );

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
