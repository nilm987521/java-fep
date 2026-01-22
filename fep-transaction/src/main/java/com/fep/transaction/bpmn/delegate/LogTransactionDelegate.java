package com.fep.transaction.bpmn.delegate;

import com.fep.transaction.db.entity.FepTransactionLogEntity;
import com.fep.transaction.db.repository.FepTransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;

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

    private final FepTransactionLogRepository transactionLogRepository;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();

        if (log.isDebugEnabled()) {
            log.debug("[{}] 開始記錄交易日誌", transactionId);
        }

        try {
            // 1. 收集交易資訊
            FepTransactionLogEntity transactionLog = new FepTransactionLogEntity();
            transactionLog.setTransactionId(transactionId);
            transactionLog.setStan((String) execution.getVariable("stan"));
            transactionLog.setLogStage(FepTransactionLogEntity.LogStage.REQUEST_RECEIVED);
            transactionLog.setLogLevel(FepTransactionLogEntity.LogLevel.INFO);
            transactionLog.setLogTime(LocalDateTime.now());
            transactionLog.setLogDate(sdf.format(new Date()));

            transactionLogRepository.save(transactionLog);

            // 模擬記錄成功
            if (log.isDebugEnabled()) {
                log.debug("[{}] 交易日誌內容: {}", transactionId, transactionLog);
            }

            // 3. 設定完成標記
            execution.setVariable("logStatus", "LOGGED");
            execution.setVariable("transactionStatus", "SUCCESS");
            execution.setVariable("completionTime", LocalDateTime.now().toString());

            if (log.isDebugEnabled()) {
                log.debug("[{}] 交易日誌記錄完成", transactionId);
            }

        } catch (Exception e) {
            log.error("[{}] 交易日誌記錄失敗: {}", transactionId, e.getMessage());
            // 日誌記錄失敗不應影響交易結果，僅記錄警告
            execution.setVariable("logStatus", "FAILED");
            execution.setVariable("logError", e.getMessage());
        }
    }
}
