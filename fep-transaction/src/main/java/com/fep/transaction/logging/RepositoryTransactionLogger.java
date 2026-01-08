package com.fep.transaction.logging;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.repository.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * TransactionLogger implementation that persists logs to the repository.
 */
public class RepositoryTransactionLogger implements TransactionLogger {

    private static final Logger log = LoggerFactory.getLogger(RepositoryTransactionLogger.class);

    private final TransactionRepository repository;

    public RepositoryTransactionLogger(TransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void logRequest(TransactionRequest request) {
        log.debug("[{}] Logging request", request.getTransactionId());

        TransactionRecord record = TransactionRecord.builder()
                .transactionId(request.getTransactionId())
                .transactionType(request.getTransactionType())
                .processingCode(request.getProcessingCode())
                .maskedPan(maskPan(request.getPan()))
                .pan(request.getPan())
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode())
                .sourceAccount(request.getSourceAccount())
                .destinationAccount(request.getDestinationAccount())
                .destinationBankCode(request.getDestinationBankCode())
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

        repository.save(record);
    }

    @Override
    public void logResponse(TransactionRequest request, TransactionResponse response) {
        log.debug("[{}] Logging response: {}", request.getTransactionId(), response.getResponseCode());

        TransactionStatus status = response.isApproved() ?
                TransactionStatus.APPROVED : TransactionStatus.DECLINED;

        repository.updateResponse(
                request.getTransactionId(),
                response.getResponseCode(),
                response.getAuthorizationCode(),
                status
        );
    }

    @Override
    public void logError(TransactionRequest request, Throwable error) {
        log.debug("[{}] Logging error: {}", request.getTransactionId(), error.getMessage());

        repository.updateResponse(
                request.getTransactionId(),
                "96", // System malfunction
                null,
                TransactionStatus.FAILED
        );
    }

    @Override
    public TransactionLog createLog(TransactionRequest request, TransactionResponse response) {
        return TransactionLog.builder()
                .transactionId(request.getTransactionId())
                .transactionType(request.getTransactionType())
                .maskedPan(maskPan(request.getPan()))
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode())
                .terminalId(request.getTerminalId())
                .acquiringBankCode(request.getAcquiringBankCode())
                .stan(request.getStan())
                .rrn(request.getRrn())
                .channel(request.getChannel())
                .responseCode(response != null ? response.getResponseCode() : null)
                .authorizationCode(response != null ? response.getAuthorizationCode() : null)
                .approved(response != null && response.isApproved())
                .requestTime(LocalDateTime.now())
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
