package com.fep.transaction.pipeline;

/**
 * Represents a stage in the transaction processing pipeline.
 */
public enum PipelineStage {

    /** Initial request reception */
    RECEIVE("接收", 1),

    /** Message parsing and validation */
    PARSE("解析", 2),

    /** Duplicate transaction check */
    DUPLICATE_CHECK("重複檢查", 3),

    /** Security validation (PIN, MAC) */
    SECURITY_CHECK("安全驗證", 4),

    /** Business validation (limits, card status) */
    VALIDATION("業務驗證", 5),

    /** Transaction routing */
    ROUTING("路由", 6),

    /** Core processing */
    PROCESSING("處理", 7),

    /** Response generation */
    RESPONSE("回應", 8),

    /** Logging and audit */
    AUDIT("稽核", 9),

    /** Completion */
    COMPLETE("完成", 10);

    private final String description;
    private final int order;

    PipelineStage(String description, int order) {
        this.description = description;
        this.order = order;
    }

    public String getDescription() {
        return description;
    }

    public int getOrder() {
        return order;
    }
}
