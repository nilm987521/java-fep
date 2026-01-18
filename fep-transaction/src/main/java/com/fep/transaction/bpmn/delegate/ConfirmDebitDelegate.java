package com.fep.transaction.bpmn.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * BPMN Service Task Delegate: 確認扣款
 *
 * <p>對應 BPMN 中的 Task_ConfirmDebit
 * <p>收到 FISC 成功回應後，將凍結款項轉為正式扣款
 */
@Slf4j
@Component("confirmDebitDelegate")
@RequiredArgsConstructor
public class ConfirmDebitDelegate implements JavaDelegate {

    // 注入核心銀行服務
    // private final CoreBankingService coreBankingService;
    // private final AccountService accountService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        Long amount = (Long) execution.getVariable("amount");
        String freezeId = (String) execution.getVariable("freezeId");
        String rrn = (String) execution.getVariable("rrn");

        log.info("[{}] 開始確認扣款: 帳號={}, 金額={}, freezeId={}",
            transactionId, maskAccount(sourceAccount), amount, freezeId);

        try {
            // 1. 驗證凍結狀態
            String freezeStatus = (String) execution.getVariable("freezeStatus");
            if (!"FROZEN".equals(freezeStatus)) {
                throw new IllegalStateException("凍結狀態異常: " + freezeStatus);
            }

            // 2. 將凍結轉為正式扣款
            // coreBankingService.confirmDebit(freezeId, sourceAccount, amount);

            // 模擬扣款成功
            log.info("[{}] 模擬確認扣款成功", transactionId);

            // 3. 更新流程變數
            execution.setVariable("debitStatus", "CONFIRMED");
            execution.setVariable("debitTime", LocalDateTime.now().toString());
            execution.setVariable("freezeStatus", "RELEASED");

            log.info("[{}] 確認扣款完成: RRN={}", transactionId, rrn);

        } catch (Exception e) {
            log.error("[{}] 確認扣款失敗: {}", transactionId, e.getMessage());
            execution.setVariable("debitStatus", "FAILED");
            execution.setVariable("errorMessage", e.getMessage());
            throw new BpmnError("DEBIT_ERROR", "確認扣款失敗: " + e.getMessage());
        }
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 8) return "****";
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }
}
