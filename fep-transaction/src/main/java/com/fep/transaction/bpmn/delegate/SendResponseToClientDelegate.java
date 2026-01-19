package com.fep.transaction.bpmn.delegate;

import com.fep.transaction.bpmn.listener.TransactionEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

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

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processId = execution.getProcessInstanceId();
        String stan = (String) execution.getVariable("stan");
        String responseCode = (String) execution.getVariable("responseCode");

        log.info("[{}] 準備發送回應給客戶端: STAN={}, RC={}", processId, stan, responseCode);

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
                log.info("[{}] 回應已發送給客戶端: STAN={}, RC={}", processId, stan, responseCode);
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

    /**
     * 建立預設回應
     *
     * <p>當沒有組裝好的回應訊息時，建立一個簡單的回應資料
     */
    private byte[] buildDefaultResponse(DelegateExecution execution) {
        try {
            // 建立一個簡單的回應物件
            ResponseData response = new ResponseData();
            response.processId = execution.getProcessInstanceId();
            response.stan = (String) execution.getVariable("stan");
            response.responseCode = (String) execution.getVariable("responseCode");
            response.authCode = (String) execution.getVariable("authCode");
            response.message = (String) execution.getVariable("validationMessage");

            // 序列化
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(response);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            log.error("建立預設回應失敗: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * 簡單的回應資料類別
     */
    @lombok.Data
    private static class ResponseData implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        String processId;
        String stan;
        String responseCode;
        String authCode;
        String message;
    }
}
