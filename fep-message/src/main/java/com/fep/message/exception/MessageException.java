package com.fep.message.exception;

import com.fep.common.exception.FepException;

/**
 * Exception thrown during ISO 8583 message processing.
 */
public class MessageException extends FepException {

    public MessageException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public MessageException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage, cause);
    }

    public static MessageException parseError(String message) {
        return new MessageException("MSG001", "Message parse error: " + message);
    }

    public static MessageException parseError(String message, Throwable cause) {
        return new MessageException("MSG001", "Message parse error: " + message, cause);
    }

    public static MessageException assembleError(String message) {
        return new MessageException("MSG002", "Message assembly error: " + message);
    }

    public static MessageException fieldError(int fieldNumber, String message) {
        return new MessageException("MSG003", "Field " + fieldNumber + " error: " + message);
    }

    public static MessageException fieldError(String fieldId, String message) {
        return new MessageException("MSG003", "Field '" + fieldId + "' error: " + message);
    }

    public static MessageException bitmapError(String message) {
        return new MessageException("MSG004", "Bitmap error: " + message);
    }

    public static MessageException codecError(String message) {
        return new MessageException("MSG005", "Codec error: " + message);
    }
}
