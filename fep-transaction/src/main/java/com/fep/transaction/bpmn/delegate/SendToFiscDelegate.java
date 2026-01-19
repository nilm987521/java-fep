package com.fep.transaction.bpmn.delegate;

import com.fep.transaction.bpmn.handler.FiscResponseHandler;
import com.fep.transaction.bpmn.service.FiscCommunicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BPMN Send Task Delegate: 發送電文至財金公司
 *
 * <p>此 Delegate 負責將組裝好的 0200 電文發送至 FISC。
 * 使用非同步模式，發送後立即返回，回應由 Message Catch Event 接收。
 *
 * <p>流程：
 * <pre>
 * SendToFiscDelegate.execute()
 *        │
 *        ├── 1. 取得組裝好的電文 (assembledMessage)
 *        │
 *        ├── 2. 產生/取得 STAN
 *        │
 *        ├── 3. 透過 FiscCommunicationService 發送 (非同步)
 *        │
 *        └── 4. 返回 (不等待回應)
 *               └── 回應將由 Event_ReceiveResponse (Message Catch Event) 接收
 * </pre>
 *
 * <p>注意事項：
 * <ul>
 *   <li>此 Delegate 不等待 FISC 回應，回應由後續的 Message Catch Event 處理</li>
 *   <li>如果 FISC 連線不可用，會拋出 BPMN Error</li>
 *   <li>STAN 會存入流程變數，供回應匹配使用</li>
 * </ul>
 */
@Slf4j
@Component("sendToFiscDelegate")
@RequiredArgsConstructor
public class SendToFiscDelegate implements JavaDelegate {

    private final FiscCommunicationService fiscCommunicationService;
    private final FiscResponseHandler fiscResponseHandler;

    /**
     * STAN 產生器 (簡易實作，正式環境應使用獨立的 StanGenerator)
     */
    private static final AtomicInteger stanCounter = new AtomicInteger(0);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processId = execution.getProcessInstanceId();
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        String targetAccount = (String) execution.getVariable("targetAccount");
        Long amount = (Long) execution.getVariable("amount");
        String sourceBankCode = (String) execution.getVariable("sourceBankCode");
        String targetBankCode = (String) execution.getVariable("targetBankCode");

        log.info("[{}] 準備發送至 FISC: {} -> {}, 金額={}",
                processId, sourceBankCode, targetBankCode, amount);

        try {
            // 1. 檢查 FISC 連線狀態
            if (!fiscCommunicationService.isConnected()) {
                log.error("[{}] FISC 連線不可用", processId);
                throw new BpmnError("FISC_UNAVAILABLE", "FISC 連線不可用");
            }

            // 2. 取得或產生 STAN
            String stan = getOrGenerateStan(execution);
            execution.setVariable("stan", stan);

            // 3. 取得組裝好的電文
            byte[] assembledMessage = (byte[]) execution.getVariable("assembledMessage");
            if (assembledMessage == null || assembledMessage.length == 0) {
                log.error("[{}] 找不到組裝好的電文", processId);
                throw new BpmnError("MESSAGE_NOT_FOUND", "找不到組裝好的電文");
            }

            // 4. 註冊 STAN 與流程的關聯 (用於回應匹配)
            fiscResponseHandler.registerStan(stan, processId);

            // 5. 發送電文 (非同步，不等待回應)
            log.info("[{}] 發送 0200 電文: STAN={}", processId, stan);

            fiscCommunicationService.sendAsync(assembledMessage, processId, stan)
                    .whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            log.error("[{}] FISC 發送失敗: STAN={}, error={}",
                                    processId, stan, throwable.getMessage());
                            // 注意：這裡不能拋出 BpmnError，因為是在非同步 callback 中
                            // 超時或錯誤將由 BPMN 的 Timer Boundary Event 處理
                        }
                    });

            // 6. 記錄發送時間
            execution.setVariable("fiscSendTime", LocalDateTime.now().toString());
            execution.setVariable("fiscRequestSent", true);

            log.info("[{}] 0200 電文已發送，等待 FISC 回應: STAN={}", processId, stan);

            // 此 Delegate 返回後，流程會繼續到 Message Catch Event (Event_ReceiveResponse)
            // 等待 FISC 回應或超時

        } catch (BpmnError e) {
            throw e; // 重新拋出 BPMN 錯誤
        } catch (Exception e) {
            log.error("[{}] 發送至 FISC 失敗: {}", processId, e.getMessage(), e);
            throw new BpmnError("FISC_ERROR", "發送失敗: " + e.getMessage());
        }
    }

    /**
     * 取得或產生 STAN
     *
     * <p>如果流程變數中已有 STAN（例如由 AssembleMessageDelegate 設定），則使用該值；
     * 否則產生新的 STAN。
     */
    private String getOrGenerateStan(DelegateExecution execution) {
        String existingStan = (String) execution.getVariable("stan");
        if (existingStan != null && !existingStan.isEmpty()) {
            return existingStan;
        }
        return generateStan();
    }

    /**
     * 產生 STAN (System Trace Audit Number)
     *
     * <p>STAN 是 6 位數字，範圍 000001-999999，循環使用
     */
    private String generateStan() {
        int stan = stanCounter.incrementAndGet();
        if (stan > 999999) {
            stanCounter.set(1);
            stan = 1;
        }
        return String.format("%06d", stan);
    }
}
