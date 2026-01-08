package com.fep.transaction.report;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Report output formats.
 */
@Getter
@RequiredArgsConstructor
public enum ReportFormat {

    /** JSON format */
    JSON("application/json", ".json"),

    /** CSV format */
    CSV("text/csv", ".csv"),

    /** Excel format */
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),

    /** PDF format */
    PDF("application/pdf", ".pdf"),

    /** HTML format */
    HTML("text/html", ".html"),

    /** Plain text */
    TEXT("text/plain", ".txt");

    private final String mimeType;
    private final String fileExtension;
}
