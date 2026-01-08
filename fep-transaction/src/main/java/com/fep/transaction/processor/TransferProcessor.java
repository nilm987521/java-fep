package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;

/**
 * Processor for interbank transfer transactions (跨行轉帳).
 * Handles fund transfers between accounts at different banks.
 */
public class TransferProcessor extends AbstractTransactionProcessor {

    /** Maximum single transfer amount (default 2 million TWD) */
    private static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("2000000");

    /** Maximum non-designated transfer amount */
    private static final BigDecimal MAX_NON_DESIGNATED_AMOUNT = new BigDecimal("50000");

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.TRANSFER;
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate source account
        validateSourceAccount(request);

        // Validate destination account
        validateDestinationAccount(request);

        // Validate PIN block (required for transfer)
        validatePinBlock(request);

        // Validate transfer-specific rules
        validateTransferRules(request);
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
     * Validates the destination account and bank.
     */
    private void validateDestinationAccount(TransactionRequest request) {
        if (request.getDestinationAccount() == null || request.getDestinationAccount().isBlank()) {
            throw TransactionException.invalidRequest("Destination account is required");
        }

        // For interbank transfers, destination bank code is required
        if (request.getDestinationBankCode() == null || request.getDestinationBankCode().isBlank()) {
            throw TransactionException.invalidRequest("Destination bank code is required for interbank transfer");
        }

        // Validate bank code format (3 digits for Taiwan)
        if (!request.getDestinationBankCode().matches("\\d{3}")) {
            throw TransactionException.invalidRequest("Invalid destination bank code format");
        }

        // Source and destination cannot be the same
        if (request.getSourceAccount().equals(request.getDestinationAccount())) {
            throw TransactionException.invalidRequest("Source and destination accounts cannot be the same");
        }
    }

    /**
     * Validates transfer-specific business rules.
     */
    private void validateTransferRules(TransactionRequest request) {
        BigDecimal amount = request.getAmount();

        // Check maximum transfer limit
        if (amount.compareTo(MAX_TRANSFER_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("transfer");
        }

        // TODO: Check if destination is designated account
        // For non-designated accounts, apply stricter limits
        // This would require checking against account configuration
    }

    @Override
    protected void preProcess(TransactionRequest request) {
        log.debug("[{}] Pre-processing transfer: {} -> {} (Bank: {})",
                request.getTransactionId(),
                maskAccount(request.getSourceAccount()),
                maskAccount(request.getDestinationAccount()),
                request.getDestinationBankCode());

        // Here we would typically:
        // 1. Verify PIN with HSM
        // 2. Check if destination is designated account
        // 3. Apply appropriate transfer limits
        // 4. Check source account balance (optional pre-check)
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        log.info("[{}] Processing transfer: {} TWD from {} to {} (Bank: {})",
                request.getTransactionId(),
                request.getAmount(),
                maskAccount(request.getSourceAccount()),
                maskAccount(request.getDestinationAccount()),
                request.getDestinationBankCode());

        // In a real implementation, this would:
        // 1. Debit source account via core banking
        // 2. Send transfer request to FISC
        // 3. Credit destination account (via FISC clearing)
        // 4. Handle two-phase commit for atomicity

        // TODO: Integrate with core banking system and FISC

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
        if (response.isApproved()) {
            log.info("[{}] Transfer approved: {} TWD, Auth: {}, From: {} To: {} (Bank: {})",
                    request.getTransactionId(),
                    response.getAmount(),
                    response.getAuthorizationCode(),
                    maskAccount(request.getSourceAccount()),
                    maskAccount(request.getDestinationAccount()),
                    request.getDestinationBankCode());

            // Here we would typically:
            // 1. Update transaction log
            // 2. Update daily transfer limits
            // 3. Queue for FISC settlement
            // 4. Send notification to customer
        } else {
            log.warn("[{}] Transfer declined: {} - From: {} To: {}",
                    request.getTransactionId(),
                    response.getResponseCode(),
                    maskAccount(request.getSourceAccount()),
                    maskAccount(request.getDestinationAccount()));
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
