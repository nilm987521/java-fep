package com.fep.transaction.logging;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

/**
 * Default implementation of TransactionLogger.
 * Logs to SLF4J and creates TransactionLog entries.
 */
public class DefaultTransactionLogger implements TransactionLogger {

    private static final Logger log = LoggerFactory.getLogger("TRANSACTION_LOG");
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT_LOG");

    @Override
    public void logRequest(TransactionRequest request) {
        log.info("[REQUEST] {} | Type: {} | PAN: {} | Amount: {} {} | Account: {} | Terminal: {} | Channel: {}",
                request.getTransactionId(),
                request.getTransactionType(),
                request.getMaskedPan(),
                request.getAmount(),
                request.getCurrencyCode(),
                maskAccount(request.getSourceAccount()),
                request.getTerminalId(),
                request.getChannel());

        // Audit log for compliance
        auditLog.info("TXN_REQUEST|{}|{}|{}|{}|{}|{}|{}",
                request.getTransactionId(),
                request.getTransactionType(),
                request.getMaskedPan(),
                request.getAmount(),
                request.getTerminalId(),
                request.getAcquiringBankCode(),
                request.getRequestTime());
    }

    @Override
    public void logResponse(TransactionRequest request, TransactionResponse response) {
        if (response.isApproved()) {
            log.info("[RESPONSE] {} | APPROVED | Code: {} | Auth: {} | Time: {}ms",
                    response.getTransactionId(),
                    response.getResponseCode(),
                    response.getAuthorizationCode(),
                    response.getProcessingTimeMs());
        } else {
            log.warn("[RESPONSE] {} | DECLINED | Code: {} ({}) | Time: {}ms | Error: {}",
                    response.getTransactionId(),
                    response.getResponseCode(),
                    response.getResponseDescriptionChinese(),
                    response.getProcessingTimeMs(),
                    response.getErrorDetails());
        }

        // Audit log for compliance
        auditLog.info("TXN_RESPONSE|{}|{}|{}|{}|{}|{}",
                response.getTransactionId(),
                response.getResponseCode(),
                response.isApproved() ? "APPROVED" : "DECLINED",
                response.getAuthorizationCode(),
                response.getProcessingTimeMs(),
                response.getResponseTime());
    }

    @Override
    public void logError(TransactionRequest request, Throwable error) {
        log.error("[ERROR] {} | Type: {} | Error: {} | Message: {}",
                request.getTransactionId(),
                request.getTransactionType(),
                error.getClass().getSimpleName(),
                error.getMessage());

        // Audit log for compliance
        auditLog.error("TXN_ERROR|{}|{}|{}|{}",
                request.getTransactionId(),
                request.getTransactionType(),
                error.getClass().getName(),
                error.getMessage());
    }

    @Override
    public TransactionLog createLog(TransactionRequest request, TransactionResponse response) {
        return TransactionLog.builder()
                .logId(generateLogId())
                .transactionId(request.getTransactionId())
                .transactionType(request.getTransactionType())
                .processingCode(request.getProcessingCode())
                .maskedPan(request.getMaskedPan())
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode())
                .sourceAccount(maskAccount(request.getSourceAccount()))
                .destinationAccount(maskAccount(request.getDestinationAccount()))
                .destinationBankCode(request.getDestinationBankCode())
                .terminalId(request.getTerminalId())
                .merchantId(request.getMerchantId())
                .acquiringBankCode(request.getAcquiringBankCode())
                .stan(request.getStan())
                .rrn(request.getRrn())
                .channel(request.getChannel())
                .responseCode(response.getResponseCode())
                .responseDescription(response.getResponseDescription())
                .approved(response.isApproved())
                .authorizationCode(response.getAuthorizationCode())
                .requestTime(request.getRequestTime())
                .responseTime(response.getResponseTime())
                .processingTimeMs(response.getProcessingTimeMs())
                .errorDetails(response.getErrorDetails())
                .hostReferenceNumber(response.getHostReferenceNumber())
                .originalTransactionId(request.getOriginalTransactionId())
                .build();
    }

    /**
     * Generates a unique log ID.
     */
    private String generateLogId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    /**
     * Masks an account number for logging.
     */
    private String maskAccount(String account) {
        if (account == null || account.length() < 8) {
            return account;
        }
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }
}
