package com.fep.transaction.bpmn.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BPMN Service Task Delegate: 發送沖正電文
 *
 * <p>對應 BPMN 中的 Task_SendReversal
 * <p>當原交易超時或失敗時，發送 0400 沖正請求
 */
@Slf4j
@Component("sendReversalDelegate")
@RequiredArgsConstructor
public class SendReversalDelegate implements JavaDelegate {

    // 注入通訊服務
    // private final FiscClient fiscClient;
    // private final MessageAssembler messageAssembler;

    private static final AtomicLong REVERSAL_STAN_SEQUENCE = new AtomicLong(1);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();
        String originalStan = (String) execution.getVariable("stan");
        String originalRrn = (String) execution.getVariable("rrn");
        Long amount = (Long) execution.getVariable("amount");

        log.info("[{}] 開始發送沖正電文: 原始STAN={}, 原始RRN={}",
            transactionId, originalStan, originalRrn);

        try {
            // 1. 產生沖正交易序號
            String reversalStan = generateReversalStan();
            String transmissionDateTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("MMddHHmmss"));

            // 2. 組裝 0400 沖正電文
            // GenericMessage reversalMessage = new GenericMessage(schema);
            // reversalMessage.setMti("0400");
            // reversalMessage.setField(2, execution.getVariable("sourceAccount"));
            // reversalMessage.setField(3, "400000");  // Processing Code
            // reversalMessage.setField(4, amount.toString());
            // reversalMessage.setField(7, transmissionDateTime);
            // reversalMessage.setField(11, reversalStan);
            // reversalMessage.setField(37, originalRrn);  // 原始 RRN
            // reversalMessage.setField(90, buildOriginalDataElement(originalStan));

            // 模擬組裝沖正電文
            String reversalMessageHex = buildMockReversalMessage(reversalStan, originalRrn, amount);

            // 3. 發送沖正電文
            // byte[] response = fiscClient.send(reversalMessage.toBytes());

            // 模擬發送成功
            log.info("[{}] 模擬發送沖正電文: STAN={}", transactionId, reversalStan);

            // 4. 設定流程變數（等待回應）
            execution.setVariable("reversalStan", reversalStan);
            execution.setVariable("reversalMti", "0400");
            execution.setVariable("reversalMessage", reversalMessageHex);
            execution.setVariable("reversalSendTime", LocalDateTime.now().toString());
            execution.setVariable("reversalStatus", "SENT");

            log.info("[{}] 沖正電文已發送: STAN={}", transactionId, reversalStan);

        } catch (Exception e) {
            log.error("[{}] 發送沖正電文失敗: {}", transactionId, e.getMessage());
            execution.setVariable("reversalStatus", "SEND_FAILED");
            execution.setVariable("reversalError", e.getMessage());
            // 沖正失敗需要繼續流程，標記待調帳
        }
    }

    private String generateReversalStan() {
        long seq = REVERSAL_STAN_SEQUENCE.getAndIncrement() % 1_000_000;
        return String.format("%06d", seq);
    }

    private String buildMockReversalMessage(String stan, String originalRrn, Long amount) {
        return String.format("0400F23844810AE08000%s%s%012d", stan, originalRrn, amount);
    }
}
