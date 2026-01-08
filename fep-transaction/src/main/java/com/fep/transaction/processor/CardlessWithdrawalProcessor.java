package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Processor for cardless withdrawal transactions (無卡提款).
 * Supports OTP-based and app-based cardless withdrawal methods.
 */
public class CardlessWithdrawalProcessor extends AbstractTransactionProcessor {

    /** Maximum single cardless withdrawal amount (default 30,000 TWD) */
    private static final BigDecimal MAX_WITHDRAWAL_AMOUNT = new BigDecimal("30000");

    /** Minimum withdrawal amount */
    private static final BigDecimal MIN_WITHDRAWAL_AMOUNT = new BigDecimal("100");

    /** Maximum daily cardless withdrawal limit */
    private static final BigDecimal MAX_DAILY_LIMIT = new BigDecimal("100000");

    /** Valid withdrawal amounts (must be multiples of 100) */
    private static final BigDecimal WITHDRAWAL_DENOMINATION = new BigDecimal("100");

    /** Cardless code expiry duration in minutes */
    private static final int CARDLESS_CODE_EXPIRY_MINUTES = 30;

    /**
     * Cardless withdrawal methods.
     */
    public enum CardlessMethod {
        /** OTP via SMS */
        OTP("01", "OTP via SMS", "簡訊OTP"),
        /** Mobile banking app */
        MOBILE_APP("02", "Mobile App", "行動APP"),
        /** QR code scan */
        QR_CODE("03", "QR Code", "QR碼");

        private final String code;
        private final String description;
        private final String chineseDescription;

        CardlessMethod(String code, String description, String chineseDescription) {
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

        public static CardlessMethod fromCode(String code) {
            for (CardlessMethod method : values()) {
                if (method.code.equals(code)) {
                    return method;
                }
            }
            return null;
        }
    }

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.CARDLESS_WITHDRAWAL;
    }

    /**
     * Overrides base validation to skip PAN validation.
     * Cardless withdrawal uses mobile phone/OTP instead of card.
     */
    @Override
    public void validate(TransactionRequest request) {
        // Only validate transaction ID
        validateTransactionId(request);

        // Cardless specific validations
        doValidate(request);
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate source account
        validateSourceAccount(request);

        // Validate mobile phone
        validateMobilePhone(request);

        // Validate cardless code or OTP
        validateCardlessCredentials(request);

        // Validate withdrawal amount
        validateWithdrawalAmount(request);

        // Validate terminal (ATM)
        validateTerminal(request);
    }

    /**
     * Validates the source account.
     */
    private void validateSourceAccount(TransactionRequest request) {
        if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
            throw TransactionException.invalidRequest("Source account is required for cardless withdrawal");
        }
    }

    /**
     * Validates the mobile phone number.
     */
    private void validateMobilePhone(TransactionRequest request) {
        String phone = request.getMobilePhone();
        if (phone == null || phone.isBlank()) {
            throw TransactionException.invalidRequest("Mobile phone number is required");
        }

        // Taiwan mobile phone format: 09XX-XXX-XXX (10 digits)
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        if (cleanPhone.length() != 10 || !cleanPhone.startsWith("09")) {
            throw TransactionException.invalidRequest("Invalid mobile phone number format");
        }
    }

    /**
     * Validates cardless code or OTP.
     */
    private void validateCardlessCredentials(TransactionRequest request) {
        // Either cardless code or OTP is required
        String cardlessCode = request.getCardlessCode();
        String otpCode = request.getOtpCode();

        if ((cardlessCode == null || cardlessCode.isBlank()) &&
            (otpCode == null || otpCode.isBlank())) {
            throw TransactionException.invalidRequest("Cardless code or OTP is required");
        }

        // Validate cardless code format (typically 8-16 characters)
        if (cardlessCode != null && !cardlessCode.isBlank()) {
            if (cardlessCode.length() < 8 || cardlessCode.length() > 16) {
                throw TransactionException.invalidRequest("Invalid cardless code format");
            }
        }

        // Validate OTP format (typically 6 digits)
        if (otpCode != null && !otpCode.isBlank()) {
            if (!otpCode.matches("\\d{6}")) {
                throw TransactionException.invalidRequest("Invalid OTP format (must be 6 digits)");
            }
        }
    }

    /**
     * Validates withdrawal amount.
     */
    private void validateWithdrawalAmount(TransactionRequest request) {
        BigDecimal amount = request.getAmount();

        if (amount == null) {
            throw TransactionException.invalidRequest("Withdrawal amount is required");
        }

        if (amount.compareTo(MIN_WITHDRAWAL_AMOUNT) < 0) {
            throw TransactionException.invalidRequest(
                    "Withdrawal amount must be at least " + MIN_WITHDRAWAL_AMOUNT + " TWD");
        }

        if (amount.compareTo(MAX_WITHDRAWAL_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("cardless withdrawal");
        }

        // Amount must be a multiple of 100
        if (amount.remainder(WITHDRAWAL_DENOMINATION).compareTo(BigDecimal.ZERO) != 0) {
            throw TransactionException.invalidRequest("Withdrawal amount must be a multiple of 100 TWD");
        }
    }

    /**
     * Validates the terminal (ATM).
     */
    private void validateTerminal(TransactionRequest request) {
        if (request.getTerminalId() == null || request.getTerminalId().isBlank()) {
            throw TransactionException.invalidRequest("Terminal ID is required for cardless withdrawal");
        }
    }

    @Override
    protected void preProcess(TransactionRequest request) {
        log.debug("[{}] Pre-processing cardless withdrawal: phone={}, amount={}, terminal={}",
                request.getTransactionId(),
                maskPhone(request.getMobilePhone()),
                request.getAmount(),
                request.getTerminalId());

        // Here we would typically:
        // 1. Verify the cardless code/OTP against the reservation
        // 2. Check if the code has expired
        // 3. Verify the mobile phone matches the account
        // 4. Check account balance and daily limits
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        log.info("[{}] Processing cardless withdrawal: phone={}, amount={} TWD, terminal={}",
                request.getTransactionId(),
                maskPhone(request.getMobilePhone()),
                request.getAmount(),
                request.getTerminalId());

        // In a real implementation, this would:
        // 1. Validate the cardless code/OTP with the reservation system
        // 2. Debit the customer's account
        // 3. Instruct ATM to dispense cash
        // 4. Mark the cardless code as used

        // TODO: Integrate with cardless reservation system and ATM

        String cardlessReference = generateCardlessReference();

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
                .cardlessReference(cardlessReference)
                .build();
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        if (response.isApproved()) {
            log.info("[{}] Cardless withdrawal approved: {} TWD, Auth: {}, Ref: {}, Phone: {}, Terminal: {}",
                    request.getTransactionId(),
                    response.getAmount(),
                    response.getAuthorizationCode(),
                    response.getCardlessReference(),
                    maskPhone(request.getMobilePhone()),
                    request.getTerminalId());

            // Here we would typically:
            // 1. Invalidate the used cardless code
            // 2. Update transaction log
            // 3. Send SMS confirmation to customer
            // 4. Update daily withdrawal limits
        } else {
            log.warn("[{}] Cardless withdrawal declined: {} - Phone: {}",
                    request.getTransactionId(),
                    response.getResponseCode(),
                    maskPhone(request.getMobilePhone()));
        }
    }

    /**
     * Generates a cardless withdrawal reference.
     */
    private String generateCardlessReference() {
        return "CL" + System.currentTimeMillis() + String.format("%04d", (int) (Math.random() * 10000));
    }

    /**
     * Masks phone number for logging.
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) {
            return "****";
        }
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        return cleanPhone.substring(0, 4) + "****" + cleanPhone.substring(cleanPhone.length() - 2);
    }
}
