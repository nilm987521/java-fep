package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Processor for e-ticket/stored value card top-up transactions (電子票證加值).
 * Supports various e-ticket types including EasyCard, iPASS, icash, etc.
 */
public class ETicketTopupProcessor extends AbstractTransactionProcessor {

    /** Maximum single top-up amount (default 10,000 TWD) */
    private static final BigDecimal MAX_TOPUP_AMOUNT = new BigDecimal("10000");

    /** Minimum top-up amount (100 TWD) */
    private static final BigDecimal MIN_TOPUP_AMOUNT = new BigDecimal("100");

    /** Maximum card balance limit (regulatory limit in Taiwan) */
    private static final BigDecimal MAX_CARD_BALANCE = new BigDecimal("10000");

    /** Supported e-ticket types */
    public enum ETicketType {
        /** 悠遊卡 */
        EASYCARD("01", "EasyCard", "悠遊卡"),
        /** 一卡通 */
        IPASS("02", "iPASS", "一卡通"),
        /** icash */
        ICASH("03", "icash", "愛金卡"),
        /** 有錢卡 */
        HAPPYCASH("04", "HappyCash", "有錢卡"),
        /** 遠鑫卡 */
        HAPPYGO("05", "HappyGo Pay", "遠鑫卡");

        private final String code;
        private final String description;
        private final String chineseDescription;

        ETicketType(String code, String description, String chineseDescription) {
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

        public static ETicketType fromCode(String code) {
            for (ETicketType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** Valid top-up amounts (must be multiples of 100) */
    private static final Set<BigDecimal> VALID_TOPUP_AMOUNTS = Set.of(
            new BigDecimal("100"),
            new BigDecimal("200"),
            new BigDecimal("300"),
            new BigDecimal("500"),
            new BigDecimal("1000"),
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            new BigDecimal("5000"),
            new BigDecimal("10000")
    );

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.E_TICKET_TOPUP;
    }

    /**
     * Overrides base validation to skip PAN validation.
     * E-ticket top-up uses e-ticket card number instead of PAN.
     */
    @Override
    public void validate(TransactionRequest request) {
        // Only validate transaction ID (PAN not required for e-ticket top-up)
        validateTransactionId(request);

        // E-ticket specific validations
        doValidate(request);
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate source account (for debit)
        validateSourceAccount(request);

        // Validate e-ticket card number
        validateETicketCardNumber(request);

        // Validate e-ticket type
        validateETicketType(request);

        // Validate top-up amount
        validateTopupAmount(request);
    }

    /**
     * Validates the source account.
     */
    private void validateSourceAccount(TransactionRequest request) {
        if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
            throw TransactionException.invalidRequest("Source account is required for e-ticket top-up");
        }
    }

    /**
     * Validates the e-ticket card number.
     */
    private void validateETicketCardNumber(TransactionRequest request) {
        String cardNumber = request.getETicketCardNumber();
        if (cardNumber == null || cardNumber.isBlank()) {
            throw TransactionException.invalidRequest("E-ticket card number is required");
        }

        // E-ticket card numbers are typically 16-20 digits
        if (cardNumber.length() < 16 || cardNumber.length() > 20) {
            throw TransactionException.invalidRequest("Invalid e-ticket card number length");
        }

        // Should be numeric
        if (!cardNumber.matches("\\d+")) {
            throw TransactionException.invalidRequest("E-ticket card number must be numeric");
        }
    }

    /**
     * Validates the e-ticket type.
     */
    private void validateETicketType(TransactionRequest request) {
        String eTicketType = request.getETicketType();
        if (eTicketType == null || eTicketType.isBlank()) {
            throw TransactionException.invalidRequest("E-ticket type is required");
        }

        ETicketType type = ETicketType.fromCode(eTicketType);
        if (type == null) {
            throw TransactionException.invalidRequest("Invalid e-ticket type: " + eTicketType);
        }
    }

    /**
     * Validates top-up amount against limits and valid values.
     */
    private void validateTopupAmount(TransactionRequest request) {
        BigDecimal amount = request.getAmount();

        if (amount == null) {
            throw TransactionException.invalidRequest("Top-up amount is required");
        }

        if (amount.compareTo(MIN_TOPUP_AMOUNT) < 0) {
            throw TransactionException.invalidRequest(
                    "Top-up amount must be at least " + MIN_TOPUP_AMOUNT + " TWD");
        }

        if (amount.compareTo(MAX_TOPUP_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("e-ticket top-up");
        }

        // Check if amount is a valid top-up denomination
        if (!VALID_TOPUP_AMOUNTS.contains(amount)) {
            throw TransactionException.invalidRequest(
                    "Top-up amount must be one of: 100, 200, 300, 500, 1000, 2000, 3000, 5000, 10000 TWD");
        }
    }

    @Override
    protected void preProcess(TransactionRequest request) {
        ETicketType eTicketType = ETicketType.fromCode(request.getETicketType());
        log.debug("[{}] Pre-processing e-ticket top-up: type={}, cardNumber={}, amount={}",
                request.getTransactionId(),
                eTicketType != null ? eTicketType.getChineseDescription() : request.getETicketType(),
                maskCardNumber(request.getETicketCardNumber()),
                request.getAmount());

        // Here we would typically:
        // 1. Query current card balance from e-ticket operator
        // 2. Verify the card is active and not blocked
        // 3. Check if resulting balance would exceed maximum limit
        // 4. Validate card expiration date if applicable
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        ETicketType eTicketType = ETicketType.fromCode(request.getETicketType());
        log.info("[{}] Processing e-ticket top-up: {} ({}) - Amount: {} TWD, CardNumber: {}",
                request.getTransactionId(),
                eTicketType != null ? eTicketType.getChineseDescription() : request.getETicketType(),
                eTicketType != null ? eTicketType.getDescription() : "Unknown",
                request.getAmount(),
                maskCardNumber(request.getETicketCardNumber()));

        // In a real implementation, this would:
        // 1. Debit customer's bank account
        // 2. Send top-up request to e-ticket operator (悠遊卡公司, 一卡通公司, etc.)
        // 3. Update card balance in the e-ticket system
        // 4. Return new balance in response

        // TODO: Integrate with e-ticket operators

        // Simulate new balance (in real system, this would come from e-ticket operator)
        BigDecimal currentBalance = new BigDecimal("500"); // Simulated current balance
        BigDecimal newBalance = currentBalance.add(request.getAmount());

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
                .eTicketCardNumber(request.getETicketCardNumber())
                .eTicketBalance(newBalance)
                .build();
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        ETicketType eTicketType = ETicketType.fromCode(request.getETicketType());
        if (response.isApproved()) {
            log.info("[{}] E-ticket top-up approved: {} ({}) - Amount: {} TWD, NewBalance: {} TWD, Auth: {}, CardNumber: {}",
                    request.getTransactionId(),
                    eTicketType != null ? eTicketType.getChineseDescription() : request.getETicketType(),
                    eTicketType != null ? eTicketType.getDescription() : "Unknown",
                    response.getAmount(),
                    response.getETicketBalance(),
                    response.getAuthorizationCode(),
                    maskCardNumber(request.getETicketCardNumber()));

            // Here we would typically:
            // 1. Log the successful top-up
            // 2. Send confirmation to e-ticket operator
            // 3. Print receipt if at ATM
            // 4. Send notification to customer
        } else {
            log.warn("[{}] E-ticket top-up declined: {} - {} ({})",
                    request.getTransactionId(),
                    response.getResponseCode(),
                    eTicketType != null ? eTicketType.getChineseDescription() : request.getETicketType(),
                    maskCardNumber(request.getETicketCardNumber()));
        }
    }

    /**
     * Masks e-ticket card number for logging.
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return "****";
        }
        return cardNumber.substring(0, 4) + "****" + cardNumber.substring(cardNumber.length() - 4);
    }
}
