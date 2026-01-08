package com.fep.communication.exception;

import com.fep.common.exception.FepException;

/**
 * Exception thrown during communication operations.
 */
public class CommunicationException extends FepException {

    public CommunicationException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public CommunicationException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage, cause);
    }

    public static CommunicationException connectionFailed(String host, int port, Throwable cause) {
        return new CommunicationException("COMM001",
            String.format("Failed to connect to %s:%d", host, port), cause);
    }

    public static CommunicationException connectionTimeout(String host, int port) {
        return new CommunicationException("COMM002",
            String.format("Connection timeout to %s:%d", host, port));
    }

    public static CommunicationException sendFailed(String message) {
        return new CommunicationException("COMM003", "Failed to send message: " + message);
    }

    public static CommunicationException sendFailed(String message, Throwable cause) {
        return new CommunicationException("COMM003", "Failed to send message: " + message, cause);
    }

    public static CommunicationException receiveFailed(String message) {
        return new CommunicationException("COMM004", "Failed to receive message: " + message);
    }

    public static CommunicationException receiveFailed(String message, Throwable cause) {
        return new CommunicationException("COMM004", "Failed to receive message: " + message, cause);
    }

    public static CommunicationException responseTimeout() {
        return new CommunicationException("COMM005", "Response timeout");
    }

    public static CommunicationException channelClosed() {
        return new CommunicationException("COMM006", "Channel is closed");
    }

    public static CommunicationException poolExhausted() {
        return new CommunicationException("COMM007", "Connection pool exhausted");
    }

    public static CommunicationException invalidState(String message) {
        return new CommunicationException("COMM008", "Invalid state: " + message);
    }
}
