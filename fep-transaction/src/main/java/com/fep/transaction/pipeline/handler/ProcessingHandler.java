package com.fep.transaction.pipeline.handler;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.exception.TransactionException;
import com.fep.transaction.pipeline.PipelineContext;
import com.fep.transaction.pipeline.PipelineHandler;
import com.fep.transaction.pipeline.PipelineStage;
import com.fep.transaction.processor.TransactionProcessor;
import com.fep.transaction.timeout.TimeoutManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pipeline handler for core transaction processing.
 */
public class ProcessingHandler implements PipelineHandler {

    private static final Logger log = LoggerFactory.getLogger(ProcessingHandler.class);

    private final Map<String, TransactionProcessor> processors;
    private final TimeoutManager timeoutManager;

    public ProcessingHandler(List<TransactionProcessor> processorList) {
        this(processorList, null);
    }

    public ProcessingHandler(List<TransactionProcessor> processorList, TimeoutManager timeoutManager) {
        this.processors = processorList.stream()
                .collect(Collectors.toMap(
                        p -> p.getSupportedType().name(),
                        Function.identity()
                ));
        this.timeoutManager = timeoutManager;
    }

    @Override
    public PipelineStage getStage() {
        return PipelineStage.PROCESSING;
    }

    @Override
    public void handle(PipelineContext context) {
        TransactionRequest request = context.getRequest();
        String txnType = request.getTransactionType().name();

        log.debug("[{}] Processing transaction type: {}", context.getPipelineId(), txnType);

        TransactionProcessor processor = processors.get(txnType);
        if (processor == null) {
            throw TransactionException.invalidRequest("Unsupported transaction type: " + txnType);
        }

        TransactionResponse response;
        if (timeoutManager != null && context.getRoutingResult() != null) {
            // Execute with timeout management
            long timeout = context.getRoutingResult().getTimeoutMs();

            response = timeoutManager.executeWithTimeout(
                    request.getTransactionId(),
                    request.getTransactionType(),
                    timeout,
                    () -> processor.process(request)
            );
        } else {
            // Execute without timeout management
            response = processor.process(request);
        }

        context.setResponse(response);

        log.info("[{}] Transaction processed: responseCode={}",
                context.getPipelineId(), response.getResponseCode());
    }
}
