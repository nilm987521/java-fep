package com.fep.transaction.bpmn.delegate;

import com.fep.transaction.db.service.PendingTransactionDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * BPMN Service Task Delegate: 清理交易資料
 *
 * <p>此 Delegate 用於 Response/Timeout BPMN 流程的最後階段。
 *
 * <p>純 DB 架構：不需要顯式清理，資料保留由 DB retention policy 處理。
 * 此 Delegate 保留是為了與 BPMN 流程相容，實際上不執行任何清理操作。
 *
 * <p>流程變數輸入：
 * <ul>
 *   <li>transactionId - 交易 ID</li>
 *   <li>stan - STAN</li>
 * </ul>
 */
@Slf4j
@Component("cleanupRedisDelegate")
@RequiredArgsConstructor
public class CleanupRedisDelegate implements JavaDelegate {

    private final PendingTransactionDbService dbService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processId = execution.getProcessInstanceId();
        String transactionId = (String) execution.getVariable("transactionId");
        String stan = (String) execution.getVariable("stan");

        log.debug("[{}] 清理交易資料 (no-op for DB): txnId={}, stan={}", processId, transactionId, stan);

        try {
            // 純 DB 架構不需要顯式清理
            // 資料保留由 DB retention policy 或排程任務處理
            dbService.cleanup(transactionId, stan);

            log.debug("[{}] 清理完成: txnId={}, stan={}", processId, transactionId, stan);

            execution.setVariable("cleanedUp", true);

        } catch (Exception e) {
            log.warn("[{}] 清理失敗 (非關鍵): txnId={}, error={}",
                    processId, transactionId, e.getMessage());
            // 非關鍵錯誤，不拋出例外
            execution.setVariable("cleanedUp", false);
        }
    }
}
