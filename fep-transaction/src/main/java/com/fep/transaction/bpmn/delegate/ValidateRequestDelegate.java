package com.fep.transaction.bpmn.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * BPMN Service Task Delegate: 驗證轉帳請求
 *
 * <p>對應 BPMN 中的 Task_ValidateRequest
 * <pre>
 * {@code <bpmn:serviceTask id="Task_ValidateRequest"
 *                         camunda:delegateExpression="${validateRequestDelegate}">}
 * </pre>
 */
@Slf4j
@Component("validateRequestDelegate")
@RequiredArgsConstructor
public class ValidateRequestDelegate implements JavaDelegate {

    // 注入 FEP 內部服務
    // private final ValidationService validationService;
    // private final BlacklistService blacklistService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        // 從流程變數取得輸入資料
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        String targetAccount = (String) execution.getVariable("targetAccount");
        Long amount = (Long) execution.getVariable("amount");
        String transactionId = execution.getProcessInstanceId();

        log.info("[{}] 開始驗證請求: 來源帳號={}, 目標帳號={}, 金額={}",
            transactionId, maskAccount(sourceAccount), maskAccount(targetAccount), amount);

        try {
            // 1. 驗證帳號格式
            validateAccountFormat(sourceAccount, "來源帳號");
            validateAccountFormat(targetAccount, "目標帳號");

            // 2. 驗證金額
            validateAmount(amount);

            // 3. 檢查黑名單
            // blacklistService.checkAccount(sourceAccount);
            // blacklistService.checkAccount(targetAccount);

            // 4. 設定驗證結果
            execution.setVariable("validationResult", "OK");
            execution.setVariable("validationMessage", "驗證通過");

            log.info("[{}] 請求驗證通過", transactionId);

        } catch (ValidationException e) {
            log.warn("[{}] 請求驗證失敗: {}", transactionId, e.getMessage());
            execution.setVariable("validationResult", "FAIL");
            execution.setVariable("validationMessage", e.getMessage());
            execution.setVariable("responseCode", e.getResponseCode());
        }
    }

    private void validateAccountFormat(String account, String fieldName) {
        if (account == null || account.isBlank()) {
            throw new ValidationException("14", fieldName + "不可為空");
        }
        if (!account.matches("\\d{12,16}")) {
            throw new ValidationException("14", fieldName + "格式錯誤");
        }
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new ValidationException("13", "金額必須大於零");
        }
        if (amount > 50_000_000L) { // 5000萬
            throw new ValidationException("61", "單筆金額超過上限");
        }
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 8) return "****";
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }

    /**
     * 驗證例外
     */
    public static class ValidationException extends RuntimeException {
        private final String responseCode;

        public ValidationException(String responseCode, String message) {
            super(message);
            this.responseCode = responseCode;
        }

        public String getResponseCode() {
            return responseCode;
        }
    }
}
