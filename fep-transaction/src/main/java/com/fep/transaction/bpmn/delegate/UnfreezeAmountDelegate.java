package com.fep.transaction.bpmn.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * BPMN Service Task Delegate: 解凍款項
 *
 * <p>對應 BPMN 中的 Task_UnfreezeAmount 和 Task_UnfreezeAfterReversal
 * <p>當交易失敗或沖正成功時，釋放凍結的款項
 */
@Slf4j
@Component("unfreezeAmountDelegate")
@RequiredArgsConstructor
public class UnfreezeAmountDelegate implements JavaDelegate {

    // 注入核心銀行服務
    // private final CoreBankingService coreBankingService;
    // private final AccountService accountService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        Long amount = (Long) execution.getVariable("amount");
        String freezeId = (String) execution.getVariable("freezeId");

        log.info("[{}] 開始解凍款項: 帳號={}, 金額={}, freezeId={}",
            transactionId, maskAccount(sourceAccount), amount, freezeId);

        try {
            // 1. 檢查凍結狀態
            String freezeStatus = (String) execution.getVariable("freezeStatus");
            if ("RELEASED".equals(freezeStatus)) {
                log.info("[{}] 款項已解凍，跳過", transactionId);
                return;
            }

            // 2. 執行解凍
            if (freezeId != null) {
                // coreBankingService.unfreezeAmount(freezeId, sourceAccount, amount);

                // 模擬解凍成功
                log.info("[{}] 模擬解凍成功: freezeId={}", transactionId, freezeId);
            }

            // 3. 更新流程變數
            execution.setVariable("freezeStatus", "RELEASED");
            execution.setVariable("unfreezeTime", LocalDateTime.now().toString());

            log.info("[{}] 款項解凍完成", transactionId);

        } catch (Exception e) {
            log.error("[{}] 款項解凍失敗: {}", transactionId, e.getMessage());
            // 解凍失敗需要人工介入
            execution.setVariable("unfreezeStatus", "FAILED");
            execution.setVariable("unfreezeError", e.getMessage());
            execution.setVariable("requireManualIntervention", true);
            // 不拋出異常，讓流程繼續
        }
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 8) return "****";
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }
}
