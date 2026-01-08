package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.exception.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Abstract base class for transaction processors.
 * Provides common validation and processing logic.
 */
public abstract class AbstractTransactionProcessor implements TransactionProcessor {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** Minimum transaction amount */
    protected static final BigDecimal MIN_AMOUNT = BigDecimal.ONE;

    /** Maximum transaction amount (default 3 million TWD) */
    protected static final BigDecimal MAX_AMOUNT = new BigDecimal("3000000");

    @Override
    public TransactionResponse process(TransactionRequest request) {
        long startTime = System.currentTimeMillis();
        String txnId = request.getTransactionId();

        log.info("[{}] Starting {} processing for PAN: {}",
                txnId, getProcessorName(), request.getMaskedPan());

        try {
            // Step 1: Validate request
            validate(request);

            // Step 2: Pre-process (template method)
            preProcess(request);

            // Step 3: Execute the actual transaction
            TransactionResponse response = doProcess(request);

            // Step 4: Post-process (template method)
            postProcess(request, response);

            long elapsed = System.currentTimeMillis() - startTime;
            response.setProcessingTimeMs(elapsed);

            log.info("[{}] Transaction completed: {} in {}ms",
                    txnId, response.getResponseCode(), elapsed);

            return response;

        } catch (TransactionException e) {
            log.warn("[{}] Transaction failed: {} - {}",
                    txnId, e.getResponseCode(), e.getMessage());

            return createErrorResponse(request, e);

        } catch (Exception e) {
            log.error("[{}] Unexpected error during processing: {}",
                    txnId, e.getMessage(), e);

            return createSystemErrorResponse(request, e);
        }
    }

    @Override
    public void validate(TransactionRequest request) {
        // Common validations
        validateTransactionId(request);
        validatePan(request);
        validateAmount(request);

        // Transaction-specific validations (template method)
        doValidate(request);
    }

    /**
     * Validates the transaction ID.
     */
    protected void validateTransactionId(TransactionRequest request) {
        if (request.getTransactionId() == null || request.getTransactionId().isBlank()) {
            throw TransactionException.invalidRequest("Transaction ID is required");
        }
    }

    /**
     * Validates the PAN.
     */
    protected void validatePan(TransactionRequest request) {
        String pan = request.getPan();
        if (pan == null || pan.isBlank()) {
            throw TransactionException.invalidRequest("PAN is required");
        }
        if (pan.length() < 13 || pan.length() > 19) {
            throw TransactionException.invalidCard("Invalid PAN length");
        }
        if (!pan.matches("\\d+")) {
            throw TransactionException.invalidCard("PAN must contain only digits");
        }
    }

    /**
     * Validates the transaction amount.
     */
    protected void validateAmount(TransactionRequest request) {
        BigDecimal amount = request.getAmount();
        if (amount == null) {
            throw TransactionException.invalidAmount();
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw TransactionException.invalidAmount();
        }
        if (amount.compareTo(MIN_AMOUNT) < 0) {
            throw TransactionException.invalidAmount();
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("transaction");
        }
    }

    /**
     * Validates the PIN block if required.
     */
    protected void validatePinBlock(TransactionRequest request) {
        if (request.getTransactionType() != null &&
            request.getTransactionType().requiresPin()) {
            if (request.getPinBlock() == null || request.getPinBlock().isBlank()) {
                throw TransactionException.invalidRequest("PIN block is required");
            }
        }
    }

    /**
     * Validates the expiration date.
     */
    protected void validateExpirationDate(TransactionRequest request) {
        String expDate = request.getExpirationDate();
        if (expDate == null || expDate.length() != 4) {
            throw TransactionException.invalidCard("Invalid expiration date format");
        }

        try {
            int year = Integer.parseInt(expDate.substring(0, 2));
            int month = Integer.parseInt(expDate.substring(2, 4));

            if (month < 1 || month > 12) {
                throw TransactionException.invalidCard("Invalid expiration month");
            }

            // Check if card is expired (YYMM format)
            LocalDateTime now = LocalDateTime.now();
            int currentYear = now.getYear() % 100;
            int currentMonth = now.getMonthValue();

            if (year < currentYear || (year == currentYear && month < currentMonth)) {
                throw TransactionException.expiredCard();
            }

        } catch (NumberFormatException e) {
            throw TransactionException.invalidCard("Invalid expiration date");
        }
    }

    /**
     * Template method for transaction-specific validation.
     * Override in subclasses for additional validations.
     */
    protected abstract void doValidate(TransactionRequest request);

    /**
     * Template method for pre-processing.
     * Override in subclasses for custom pre-processing logic.
     */
    protected void preProcess(TransactionRequest request) {
        // Default: no pre-processing
    }

    /**
     * Template method for actual transaction processing.
     * Must be implemented by subclasses.
     */
    protected abstract TransactionResponse doProcess(TransactionRequest request);

    /**
     * Template method for post-processing.
     * Override in subclasses for custom post-processing logic.
     */
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        // Default: no post-processing
    }

    /**
     * Creates an error response from a TransactionException.
     */
    protected TransactionResponse createErrorResponse(TransactionRequest request,
                                                      TransactionException e) {
        ResponseCode code = ResponseCode.fromCode(e.getResponseCode());
        if (code == null) {
            code = ResponseCode.ERROR;
        }

        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(e.getResponseCode())
                .responseCodeEnum(code)
                .responseDescription(code.getDescription())
                .responseDescriptionChinese(code.getChineseDescription())
                .approved(false)
                .errorDetails(e.getMessage())
                .build();
    }

    /**
     * Creates a system error response for unexpected exceptions.
     */
    protected TransactionResponse createSystemErrorResponse(TransactionRequest request,
                                                            Exception e) {
        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(ResponseCode.SYSTEM_MALFUNCTION.getCode())
                .responseCodeEnum(ResponseCode.SYSTEM_MALFUNCTION)
                .responseDescription(ResponseCode.SYSTEM_MALFUNCTION.getDescription())
                .responseDescriptionChinese(ResponseCode.SYSTEM_MALFUNCTION.getChineseDescription())
                .approved(false)
                .errorDetails("System error: " + e.getMessage())
                .build();
    }

    /**
     * Generates an authorization code.
     */
    protected String generateAuthorizationCode() {
        // 6-digit numeric authorization code
        return String.format("%06d", (int) (Math.random() * 1000000));
    }
}
