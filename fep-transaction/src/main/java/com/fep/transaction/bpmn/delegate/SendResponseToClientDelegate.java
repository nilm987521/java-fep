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
 * BPMN Service Task Delegate: 發送回應給客戶端
 *
 * <p>此 Delegate 在 BPMN 流程結束時執行，負責將交易結果回應發送給原始的 ATM/POS 客戶端。
 *
 * <p>職責：
 * <ul>
 *   <li>從流程變數中取得回應訊息 (responseMessage)</li>
 *   <li>透過 {@link TransactionEventListener} 發送回應給客戶端</li>
 *   <li>支援成功和失敗兩種回應</li>
 * </ul>
 *
 * <p>流程變數：
 * <ul>
 *   <li>responseMessage: 組裝好的回應訊息 (byte[])</li>
 *   <li>responseCode: 回應碼</li>
 *   <li>stan: 交易追蹤號</li>
 * </ul>
 *
 * <p>注意：此 Delegate 需要在 BPMN 流程的最後一個 Service Task 或 End Event 之前執行。
 * 在 interbank-transfer.bpmn 中，可以在以下位置使用：
 * <ul>
 *   <li>Task_LogSuccess 之後（成功路徑）</li>
 *   <li>Task_BuildFailResponse 之後（失敗路徑）</li>
 * </ul>
 */
@Slf4j
@Component("sendResponseToClientDelegate")
@RequiredArgsConstructor
public class SendResponseToClientDelegate implements JavaDelegate {

    private final TransactionEventListener eventListener;
    private final Iso8583MessageFactory messageFactory;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processId = execution.getProcessInstanceId();
        String stan = (String) execution.getVariable("stan");
        String responseCode = (String) execution.getVariable("responseCode");

        if (log.isDebugEnabled()) {
            log.debug("[{}] 準備發送回應給客戶端: STAN={}, RC={}", processId, stan, responseCode);
        }

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
                if (log.isDebugEnabled()) {
                    log.debug("[{}] 回應已發送給客戶端: STAN={}, RC={}", processId, stan, responseCode);
                }
                execution.setVariable("responseSent", true);
            } else {
                log.warn("[{}] 發送回應失敗，找不到對應的 callback: STAN={}", processId, stan);
                execution.setVariable("responseSent", false);
            }

        } catch (Exception e) {
            log.error("[{}] 發送回應給客戶端失敗: STAN={}, error={}",
                    processId, stan, e.getMessage(), e);
            execution.setVariable("responseSent", false);
            execution.setVariable("responseSendError", e.getMessage());
            // 不拋出例外，避免影響流程結束
        }
    }

    /**
     * 取得回應訊息
     *
     * <p>優先從 responseMessage 變數取得，如果不存在則從 assembledResponse 取得
     */
    private byte[] getResponseMessage(DelegateExecution execution) {
        // 優先使用 responseMessage
        Object responseMessage = execution.getVariable("responseMessage");
        if (responseMessage instanceof byte[] bytes) {
            return bytes;
        }

        // 其次使用 assembledResponse
        Object assembledResponse = execution.getVariable("assembledResponse");
        if (assembledResponse instanceof byte[] bytes) {
            return bytes;
        }

        return null;
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMdd");

    /**
     * 建立預設回應
     *
     * <p>當沒有組裝好的回應訊息時，基於原始請求建立 Iso8583Message 回應
     */
    private byte[] buildDefaultResponse(DelegateExecution execution) {
        try {
            String responseCode = (String) execution.getVariable("responseCode");
            if (responseCode == null || responseCode.isBlank()) {
                // 根據交易狀態決定預設回應碼
                String transactionStatus = (String) execution.getVariable("transactionStatus");
                if ("SUCCESS".equals(transactionStatus)) {
                    responseCode = "00"; // 成功
                } else {
                    responseCode = "96"; // 系統異常
                }
            }

            // 嘗試從原始請求建立回應
            byte[] rawMessage = (byte[]) execution.getVariable("rawMessage");
            if (rawMessage != null && rawMessage.length > 0) {
                Iso8583Message request = deserializeMessage(rawMessage);
                if (request != null) {
                    return buildResponseFromRequest(request, responseCode, execution);
                }
            }

            // 無原始請求，建立基本回應
            return buildBasicResponse(execution, responseCode);
        } catch (Exception e) {
            log.error("建立預設回應失敗: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * 基於原始請求建立回應
     */
    private byte[] buildResponseFromRequest(Iso8583Message request, String responseCode,
                                             DelegateExecution execution) {
        Iso8583Message response = new Iso8583Message();

        // 計算回應 MTI
        String responseMti = calculateResponseMti(request.getMti());
        response.setMti(responseMti);

        // 複製關鍵欄位
        copyField(request, response, 2);  // PAN
        copyField(request, response, 3);  // Processing Code
        copyField(request, response, 4);  // Amount
        copyField(request, response, 11); // STAN
        copyField(request, response, 37); // RRN
        copyField(request, response, 41); // Terminal ID
        copyField(request, response, 42); // Merchant ID
        copyField(request, response, 102); // Source Account
        copyField(request, response, 103); // Target Account

        // 設定回應碼
        response.setField(39, responseCode);

        // 設定授權碼 (若有)
        String authCode = (String) execution.getVariable("authCode");
        if (authCode != null) {
            response.setField(38, authCode);
        }

        // 設定時間
        LocalDateTime now = LocalDateTime.now();
        response.setField(12, now.format(TIME_FORMAT));
        response.setField(13, now.format(DATE_FORMAT));

        return serializeMessage(response);
    }

    /**
     * 建立基本回應 (無原始請求時使用)
     */
    private byte[] buildBasicResponse(DelegateExecution execution, String responseCode) {
        Iso8583Message response = new Iso8583Message();

        String mti = (String) execution.getVariable("mti");
        response.setMti(calculateResponseMti(mti != null ? mti : "0200"));

        String stan = (String) execution.getVariable("stan");
        if (stan != null) {
            response.setField(11, stan);
        }

        String processingCode = (String) execution.getVariable("processingCode");
        if (processingCode != null) {
            response.setField(3, processingCode);
        }

        response.setField(39, responseCode);

        LocalDateTime now = LocalDateTime.now();
        response.setField(12, now.format(TIME_FORMAT));
        response.setField(13, now.format(DATE_FORMAT));

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
     * 複製欄位
     */
    private void copyField(Iso8583Message source, Iso8583Message target, int fieldNum) {
        Object value = source.getField(fieldNum);
        if (value != null) {
            target.setField(fieldNum, value);
        }
    }

    /**
     * 反序列化訊息
     *
     * <p>使用 Iso8583MessageFactory 解析 ISO 8583 格式電文
     */
    private Iso8583Message deserializeMessage(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return messageFactory.parse(data);
        } catch (Exception e) {
            log.error("反序列化訊息失敗: {}", e.getMessage());
            return null;
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
