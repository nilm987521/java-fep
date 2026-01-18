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
 * BPMN Service Task Delegate: 組裝 0200 電文
 *
 * <p>對應 BPMN 中的 Task_AssembleMessage
 * <p>組裝 ISO 8583 0200 金融交易請求電文
 */
@Slf4j
@Component("assembleMessageDelegate")
@RequiredArgsConstructor
public class AssembleMessageDelegate implements JavaDelegate {

    // 注入電文服務
    // private final MessageAssembler messageAssembler;
    // private final ChannelMessageService channelMessageService;

    private static final AtomicLong STAN_SEQUENCE = new AtomicLong(1);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMddHHmmss");

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String transactionId = execution.getProcessInstanceId();
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        String targetAccount = (String) execution.getVariable("targetAccount");
        Long amount = (Long) execution.getVariable("amount");
        String channelId = (String) execution.getVariable("channelId");

        log.info("[{}] 開始組裝 0200 電文", transactionId);

        try {
            // 1. 產生交易序號
            String stan = generateStan();
            String rrn = generateRrn();
            String transmissionDateTime = LocalDateTime.now().format(DATE_FORMAT);

            // 2. 組裝電文欄位
            // GenericMessage message = new GenericMessage(schema);
            // message.setMti("0200");
            // message.setField(2, sourceAccount);        // Primary Account Number
            // message.setField(3, "400000");             // Processing Code (Transfer)
            // message.setField(4, amount.toString());    // Transaction Amount
            // message.setField(7, transmissionDateTime); // Transmission Date/Time
            // message.setField(11, stan);                // STAN
            // message.setField(37, rrn);                 // RRN
            // message.setField(102, sourceAccount);      // Account ID 1
            // message.setField(103, targetAccount);      // Account ID 2

            // 模擬組裝完成
            String messageHex = buildMockMessageHex(stan, rrn, amount);

            // 3. 儲存到流程變數
            execution.setVariable("stan", stan);
            execution.setVariable("rrn", rrn);
            execution.setVariable("transmissionDateTime", transmissionDateTime);
            execution.setVariable("requestMessage", messageHex);
            execution.setVariable("mti", "0200");

            log.info("[{}] 電文組裝完成: STAN={}, RRN={}", transactionId, stan, rrn);

        } catch (Exception e) {
            log.error("[{}] 電文組裝失敗: {}", transactionId, e.getMessage());
            throw e;
        }
    }

    private String generateStan() {
        long seq = STAN_SEQUENCE.getAndIncrement() % 1_000_000;
        return String.format("%06d", seq);
    }

    private String generateRrn() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
    }

    private String buildMockMessageHex(String stan, String rrn, Long amount) {
        // 模擬的 ISO 8583 電文 HEX
        return String.format("0200F23844810AE08000%s%s%012d", stan, rrn, amount);
    }
}
