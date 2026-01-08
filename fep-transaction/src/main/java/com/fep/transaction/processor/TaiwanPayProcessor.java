package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Processor for Taiwan Pay (台灣Pay) QR code payment transactions.
 * Supports both consumer-presented QR (PUSH) and merchant-presented QR (PULL) modes.
 */
public class TaiwanPayProcessor extends AbstractTransactionProcessor {

    /** Maximum single payment amount (default 50,000 TWD) */
    private static final BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("50000");

    /** Minimum payment amount */
    private static final BigDecimal MIN_PAYMENT_AMOUNT = new BigDecimal("1");

    /** Payment type: Consumer presents QR code to merchant */
    public static final String PAYMENT_TYPE_PUSH = "PUSH";

    /** Payment type: Consumer scans merchant's QR code */
    public static final String PAYMENT_TYPE_PULL = "PULL";

    /**
     * Taiwan Pay transaction sub-types.
     */
    public enum TaiwanPayType {
        /** Consumer payment (消費) */
        PAYMENT("01", "Payment", "消費"),
        /** Transfer (轉帳) */
        TRANSFER("02", "Transfer", "轉帳"),
        /** Tax payment (繳稅) */
        TAX_PAYMENT("03", "Tax Payment", "繳稅"),
        /** Bill payment (繳費) */
        BILL_PAYMENT("04", "Bill Payment", "繳費");

        private final String code;
        private final String description;
        private final String chineseDescription;

        TaiwanPayType(String code, String description, String chineseDescription) {
            this.code = code;
            this.description = description;
            this.chineseDescription = chineseDescription;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public String getChineseDescription() {
            return chineseDescription;
        }
    }

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.TAIWAN_PAY;
    }

    /**
     * Overrides base validation to skip PAN validation.
     * Taiwan Pay uses token/QR code instead of physical card.
     */
    @Override
    public void validate(TransactionRequest request) {
        // Only validate transaction ID
        validateTransactionId(request);

        // Taiwan Pay specific validations
        doValidate(request);
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate source account
        validateSourceAccount(request);

        // Validate payment type
        validatePaymentType(request);

        // Validate QR code or token
        validateQrCodeOrToken(request);

        // Validate payment amount
        validatePaymentAmount(request);
    }

    /**
     * Validates the source account.
     */
    private void validateSourceAccount(TransactionRequest request) {
        if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
            throw TransactionException.invalidRequest("Source account is required for Taiwan Pay");
        }
    }

    /**
     * Validates the payment type (PUSH or PULL).
     */
    private void validatePaymentType(TransactionRequest request) {
        String paymentType = request.getPaymentType();
        if (paymentType == null || paymentType.isBlank()) {
            throw TransactionException.invalidRequest("Payment type is required (PUSH or PULL)");
        }

        if (!PAYMENT_TYPE_PUSH.equals(paymentType) && !PAYMENT_TYPE_PULL.equals(paymentType)) {
            throw TransactionException.invalidRequest("Invalid payment type: " + paymentType);
        }
    }

    /**
     * Validates QR code data or Taiwan Pay token.
     */
    private void validateQrCodeOrToken(TransactionRequest request) {
        String paymentType = request.getPaymentType();

        if (PAYMENT_TYPE_PULL.equals(paymentType)) {
            // PULL mode: consumer scans merchant QR
            if (request.getMerchantQrCode() == null || request.getMerchantQrCode().isBlank()) {
                throw TransactionException.invalidRequest("Merchant QR code is required for PULL payment");
            }

            // Validate QR code format (Taiwan Pay format: 26 chars starting with specific prefix)
            String qrCode = request.getMerchantQrCode();
            if (qrCode.length() < 20) {
                throw TransactionException.invalidRequest("Invalid merchant QR code format");
            }
        } else {
            // PUSH mode: merchant scans consumer's QR (token-based)
            if (request.getTaiwanPayToken() == null || request.getTaiwanPayToken().isBlank()) {
                throw TransactionException.invalidRequest("Taiwan Pay token is required for PUSH payment");
            }

            // Token should be at least 16 characters
            if (request.getTaiwanPayToken().length() < 16) {
                throw TransactionException.invalidRequest("Invalid Taiwan Pay token format");
            }
        }
    }

    /**
     * Validates payment amount against limits.
     */
    private void validatePaymentAmount(TransactionRequest request) {
        BigDecimal amount = request.getAmount();

        if (amount == null) {
            throw TransactionException.invalidRequest("Payment amount is required");
        }

        if (amount.compareTo(MIN_PAYMENT_AMOUNT) < 0) {
            throw TransactionException.invalidRequest(
                    "Payment amount must be at least " + MIN_PAYMENT_AMOUNT + " TWD");
        }

        if (amount.compareTo(MAX_PAYMENT_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("Taiwan Pay");
        }
    }

    @Override
    protected void preProcess(TransactionRequest request) {
        log.debug("[{}] Pre-processing Taiwan Pay payment: type={}, amount={}",
                request.getTransactionId(),
                request.getPaymentType(),
                request.getAmount());

        // Here we would typically:
        // 1. Validate the QR code/token with Taiwan Pay platform
        // 2. Retrieve merchant information
        // 3. Check consumer's account status and balance
        // 4. Apply any promotional rules/discounts
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        log.info("[{}] Processing Taiwan Pay payment: type={}, amount={} TWD, account={}",
                request.getTransactionId(),
                request.getPaymentType(),
                request.getAmount(),
                maskAccount(request.getSourceAccount()));

        // In a real implementation, this would:
        // 1. Debit consumer's account
        // 2. Send payment request to Taiwan Pay clearing system
        // 3. Credit merchant's account (via clearing)
        // 4. Generate payment reference

        // TODO: Integrate with Taiwan Pay platform

        String taiwanPayReference = generateTaiwanPayReference();
        String merchantName = extractMerchantName(request);

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
                .taiwanPayReference(taiwanPayReference)
                .merchantName(merchantName)
                .build();
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        if (response.isApproved()) {
            log.info("[{}] Taiwan Pay payment approved: {} TWD, Auth: {}, TwpRef: {}, Merchant: {}",
                    request.getTransactionId(),
                    response.getAmount(),
                    response.getAuthorizationCode(),
                    response.getTaiwanPayReference(),
                    response.getMerchantName());

            // Here we would typically:
            // 1. Send payment confirmation to Taiwan Pay platform
            // 2. Update transaction log
            // 3. Send notification to consumer
            // 4. Queue for settlement
        } else {
            log.warn("[{}] Taiwan Pay payment declined: {}",
                    request.getTransactionId(),
                    response.getResponseCode());
        }
    }

    /**
     * Generates a Taiwan Pay transaction reference.
     */
    private String generateTaiwanPayReference() {
        return "TWP" + System.currentTimeMillis() + String.format("%04d", (int) (Math.random() * 10000));
    }

    /**
     * Extracts merchant name from QR code or returns default.
     */
    private String extractMerchantName(TransactionRequest request) {
        // In real implementation, parse from QR code or query merchant database
        if (request.getMerchantQrCode() != null) {
            // Would parse merchant info from QR code
            return "Taiwan Pay Merchant";
        }
        return "Taiwan Pay";
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
