package com.fep.transaction.bpmn.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BPMN Service Task Delegate: 扣款凍結
 *
 * <p>對應 BPMN 中的 Task_FreezeAmount
 * <p>在發送至 FISC 前，先凍結來源帳戶的款項
 */
@Slf4j
@Component("freezeAmountDelegate")
@RequiredArgsConstructor
public class FreezeAmountDelegate implements JavaDelegate {

    // 注入核心銀行服務
    // private final CoreBankingService coreBankingService;
    // private final AccountService accountService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        Long amount = (Long) execution.getVariable("amount");

        log.info("[{}] 開始扣款凍結: 帳號={}, 金額={}",
            transactionId, maskAccount(sourceAccount), amount);

        try {
            // 1. 檢查帳戶餘額
            // BigDecimal balance = accountService.getAvailableBalance(sourceAccount);
            // if (balance.compareTo(BigDecimal.valueOf(amount)) < 0) {
            //     throw new InsufficientBalanceException("餘額不足");
            // }

            // 2. 執行凍結
            String freezeId = generateFreezeId();
            // coreBankingService.freezeAmount(sourceAccount, amount, freezeId);

            // 模擬凍結成功
            log.info("[{}] 模擬凍結成功: freezeId={}", transactionId, freezeId);

            // 3. 記錄凍結資訊到流程變數
            execution.setVariable("freezeId", freezeId);
            execution.setVariable("freezeTime", LocalDateTime.now().toString());
            execution.setVariable("freezeStatus", "FROZEN");

            log.info("[{}] 扣款凍結完成: freezeId={}", transactionId, freezeId);

        } catch (Exception e) {
            log.error("[{}] 扣款凍結失敗: {}", transactionId, e.getMessage());
            execution.setVariable("freezeStatus", "FAILED");
            execution.setVariable("errorMessage", e.getMessage());
            throw new BpmnError("FREEZE_ERROR", "扣款凍結失敗: " + e.getMessage());
        }
    }

    private String generateFreezeId() {
        return "FRZ" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 8) return "****";
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }
}
