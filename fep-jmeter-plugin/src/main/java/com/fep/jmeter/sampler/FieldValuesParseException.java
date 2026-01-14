package com.fep.jmeter.sampler;

/**
 * Exception thrown when Field Values JSON parsing fails.
 *
 * <p>This exception is used to distinguish JSON parsing errors from
 * other types of exceptions, allowing for proper error messages to be
 * displayed in the Sample Result instead of misleading validation errors.
 */
public class FieldValuesParseException extends Exception {

    private static final long serialVersionUID = 1L;

    public FieldValuesParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
