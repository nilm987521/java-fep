package com.fep.transaction.bpmn.delegate;

import com.fep.transaction.db.entity.FepTransactionEntity.TransactionStatus;
import com.fep.transaction.db.entity.FepTransactionEntity.ReversalStatus;
import com.fep.transaction.db.service.PendingTransactionDbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * BPMN Service Task Delegate: 更新交易狀態至 DB
 *
 * <p>此 Delegate 用於 Response/Timeout BPMN 流程，更新交易的最終狀態。
 *
 * <p>純 DB 架構：直接更新 DB 中的交易狀態。
 *
 * <p>更新內容：
 * <ul>
 *   <li>交易狀態 (COMPLETED/FAILED/TIMEOUT/REVERSED)</li>
 *   <li>回應碼</li>
 *   <li>FISC 回應時間</li>
 *   <li>沖正狀態 (如果有)</li>
 * </ul>
 *
 * <p>流程變數輸入：
 * <ul>
 *   <li>transactionId - 交易 ID</li>
 *   <li>responseCode - 回應碼</li>
 *   <li>transactionStatus - 交易狀態 (COMPLETED/FAILED/TIMEOUT/REVERSED)</li>
 *   <li>reversalStatus - 沖正狀態 (可選)</li>
 * </ul>
 */
@Slf4j
@Component("updateTransactionStatusDelegate")
@RequiredArgsConstructor
public class UpdateTransactionStatusDelegate implements JavaDelegate {

    private final PendingTransactionDbService dbService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processId = execution.getProcessInstanceId();
        String transactionId = (String) execution.getVariable("transactionId");
        String stan = (String) execution.getVariable("stan");
        String responseCode = (String) execution.getVariable("responseCode");
        String transactionStatus = (String) execution.getVariable("transactionStatus");

        log.info("[{}] 更新交易狀態: txnId={}, stan={}, RC={}, status={}",
                processId, transactionId, stan, responseCode, transactionStatus);

        try {
            // 1. 決定新狀態
            TransactionStatus newStatus = determineStatus(transactionStatus, responseCode);

            // 2. 更新交易狀態
            dbService.updateStatus(transactionId, newStatus, responseCode);

            // 3. 更新沖正狀態 (如果有)
            String reversalStatusStr = (String) execution.getVariable("reversalStatus");
            if (reversalStatusStr != null && !reversalStatusStr.isEmpty()) {
                try {
                    ReversalStatus reversalStatus = ReversalStatus.valueOf(reversalStatusStr);
                    dbService.updateReversalStatus(transactionId, reversalStatus);
                } catch (IllegalArgumentException e) {
                    log.warn("無效的沖正狀態: {}", reversalStatusStr);
                }
            }

            log.info("[{}] 交易狀態已更新: txnId={}, stan={}, status={}",
                    processId, transactionId, stan, newStatus);

        } catch (Exception e) {
            log.error("[{}] 更新交易狀態失敗: txnId={}, error={}",
                    processId, transactionId, e.getMessage(), e);
            // 非關鍵錯誤，不拋出例外，避免影響流程
        }
    }

    /**
     * 根據流程變數決定交易狀態
     */
    private TransactionStatus determineStatus(String transactionStatus, String responseCode) {
        // 優先使用明確指定的狀態
        if (transactionStatus != null && !transactionStatus.isEmpty()) {
            try {
                return TransactionStatus.valueOf(transactionStatus);
            } catch (IllegalArgumentException e) {
                log.warn("無效的交易狀態: {}", transactionStatus);
            }
        }

        // 根據回應碼判斷
        if ("00".equals(responseCode)) {
            return TransactionStatus.COMPLETED;
        } else {
            return TransactionStatus.FAILED;
        }
    }
}
