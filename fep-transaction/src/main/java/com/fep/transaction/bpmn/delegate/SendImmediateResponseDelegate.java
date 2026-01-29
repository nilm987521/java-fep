package com.fep.transaction.bpmn.delegate;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.transaction.bpmn.listener.TransactionEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * BPMN Service Task Delegate: 立即回應客戶端 (驗證失敗時)
 *
 * <p>用於 Request BPMN 流程中，當驗證或限額檢查失敗時，立即回應客戶端。
 * 此 Delegate 不等待 FISC 回應，直接發送失敗回應。
 *
 * <p>與 SendResponseToClientDelegate 的差異：
 * <ul>
 *   <li>本 Delegate 用於 Request 流程的失敗路徑</li>
 *   <li>SendResponseToClientDelegate 用於 Response/Timeout 流程</li>
 *   <li>兩者在技術上相似，但語義上區分不同的使用場景</li>
 * </ul>
 *
 * <p>流程變數：
 * <ul>
 *   <li>responseMessage: 組裝好的回應訊息 (byte[])</li>
 *   <li>responseCode: 回應碼</li>
 *   <li>stan: 交易追蹤號</li>
 *   <li>failReason: 失敗原因</li>
 * </ul>
 */
@Slf4j
@Component("sendImmediateResponseDelegate")
@RequiredArgsConstructor
public class SendImmediateResponseDelegate implements JavaDelegate {

    private final TransactionEventListener eventListener;
    private final Iso8583MessageFactory messageFactory;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMdd");

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processId = execution.getProcessInstanceId();
        String stan = (String) execution.getVariable("stan");
        String responseCode = (String) execution.getVariable("responseCode");
        String failReason = (String) execution.getVariable("failReason");

        log.info("[{}] 立即回應客戶端 (驗證失敗): STAN={}, RC={}, reason={}",
                processId, stan, responseCode, failReason);

        try {
            // 取得組裝好的回應訊息
            byte[] responseMessage = getResponseMessage(execution);

            if (responseMessage == null || responseMessage.length == 0) {
                log.warn("[{}] 回應訊息為空，嘗試建立預設回應", processId);
                responseMessage = buildDefaultResponse(execution);
            }

            // 發送回應給客戶端
            boolean sent;
            if (stan != null && !stan.isEmpty()) {
                sent = eventListener.sendResponseToClientByStan(stan, responseMessage);
            } else {
                sent = eventListener.sendResponseToClient(processId, responseMessage);
            }

            if (sent) {
                log.info("[{}] 立即回應已發送: STAN={}, RC={}", processId, stan, responseCode);
                execution.setVariable("immediateResponseSent", true);
            } else {
                log.warn("[{}] 發送立即回應失敗，找不到對應的 callback: STAN={}", processId, stan);
                execution.setVariable("immediateResponseSent", false);
            }

        } catch (Exception e) {
            log.error("[{}] 發送立即回應失敗: STAN={}, error={}",
                    processId, stan, e.getMessage(), e);
            execution.setVariable("immediateResponseSent", false);
            execution.setVariable("immediateResponseError", e.getMessage());
            // 不拋出例外，避免影響流程結束
        }
    }

    private byte[] getResponseMessage(DelegateExecution execution) {
        Object responseMessage = execution.getVariable("responseMessage");
        if (responseMessage instanceof byte[] bytes) {
            return bytes;
        }

        Object assembledResponse = execution.getVariable("assembledResponse");
        if (assembledResponse instanceof byte[] bytes) {
            return bytes;
        }

        return null;
    }

    /**
     * 建立預設回應
     *
     * <p>注意：不再嘗試反序列化 rawMessage，因為原始電文可能是 GenericMessage 格式（ASCII），
     * 而 Iso8583MessageFactory 期望的是 FISC 格式（BCD）。直接使用流程變數中已解析的欄位。
     */
    private byte[] buildDefaultResponse(DelegateExecution execution) {
        try {
            String responseCode = (String) execution.getVariable("responseCode");
            if (responseCode == null || responseCode.isBlank()) {
                responseCode = "96"; // 系統異常
            }

            // 直接使用流程變數中的欄位來建立回應
            return buildBasicResponse(execution, responseCode);
        } catch (Exception e) {
            log.error("建立預設回應失敗: {}", e.getMessage(), e);
            return new byte[0];
        }
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

    private String calculateResponseMti(String requestMti) {
        try {
            int mti = Integer.parseInt(requestMti);
            return String.format("%04d", mti + 10);
        } catch (NumberFormatException e) {
            return "0210";
        }
    }

    private byte[] serializeMessage(Iso8583Message message) {
        try {
            return messageFactory.assemble(message);
        } catch (Exception e) {
            log.error("序列化訊息失敗: {}", e.getMessage());
            return new byte[0];
        }
    }
}
