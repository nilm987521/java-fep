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
 * <p>提供啟動和管理 BPMN 流程的 API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferProcessService {

    private final RuntimeService runtimeService;

    /**
     * BPMN Process Key
     *
     * <p>必須與 interbank-transfer.bpmn 中定義的 process id 一致：
     * {@code <bpmn:process id="Process_InterbankTransfer" ...>}
     */
    private static final String PROCESS_KEY = "Process_InterbankTransfer";

    /**
     * 啟動跨行轉帳流程
     *
     * @param request 轉帳請求
     * @return 流程實例 ID
     */
    public String startTransferProcess(TransferRequest request) {
        log.info("啟動跨行轉帳流程: {} -> {}, 金額={}",
            request.getSourceAccount(), request.getTargetAccount(), request.getAmount());

        // 準備流程變數
        Map<String, Object> variables = new HashMap<>();
        variables.put("sourceAccount", request.getSourceAccount());
        variables.put("targetAccount", request.getTargetAccount());
        variables.put("amount", request.getAmount());
        variables.put("sourceBankCode", request.getSourceBankCode());
        variables.put("targetBankCode", request.getTargetBankCode());
        variables.put("isDesignated", request.isDesignated());
        variables.put("channel", request.getChannel());
        variables.put("requestTime", System.currentTimeMillis());

        // 啟動流程
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            PROCESS_KEY,
            request.getBusinessKey(),
            variables
        );

        log.info("流程已啟動: processId={}, businessKey={}",
            instance.getId(), request.getBusinessKey());

        return instance.getId();
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
        private String sourceAccount;     // 來源帳號
        private String targetAccount;     // 目標帳號
        private long amount;              // 金額
        private String sourceBankCode;    // 來源銀行代碼
        private String targetBankCode;    // 目標銀行代碼
        private boolean designated;       // 是否約定轉帳
        private String channel;           // 通道 (ATM/WEB/MOBILE)
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
