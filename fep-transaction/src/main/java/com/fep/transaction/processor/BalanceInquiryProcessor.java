package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;

/**
 * Processor for balance inquiry transactions (餘額查詢).
 * Handles ATM balance check requests.
 */
public class BalanceInquiryProcessor extends AbstractTransactionProcessor {

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.BALANCE_INQUIRY;
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate source account
        validateSourceAccount(request);

        // Validate PIN block (required for balance inquiry)
        validatePinBlock(request);

        // Validate terminal ID
        validateTerminalId(request);
    }

    /**
     * Validates the source account.
     */
    private void validateSourceAccount(TransactionRequest request) {
        if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
            throw TransactionException.invalidRequest("Source account is required");
        }
    }

    /**
     * Validates terminal ID is present.
     */
    private void validateTerminalId(TransactionRequest request) {
        if (request.getTerminalId() == null || request.getTerminalId().isBlank()) {
            throw TransactionException.invalidRequest("Terminal ID is required");
        }
    }

    @Override
    protected void validateAmount(TransactionRequest request) {
        // Balance inquiry doesn't require amount, skip validation
        // Or set amount to 0 if not provided
        if (request.getAmount() == null) {
            request.setAmount(BigDecimal.ZERO);
        }
    }

    @Override
    protected void preProcess(TransactionRequest request) {
        log.debug("[{}] Pre-processing balance inquiry for account: {}",
                request.getTransactionId(),
                maskAccount(request.getSourceAccount()));

        // Here we would typically:
        // 1. Verify PIN with HSM
        // 2. Check account status
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        log.info("[{}] Processing balance inquiry for account: {}",
                request.getTransactionId(),
                maskAccount(request.getSourceAccount()));

        // In a real implementation, this would:
        // 1. Query core banking system for account balance
        // 2. Return available and ledger balances

        // TODO: Integrate with core banking system

        // Simulated balance response
        BigDecimal availableBalance = new BigDecimal("125000.00");
        BigDecimal ledgerBalance = new BigDecimal("125500.00");

        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(ResponseCode.APPROVED.getCode())
                .responseCodeEnum(ResponseCode.APPROVED)
                .responseDescription(ResponseCode.APPROVED.getDescription())
                .responseDescriptionChinese(ResponseCode.APPROVED.getChineseDescription())
                .approved(true)
                .availableBalance(availableBalance)
                .ledgerBalance(ledgerBalance)
                .currencyCode(request.getCurrencyCode())
                .rrn(request.getRrn())
                .stan(request.getStan())
                .build();
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        if (response.isApproved()) {
            log.info("[{}] Balance inquiry completed: Available={}, Ledger={}",
                    request.getTransactionId(),
                    response.getAvailableBalance(),
                    response.getLedgerBalance());

            // Here we would typically:
            // 1. Log the inquiry for audit purposes
            // 2. Update inquiry count (some banks limit daily inquiries)
        } else {
            log.warn("[{}] Balance inquiry failed: {}",
                    request.getTransactionId(),
                    response.getResponseCode());
        }
    }

    /**
     * Masks account number for logging.
     */
    private String maskAccount(String account) {
        if (account == null || account.length() < 8) {
            return "****";
        }
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }
}
