package com.fep.transaction.pipeline;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.routing.RoutingResult;
import lombok.Data;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Context object that flows through the transaction pipeline.
 */
@Data
public class PipelineContext {

    /** Unique identifier for this pipeline execution */
    private final String pipelineId;

    /** The transaction request */
    private TransactionRequest request;

    /** The transaction response (populated after processing) */
    private TransactionResponse response;

    /** Current stage in the pipeline */
    private PipelineStage currentStage;

    /** Routing result from routing stage */
    private RoutingResult routingResult;

    /** Whether the pipeline should continue */
    private boolean continueProcessing;

    /** Error message if pipeline failed */
    private String errorMessage;

    /** Start time of pipeline execution */
    private final Instant startTime;

    /** End time of pipeline execution */
    private Instant endTime;

    /** Stage timing information */
    private final Map<PipelineStage, Long> stageTiming;

    /** Additional attributes */
    private final Map<String, Object> attributes;

    /** Stage-specific start time for timing */
    private long stageStartTime;

    public PipelineContext(String pipelineId, TransactionRequest request) {
        this.pipelineId = pipelineId;
        this.request = request;
        this.currentStage = PipelineStage.RECEIVE;
        this.continueProcessing = true;
        this.startTime = Instant.now();
        this.stageTiming = new HashMap<>();
        this.attributes = new HashMap<>();
    }

    /**
     * Creates a context with auto-generated pipeline ID.
     */
    public static PipelineContext create(TransactionRequest request) {
        String pipelineId = "PL-" + System.currentTimeMillis() + "-" +
                String.format("%04d", (int) (Math.random() * 10000));
        return new PipelineContext(pipelineId, request);
    }

    /**
     * Moves to the next stage and records timing.
     */
    public void advanceToStage(PipelineStage stage) {
        recordCurrentStageTiming();
        this.currentStage = stage;
        this.stageStartTime = System.currentTimeMillis();
    }

    /**
     * Records the current stage timing.
     */
    private void recordCurrentStageTiming() {
        if (stageStartTime > 0 && currentStage != null) {
            long elapsed = System.currentTimeMillis() - stageStartTime;
            stageTiming.put(currentStage, elapsed);
        }
    }

    /**
     * Marks the pipeline as complete.
     */
    public void complete() {
        recordCurrentStageTiming();
        this.endTime = Instant.now();
        this.currentStage = PipelineStage.COMPLETE;
    }

    /**
     * Marks the pipeline as failed.
     */
    public void fail(String message) {
        recordCurrentStageTiming();
        this.endTime = Instant.now();
        this.continueProcessing = false;
        this.errorMessage = message;
    }

    /**
     * Gets the total elapsed time in milliseconds.
     */
    public long getElapsedMs() {
        Instant end = endTime != null ? endTime : Instant.now();
        return end.toEpochMilli() - startTime.toEpochMilli();
    }

    /**
     * Gets the time spent in a specific stage.
     */
    public long getStageTime(PipelineStage stage) {
        return stageTiming.getOrDefault(stage, 0L);
    }

    /**
     * Sets an attribute.
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Gets an attribute.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Checks if an attribute exists.
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    /**
     * Checks if pipeline execution was successful.
     */
    public boolean isSuccess() {
        return continueProcessing && errorMessage == null &&
               response != null && response.isApproved();
    }

    /**
     * Gets a summary of stage timings.
     */
    public String getTimingSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Pipeline timing [").append(pipelineId).append("]: ");
        for (PipelineStage stage : PipelineStage.values()) {
            Long time = stageTiming.get(stage);
            if (time != null) {
                sb.append(stage.name()).append("=").append(time).append("ms, ");
            }
        }
        sb.append("TOTAL=").append(getElapsedMs()).append("ms");
        return sb.toString();
    }
}
