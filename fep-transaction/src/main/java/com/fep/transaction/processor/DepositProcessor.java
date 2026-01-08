package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;

/**
 * Processor for cash deposit transactions (存款).
 * Handles ATM cash deposit requests.
 */
public class DepositProcessor extends AbstractTransactionProcessor {

    /** Maximum single deposit amount (default 200,000 TWD) */
    private static final BigDecimal MAX_DEPOSIT_AMOUNT = new BigDecimal("200000");

    /** Minimum deposit amount */
    private static final BigDecimal MIN_DEPOSIT_AMOUNT = new BigDecimal("100");

    /** Deposit amount must be multiple of this value */
    private static final BigDecimal DEPOSIT_MULTIPLE = new BigDecimal("100");

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.DEPOSIT;
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate destination account (where deposit goes)
        validateDestinationAccount(request);

        // Validate deposit-specific amount rules
        validateDepositAmount(request);

        // Validate terminal ID
        validateTerminalId(request);
    }

    @Override
    protected void validatePinBlock(TransactionRequest request) {
        // PIN is optional for deposit - card holder may deposit to any account
        // Some banks require PIN for deposits to own account
    }

    /**
     * Validates the destination account for deposit.
     */
    private void validateDestinationAccount(TransactionRequest request) {
        // For deposits, destination account is where the money goes
        // Could be the card holder's own account or another account
        if (request.getDestinationAccount() == null || request.getDestinationAccount().isBlank()) {
            // If no destination, use source account (deposit to own account)
            if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
                throw TransactionException.invalidRequest("Account is required for deposit");
            }
        }
    }

    /**
     * Validates deposit-specific amount rules.
     */
    private void validateDepositAmount(TransactionRequest request) {
        BigDecimal amount = request.getAmount();

        // Check minimum deposit
        if (amount.compareTo(MIN_DEPOSIT_AMOUNT) < 0) {
            throw TransactionException.invalidAmount();
        }

        // Check maximum deposit limit
        if (amount.compareTo(MAX_DEPOSIT_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("deposit");
        }

        // Check if amount is multiple of 100
        if (amount.remainder(DEPOSIT_MULTIPLE).compareTo(BigDecimal.ZERO) != 0) {
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
        String targetAccount = request.getDestinationAccount() != null ?
                request.getDestinationAccount() : request.getSourceAccount();

        log.debug("[{}] Pre-processing deposit request to account: {}",
                request.getTransactionId(), maskAccount(targetAccount));

        // Here we would typically:
        // 1. Validate account status
        // 2. Check if account accepts deposits
        // 3. Verify cash received by ATM
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        String targetAccount = request.getDestinationAccount() != null ?
                request.getDestinationAccount() : request.getSourceAccount();

        log.info("[{}] Processing deposit: {} TWD to account {}",
                request.getTransactionId(),
                request.getAmount(),
                maskAccount(targetAccount));

        // In a real implementation, this would:
        // 1. Confirm cash acceptance by ATM
        // 2. Credit the target account via core banking
        // 3. Handle pending deposits (counted later)

        // TODO: Integrate with core banking system

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
                .build();
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        String targetAccount = request.getDestinationAccount() != null ?
                request.getDestinationAccount() : request.getSourceAccount();

        if (response.isApproved()) {
            log.info("[{}] Deposit approved: {} TWD to {}, Auth: {}",
                    request.getTransactionId(),
                    response.getAmount(),
                    maskAccount(targetAccount),
                    response.getAuthorizationCode());

            // Here we would typically:
            // 1. Update transaction log
            // 2. Send deposit confirmation receipt
            // 3. Trigger AML checks for large deposits
        } else {
            log.warn("[{}] Deposit declined: {}",
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
