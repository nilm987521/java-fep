package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Processor for bill payment transactions (代收代付/繳費).
 * Handles various bill payment types including utilities, taxes, insurance, etc.
 */
public class BillPaymentProcessor extends AbstractTransactionProcessor {

    /** Maximum single bill payment amount (default 10 million TWD) */
    private static final BigDecimal MAX_BILL_PAYMENT_AMOUNT = new BigDecimal("10000000");

    /** Minimum payment amount */
    private static final BigDecimal MIN_PAYMENT_AMOUNT = new BigDecimal("1");

    /** Supported bill types */
    public enum BillType {
        /** 水費 */
        WATER("01", "Water Bill", "水費"),
        /** 電費 */
        ELECTRICITY("02", "Electricity Bill", "電費"),
        /** 瓦斯費 */
        GAS("03", "Gas Bill", "瓦斯費"),
        /** 電信費 */
        TELECOM("04", "Telecom Bill", "電信費"),
        /** 信用卡費 */
        CREDIT_CARD("05", "Credit Card Bill", "信用卡費"),
        /** 貸款繳款 */
        LOAN("06", "Loan Payment", "貸款繳款"),
        /** 保險費 */
        INSURANCE("07", "Insurance Premium", "保險費"),
        /** 牌照稅 */
        VEHICLE_TAX("11", "Vehicle License Tax", "牌照稅"),
        /** 房屋稅 */
        HOUSE_TAX("12", "House Tax", "房屋稅"),
        /** 地價稅 */
        LAND_TAX("13", "Land Value Tax", "地價稅"),
        /** 所得稅 */
        INCOME_TAX("14", "Income Tax", "所得稅"),
        /** 營業稅 */
        BUSINESS_TAX("15", "Business Tax", "營業稅"),
        /** 學費 */
        TUITION("21", "Tuition Fee", "學費"),
        /** 停車費 */
        PARKING("31", "Parking Fee", "停車費"),
        /** 交通罰鍰 */
        TRAFFIC_FINE("32", "Traffic Fine", "交通罰鍰"),
        /** 健保費 */
        HEALTH_INSURANCE("41", "Health Insurance", "健保費"),
        /** 勞保費 */
        LABOR_INSURANCE("42", "Labor Insurance", "勞保費"),
        /** 其他 */
        OTHER("99", "Other Bill", "其他");

        private final String code;
        private final String description;
        private final String chineseDescription;

        BillType(String code, String description, String chineseDescription) {
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

        public static BillType fromCode(String code) {
            for (BillType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** Tax payment codes that require special validation */
    private static final Set<String> TAX_BILL_CODES = Set.of("11", "12", "13", "14", "15");

    /** Utility payment codes */
    private static final Set<String> UTILITY_BILL_CODES = Set.of("01", "02", "03", "04");

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.BILL_PAYMENT;
    }

    /**
     * Overrides base validation to skip PAN validation.
     * Bill payments don't require a card number.
     */
    @Override
    public void validate(TransactionRequest request) {
        // Only validate transaction ID (PAN not required for bill payment)
        validateTransactionId(request);

        // Bill payment specific validations
        doValidate(request);
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate source account
        validateSourceAccount(request);

        // Validate bill payment number
        validateBillPaymentNumber(request);

        // Validate bill type
        validateBillType(request);

        // Validate payment amount
        validatePaymentAmount(request);
    }

    /**
     * Validates the source account.
     */
    private void validateSourceAccount(TransactionRequest request) {
        if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
            throw TransactionException.invalidRequest("Source account is required for bill payment");
        }
    }

    /**
     * Validates the bill payment number (繳費編號/銷帳編號).
     */
    private void validateBillPaymentNumber(TransactionRequest request) {
        String paymentNumber = request.getBillPaymentNumber();
        if (paymentNumber == null || paymentNumber.isBlank()) {
            throw TransactionException.invalidRequest("Bill payment number is required");
        }

        // Payment number should be 14-20 digits for most bills
        if (paymentNumber.length() < 14 || paymentNumber.length() > 20) {
            throw TransactionException.invalidRequest("Invalid bill payment number length");
        }

        // Should be numeric
        if (!paymentNumber.matches("\\d+")) {
            throw TransactionException.invalidRequest("Bill payment number must be numeric");
        }
    }

    /**
     * Validates the bill type code.
     */
    private void validateBillType(TransactionRequest request) {
        String billTypeCode = request.getBillTypeCode();
        if (billTypeCode == null || billTypeCode.isBlank()) {
            throw TransactionException.invalidRequest("Bill type code is required");
        }

        BillType billType = BillType.fromCode(billTypeCode);
        if (billType == null) {
            throw TransactionException.invalidRequest("Invalid bill type code: " + billTypeCode);
        }

        // Additional validation for tax payments
        if (TAX_BILL_CODES.contains(billTypeCode)) {
            validateTaxPayment(request);
        }
    }

    /**
     * Validates tax payment specific requirements.
     */
    private void validateTaxPayment(TransactionRequest request) {
        // Tax payments may require tax ID verification
        String taxId = request.getTaxId();
        if (taxId != null && !taxId.isBlank()) {
            // Validate Taiwan tax ID format:
            // - Individual (ROC ID): 1 letter + 9 digits (e.g., A123456789)
            // - Business (統一編號): 8 digits (e.g., 12345678)
            // - Resident Certificate: 10 digits (e.g., 1234567890)
            if (!taxId.matches("[A-Z][12][0-9]{8}|\\d{8}|\\d{10}")) {
                throw TransactionException.invalidRequest("Invalid tax ID format");
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
            throw TransactionException.invalidRequest("Payment amount must be at least " + MIN_PAYMENT_AMOUNT);
        }

        if (amount.compareTo(MAX_BILL_PAYMENT_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("bill payment");
        }
    }

    @Override
    protected void preProcess(TransactionRequest request) {
        BillType billType = BillType.fromCode(request.getBillTypeCode());
        log.debug("[{}] Pre-processing bill payment: type={}, paymentNumber={}, amount={}",
                request.getTransactionId(),
                billType != null ? billType.getChineseDescription() : request.getBillTypeCode(),
                maskPaymentNumber(request.getBillPaymentNumber()),
                request.getAmount());

        // Here we would typically:
        // 1. Query bill details from the payee system
        // 2. Verify the payment amount matches the bill
        // 3. Check if the bill is already paid
        // 4. Validate payment deadline
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        BillType billType = BillType.fromCode(request.getBillTypeCode());
        log.info("[{}] Processing bill payment: {} ({}) - Amount: {} TWD, PaymentNumber: {}",
                request.getTransactionId(),
                billType != null ? billType.getChineseDescription() : request.getBillTypeCode(),
                billType != null ? billType.getDescription() : "Unknown",
                request.getAmount(),
                maskPaymentNumber(request.getBillPaymentNumber()));

        // In a real implementation, this would:
        // 1. Debit customer's account
        // 2. Send payment notification to the payee institution
        // 3. Update bill status in the payee's system
        // 4. Generate payment receipt

        // TODO: Integrate with core banking and payee institutions

        // Generate payment receipt number
        String receiptNumber = "RCP" + System.currentTimeMillis() +
                String.format("%04d", (int) (Math.random() * 10000));

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
                .billPaymentNumber(request.getBillPaymentNumber())
                .billTypeCode(request.getBillTypeCode())
                .paymentReceiptNumber(receiptNumber)
                .build();
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        BillType billType = BillType.fromCode(request.getBillTypeCode());
        if (response.isApproved()) {
            log.info("[{}] Bill payment approved: {} ({}) - Amount: {} TWD, Auth: {}, PaymentNumber: {}",
                    request.getTransactionId(),
                    billType != null ? billType.getChineseDescription() : request.getBillTypeCode(),
                    billType != null ? billType.getDescription() : "Unknown",
                    response.getAmount(),
                    response.getAuthorizationCode(),
                    maskPaymentNumber(request.getBillPaymentNumber()));

            // Here we would typically:
            // 1. Send confirmation to payee institution
            // 2. Update transaction log
            // 3. Generate receipt for customer
            // 4. Queue for batch settlement
        } else {
            log.warn("[{}] Bill payment declined: {} - {} ({})",
                    request.getTransactionId(),
                    response.getResponseCode(),
                    billType != null ? billType.getChineseDescription() : request.getBillTypeCode(),
                    maskPaymentNumber(request.getBillPaymentNumber()));
        }
    }

    /**
     * Masks bill payment number for logging.
     */
    private String maskPaymentNumber(String paymentNumber) {
        if (paymentNumber == null || paymentNumber.length() < 8) {
            return "****";
        }
        return paymentNumber.substring(0, 4) + "****" + paymentNumber.substring(paymentNumber.length() - 4);
    }
}
