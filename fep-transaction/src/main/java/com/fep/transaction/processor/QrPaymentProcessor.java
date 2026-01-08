package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;

/**
 * Processor for QR Code payment transactions (QR碼支付/台灣Pay).
 * Handles mobile QR code payment requests.
 */
public class QrPaymentProcessor extends AbstractTransactionProcessor {

    /** Maximum single QR payment amount */
    private static final BigDecimal MAX_QR_PAYMENT_AMOUNT = new BigDecimal("50000");

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.QR_PAYMENT;
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate source account
        validateSourceAccount(request);

        // Validate QR payment specific fields
        validateQrPaymentFields(request);

        // Validate amount
        validateQrPaymentAmount(request);
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
     * Validates QR payment specific fields.
     */
    private void validateQrPaymentFields(TransactionRequest request) {
        // QR payment requires merchant info
        if (request.getMerchantId() == null || request.getMerchantId().isBlank()) {
            throw TransactionException.invalidRequest("Merchant ID is required for QR payment");
        }

        // Additional data should contain QR code info
        if (request.getAdditionalData() == null || request.getAdditionalData().isBlank()) {
            throw TransactionException.invalidRequest("QR code data is required");
        }
    }

    /**
     * Validates QR payment amount.
     */
    private void validateQrPaymentAmount(TransactionRequest request) {
        BigDecimal amount = request.getAmount();

        if (amount.compareTo(MAX_QR_PAYMENT_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("QR payment");
        }
    }

    @Override
    protected void preProcess(TransactionRequest request) {
        log.debug("[{}] Pre-processing QR payment from account: {} to merchant: {}",
                request.getTransactionId(),
                maskAccount(request.getSourceAccount()),
                request.getMerchantId());

        // Here we would typically:
        // 1. Validate QR code format and content
        // 2. Verify merchant is active
        // 3. Check if user has enabled QR payment
        // 4. Verify PIN if required
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        log.info("[{}] Processing QR payment: {} TWD from {} to merchant {}",
                request.getTransactionId(),
                request.getAmount(),
                maskAccount(request.getSourceAccount()),
                request.getMerchantId());

        // In a real implementation, this would:
        // 1. Parse QR code to get merchant and payment details
        // 2. Debit customer's account
        // 3. Credit merchant's account (or settlement pending)
        // 4. Send notification to both parties

        // TODO: Integrate with Taiwan Pay / FISC QR payment gateway

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
                .additionalData("Merchant: " + request.getMerchantId())
                .build();
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        if (response.isApproved()) {
            log.info("[{}] QR payment approved: {} TWD to merchant {}, Auth: {}",
                    request.getTransactionId(),
                    response.getAmount(),
                    request.getMerchantId(),
                    response.getAuthorizationCode());

            // Here we would typically:
            // 1. Send push notification to customer
            // 2. Update loyalty points if applicable
            // 3. Generate digital receipt
        } else {
            log.warn("[{}] QR payment declined: {}",
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
