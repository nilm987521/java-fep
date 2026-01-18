package com.fep.transaction.bpmn.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BPMN Service Task Delegate: 標記待調帳
 *
 * <p>對應 BPMN 中的 Task_MarkPendingAdjust
 * <p>當沖正失敗或超時時，將交易標記為待人工調帳
 */
@Slf4j
@Component("markPendingAdjustDelegate")
@RequiredArgsConstructor
public class MarkPendingAdjustDelegate implements JavaDelegate {

    // 注入調帳服務
    // private final AdjustmentService adjustmentService;
    // private final AlertService alertService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();

        log.warn("[{}] 交易需要人工調帳處理", transactionId);

        try {
            // 1. 收集調帳資訊
            Map<String, Object> adjustmentRecord = new HashMap<>();
            adjustmentRecord.put("adjustmentId", generateAdjustmentId());
            adjustmentRecord.put("processInstanceId", transactionId);
            adjustmentRecord.put("businessKey", execution.getProcessBusinessKey());

            // 交易資訊
            adjustmentRecord.put("sourceAccount", execution.getVariable("sourceAccount"));
            adjustmentRecord.put("targetAccount", execution.getVariable("targetAccount"));
            adjustmentRecord.put("amount", execution.getVariable("amount"));
            adjustmentRecord.put("channelId", execution.getVariable("channelId"));

            // 原始交易資訊
            adjustmentRecord.put("originalStan", execution.getVariable("stan"));
            adjustmentRecord.put("originalRrn", execution.getVariable("rrn"));
            adjustmentRecord.put("originalMti", execution.getVariable("mti"));

            // 沖正資訊
            adjustmentRecord.put("reversalStan", execution.getVariable("reversalStan"));
            adjustmentRecord.put("reversalStatus", execution.getVariable("reversalStatus"));
            adjustmentRecord.put("reversalError", execution.getVariable("reversalError"));

            // 凍結資訊
            adjustmentRecord.put("freezeId", execution.getVariable("freezeId"));
            adjustmentRecord.put("freezeStatus", execution.getVariable("freezeStatus"));

            // 調帳原因
            String reason = determineAdjustmentReason(execution);
            adjustmentRecord.put("adjustmentReason", reason);
            adjustmentRecord.put("createTime", LocalDateTime.now().toString());
            adjustmentRecord.put("status", "PENDING");
            adjustmentRecord.put("priority", determinePriority(execution));

            // 2. 儲存調帳記錄
            // adjustmentService.createAdjustmentRecord(adjustmentRecord);

            // 模擬儲存
            log.warn("[{}] 調帳記錄: {}", transactionId, adjustmentRecord);

            // 3. 發送告警通知
            // alertService.sendAlert(AlertType.PENDING_ADJUSTMENT, adjustmentRecord);

            log.warn("[{}] 已發送待調帳告警通知", transactionId);

            // 4. 設定流程變數
            execution.setVariable("adjustmentId", adjustmentRecord.get("adjustmentId"));
            execution.setVariable("adjustmentReason", reason);
            execution.setVariable("adjustmentStatus", "PENDING");
            execution.setVariable("transactionStatus", "PENDING_ADJUSTMENT");
            execution.setVariable("requireManualIntervention", true);
            execution.setVariable("completionTime", LocalDateTime.now().toString());

            log.warn("[{}] 交易已標記為待調帳: adjustmentId={}",
                transactionId, adjustmentRecord.get("adjustmentId"));

        } catch (Exception e) {
            log.error("[{}] 標記待調帳失敗: {}", transactionId, e.getMessage());
            // 即使標記失敗也要記錄，避免遺漏
            execution.setVariable("adjustmentStatus", "MARK_FAILED");
            execution.setVariable("adjustmentError", e.getMessage());
            execution.setVariable("requireManualIntervention", true);
        }
    }

    private String generateAdjustmentId() {
        return "ADJ" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String determineAdjustmentReason(DelegateExecution execution) {
        String reversalStatus = (String) execution.getVariable("reversalStatus");
        String reversalError = (String) execution.getVariable("reversalError");

        if ("SEND_FAILED".equals(reversalStatus)) {
            return "沖正電文發送失敗: " + (reversalError != null ? reversalError : "未知錯誤");
        } else if (reversalStatus == null || "SENT".equals(reversalStatus)) {
            return "沖正回應超時";
        } else {
            return "沖正處理異常: " + reversalStatus;
        }
    }

    private String determinePriority(DelegateExecution execution) {
        Long amount = (Long) execution.getVariable("amount");
        if (amount != null && amount >= 1_000_000L) { // 100萬以上
            return "HIGH";
        } else if (amount != null && amount >= 100_000L) { // 10萬以上
            return "MEDIUM";
        }
        return "LOW";
    }
}
