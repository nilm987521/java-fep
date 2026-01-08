package com.fep.integration.mq.exception;

import com.fep.common.exception.FepException;

/**
 * Exception thrown for MQ-related errors.
 */
public class MqException extends FepException {

    private final String queueName;
    private final String mqErrorCode;

    public MqException(String message) {
        super("MQ_ERROR", message);
        this.queueName = null;
        this.mqErrorCode = null;
    }

    public MqException(String message, Throwable cause) {
        super("MQ_ERROR", message, cause);
        this.queueName = null;
        this.mqErrorCode = null;
    }

    public MqException(String message, String queueName, String mqErrorCode) {
        super("MQ_ERROR", message);
        this.queueName = queueName;
        this.mqErrorCode = mqErrorCode;
    }

    public MqException(String message, String queueName, String mqErrorCode, Throwable cause) {
        super("MQ_ERROR", message, cause);
        this.queueName = queueName;
        this.mqErrorCode = mqErrorCode;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getMqErrorCode() {
        return mqErrorCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if (queueName != null) {
            sb.append(" [Queue: ").append(queueName).append("]");
        }
        if (mqErrorCode != null) {
            sb.append(" [ErrorCode: ").append(mqErrorCode).append("]");
        }
        return sb.toString();
    }
}
