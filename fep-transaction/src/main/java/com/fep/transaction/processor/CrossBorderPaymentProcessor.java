package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Processor for cross-border payment transactions (跨境支付).
 * Handles international remittance and cross-border transfers via SWIFT network.
 */
public class CrossBorderPaymentProcessor extends AbstractTransactionProcessor {

    /** Maximum single cross-border payment (default 500,000 TWD) */
    private static final BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("500000");

    /** Minimum cross-border payment */
    private static final BigDecimal MIN_PAYMENT_AMOUNT = new BigDecimal("100");

    /** Daily transaction limit */
    private static final BigDecimal MAX_DAILY_LIMIT = new BigDecimal("5000000");

    /** Standard cross-border fee rate (0.5%) */
    private static final BigDecimal FEE_RATE = new BigDecimal("0.005");

    /** Minimum fee */
    private static final BigDecimal MIN_FEE = new BigDecimal("300");

    /** Maximum fee */
    private static final BigDecimal MAX_FEE = new BigDecimal("3000");

    /** Supported destination countries (ISO 3166-1 alpha-2) */
    private static final Set<String> SUPPORTED_COUNTRIES = Set.of(
            "US", "JP", "CN", "HK", "SG", "MY", "TH", "VN", "PH", "ID",
            "AU", "NZ", "GB", "DE", "FR", "CA", "KR"
    );

    /** Remittance purpose codes */
    public enum RemittancePurpose {
        FAMILY_SUPPORT("01", "Family Support", "家庭匯款"),
        EDUCATION("02", "Education", "教育費用"),
        MEDICAL("03", "Medical Expenses", "醫療費用"),
        TRADE_PAYMENT("04", "Trade Payment", "貿易款項"),
        INVESTMENT("05", "Investment", "投資"),
        SALARY("06", "Salary", "薪資"),
        TRAVEL("07", "Travel", "旅遊"),
        OTHER("99", "Other", "其他");

        private final String code;
        private final String description;
        private final String chineseDescription;

        RemittancePurpose(String code, String description, String chineseDescription) {
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

        public static RemittancePurpose fromCode(String code) {
            for (RemittancePurpose purpose : values()) {
                if (purpose.code.equals(code)) {
                    return purpose;
                }
            }
            return null;
        }
    }

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.CROSS_BORDER_PAYMENT;
    }

    /**
     * Overrides base validation to skip PAN validation.
     * Cross-border payment uses account number instead of card.
     */
    @Override
    public void validate(TransactionRequest request) {
        validateTransactionId(request);
        doValidate(request);
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate source account
        validateSourceAccount(request);

        // Validate destination information
        validateDestination(request);

        // Validate beneficiary
        validateBeneficiary(request);

        // Validate amount
        validatePaymentAmount(request);

        // Validate remittance purpose
        validateRemittancePurpose(request);
    }

    /**
     * Validates the source account.
     */
    private void validateSourceAccount(TransactionRequest request) {
        if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
            throw TransactionException.invalidRequest("Source account is required for cross-border payment");
        }
    }

    /**
     * Validates destination country and bank information.
     */
    private void validateDestination(TransactionRequest request) {
        // Validate country code
        String countryCode = request.getDestinationCountryCode();
        if (countryCode == null || countryCode.isBlank()) {
            throw TransactionException.invalidRequest("Destination country code is required");
        }

        if (!SUPPORTED_COUNTRIES.contains(countryCode.toUpperCase())) {
            throw TransactionException.invalidRequest("Unsupported destination country: " + countryCode);
        }

        // Validate destination account
        if (request.getDestinationAccount() == null || request.getDestinationAccount().isBlank()) {
            throw TransactionException.invalidRequest("Destination account is required");
        }

        // Validate SWIFT code
        String swiftCode = request.getBeneficiaryBankSwift();
        if (swiftCode == null || swiftCode.isBlank()) {
            throw TransactionException.invalidRequest("Beneficiary bank SWIFT code is required");
        }

        // SWIFT code format: 8 or 11 characters
        if (swiftCode.length() != 8 && swiftCode.length() != 11) {
            throw TransactionException.invalidRequest("Invalid SWIFT code format");
        }

        // Basic SWIFT code validation (letters only for first 6 chars)
        String bankCode = swiftCode.substring(0, 6);
        if (!bankCode.matches("[A-Z]{6}")) {
            throw TransactionException.invalidRequest("Invalid SWIFT code format");
        }
    }

    /**
     * Validates beneficiary information.
     */
    private void validateBeneficiary(TransactionRequest request) {
        String beneficiaryName = request.getBeneficiaryName();
        if (beneficiaryName == null || beneficiaryName.isBlank()) {
            throw TransactionException.invalidRequest("Beneficiary name is required");
        }

        // Name should not contain special characters that could cause SWIFT issues
        if (!beneficiaryName.matches("[A-Za-z0-9\\s\\-\\.,']+")) {
            throw TransactionException.invalidRequest("Beneficiary name contains invalid characters");
        }

        // Name length limit (SWIFT standard)
        if (beneficiaryName.length() > 35) {
            throw TransactionException.invalidRequest("Beneficiary name exceeds maximum length (35 characters)");
        }
    }

    /**
     * Validates payment amount.
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
            throw TransactionException.exceedsLimit("cross-border payment");
        }
    }

    /**
     * Validates remittance purpose code.
     */
    private void validateRemittancePurpose(TransactionRequest request) {
        String purposeCode = request.getRemittancePurposeCode();
        if (purposeCode == null || purposeCode.isBlank()) {
            throw TransactionException.invalidRequest("Remittance purpose code is required");
        }

        if (RemittancePurpose.fromCode(purposeCode) == null) {
            throw TransactionException.invalidRequest("Invalid remittance purpose code: " + purposeCode);
        }
    }

    @Override
    protected void preProcess(TransactionRequest request) {
        log.debug("[{}] Pre-processing cross-border payment: dest={}, amount={}",
                request.getTransactionId(),
                request.getDestinationCountryCode(),
                request.getAmount());

        // Here we would typically:
        // 1. Check AML/CFT compliance
        // 2. Verify sender/beneficiary against sanctions lists
        // 3. Get real-time exchange rate
        // 4. Calculate and reserve fees
        // 5. Verify account balance
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        log.info("[{}] Processing cross-border payment: dest={}, beneficiary={}, amount={} TWD",
                request.getTransactionId(),
                request.getDestinationCountryCode(),
                maskName(request.getBeneficiaryName()),
                request.getAmount());

        // In a real implementation, this would:
        // 1. Debit customer's account
        // 2. Send SWIFT MT103 message
        // 3. Reserve correspondent bank fees
        // 4. Generate tracking reference

        BigDecimal fee = calculateFee(request.getAmount());
        String crossBorderRef = generateCrossBorderReference();
        String swiftRef = generateSwiftReference();

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
                .crossBorderReference(crossBorderRef)
                .swiftReference(swiftRef)
                .crossBorderFee(fee)
                .estimatedArrival(calculateEstimatedArrival(request.getDestinationCountryCode()))
                .build();
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        if (response.isApproved()) {
            log.info("[{}] Cross-border payment approved: {} TWD -> {}, Beneficiary: {}, " +
                            "Fee: {}, SWIFT Ref: {}, Est. Arrival: {}",
                    request.getTransactionId(),
                    response.getAmount(),
                    request.getDestinationCountryCode(),
                    maskName(request.getBeneficiaryName()),
                    response.getCrossBorderFee(),
                    response.getSwiftReference(),
                    response.getEstimatedArrival());

            // Here we would typically:
            // 1. Log to AML reporting system
            // 2. Send confirmation to customer
            // 3. Queue for SWIFT processing
            // 4. Update regulatory reporting
        } else {
            log.warn("[{}] Cross-border payment declined: {}",
                    request.getTransactionId(),
                    response.getResponseCode());
        }
    }

    /**
     * Calculates the cross-border transfer fee.
     */
    private BigDecimal calculateFee(BigDecimal amount) {
        BigDecimal fee = amount.multiply(FEE_RATE);
        if (fee.compareTo(MIN_FEE) < 0) {
            return MIN_FEE;
        }
        if (fee.compareTo(MAX_FEE) > 0) {
            return MAX_FEE;
        }
        return fee.setScale(0, java.math.RoundingMode.CEILING);
    }

    /**
     * Estimates arrival date based on destination country.
     */
    private LocalDateTime calculateEstimatedArrival(String countryCode) {
        // Simplified estimation - in reality would depend on correspondent banks
        int businessDays = switch (countryCode.toUpperCase()) {
            case "HK", "SG", "JP" -> 1;
            case "US", "CA", "AU", "NZ" -> 2;
            case "CN", "KR" -> 2;
            default -> 3;
        };
        return LocalDateTime.now().plusDays(businessDays);
    }

    /**
     * Generates a cross-border payment reference.
     */
    private String generateCrossBorderReference() {
        return "CBP" + System.currentTimeMillis() + String.format("%04d", (int) (Math.random() * 10000));
    }

    /**
     * Generates a SWIFT-style reference number.
     */
    private String generateSwiftReference() {
        return "TW" + String.format("%016d", System.currentTimeMillis());
    }

    /**
     * Masks beneficiary name for logging.
     */
    private String maskName(String name) {
        if (name == null || name.length() < 3) {
            return "***";
        }
        return name.substring(0, 2) + "***";
    }
}
