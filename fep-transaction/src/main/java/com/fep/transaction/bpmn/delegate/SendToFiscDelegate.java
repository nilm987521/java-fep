package com.fep.transaction.bpmn.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * BPMN Service Task Delegate: 發送電文至財金公司
 *
 * <p>整合 FiscDualChannelClient 進行實際的電文發送
 */
@Slf4j
@Component("sendToFISCDelegate")
@RequiredArgsConstructor
public class SendToFiscDelegate implements JavaDelegate {

    // 實際整合時注入
    // private final FiscDualChannelClient fiscClient;
    // private final Iso8583MessageFactory messageFactory;
    // private final StanGenerator stanGenerator;

    private static final long FISC_TIMEOUT_SECONDS = 30L;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        String targetAccount = (String) execution.getVariable("targetAccount");
        Long amount = (Long) execution.getVariable("amount");
        String sourceBankCode = (String) execution.getVariable("sourceBankCode");
        String targetBankCode = (String) execution.getVariable("targetBankCode");

        log.info("[{}] 準備發送至 FISC: {} -> {}, 金額={}",
            transactionId, sourceBankCode, targetBankCode, amount);

        try {
            // 1. 產生 STAN
            String stan = generateStan();
            execution.setVariable("stan", stan);

            // 2. 組裝 0200 電文
            // Iso8583Message request = buildTransferRequest(
            //     stan, sourceAccount, targetAccount, amount, sourceBankCode, targetBankCode);

            // 3. 發送並等待回應
            log.info("[{}] 發送 0200 電文: STAN={}", transactionId, stan);

            // CompletableFuture<Iso8583Message> future = fiscClient.sendAsync(request);
            // Iso8583Message response = future.get(FISC_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // 模擬回應 (實際整合時移除)
            String responseCode = simulateFiscResponse();

            // 4. 解析回應
            // String responseCode = response.getFieldAsString(39);
            execution.setVariable("responseCode", responseCode);
            execution.setVariable("fiscResponseTime", LocalDateTime.now().toString());

            log.info("[{}] 收到 FISC 回應: RC={}", transactionId, responseCode);

        } catch (Exception e) {
            if (e instanceof TimeoutException) {
                log.error("[{}] FISC 回應超時", transactionId);
                // 拋出 BPMN Error 觸發超時處理流程
                throw new BpmnError("FISC_TIMEOUT", "財金回應超時");
            }
            log.error("[{}] 發送至 FISC 失敗: {}", transactionId, e.getMessage(), e);
            throw new BpmnError("FISC_ERROR", "發送失敗: " + e.getMessage());
        }
    }

    /**
     * 產生 STAN (System Trace Audit Number)
     */
    private String generateStan() {
        // 實際應使用 StanGenerator
        // return stanGenerator.next();
        return String.format("%06d", (int) (Math.random() * 999999));
    }

    /**
     * 組裝跨行轉帳請求電文 (0200)
     */
    /*
    private Iso8583Message buildTransferRequest(String stan, String sourceAccount,
            String targetAccount, Long amount, String sourceBankCode, String targetBankCode) {

        Iso8583Message message = messageFactory.createRequest("0200");

        // 主要欄位
        message.setField(2, sourceAccount);                           // 主帳號
        message.setField(3, "400000");                                // 處理碼: 轉帳
        message.setField(4, String.format("%012d", amount));          // 交易金額
        message.setField(11, stan);                                   // STAN
        message.setField(12, LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("HHmmss")));                  // 本地時間
        message.setField(13, LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("MMdd")));                    // 本地日期
        message.setField(32, sourceBankCode);                         // 收單行代碼
        message.setField(41, "ATM00001");                             // 終端機代號
        message.setField(42, sourceBankCode + "000000001");           // 商店代號
        message.setField(100, targetBankCode);                        // 目標銀行代碼
        message.setField(103, targetAccount);                         // 目標帳號

        return message;
    }
    */

    /**
     * 模擬 FISC 回應 (測試用)
     */
    private String simulateFiscResponse() throws InterruptedException {
        // 模擬網路延遲
        Thread.sleep(100);

        // 90% 成功率
        double random = Math.random();
        if (random < 0.9) {
            return "00"; // 成功
        } else if (random < 0.95) {
            return "51"; // 餘額不足
        } else {
            return "96"; // 系統異常
        }
    }
}
