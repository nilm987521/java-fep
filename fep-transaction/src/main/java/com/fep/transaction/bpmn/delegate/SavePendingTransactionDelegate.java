package com.fep.transaction.bpmn.delegate;

import com.fep.transaction.db.service.PendingTransactionDbService;
import com.fep.transaction.redis.dto.PendingTransactionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * BPMN Service Task Delegate: 儲存 Pending 交易至 DB
 *
 * <p>此 Delegate 在發送至 FISC 之前執行，負責：
 * <ul>
 *   <li>產生唯一的交易 ID (transactionId)</li>
 *   <li>儲存交易資訊至 DB (FEP_TRANSACTION 表)</li>
 *   <li>設定過期時間 (供 TimeoutScanner 掃描)</li>
 * </ul>
 *
 * <p>純 DB 架構：不使用 Redis，所有交易上下文存於 DB。
 *
 * <p>流程變數輸入：
 * <ul>
 *   <li>stan - STAN</li>
 *   <li>sourceAccount - 來源帳號</li>
 *   <li>targetAccount - 目標帳號</li>
 *   <li>amount - 交易金額</li>
 *   <li>sourceBankCode - 來源銀行代碼</li>
 *   <li>targetBankCode - 目標銀行代碼</li>
 *   <li>assembledMessage - 組裝好的電文 (byte[])</li>
 *   <li>channelId - 通道 ID</li>
 * </ul>
 *
 * <p>流程變數輸出：
 * <ul>
 *   <li>transactionId - 交易唯一識別碼</li>
 *   <li>expireAt - 過期時間 (epoch ms)</li>
 * </ul>
 */
@Slf4j
@Component("savePendingTransactionDelegate")
@RequiredArgsConstructor
public class SavePendingTransactionDelegate implements JavaDelegate {

    private final PendingTransactionDbService dbService;

    @Value("${fep.pending.fisc-timeout-ms:30000}")
    private long fiscTimeoutMs;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processId = execution.getProcessInstanceId();
        String stan = (String) execution.getVariable("stan");

        log.info("[{}] 儲存 Pending 交易: STAN={}", processId, stan);

        try {
            // 1. 產生交易 ID
            String transactionId = generateTransactionId();
            execution.setVariable("transactionId", transactionId);

            // 2. 計算過期時間
            long now = System.currentTimeMillis();
            long expireAt = now + fiscTimeoutMs;
            execution.setVariable("expireAt", expireAt);

            // 3. 建立 DTO
            PendingTransactionDTO dto = buildPendingTransactionDTO(execution, transactionId, now, expireAt);

            // 4. 儲存至 DB
            dbService.savePendingTransaction(dto);

            log.info("[{}] Pending 交易已儲存至 DB: txnId={}, stan={}, expireAt={}",
                    processId, transactionId, stan, expireAt);

        } catch (Exception e) {
            log.error("[{}] 儲存 Pending 交易失敗: STAN={}, error={}",
                    processId, stan, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 產生交易唯一識別碼
     *
     * <p>格式：TXN-{yyyyMMdd}-{UUID}
     */
    private String generateTransactionId() {
        String date = LocalDate.now().format(DATE_FORMATTER);
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("TXN-%s-%s", date, uuid);
    }

    /**
     * 建立 PendingTransactionDTO
     */
    private PendingTransactionDTO buildPendingTransactionDTO(
            DelegateExecution execution,
            String transactionId,
            long createdAt,
            long expireAt) {

        String processId = execution.getProcessInstanceId();
        String stan = (String) execution.getVariable("stan");
        String sourceAccount = (String) execution.getVariable("sourceAccount");
        String targetAccount = (String) execution.getVariable("targetAccount");
        Long amount = (Long) execution.getVariable("amount");
        String sourceBankCode = (String) execution.getVariable("sourceBankCode");
        String targetBankCode = (String) execution.getVariable("targetBankCode");
        String channelId = (String) execution.getVariable("channel");
        byte[] assembledMessage = (byte[]) execution.getVariable("assembledMessage");

        // MTI 和 Processing Code
        String mti = (String) execution.getVariable("mti");
        if (mti == null) {
            mti = "0200"; // 預設為金融交易請求
        }
        String processingCode = (String) execution.getVariable("processingCode");

        // 原始請求電文 Base64 編碼
        String rawRequestBase64 = null;
        if (assembledMessage != null) {
            rawRequestBase64 = Base64.getEncoder().encodeToString(assembledMessage);
        }

        // 交易日期
        String transactionDate = LocalDate.now().format(DATE_FORMATTER);

        return PendingTransactionDTO.builder()
                .transactionId(transactionId)
                .stan(stan)
                .mti(mti)
                .processingCode(processingCode)
                .sourceAccount(sourceAccount)
                .targetAccount(targetAccount)
                .amount(amount != null ? BigDecimal.valueOf(amount) : BigDecimal.ZERO)
                .sourceBankCode(sourceBankCode)
                .targetBankCode(targetBankCode)
                .status(PendingTransactionDTO.TransactionStatus.PENDING)
                .rawRequestBase64(rawRequestBase64)
                .callbackKey(stan) // 使用 STAN 作為 callback key
                .channelId(channelId)
                .processId(processId)
                .createdAt(createdAt)
                .expireAt(expireAt)
                .reversalStatus(PendingTransactionDTO.ReversalStatus.NONE)
                .transactionDate(transactionDate)
                .build();
    }
}
