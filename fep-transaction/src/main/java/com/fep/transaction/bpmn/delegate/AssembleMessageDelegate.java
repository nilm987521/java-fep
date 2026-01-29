package com.fep.transaction.bpmn.delegate;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.parser.FiscMessageAssembler;
import com.fep.transaction.bpmn.listener.TransactionEventListener;
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

    private final TransactionEventListener transactionEventListener;

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
            // 1. 產生交易序號 (FISC STAN，與 ATM 原始 STAN 不同)
            String fiscStan = generateStan();
            String rrn = generateRrn();
            String transmissionDateTime = LocalDateTime.now().format(DATE_FORMAT);

            // 2. 註冊 FISC STAN → CallbackKey 映射
            // CallbackKey 包含 channelId:clientId:atmStan，支援多台 ATM 使用相同 STAN
            String callbackKey = (String) execution.getVariable("callbackKey");
            String atmStan = (String) execution.getVariable("atmStan");
            if (callbackKey != null) {
                transactionEventListener.registerFiscStanMapping(fiscStan, callbackKey);
                log.debug("[{}] 已註冊 FISC STAN 映射: fiscStan={} -> callbackKey={}", transactionId, fiscStan, callbackKey);
            } else if (atmStan != null) {
                // 向下相容：如果沒有 callbackKey，使用 atmStan
                log.warn("[{}] 未設置 callbackKey，使用 atmStan（不支援多台 ATM 同 STAN）: atmStan={}", transactionId, atmStan);
            }

            // 3. 組裝電文欄位
            Iso8583Message message = new Iso8583Message();
            message.setMti("0200");
            message.setField(2, sourceAccount);        // Primary Account Number
            message.setField(3, "400000");             // Processing Code (Transfer)
            message.setField(4, amount.toString());    // Transaction Amount
            message.setField(7, transmissionDateTime); // Transmission Date/Time
            message.setField(11, fiscStan);            // STAN (FISC 專用)
            message.setField(37, rrn);                 // RRN
            message.setField(102, sourceAccount);      // Account ID 1
            message.setField(103, targetAccount);      // Account ID 2

            // 4. 儲存到流程變數
            // 使用 ASCII 4-byte 長度前綴，與 FISC 連線格式一致
            byte[] assembledMessage = new FiscMessageAssembler(FiscMessageAssembler.LengthEncoding.ASCII).assemble(message);
            execution.setVariable("stan", fiscStan);   // 更新為 FISC STAN
            execution.setVariable("rrn", rrn);
            execution.setVariable("transmissionDateTime", transmissionDateTime);
            execution.setVariable("mti", "0200");

            execution.setVariable("assembledMessage", assembledMessage);
            log.info("[{}] 電文組裝完成: fiscStan={}, callbackKey={}, RRN={}", transactionId, fiscStan, callbackKey, rrn);

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
}
