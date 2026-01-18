package com.fep.transaction.bpmn.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * BPMN Service Task Delegate: 檢查交易限額
 *
 * <p>對應 BPMN 中的 Task_CheckLimit
 */
@Slf4j
@Component("checkLimitDelegate")
@RequiredArgsConstructor
public class CheckLimitDelegate implements JavaDelegate {

    // 限額設定 (實際應從設定或資料庫讀取)
    private static final long SINGLE_TX_LIMIT = 2_000_000L;      // 單筆限額 200萬
    private static final long DAILY_LIMIT = 3_000_000L;          // 日累計限額 300萬
    private static final long NON_DESIGNATED_LIMIT = 50_000L;    // 非約定轉帳限額 5萬

    // private final LimitService limitService;
    // private final TransactionRepository transactionRepository;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        Long amount = (Long) execution.getVariable("amount");
        Boolean isDesignated = (Boolean) execution.getVariable("isDesignated");
        String transactionId = execution.getProcessInstanceId();

        log.info("[{}] 檢查限額: 帳號={}, 金額={}, 約定轉帳={}",
            transactionId, maskAccount(sourceAccount), amount, isDesignated);

        try {
            // 1. 單筆限額檢查
            checkSingleTransactionLimit(amount);

            // 2. 約定/非約定轉帳限額檢查
            if (Boolean.FALSE.equals(isDesignated)) {
                checkNonDesignatedLimit(amount);
            }

            // 3. 日累計限額檢查 (需查詢當日累計)
            // long dailyTotal = transactionRepository.getDailyTotal(sourceAccount);
            long dailyTotal = 0L; // 模擬
            checkDailyLimit(dailyTotal, amount);

            // 設定檢查結果
            execution.setVariable("limitCheck", "OK");
            execution.setVariable("limitMessage", "限額檢查通過");

            log.info("[{}] 限額檢查通過", transactionId);

        } catch (LimitExceededException e) {
            log.warn("[{}] 限額檢查失敗: {}", transactionId, e.getMessage());
            execution.setVariable("limitCheck", "FAIL");
            execution.setVariable("limitMessage", e.getMessage());
            execution.setVariable("responseCode", e.getResponseCode());
        }
    }

    private void checkSingleTransactionLimit(Long amount) {
        if (amount > SINGLE_TX_LIMIT) {
            throw new LimitExceededException("61",
                String.format("單筆金額 %d 超過限額 %d", amount, SINGLE_TX_LIMIT));
        }
    }

    private void checkNonDesignatedLimit(Long amount) {
        if (amount > NON_DESIGNATED_LIMIT) {
            throw new LimitExceededException("61",
                String.format("非約定轉帳金額 %d 超過限額 %d", amount, NON_DESIGNATED_LIMIT));
        }
    }

    private void checkDailyLimit(long dailyTotal, Long amount) {
        if (dailyTotal + amount > DAILY_LIMIT) {
            throw new LimitExceededException("65",
                String.format("日累計金額 %d 超過限額 %d", dailyTotal + amount, DAILY_LIMIT));
        }
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 8) return "****";
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }

    public static class LimitExceededException extends RuntimeException {
        private final String responseCode;

        public LimitExceededException(String responseCode, String message) {
            super(message);
            this.responseCode = responseCode;
        }

        public String getResponseCode() {
            return responseCode;
        }
    }
}
