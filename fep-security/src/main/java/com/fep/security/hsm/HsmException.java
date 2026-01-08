package com.fep.security.hsm;

/**
 * Exception for HSM operations.
 */
public class HsmException extends RuntimeException {

    private final String errorCode;

    public HsmException(String message) {
        super(message);
        this.errorCode = "99";
    }

    public HsmException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public HsmException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "99";
    }

    public HsmException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
