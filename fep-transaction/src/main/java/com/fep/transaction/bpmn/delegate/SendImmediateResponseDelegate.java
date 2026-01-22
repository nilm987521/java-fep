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

    private byte[] buildDefaultResponse(DelegateExecution execution) {
        try {
            String responseCode = (String) execution.getVariable("responseCode");
            if (responseCode == null || responseCode.isBlank()) {
                responseCode = "96"; // 系統異常
            }

            byte[] rawMessage = (byte[]) execution.getVariable("rawMessage");
            if (rawMessage != null && rawMessage.length > 0) {
                Iso8583Message request = deserializeMessage(rawMessage);
                if (request != null) {
                    return buildResponseFromRequest(request, responseCode, execution);
                }
            }

            return buildBasicResponse(execution, responseCode);
        } catch (Exception e) {
            log.error("建立預設回應失敗: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    private byte[] buildResponseFromRequest(Iso8583Message request, String responseCode,
                                             DelegateExecution execution) {
        Iso8583Message response = new Iso8583Message();

        String responseMti = calculateResponseMti(request.getMti());
        response.setMti(responseMti);

        copyField(request, response, 2);  // PAN
        copyField(request, response, 3);  // Processing Code
        copyField(request, response, 4);  // Amount
        copyField(request, response, 11); // STAN
        copyField(request, response, 37); // RRN
        copyField(request, response, 41); // Terminal ID
        copyField(request, response, 42); // Merchant ID
        copyField(request, response, 102); // Source Account
        copyField(request, response, 103); // Target Account

        response.setField(39, responseCode);

        LocalDateTime now = LocalDateTime.now();
        response.setField(12, now.format(TIME_FORMAT));
        response.setField(13, now.format(DATE_FORMAT));

        return serializeMessage(response);
    }

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

    private String calculateResponseMti(String requestMti) {
        try {
            int mti = Integer.parseInt(requestMti);
            return String.format("%04d", mti + 10);
        } catch (NumberFormatException e) {
            return "0210";
        }
    }

    private void copyField(Iso8583Message source, Iso8583Message target, int fieldNum) {
        Object value = source.getField(fieldNum);
        if (value != null) {
            target.setField(fieldNum, value);
        }
    }

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

    private byte[] serializeMessage(Iso8583Message message) {
        try {
            return messageFactory.assemble(message);
        } catch (Exception e) {
            log.error("序列化訊息失敗: {}", e.getMessage());
            return new byte[0];
        }
    }
}
