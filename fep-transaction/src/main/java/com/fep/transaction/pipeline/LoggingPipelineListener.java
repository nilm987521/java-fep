package com.fep.transaction.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pipeline listener that logs all events.
 */
public class LoggingPipelineListener implements PipelineListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingPipelineListener.class);

    @Override
    public void onPipelineStart(PipelineContext context) {
        log.info("[{}] Pipeline started for transaction {}",
                context.getPipelineId(),
                context.getRequest().getTransactionId());
    }

    @Override
    public void onPipelineComplete(PipelineContext context) {
        log.info("[{}] Pipeline completed: success={}, elapsed={}ms",
                context.getPipelineId(),
                context.isSuccess(),
                context.getElapsedMs());
    }

    @Override
    public void onPipelineError(PipelineContext context, Exception e) {
        log.error("[{}] Pipeline error at stage {}: {}",
                context.getPipelineId(),
                context.getCurrentStage(),
                e.getMessage());
    }

    @Override
    public void onStageStart(PipelineContext context, PipelineStage stage) {
        log.debug("[{}] Stage {} started",
                context.getPipelineId(),
                stage.name());
    }

    @Override
    public void onStageComplete(PipelineContext context, PipelineStage stage) {
        log.debug("[{}] Stage {} completed in {}ms",
                context.getPipelineId(),
                stage.name(),
                context.getStageTime(stage));
    }
}
