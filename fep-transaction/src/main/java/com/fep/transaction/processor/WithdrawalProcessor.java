package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;

/**
 * Processor for interbank withdrawal transactions (跨行提款).
 * Handles ATM cash withdrawal requests from other banks.
 */
public class WithdrawalProcessor extends AbstractTransactionProcessor {

    /** Maximum single withdrawal amount (default 20,000 TWD for ATM) */
    private static final BigDecimal MAX_WITHDRAWAL_AMOUNT = new BigDecimal("20000");

    /** Withdrawal amount must be multiple of this value */
    private static final BigDecimal WITHDRAWAL_MULTIPLE = new BigDecimal("100");

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.WITHDRAWAL;
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate source account
        validateSourceAccount(request);

        // Validate PIN block (required for withdrawal)
        validatePinBlock(request);

        // Validate withdrawal-specific amount rules
        validateWithdrawalAmount(request);

        // Validate terminal ID
        validateTerminalId(request);
    }

    /**
     * Validates the source account for withdrawal.
     */
    private void validateSourceAccount(TransactionRequest request) {
        if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
            throw TransactionException.invalidRequest("Source account is required for withdrawal");
        }
    }

    /**
     * Validates withdrawal-specific amount rules.
     */
    private void validateWithdrawalAmount(TransactionRequest request) {
        BigDecimal amount = request.getAmount();

        // Check maximum withdrawal limit
        if (amount.compareTo(MAX_WITHDRAWAL_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("withdrawal");
        }

        // Check if amount is multiple of 100
        if (amount.remainder(WITHDRAWAL_MULTIPLE).compareTo(BigDecimal.ZERO) != 0) {
            throw TransactionException.invalidAmount();
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
    protected void preProcess(TransactionRequest request) {
        log.debug("[{}] Pre-processing withdrawal request for account: {}",
                request.getTransactionId(), maskAccount(request.getSourceAccount()));

        // Here we would typically:
        // 1. Verify PIN with HSM
        // 2. Check account status
        // 3. Check daily withdrawal limits
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        log.info("[{}] Processing withdrawal: {} TWD from account {}",
                request.getTransactionId(),
                request.getAmount(),
                maskAccount(request.getSourceAccount()));

        // In a real implementation, this would:
        // 1. Send request to core banking system
        // 2. Wait for response
        // 3. Handle timeout/retry logic

        // For now, simulate successful processing
        // TODO: Integrate with core banking system via adapter

        // Simulate available balance after withdrawal
        // In real implementation, this would come from core banking system
        BigDecimal simulatedBalance = new BigDecimal("10000.00").subtract(request.getAmount());

        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(ResponseCode.APPROVED.getCode())
                .responseCodeEnum(ResponseCode.APPROVED)
                .responseDescription(ResponseCode.APPROVED.getDescription())
                .responseDescriptionChinese(ResponseCode.APPROVED.getChineseDescription())
                .approved(true)
                .authorizationCode(generateAuthorizationCode())
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode())
                .rrn(request.getRrn())
                .stan(request.getStan())
                .availableBalance(simulatedBalance)
                .build();
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        if (response.isApproved()) {
            log.info("[{}] Withdrawal approved: {} TWD, Auth: {}",
                    request.getTransactionId(),
                    response.getAmount(),
                    response.getAuthorizationCode());

            // Here we would typically:
            // 1. Update transaction log
            // 2. Update daily limits counter
            // 3. Send notification if configured
        } else {
            log.warn("[{}] Withdrawal declined: {}",
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
