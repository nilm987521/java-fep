package com.fep.transaction.pipeline.handler;

import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.limit.LimitCheckResult;
import com.fep.transaction.limit.LimitManager;
import com.fep.transaction.pipeline.PipelineContext;
import com.fep.transaction.pipeline.PipelineHandler;
import com.fep.transaction.pipeline.PipelineStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pipeline handler for checking transaction limits.
 * Verifies single transaction, daily cumulative, and count limits.
 */
public class LimitCheckHandler implements PipelineHandler {

    private static final Logger log = LoggerFactory.getLogger(LimitCheckHandler.class);

    private final LimitManager limitManager;

    public LimitCheckHandler(LimitManager limitManager) {
        this.limitManager = limitManager;
    }

    @Override
    public PipelineStage getStage() {
        return PipelineStage.VALIDATION;
    }

    @Override
    public int getOrder() {
        // Run after basic validation but before routing
        return 200;
    }

    @Override
    public void handle(PipelineContext context) {
        log.debug("[{}] Checking transaction limits",
                context.getRequest().getTransactionId());

        LimitCheckResult result = limitManager.checkLimits(context.getRequest());

        if (!result.isPassed()) {
            log.warn("[{}] Limit check failed: {}",
                    context.getRequest().getTransactionId(),
                    result.getMessage());

            TransactionResponse response = TransactionResponse.builder()
                    .transactionId(context.getRequest().getTransactionId())
                    .responseCode(ResponseCode.EXCEEDS_WITHDRAWAL_LIMIT.getCode())
                    .responseCodeEnum(ResponseCode.EXCEEDS_WITHDRAWAL_LIMIT)
                    .responseDescription("Transaction limit exceeded: " + result.getMessage())
                    .responseDescriptionChinese("交易限額超過: " +
                            (result.getExceededLimit() != null ?
                                    result.getExceededLimit().getLimitType().getChineseDescription() :
                                    "限額檢查失敗"))
                    .approved(false)
                    .errorDetails(result.getMessage())
                    .build();

            context.setResponse(response);
            context.setContinueProcessing(false);
            return;
        }

        // Store limit check result in context for later use
        context.setAttribute("limitCheckResult", result);
        context.setAttribute("remainingLimit", result.getRemainingAmount());

        log.debug("[{}] Limit check passed, remaining: {}",
                context.getRequest().getTransactionId(),
                result.getRemainingAmount());
    }

    /**
     * Records usage after successful transaction.
     * Should be called externally after pipeline completion.
     */
    public void recordUsageIfSuccess(PipelineContext context) {
        if (context.getResponse() != null && context.getResponse().isApproved()) {
            limitManager.recordUsage(context.getRequest());
            log.debug("[{}] Recorded usage for successful transaction",
                    context.getRequest().getTransactionId());
        }
    }
}
