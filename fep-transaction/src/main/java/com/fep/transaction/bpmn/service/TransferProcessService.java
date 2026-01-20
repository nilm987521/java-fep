package com.fep.transaction.bpmn.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 跨行轉帳流程服務
 *
 * <p>提供啟動和管理 BPMN 流程的 API。支援動態流程路由，
 * 可根據通道、MTI 和 Processing Code 決定啟動哪個 BPMN 流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferProcessService {

    private final RuntimeService runtimeService;
    private final ProcessRouterService processRouterService;

    /**
     * 預設 BPMN Process Key
     *
     * <p>必須與 interbank-transfer.bpmn 中定義的 process id 一致：
     * {@code <bpmn:process id="Process_InterbankTransfer" ...>}
     */
    private static final String DEFAULT_PROCESS_KEY = "Process_InterbankTransfer";

    /**
     * 啟動跨行轉帳流程（使用預設流程）
     *
     * @param request 轉帳請求
     * @return 流程實例 ID
     */
    public String startTransferProcess(TransferRequest request) {
        return startTransferProcess(request, null, null, null);
    }

    /**
     * 啟動流程（支援動態路由）
     *
     * <p>根據通道 ID、MTI 和 Processing Code 決定啟動哪個 BPMN 流程。
     *
     * @param request 轉帳請求
     * @param channelId 通道 ID (e.g., "ATM_FISC_V1")
     * @param mti MTI (e.g., "0200", "2500")
     * @param processingCode Processing Code (e.g., "400000")
     * @return 流程實例 ID
     */
    public String startTransferProcess(TransferRequest request, String channelId, String mti, String processingCode) {
        // 解析流程 Key
        String processKey = resolveProcessKey(channelId, mti, processingCode);
        return startProcessWithKey(request, processKey, channelId, mti, processingCode);
    }

    /**
     * 啟動流程（直接使用指定的 processKey）
     *
     * <p>此方法用於已經由 ProcessRouterService 解析好 processKey 的情況，
     * 避免重複解析，提高效能。
     *
     * @param request 轉帳請求
     * @param processKey BPMN 流程 Key（由 ProcessRouterService 預先解析）
     * @param channelId 通道 ID
     * @param mti MTI
     * @param processingCode Processing Code
     * @return 流程實例 ID
     */
    public String startProcessWithKey(TransferRequest request, String processKey,
                                       String channelId, String mti, String processingCode) {
        log.info("啟動流程: processKey={}, channel={}, mti={}, {} -> {}, 金額={}",
            processKey, channelId, mti,
            request.getSourceAccount(), request.getTargetAccount(), request.getAmount());

        // 準備流程變數
        Map<String, Object> variables = new HashMap<>();
        variables.put("stan", request.getStan());
        variables.put("sourceAccount", request.getSourceAccount());
        variables.put("targetAccount", request.getTargetAccount());
        variables.put("amount", request.getAmount());
        variables.put("sourceBankCode", request.getSourceBankCode());
        variables.put("targetBankCode", request.getTargetBankCode());
        variables.put("isDesignated", request.isDesignated());
        variables.put("channel", request.getChannel());
        variables.put("channelId", channelId);
        variables.put("mti", mti);
        variables.put("processingCode", processingCode);
        variables.put("requestTime", System.currentTimeMillis());
        // 原始電文 (用於組裝回應)
        if (request.getRawMessage() != null) {
            variables.put("rawMessage", request.getRawMessage());
        }

        // 啟動流程
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            processKey,
            request.getBusinessKey(),
            variables
        );

        log.info("流程已啟動: processId={}, processKey={}, businessKey={}",
            instance.getId(), processKey, request.getBusinessKey());

        return instance.getId();
    }

    /**
     * 解析流程 Key
     */
    private String resolveProcessKey(String channelId, String mti, String processingCode) {
        if (processRouterService != null && processRouterService.isEnabled()) {
            return processRouterService.resolveProcessKey(channelId, mti, processingCode);
        }
        return DEFAULT_PROCESS_KEY;
    }

    /**
     * 查詢流程狀態
     *
     * @param processId 流程實例 ID
     * @return 流程狀態
     */
    public ProcessStatus getProcessStatus(String processId) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processId)
            .singleResult();

        if (instance == null) {
            // 流程已結束，查詢歷史
            return ProcessStatus.COMPLETED;
        }

        if (instance.isSuspended()) {
            return ProcessStatus.SUSPENDED;
        }

        return ProcessStatus.RUNNING;
    }

    /**
     * 取消流程
     *
     * @param processId 流程實例 ID
     * @param reason 取消原因
     */
    public void cancelProcess(String processId, String reason) {
        log.info("取消流程: processId={}, reason={}", processId, reason);
        runtimeService.deleteProcessInstance(processId, reason);
    }

    /**
     * 發送訊號事件 (用於接收 FISC 回應)
     *
     * @param processId 流程實例 ID
     * @param messageName 訊息名稱
     * @param variables 變數
     */
    public void correlateMessage(String processId, String messageName, Map<String, Object> variables) {
        log.info("發送訊息至流程: processId={}, message={}", processId, messageName);
        runtimeService.createMessageCorrelation(messageName)
            .processInstanceId(processId)
            .setVariables(variables)
            .correlate();
    }

    // ==================== Inner Classes ====================

    /**
     * 轉帳請求
     */
    @lombok.Data
    @lombok.Builder
    public static class TransferRequest {
        private String businessKey;       // 業務鍵 (如交易序號)
        private String stan;              // 交易追蹤號 (System Trace Audit Number)
        private String sourceAccount;     // 來源帳號
        private String targetAccount;     // 目標帳號
        private long amount;              // 金額
        private String sourceBankCode;    // 來源銀行代碼
        private String targetBankCode;    // 目標銀行代碼
        private boolean designated;       // 是否約定轉帳
        private String channel;           // 通道 (ATM/WEB/MOBILE)
        private byte[] rawMessage;        // 原始電文 (序列化的 Iso8583Message)
    }

    /**
     * 流程狀態
     */
    public enum ProcessStatus {
        RUNNING,
        SUSPENDED,
        COMPLETED,
        CANCELLED
    }
}
