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

/**
 * BPMN Service Task Delegate: 組裝成功回應
 *
 * <p>對應 BPMN 中的 Task_BuildSuccessResponse
 * <p>當交易處理成功時，組裝成功回應 (RC=00)
 *
 * <p>流程變數輸入：
 * <ul>
 *   <li>rawMessage: 原始請求訊息 (byte[])</li>
 *   <li>mti: MTI 類型</li>
 *   <li>stan: 交易追蹤號</li>
 *   <li>processingCode: 處理碼</li>
 * </ul>
 *
 * <p>流程變數輸出：
 * <ul>
 *   <li>responseCode: "00"</li>
 *   <li>assembledResponse: 組裝好的回應訊息 (byte[])</li>
 *   <li>transactionStatus: "SUCCESS"</li>
 * </ul>
 */
@Slf4j
@Component("buildSuccessResponseDelegate")
@RequiredArgsConstructor
public class BuildSuccessResponseDelegate implements JavaDelegate {

    private final Iso8583MessageFactory messageFactory;

    private static final String SUCCESS_CODE = "00";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMdd");

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();

        if (log.isDebugEnabled()) {
            log.debug("[{}] 開始組裝成功回應", transactionId);
        }

        try {
            // 組裝 ISO 8583 回應
            byte[] assembledResponse = buildIso8583Response(execution);

            // 設定流程變數
            execution.setVariable("responseCode", SUCCESS_CODE);
            execution.setVariable("assembledResponse", assembledResponse);
            execution.setVariable("transactionStatus", "SUCCESS");
            execution.setVariable("completionTime", LocalDateTime.now().toString());

            if (log.isDebugEnabled()) {
                log.debug("[{}] 成功回應組裝完成: RC={}, responseSize={}",
                    transactionId, SUCCESS_CODE,
                    assembledResponse != null ? assembledResponse.length : 0);
            }

        } catch (Exception e) {
            log.error("[{}] 組裝成功回應時發生錯誤: {}", transactionId, e.getMessage(), e);

            // 即使發生錯誤，也嘗試建立一個基本回應
            byte[] errorResponse = buildBasicResponse(execution, SUCCESS_CODE);
            execution.setVariable("responseCode", SUCCESS_CODE);
            execution.setVariable("assembledResponse", errorResponse);
            execution.setVariable("transactionStatus", "SUCCESS");
        }
    }

    /**
     * 組裝 ISO 8583 回應訊息
     *
     * <p>注意：不再嘗試反序列化 rawMessage，因為原始電文可能是 GenericMessage 格式（ASCII），
     * 而 Iso8583MessageFactory 期望的是 FISC 格式（BCD）。直接使用流程變數中已解析的欄位。
     */
    private byte[] buildIso8583Response(DelegateExecution execution) {
        // 直接使用流程變數中的欄位來建立回應（避免格式不相容問題）
        return buildBasicResponse(execution, SUCCESS_CODE);
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

        // Field 38 - Auth Code (若有)
        String authCode = (String) execution.getVariable("authCode");
        if (authCode != null && !authCode.isBlank()) {
            response.setField(38, authCode);
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
