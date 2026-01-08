package com.fep.transaction.service;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import com.fep.transaction.processor.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of TransactionService.
 * Routes transactions to appropriate processors based on transaction type.
 */
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final Map<TransactionType, TransactionProcessor> processorMap;

    /**
     * Creates a new TransactionServiceImpl with the given processors.
     *
     * @param processors list of transaction processors to register
     */
    public TransactionServiceImpl(List<TransactionProcessor> processors) {
        this.processorMap = new EnumMap<>(TransactionType.class);
        for (TransactionProcessor processor : processors) {
            registerProcessor(processor);
        }
        log.info("TransactionService initialized with {} processors", processorMap.size());
    }

    /**
     * Registers a transaction processor.
     *
     * @param processor the processor to register
     */
    public void registerProcessor(TransactionProcessor processor) {
        TransactionType type = processor.getSupportedType();
        if (processorMap.containsKey(type)) {
            log.warn("Overwriting existing processor for type: {}", type);
        }
        processorMap.put(type, processor);
        log.debug("Registered processor {} for type {}", processor.getProcessorName(), type);
    }

    @Override
    public TransactionResponse process(TransactionRequest request) {
        // Ensure transaction ID is set
        if (request.getTransactionId() == null || request.getTransactionId().isBlank()) {
            request.setTransactionId(generateTransactionId());
        }

        String txnId = request.getTransactionId();
        TransactionType type = request.getTransactionType();

        log.info("[{}] Processing transaction type: {}", txnId, type);

        try {
            // Validate transaction type
            if (type == null) {
                throw TransactionException.invalidRequest("Transaction type is required");
            }

            // Find appropriate processor
            TransactionProcessor processor = processorMap.get(type);
            if (processor == null) {
                log.error("[{}] No processor found for transaction type: {}", txnId, type);
                throw TransactionException.transactionNotPermitted();
            }

            // Process the transaction
            log.debug("[{}] Routing to processor: {}", txnId, processor.getProcessorName());
            return processor.process(request);

        } catch (TransactionException e) {
            log.warn("[{}] Transaction failed: {} - {}", txnId, e.getErrorCode(), e.getMessage());
            return createErrorResponse(txnId, e);

        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", txnId, e.getMessage(), e);
            return createSystemErrorResponse(txnId, e);
        }
    }

    @Override
    public boolean isSupported(TransactionType transactionType) {
        return processorMap.containsKey(transactionType);
    }

    @Override
    public TransactionType[] getSupportedTypes() {
        return processorMap.keySet().toArray(new TransactionType[0]);
    }

    /**
     * Generates a unique transaction ID.
     */
    private String generateTransactionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * Creates an error response from a TransactionException.
     */
    private TransactionResponse createErrorResponse(String txnId, TransactionException e) {
        ResponseCode code = ResponseCode.fromCode(e.getResponseCode());
        if (code == null) {
            code = ResponseCode.ERROR;
        }

        return TransactionResponse.builder()
                .transactionId(txnId)
                .responseCode(e.getResponseCode())
                .responseCodeEnum(code)
                .responseDescription(code.getDescription())
                .responseDescriptionChinese(code.getChineseDescription())
                .approved(false)
                .errorDetails(e.getMessage())
                .build();
    }

    /**
     * Creates a system error response.
     */
    private TransactionResponse createSystemErrorResponse(String txnId, Exception e) {
        return TransactionResponse.builder()
                .transactionId(txnId)
                .responseCode(ResponseCode.SYSTEM_MALFUNCTION.getCode())
                .responseCodeEnum(ResponseCode.SYSTEM_MALFUNCTION)
                .responseDescription(ResponseCode.SYSTEM_MALFUNCTION.getDescription())
                .responseDescriptionChinese(ResponseCode.SYSTEM_MALFUNCTION.getChineseDescription())
                .approved(false)
                .errorDetails("System error: " + e.getMessage())
                .build();
    }
}
