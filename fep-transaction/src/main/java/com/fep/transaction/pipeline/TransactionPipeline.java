package com.fep.transaction.pipeline;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.exception.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Main transaction processing pipeline.
 * Orchestrates the flow of transactions through multiple stages.
 */
public class TransactionPipeline {

    private static final Logger log = LoggerFactory.getLogger(TransactionPipeline.class);

    /** Handlers organized by stage */
    private final Map<PipelineStage, List<PipelineHandler>> handlers;

    /** Pipeline execution listeners */
    private final List<PipelineListener> listeners;

    public TransactionPipeline() {
        this.handlers = new EnumMap<>(PipelineStage.class);
        this.listeners = new ArrayList<>();
    }

    /**
     * Adds a handler to the pipeline.
     */
    public TransactionPipeline addHandler(PipelineHandler handler) {
        handlers.computeIfAbsent(handler.getStage(), k -> new ArrayList<>())
                .add(handler);

        // Sort handlers within each stage by order
        handlers.get(handler.getStage())
                .sort(Comparator.comparingInt(PipelineHandler::getOrder));

        log.debug("Added handler {} for stage {}", handler.getClass().getSimpleName(), handler.getStage());
        return this;
    }

    /**
     * Adds multiple handlers to the pipeline.
     */
    public TransactionPipeline addHandlers(List<PipelineHandler> handlerList) {
        handlerList.forEach(this::addHandler);
        return this;
    }

    /**
     * Adds a listener for pipeline events.
     */
    public TransactionPipeline addListener(PipelineListener listener) {
        listeners.add(listener);
        return this;
    }

    /**
     * Executes the pipeline for a transaction request.
     *
     * @param request The transaction request
     * @return The transaction response
     */
    public TransactionResponse execute(TransactionRequest request) {
        PipelineContext context = PipelineContext.create(request);

        log.info("[{}] Starting pipeline for transaction: {}, type={}",
                context.getPipelineId(),
                request.getTransactionId(),
                request.getTransactionType());

        notifyStart(context);

        try {
            // Execute each stage in order
            for (PipelineStage stage : PipelineStage.values()) {
                if (stage == PipelineStage.COMPLETE) {
                    continue; // Skip complete stage, it's just a marker
                }

                if (!context.isContinueProcessing()) {
                    log.debug("[{}] Pipeline stopped at stage {}", context.getPipelineId(), stage);
                    break;
                }

                executeStage(context, stage);
            }

            context.complete();
            notifyComplete(context);

            return context.getResponse() != null ? context.getResponse() : createErrorResponse(context, ResponseCode.SYSTEM_MALFUNCTION);

        } catch (TransactionException e) {
            log.warn("[{}] Pipeline failed with transaction error: {}",
                    context.getPipelineId(), e.getMessage());
            context.fail(e.getMessage());
            notifyError(context, e);
            ResponseCode code = ResponseCode.fromCode(e.getResponseCode());
            return createErrorResponse(context, code != null ? code : ResponseCode.ERROR);

        } catch (Exception e) {
            log.error("[{}] Pipeline failed with unexpected error: {}",
                    context.getPipelineId(), e.getMessage(), e);
            context.fail(e.getMessage());
            notifyError(context, e);
            return createErrorResponse(context, ResponseCode.SYSTEM_MALFUNCTION);
        }
    }

    /**
     * Executes a single stage of the pipeline.
     */
    private void executeStage(PipelineContext context, PipelineStage stage) throws Exception {
        List<PipelineHandler> stageHandlers = handlers.get(stage);
        if (stageHandlers == null || stageHandlers.isEmpty()) {
            log.trace("[{}] No handlers for stage {}", context.getPipelineId(), stage);
            return;
        }

        context.advanceToStage(stage);
        notifyStageStart(context, stage);

        log.debug("[{}] Executing stage {} with {} handler(s)",
                context.getPipelineId(), stage, stageHandlers.size());

        for (PipelineHandler handler : stageHandlers) {
            if (!context.isContinueProcessing()) {
                break;
            }
            handler.handle(context);
        }

        notifyStageComplete(context, stage);
    }

    /**
     * Creates an error response with specific response code.
     */
    private TransactionResponse createErrorResponse(PipelineContext context, ResponseCode code) {
        return TransactionResponse.builder()
                .transactionId(context.getRequest().getTransactionId())
                .responseCode(code.getCode())
                .responseCodeEnum(code)
                .responseDescription(code.getDescription())
                .responseDescriptionChinese(code.getChineseDescription())
                .approved(false)
                .errorDetails(context.getErrorMessage())
                .build();
    }

    // Listener notification methods

    private void notifyStart(PipelineContext context) {
        listeners.forEach(l -> l.onPipelineStart(context));
    }

    private void notifyComplete(PipelineContext context) {
        listeners.forEach(l -> l.onPipelineComplete(context));
    }

    private void notifyError(PipelineContext context, Exception e) {
        listeners.forEach(l -> l.onPipelineError(context, e));
    }

    private void notifyStageStart(PipelineContext context, PipelineStage stage) {
        listeners.forEach(l -> l.onStageStart(context, stage));
    }

    private void notifyStageComplete(PipelineContext context, PipelineStage stage) {
        listeners.forEach(l -> l.onStageComplete(context, stage));
    }

    /**
     * Gets the total number of handlers.
     */
    public int getHandlerCount() {
        return handlers.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Gets handlers for a specific stage.
     */
    public List<PipelineHandler> getHandlersForStage(PipelineStage stage) {
        return handlers.getOrDefault(stage, Collections.emptyList());
    }
}
