package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

/**
 * Processor for reversal transactions (沖正).
 * Handles reversal of failed or timed-out transactions.
 */
public class ReversalProcessor extends AbstractTransactionProcessor {

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.REVERSAL;
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate original transaction reference
        validateOriginalTransaction(request);

        // Validate matching criteria
        validateMatchingCriteria(request);
    }

    @Override
    protected void validateAmount(TransactionRequest request) {
        // Amount validation is different for reversals
        // Amount should match original transaction
        if (request.getAmount() == null) {
            throw TransactionException.invalidRequest("Original amount is required for reversal");
        }
    }

    @Override
    protected void validatePinBlock(TransactionRequest request) {
        // PIN is not required for reversal - it's a system-initiated correction
    }

    /**
     * Validates original transaction reference is provided.
     */
    private void validateOriginalTransaction(TransactionRequest request) {
        if (request.getOriginalTransactionId() == null || request.getOriginalTransactionId().isBlank()) {
            throw TransactionException.invalidRequest("Original transaction ID is required for reversal");
        }

        // Original RRN should be provided
        if (request.getRrn() == null || request.getRrn().isBlank()) {
            throw TransactionException.invalidRequest("Original RRN is required for reversal");
        }

        // Original STAN should be provided
        if (request.getStan() == null || request.getStan().isBlank()) {
            throw TransactionException.invalidRequest("Original STAN is required for reversal");
        }
    }

    /**
     * Validates matching criteria for reversal.
     */
    private void validateMatchingCriteria(TransactionRequest request) {
        // Terminal ID should match original
        if (request.getTerminalId() == null || request.getTerminalId().isBlank()) {
            throw TransactionException.invalidRequest("Terminal ID is required for reversal");
        }

        // Acquiring bank should match
        if (request.getAcquiringBankCode() == null || request.getAcquiringBankCode().isBlank()) {
            throw TransactionException.invalidRequest("Acquiring bank code is required for reversal");
        }
    }

    @Override
    protected void preProcess(TransactionRequest request) {
        log.debug("[{}] Pre-processing reversal for original transaction: {}",
                request.getTransactionId(), request.getOriginalTransactionId());

        // Here we would typically:
        // 1. Look up original transaction
        // 2. Verify original transaction exists
        // 3. Check if already reversed
        // 4. Validate reversal is within time window
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        log.info("[{}] Processing reversal for original: {}, Amount: {} TWD",
                request.getTransactionId(),
                request.getOriginalTransactionId(),
                request.getAmount());

        // In a real implementation, this would:
        // 1. Find original transaction in database
        // 2. Check if reversible (not already reversed, within time limit)
        // 3. Send reversal to FISC/core banking
        // 4. Credit/debit accounts as needed
        // 5. Mark original transaction as reversed

        // TODO: Integrate with transaction store and core banking

        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(ResponseCode.APPROVED.getCode())
                .responseCodeEnum(ResponseCode.APPROVED)
                .responseDescription(ResponseCode.APPROVED.getDescription())
                .responseDescriptionChinese(ResponseCode.APPROVED.getChineseDescription())
                .approved(true)
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode())
                .rrn(request.getRrn())
                .stan(request.getStan())
                .additionalData("Original TXN: " + request.getOriginalTransactionId())
                .build();
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        if (response.isApproved()) {
            log.info("[{}] Reversal approved for original: {}, Amount: {} TWD",
                    request.getTransactionId(),
                    request.getOriginalTransactionId(),
                    response.getAmount());

            // Here we would typically:
            // 1. Update original transaction status to REVERSED
            // 2. Log reversal for audit
            // 3. Update settlement records
        } else {
            log.warn("[{}] Reversal declined for original: {}, Code: {}",
                    request.getTransactionId(),
                    request.getOriginalTransactionId(),
                    response.getResponseCode());
        }
    }
}
