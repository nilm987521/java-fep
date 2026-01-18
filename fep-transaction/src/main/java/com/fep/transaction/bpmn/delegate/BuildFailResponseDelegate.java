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
 * BPMN Service Task Delegate: 組裝失敗回應
 *
 * <p>對應 BPMN 中的 Task_BuildFailResponse
 * <p>當交易驗證失敗、限額超過或 FISC 回應失敗時，組裝錯誤回應
 */
@Slf4j
@Component("buildFailResponseDelegate")
@RequiredArgsConstructor
public class BuildFailResponseDelegate implements JavaDelegate {

    // 回應碼對照表
    private static final Map<String, String> RESPONSE_CODE_MESSAGES = new HashMap<>();

    static {
        RESPONSE_CODE_MESSAGES.put("00", "交易成功");
        RESPONSE_CODE_MESSAGES.put("05", "不予承兌");
        RESPONSE_CODE_MESSAGES.put("12", "無效交易");
        RESPONSE_CODE_MESSAGES.put("13", "無效金額");
        RESPONSE_CODE_MESSAGES.put("14", "無效卡號/帳號");
        RESPONSE_CODE_MESSAGES.put("51", "餘額不足");
        RESPONSE_CODE_MESSAGES.put("54", "卡片過期");
        RESPONSE_CODE_MESSAGES.put("55", "密碼錯誤");
        RESPONSE_CODE_MESSAGES.put("61", "超過限額");
        RESPONSE_CODE_MESSAGES.put("68", "回應逾時");
        RESPONSE_CODE_MESSAGES.put("91", "發卡機構無法連線");
        RESPONSE_CODE_MESSAGES.put("96", "系統異常");
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();

        log.info("[{}] 開始組裝失敗回應", transactionId);

        try {
            // 1. 收集錯誤資訊
            String responseCode = (String) execution.getVariable("responseCode");
            String validationResult = (String) execution.getVariable("validationResult");
            String limitCheck = (String) execution.getVariable("limitCheck");
            String errorMessage = (String) execution.getVariable("errorMessage");

            // 2. 決定最終回應碼
            if (responseCode == null || responseCode.isBlank()) {
                if ("FAIL".equals(validationResult)) {
                    responseCode = (String) execution.getVariable("validationResponseCode");
                    if (responseCode == null) responseCode = "14"; // 預設驗證失敗碼
                    errorMessage = (String) execution.getVariable("validationMessage");
                } else if (!"OK".equals(limitCheck)) {
                    responseCode = "61"; // 超過限額
                    errorMessage = "交易金額超過限額";
                } else {
                    responseCode = "96"; // 系統異常
                    errorMessage = errorMessage != null ? errorMessage : "系統處理異常";
                }
            }

            // 3. 取得回應訊息
            String responseMessage = RESPONSE_CODE_MESSAGES.getOrDefault(responseCode, "交易失敗");
            if (errorMessage != null && !errorMessage.isBlank()) {
                responseMessage = errorMessage;
            }

            // 4. 組裝回應
            Map<String, Object> failResponse = new HashMap<>();
            failResponse.put("responseCode", responseCode);
            failResponse.put("responseMessage", responseMessage);
            failResponse.put("transactionId", transactionId);
            failResponse.put("rrn", execution.getVariable("rrn"));
            failResponse.put("stan", execution.getVariable("stan"));
            failResponse.put("responseTime", LocalDateTime.now().toString());

            // 5. 設定流程變數
            execution.setVariable("responseCode", responseCode);
            execution.setVariable("responseMessage", responseMessage);
            execution.setVariable("failResponse", failResponse.toString());
            execution.setVariable("transactionStatus", "FAILED");
            execution.setVariable("completionTime", LocalDateTime.now().toString());

            log.info("[{}] 失敗回應組裝完成: RC={}, Msg={}",
                transactionId, responseCode, responseMessage);

        } catch (Exception e) {
            log.error("[{}] 組裝失敗回應時發生錯誤: {}", transactionId, e.getMessage());
            execution.setVariable("responseCode", "96");
            execution.setVariable("responseMessage", "系統處理異常");
            execution.setVariable("transactionStatus", "FAILED");
        }
    }
}
