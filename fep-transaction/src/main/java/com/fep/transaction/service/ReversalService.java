package com.fep.transaction.service;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import com.fep.transaction.query.ReversalEligibility;
import com.fep.transaction.query.TransactionQueryService;
import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.repository.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service for processing reversal transactions (沖正).
 * Integrates with TransactionQueryService for eligibility checking
 * and TransactionRepository for state management.
 */
public class ReversalService {

    private static final Logger log = LoggerFactory.getLogger(ReversalService.class);

    private final TransactionQueryService queryService;
    private final TransactionRepository repository;

    public ReversalService(TransactionQueryService queryService, TransactionRepository repository) {
        this.queryService = queryService;
        this.repository = repository;
    }

    /**
     * Processes a reversal request with full eligibility checking.
     *
     * @param request the reversal request
     * @return the reversal response
     */
    public TransactionResponse processReversal(TransactionRequest request) {
        String transactionId = request.getTransactionId();
        String originalTransactionId = request.getOriginalTransactionId();

        log.info("[{}] Processing reversal for original transaction: {}",
                transactionId, originalTransactionId);

        try {
            // Step 1: Validate request
            validateReversalRequest(request);

            // Step 2: Check eligibility
            ReversalEligibility eligibility = checkEligibility(originalTransactionId);
            if (!eligibility.isEligible()) {
                return createDeclinedResponse(request, eligibility);
            }

            // Step 3: Validate amounts match
            TransactionRecord originalRecord = eligibility.getOriginalTransaction();
            validateAmountsMatch(request, originalRecord);

            // Step 4: Create reversal record
            TransactionRecord reversalRecord = createReversalRecord(request, originalRecord);
            repository.save(reversalRecord);

            // Step 5: Process reversal (call core banking / FISC)
            TransactionResponse response = executeReversal(request, originalRecord);

            // Step 6: Update status based on result
            if (response.isApproved()) {
                markTransactionAsReversed(originalTransactionId, transactionId);
                repository.updateResponse(transactionId, response.getResponseCode(),
                        response.getAuthorizationCode(), TransactionStatus.APPROVED);
            } else {
                repository.updateResponse(transactionId, response.getResponseCode(),
                        null, TransactionStatus.DECLINED);
            }

            log.info("[{}] Reversal completed with response: {}",
                    transactionId, response.getResponseCode());

            return response;

        } catch (TransactionException e) {
            log.error("[{}] Reversal failed: {}", transactionId, e.getMessage());
            return createErrorResponse(request, e);
        }
    }

    /**
     * Checks if a transaction can be reversed.
     *
     * @param transactionId the original transaction ID
     * @return the eligibility result
     */
    public ReversalEligibility checkEligibility(String transactionId) {
        return queryService.checkReversalEligibility(transactionId);
    }

    /**
     * Validates the reversal request fields.
     */
    private void validateReversalRequest(TransactionRequest request) {
        if (request.getOriginalTransactionId() == null || request.getOriginalTransactionId().isBlank()) {
            throw TransactionException.invalidRequest("Original transaction ID is required for reversal");
        }
        if (request.getRrn() == null || request.getRrn().isBlank()) {
            throw TransactionException.invalidRequest("Original RRN is required for reversal");
        }
        if (request.getStan() == null || request.getStan().isBlank()) {
            throw TransactionException.invalidRequest("Original STAN is required for reversal");
        }
        if (request.getTerminalId() == null || request.getTerminalId().isBlank()) {
            throw TransactionException.invalidRequest("Terminal ID is required for reversal");
        }
        if (request.getAmount() == null) {
            throw TransactionException.invalidRequest("Amount is required for reversal");
        }
    }

    /**
     * Validates that reversal amount matches original transaction.
     */
    private void validateAmountsMatch(TransactionRequest request, TransactionRecord originalRecord) {
        if (originalRecord.getAmount() != null) {
            BigDecimal originalAmount = originalRecord.getAmount();
            BigDecimal reversalAmount = request.getAmount();

            if (reversalAmount.compareTo(originalAmount) != 0) {
                throw TransactionException.invalidRequest(
                        String.format("Reversal amount %s does not match original amount %s",
                                reversalAmount, originalAmount));
            }
        }
    }

    /**
     * Creates a reversal transaction record.
     */
    private TransactionRecord createReversalRecord(TransactionRequest request, TransactionRecord originalRecord) {
        return TransactionRecord.builder()
                .transactionId(request.getTransactionId())
                .transactionType(TransactionType.REVERSAL)
                .processingCode(request.getProcessingCode())
                .maskedPan(maskPan(request.getPan()))
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode())
                .sourceAccount(originalRecord.getSourceAccount())
                .destinationAccount(originalRecord.getDestinationAccount())
                .destinationBankCode(originalRecord.getDestinationBankCode())
                .terminalId(request.getTerminalId())
                .merchantId(request.getMerchantId())
                .acquiringBankCode(request.getAcquiringBankCode())
                .stan(request.getStan())
                .rrn(request.getRrn())
                .channel(request.getChannel())
                .status(TransactionStatus.PROCESSING)
                .originalTransactionId(request.getOriginalTransactionId())
                .requestTime(LocalDateTime.now())
                .transactionTime(LocalDateTime.now())
                .transactionDate(LocalDateTime.now().toLocalDate().toString())
                .build();
    }

    /**
     * Executes the reversal against core banking / FISC.
     * In production, this would send the reversal message.
     */
    private TransactionResponse executeReversal(TransactionRequest request, TransactionRecord originalRecord) {
        log.info("[{}] Executing reversal for {} TWD, original type: {}",
                request.getTransactionId(),
                request.getAmount(),
                originalRecord.getTransactionType());

        // In production implementation:
        // 1. Build ISO 8583 0400 message
        // 2. Send to FISC or core banking
        // 3. Wait for response
        // 4. Parse and return result

        // For now, simulate successful reversal
        String authCode = String.format("%06d", (int) (Math.random() * 1000000));

        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(ResponseCode.APPROVED.getCode())
                .responseCodeEnum(ResponseCode.APPROVED)
                .responseDescription(ResponseCode.APPROVED.getDescription())
                .responseDescriptionChinese(ResponseCode.APPROVED.getChineseDescription())
                .approved(true)
                .authorizationCode(authCode)
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode())
                .rrn(request.getRrn())
                .stan(request.getStan())
                .additionalData("Reversed: " + request.getOriginalTransactionId() +
                        ", Original Type: " + originalRecord.getTransactionType())
                .build();
    }

    /**
     * Marks the original transaction as reversed.
     */
    private void markTransactionAsReversed(String originalTransactionId, String reversalTransactionId) {
        boolean marked = repository.markAsReversed(originalTransactionId, reversalTransactionId);
        if (marked) {
            log.info("Marked transaction {} as reversed by {}", originalTransactionId, reversalTransactionId);
        } else {
            log.warn("Failed to mark transaction {} as reversed", originalTransactionId);
        }
    }

    /**
     * Creates a declined response based on eligibility.
     */
    private TransactionResponse createDeclinedResponse(TransactionRequest request, ReversalEligibility eligibility) {
        ResponseCode code = mapReasonToResponseCode(eligibility.getReasonCode());

        log.warn("[{}] Reversal declined: {}", request.getTransactionId(), eligibility.getReason());

        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(code.getCode())
                .responseCodeEnum(code)
                .responseDescription(eligibility.getReason())
                .responseDescriptionChinese(code.getChineseDescription())
                .approved(false)
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode())
                .rrn(request.getRrn())
                .stan(request.getStan())
                .build();
    }

    /**
     * Maps eligibility reason to response code.
     */
    private ResponseCode mapReasonToResponseCode(ReversalEligibility.ReversalIneligibleReason reason) {
        if (reason == null) {
            return ResponseCode.SYSTEM_MALFUNCTION;
        }
        return switch (reason) {
            case NOT_FOUND -> ResponseCode.INVALID_TRANSACTION;
            case ALREADY_REVERSED -> ResponseCode.DUPLICATE_TRANSACTION;
            case INVALID_STATUS -> ResponseCode.TRANSACTION_NOT_PERMITTED;
            case TIME_WINDOW_EXPIRED -> ResponseCode.RESPONSE_RECEIVED_TOO_LATE;
            case ALREADY_SETTLED -> ResponseCode.TRANSACTION_NOT_PERMITTED;
        };
    }

    /**
     * Creates an error response.
     */
    private TransactionResponse createErrorResponse(TransactionRequest request, TransactionException e) {
        ResponseCode responseCode = ResponseCode.fromCode(e.getResponseCode());
        if (responseCode == null) {
            responseCode = ResponseCode.SYSTEM_MALFUNCTION;
        }

        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(e.getResponseCode())
                .responseCodeEnum(responseCode)
                .responseDescription(e.getMessage())
                .responseDescriptionChinese(responseCode.getChineseDescription())
                .approved(false)
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode())
                .rrn(request.getRrn())
                .stan(request.getStan())
                .build();
    }

    /**
     * Masks a PAN for storage.
     */
    private String maskPan(String pan) {
        if (pan == null || pan.length() < 13) {
            return pan;
        }
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }
}
