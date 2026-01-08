package com.fep.settlement.file;

/**
 * Exception thrown when settlement file parsing fails.
 */
public class SettlementFileParseException extends RuntimeException {

    private final String fileName;
    private final int lineNumber;
    private final String fieldName;

    public SettlementFileParseException(String message) {
        super(message);
        this.fileName = null;
        this.lineNumber = -1;
        this.fieldName = null;
    }

    public SettlementFileParseException(String message, Throwable cause) {
        super(message, cause);
        this.fileName = null;
        this.lineNumber = -1;
        this.fieldName = null;
    }

    public SettlementFileParseException(String message, String fileName) {
        super(message);
        this.fileName = fileName;
        this.lineNumber = -1;
        this.fieldName = null;
    }

    public SettlementFileParseException(String message, String fileName, int lineNumber) {
        super(message);
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.fieldName = null;
    }

    public SettlementFileParseException(String message, String fileName, int lineNumber, String fieldName) {
        super(message);
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.fieldName = fieldName;
    }

    public SettlementFileParseException(String message, String fileName, int lineNumber, Throwable cause) {
        super(message, cause);
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.fieldName = null;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        if (fileName != null) {
            sb.append(" [File: ").append(fileName).append("]");
        }
        if (lineNumber > 0) {
            sb.append(" [Line: ").append(lineNumber).append("]");
        }
        if (fieldName != null) {
            sb.append(" [Field: ").append(fieldName).append("]");
        }
        return sb.toString();
    }
}
