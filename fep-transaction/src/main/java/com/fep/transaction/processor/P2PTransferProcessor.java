package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Processor for P2P (Person-to-Person) transfer transactions.
 * Supports mobile number, email, or account-based transfers.
 */
public class P2PTransferProcessor extends AbstractTransactionProcessor {

    /** Maximum P2P transfer amount (TWD 50,000) */
    private static final BigDecimal MAX_P2P_AMOUNT = new BigDecimal("50000");

    /** Minimum P2P transfer amount (TWD 1) */
    private static final BigDecimal MIN_P2P_AMOUNT = BigDecimal.ONE;

    /** Taiwan mobile phone pattern (09xx-xxx-xxx) */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^09\\d{8}$");

    /** Email pattern for P2P transfer */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    /** Supported bank codes for P2P transfers */
    private static final Set<String> SUPPORTED_BANKS = Set.of(
            "004", "005", "006", "007", "008", "009",
            "011", "012", "013", "017", "021", "050",
            "700", "803", "805", "806", "807", "808",
            "812", "816", "822"
    );

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.P2P_TRANSFER;
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        log.debug("[{}] Validating P2P transfer request", request.getTransactionId());

        // Validate amount
        BigDecimal amount = request.getAmount();
        if (amount == null) {
            throw TransactionException.invalidAmount();
        }

        if (amount.compareTo(MIN_P2P_AMOUNT) < 0) {
            throw TransactionException.invalidAmount();
        }

        if (amount.compareTo(MAX_P2P_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("P2P transfer maximum is TWD 50,000");
        }

        // Validate source account
        if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
            throw TransactionException.invalidRequest("Source account required");
        }

        // Validate destination
        validateDestination(request);

        // Validate bank codes if interbank
        if (!isSameBank(request)) {
            validateInterbankTransfer(request);
        }

        log.debug("[{}] P2P transfer validation passed", request.getTransactionId());
    }

    /**
     * Validates the destination identifier (account, mobile, or email).
     */
    private void validateDestination(TransactionRequest request) {
        String toAccount = request.getDestinationAccount();
        String additionalData = request.getAdditionalData();

        // Parse additional data for mobile/email
        String toMobile = parseAdditionalField(additionalData, "beneficiaryMobile");
        String toEmail = parseAdditionalField(additionalData, "beneficiaryEmail");

        int identifierCount = 0;
        if (toAccount != null && !toAccount.isBlank()) identifierCount++;
        if (toMobile != null && !toMobile.isBlank()) identifierCount++;
        if (toEmail != null && !toEmail.isBlank()) identifierCount++;

        if (identifierCount == 0) {
            throw TransactionException.invalidRequest("Beneficiary account, mobile, or email required");
        }

        if (identifierCount > 1) {
            throw TransactionException.invalidRequest("Only one beneficiary identifier allowed");
        }

        // Validate mobile format
        if (toMobile != null && !toMobile.isBlank()) {
            if (!MOBILE_PATTERN.matcher(toMobile).matches()) {
                throw TransactionException.invalidRequest("Invalid mobile number format");
            }
        }

        // Validate email format
        if (toEmail != null && !toEmail.isBlank()) {
            if (!EMAIL_PATTERN.matcher(toEmail).matches()) {
                throw TransactionException.invalidRequest("Invalid email format");
            }
        }
    }

    /**
     * Parses a field from additional data string (format: key1=value1;key2=value2).
     */
    private String parseAdditionalField(String additionalData, String key) {
        if (additionalData == null || additionalData.isBlank()) {
            return null;
        }
        String[] pairs = additionalData.split(";");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }

    /**
     * Checks if this is a same-bank transfer.
     */
    private boolean isSameBank(TransactionRequest request) {
        String sourceBank = request.getAcquiringBankCode();
        String destBank = request.getDestinationBankCode();

        if (sourceBank == null || destBank == null) {
            return true;
        }

        return sourceBank.equals(destBank);
    }

    /**
     * Validates interbank transfer requirements.
     */
    private void validateInterbankTransfer(TransactionRequest request) {
        String destBank = request.getDestinationBankCode();

        if (destBank != null && !SUPPORTED_BANKS.contains(destBank)) {
            throw TransactionException.invalidRequest(
                    "Unsupported destination bank for P2P transfer: " + destBank);
        }
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        String txnId = request.getTransactionId();
        log.info("[{}] Processing P2P transfer: {} -> {}, amount={}",
                txnId,
                maskAccount(request.getSourceAccount()),
                getDestinationDescription(request),
                request.getAmount());

        // In a real implementation, this would:
        // 1. Call P2P service to resolve mobile/email to account
        // 2. Validate beneficiary account exists
        // 3. Route to FISC for interbank or CBS for intrabank
        // 4. Execute the debit/credit

        // Simulate successful processing
        String authCode = generateAuthorizationCode();

        TransactionResponse response = TransactionResponse.builder()
                .transactionId(txnId)
                .responseCode(ResponseCode.APPROVED.getCode())
                .responseCodeEnum(ResponseCode.APPROVED)
                .responseDescription(ResponseCode.APPROVED.getDescription())
                .responseDescriptionChinese(ResponseCode.APPROVED.getChineseDescription())
                .approved(true)
                .authorizationCode(authCode)
                .build();

        log.info("[{}] P2P transfer approved: authCode={}", txnId, authCode);

        return response;
    }

    /**
     * Gets a description of the destination for logging.
     */
    private String getDestinationDescription(TransactionRequest request) {
        String additionalData = request.getAdditionalData();
        String toMobile = parseAdditionalField(additionalData, "beneficiaryMobile");
        if (toMobile != null) {
            return "Mobile:" + maskMobile(toMobile);
        }

        String toEmail = parseAdditionalField(additionalData, "beneficiaryEmail");
        if (toEmail != null) {
            return "Email:" + maskEmail(toEmail);
        }

        return maskAccount(request.getDestinationAccount());
    }

    /**
     * Masks a mobile number for logging.
     */
    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return "****";
        }
        return mobile.substring(0, 4) + "****" + mobile.substring(mobile.length() - 2);
    }

    /**
     * Masks an email for logging.
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return "**" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }

    /**
     * Masks an account number for logging.
     */
    private String maskAccount(String account) {
        if (account == null || account.length() < 4) {
            return "****";
        }
        return "****" + account.substring(account.length() - 4);
    }
}
