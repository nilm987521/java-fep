package com.fep.transaction.bpmn.delegate;

import com.fep.transaction.db.service.PendingTransactionDbService;
import com.fep.transaction.redis.dto.PendingTransactionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Optional;

/**
 * BPMN Service Task Delegate: 從 DB 載入交易上下文
 *
 * <p>此 Delegate 用於 Response/Timeout BPMN 流程，從 DB 載入 Pending 交易的完整上下文。
 *
 * <p>純 DB 架構：不使用 Redis，所有交易上下文存於 DB。
 *
 * <p>載入的資訊會設定為流程變數，供後續 Delegate 使用：
 * <ul>
 *   <li>transactionId - 交易 ID</li>
 *   <li>stan - STAN</li>
 *   <li>sourceAccount - 來源帳號</li>
 *   <li>targetAccount - 目標帳號</li>
 *   <li>amount - 交易金額</li>
 *   <li>rawMessage - 原始請求電文 (用於組裝回應)</li>
 *   <li>channelId - 通道 ID</li>
 *   <li>等等...</li>
 * </ul>
 *
 * <p>流程變數輸入：
 * <ul>
 *   <li>transactionId - 交易 ID (來自 FiscResponseHandler)</li>
 *   <li>或 stan - STAN (備援)</li>
 * </ul>
 */
@Slf4j
@Component("loadTransactionContextDelegate")
@RequiredArgsConstructor
public class LoadTransactionContextDelegate implements JavaDelegate {

    private final PendingTransactionDbService dbService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String processId = execution.getProcessInstanceId();
        String transactionId = (String) execution.getVariable("transactionId");
        String stan = (String) execution.getVariable("stan");

        log.info("[{}] 載入交易上下文: txnId={}, stan={}", processId, transactionId, stan);

        try {
            // 1. 嘗試載入交易資料
            Optional<PendingTransactionDTO> optDto = loadTransaction(transactionId, stan);

            if (optDto.isEmpty()) {
                log.error("[{}] 找不到交易資料: txnId={}, stan={}", processId, transactionId, stan);
                throw new BpmnError("TRANSACTION_NOT_FOUND", "找不到交易資料");
            }

            PendingTransactionDTO dto = optDto.get();

            // 2. 設定流程變數
            setProcessVariables(execution, dto);

            log.info("[{}] 交易上下文已載入: txnId={}, stan={}, status={}",
                    processId, dto.getTransactionId(), dto.getStan(), dto.getStatus());

        } catch (BpmnError e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] 載入交易上下文失敗: txnId={}, stan={}, error={}",
                    processId, transactionId, stan, e.getMessage(), e);
            throw new BpmnError("LOAD_CONTEXT_ERROR", "載入交易上下文失敗: " + e.getMessage());
        }
    }

    /**
     * 載入交易資料
     *
     * <p>優先使用 transactionId，若不存在則使用 stan 查詢
     */
    private Optional<PendingTransactionDTO> loadTransaction(String transactionId, String stan) {
        // 優先使用 transactionId
        if (transactionId != null && !transactionId.isEmpty()) {
            Optional<PendingTransactionDTO> result = dbService.loadTransaction(transactionId);
            if (result.isPresent()) {
                return result;
            }
        }

        // 使用 stan 查詢
        if (stan != null && !stan.isEmpty()) {
            return dbService.loadTransactionByStan(stan);
        }

        return Optional.empty();
    }

    /**
     * 設定流程變數
     */
    private void setProcessVariables(DelegateExecution execution, PendingTransactionDTO dto) {
        // 基本資訊
        execution.setVariable("transactionId", dto.getTransactionId());
        execution.setVariable("stan", dto.getStan());
        execution.setVariable("mti", dto.getMti());
        execution.setVariable("processingCode", dto.getProcessingCode());

        // 帳戶資訊
        execution.setVariable("sourceAccount", dto.getSourceAccount());
        execution.setVariable("targetAccount", dto.getTargetAccount());
        execution.setVariable("sourceBankCode", dto.getSourceBankCode());
        execution.setVariable("targetBankCode", dto.getTargetBankCode());

        // 金額
        if (dto.getAmount() != null) {
            execution.setVariable("amount", dto.getAmount().longValue());
        }

        // 通道資訊
        execution.setVariable("channel", dto.getChannelId());
        execution.setVariable("callbackKey", dto.getCallbackKey());

        // 原始請求電文 (解碼 Base64)
        if (dto.getRawRequestBase64() != null && !dto.getRawRequestBase64().isEmpty()) {
            try {
                byte[] rawMessage = Base64.getDecoder().decode(dto.getRawRequestBase64());
                execution.setVariable("rawMessage", rawMessage);
            } catch (Exception e) {
                log.warn("解碼原始請求電文失敗: {}", e.getMessage());
            }
        }

        // 狀態資訊
        if (dto.getStatus() != null) {
            execution.setVariable("previousStatus", dto.getStatus().name());
        }

        // 時間資訊
        execution.setVariable("createdAt", dto.getCreatedAt());
        execution.setVariable("sentToFiscAt", dto.getSentToFiscAt());
        execution.setVariable("expireAt", dto.getExpireAt());

        // 沖正狀態
        if (dto.getReversalStatus() != null) {
            execution.setVariable("reversalStatus", dto.getReversalStatus().name());
        }
    }
}
