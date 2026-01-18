package com.fep.transaction.bpmn.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * BPMN Service Task Delegate: 記錄交易日誌
 *
 * <p>對應 BPMN 中的 Task_LogSuccess
 * <p>將完成的交易記錄到稽核日誌
 */
@Slf4j
@Component("logTransactionDelegate")
@RequiredArgsConstructor
public class LogTransactionDelegate implements JavaDelegate {

    // 注入稽核日誌服務
    // private final AuditLogService auditLogService;
    // private final TransactionLogRepository transactionLogRepository;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();

        log.info("[{}] 開始記錄交易日誌", transactionId);

        try {
            // 1. 收集交易資訊
            Map<String, Object> transactionLog = new HashMap<>();
            transactionLog.put("processInstanceId", transactionId);
            transactionLog.put("businessKey", execution.getProcessBusinessKey());

            // 交易基本資訊
            transactionLog.put("sourceAccount", execution.getVariable("sourceAccount"));
            transactionLog.put("targetAccount", execution.getVariable("targetAccount"));
            transactionLog.put("amount", execution.getVariable("amount"));
            transactionLog.put("channelId", execution.getVariable("channelId"));

            // 電文資訊
            transactionLog.put("mti", execution.getVariable("mti"));
            transactionLog.put("stan", execution.getVariable("stan"));
            transactionLog.put("rrn", execution.getVariable("rrn"));
            transactionLog.put("responseCode", execution.getVariable("responseCode"));

            // 狀態資訊
            transactionLog.put("debitStatus", execution.getVariable("debitStatus"));
            transactionLog.put("freezeId", execution.getVariable("freezeId"));

            // 時間戳
            transactionLog.put("logTime", LocalDateTime.now().toString());
            transactionLog.put("completionTime", LocalDateTime.now().toString());

            // 2. 儲存交易日誌
            // transactionLogRepository.save(transactionLog);
            // auditLogService.logTransaction(transactionLog);

            // 模擬記錄成功
            log.info("[{}] 交易日誌內容: {}", transactionId, transactionLog);

            // 3. 設定完成標記
            execution.setVariable("logStatus", "LOGGED");
            execution.setVariable("transactionStatus", "SUCCESS");
            execution.setVariable("completionTime", LocalDateTime.now().toString());

            log.info("[{}] 交易日誌記錄完成", transactionId);

        } catch (Exception e) {
            log.error("[{}] 交易日誌記錄失敗: {}", transactionId, e.getMessage());
            // 日誌記錄失敗不應影響交易結果，僅記錄警告
            execution.setVariable("logStatus", "FAILED");
            execution.setVariable("logError", e.getMessage());
        }
    }
}
