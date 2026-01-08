package com.fep.transaction.pipeline;

/**
 * Handler interface for processing a specific stage in the pipeline.
 */
public interface PipelineHandler {

    /**
     * Gets the stage this handler is responsible for.
     */
    PipelineStage getStage();

    /**
     * Handles the pipeline context at this stage.
     *
     * @param context The pipeline context
     * @throws Exception if processing fails
     */
    void handle(PipelineContext context) throws Exception;

    /**
     * Gets the handler order (for multiple handlers in same stage).
     */
    default int getOrder() {
        return 100;
    }
}
