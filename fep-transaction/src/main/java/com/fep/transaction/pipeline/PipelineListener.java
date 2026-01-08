package com.fep.transaction.pipeline;

/**
 * Listener interface for pipeline execution events.
 */
public interface PipelineListener {

    /**
     * Called when pipeline starts.
     */
    default void onPipelineStart(PipelineContext context) {
    }

    /**
     * Called when pipeline completes successfully.
     */
    default void onPipelineComplete(PipelineContext context) {
    }

    /**
     * Called when pipeline encounters an error.
     */
    default void onPipelineError(PipelineContext context, Exception e) {
    }

    /**
     * Called when a stage starts.
     */
    default void onStageStart(PipelineContext context, PipelineStage stage) {
    }

    /**
     * Called when a stage completes.
     */
    default void onStageComplete(PipelineContext context, PipelineStage stage) {
    }
}
