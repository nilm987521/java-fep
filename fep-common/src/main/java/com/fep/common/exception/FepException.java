package com.fep.common.exception;

import lombok.Getter;

/**
 * Base exception class for FEP system.
 */
@Getter
public class FepException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    public FepException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public FepException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
