package com.fep.transaction.bpmn.delegate;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private final Iso8583MessageFactory messageFactory;

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

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMdd");

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

            // 3. 取得回應訊息描述
            String responseDescription = RESPONSE_CODE_MESSAGES.getOrDefault(responseCode, "交易失敗");
            if (errorMessage != null && !errorMessage.isBlank()) {
                responseDescription = errorMessage;
            }

            // 4. 組裝 Iso8583Message 回應
            byte[] assembledResponse = buildIso8583Response(execution, responseCode);

            // 5. 設定流程變數
            execution.setVariable("responseCode", responseCode);
            execution.setVariable("responseMessage", responseDescription);
            execution.setVariable("assembledResponse", assembledResponse);
            execution.setVariable("transactionStatus", "FAILED");
            execution.setVariable("completionTime", LocalDateTime.now().toString());

            log.info("[{}] 失敗回應組裝完成: RC={}, Msg={}, responseSize={}",
                transactionId, responseCode, responseDescription,
                assembledResponse != null ? assembledResponse.length : 0);

        } catch (Exception e) {
            log.error("[{}] 組裝失敗回應時發生錯誤: {}", transactionId, e.getMessage(), e);
            execution.setVariable("responseCode", "96");
            execution.setVariable("responseMessage", "系統處理異常");
            execution.setVariable("transactionStatus", "FAILED");

            // 即使發生錯誤，也嘗試建立一個基本回應
            byte[] errorResponse = buildErrorResponse(execution, "96");
            execution.setVariable("assembledResponse", errorResponse);
        }
    }

    /**
     * 組裝 ISO 8583 回應訊息
     *
     * <p>注意：不再嘗試反序列化 rawMessage，因為原始電文可能是 GenericMessage 格式（ASCII），
     * 而 Iso8583MessageFactory 期望的是 FISC 格式（BCD）。直接使用流程變數中已解析的欄位。
     */
    private byte[] buildIso8583Response(DelegateExecution execution, String responseCode) {
        // 直接使用流程變數中的欄位來建立回應（避免格式不相容問題）
        return buildBasicResponse(execution, responseCode);
    }


    /**
     * 建立回應訊息（使用流程變數中的欄位）
     */
    private byte[] buildBasicResponse(DelegateExecution execution, String responseCode) {
        Iso8583Message response = new Iso8583Message();

        // MTI
        String mti = (String) execution.getVariable("mti");
        response.setMti(calculateResponseMti(mti != null ? mti : "0200"));

        // Field 2 - PAN
        String pan = (String) execution.getVariable("pan");
        if (pan != null && !pan.isEmpty()) {
            response.setField(2, pan);
        }

        // Field 3 - Processing Code
        String processingCode = (String) execution.getVariable("processingCode");
        if (processingCode != null) {
            response.setField(3, processingCode);
        }

        // Field 4 - Amount
        Object amount = execution.getVariable("amount");
        if (amount != null) {
            String amountStr = amount.toString();
            // 確保金額格式為 12 位數字
            if (amountStr.length() < 12) {
                amountStr = "0".repeat(12 - amountStr.length()) + amountStr;
            }
            response.setField(4, amountStr);
        }

        // Field 11 - STAN
        String stan = (String) execution.getVariable("stan");
        if (stan != null) {
            response.setField(11, stan);
        }

        // Field 12, 13 - Time, Date
        LocalDateTime now = LocalDateTime.now();
        response.setField(12, now.format(TIME_FORMAT));
        response.setField(13, now.format(DATE_FORMAT));

        // Field 37 - RRN
        String rrn = (String) execution.getVariable("rrn");
        if (rrn != null && !rrn.isEmpty()) {
            response.setField(37, rrn);
        }

        // Field 39 - Response Code
        response.setField(39, responseCode);

        // Field 41 - Terminal ID
        String terminalId = (String) execution.getVariable("terminalId");
        if (terminalId != null && !terminalId.isEmpty()) {
            response.setField(41, terminalId);
        }

        // Field 42 - Merchant ID
        String merchantId = (String) execution.getVariable("merchantId");
        if (merchantId != null && !merchantId.isEmpty()) {
            response.setField(42, merchantId);
        }

        // Field 102 - Source Account
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        if (sourceAccount != null && !sourceAccount.isEmpty()) {
            response.setField(102, sourceAccount);
        }

        // Field 103 - Target Account
        String targetAccount = (String) execution.getVariable("targetAccount");
        if (targetAccount != null && !targetAccount.isEmpty()) {
            response.setField(103, targetAccount);
        }

        return serializeMessage(response);
    }

    /**
     * 建立錯誤回應
     */
    private byte[] buildErrorResponse(DelegateExecution execution, String responseCode) {
        return buildBasicResponse(execution, responseCode);
    }

    /**
     * 計算回應 MTI
     */
    private String calculateResponseMti(String requestMti) {
        try {
            int mti = Integer.parseInt(requestMti);
            return String.format("%04d", mti + 10);
        } catch (NumberFormatException e) {
            return "0210";
        }
    }

    /**
     * 序列化訊息
     *
     * <p>使用 Iso8583MessageFactory 組裝符合 ISO 8583 標準格式的電文，
     * 包含欄位長度補齊、LLVAR/LLLVAR 長度前綴、BCD/ASCII 編碼等處理
     */
    private byte[] serializeMessage(Iso8583Message message) {
        try {
            return messageFactory.assemble(message);
        } catch (Exception e) {
            log.error("序列化訊息失敗: {}", e.getMessage());
            return new byte[0];
        }
    }
}
