package com.fep.transaction.report;

import com.fep.transaction.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

/**
 * Request object for generating reports.
 */
@Data
@Builder
public class ReportRequest {

    /** Report type */
    private ReportType reportType;

    /** Output format */
    @Builder.Default
    private ReportFormat format = ReportFormat.JSON;

    /** Start date (inclusive) */
    private LocalDate startDate;

    /** End date (inclusive) */
    private LocalDate endDate;

    /** Filter by transaction types (null = all) */
    private Set<TransactionType> transactionTypes;

    /** Filter by channel (null = all) */
    private String channel;

    /** Filter by bank code */
    private String bankCode;

    /** Filter by terminal ID */
    private String terminalId;

    /** Filter by merchant ID */
    private String merchantId;

    /** Include only successful transactions */
    @Builder.Default
    private boolean successOnly = false;

    /** Include only failed transactions */
    @Builder.Default
    private boolean failedOnly = false;

    /** Language for report */
    @Builder.Default
    private String language = "zh-TW";

    /** Page number (for paginated reports) */
    @Builder.Default
    private int page = 0;

    /** Page size */
    @Builder.Default
    private int pageSize = 100;

    /** Include detailed records */
    @Builder.Default
    private boolean includeDetails = true;

    /** Include summary statistics */
    @Builder.Default
    private boolean includeSummary = true;

    /**
     * Checks if Chinese language is selected.
     */
    public boolean isChinese() {
        return language != null && language.startsWith("zh");
    }

    /**
     * Validates the request.
     */
    public void validate() {
        if (reportType == null) {
            throw new IllegalArgumentException("Report type is required");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Date range is required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        if (successOnly && failedOnly) {
            throw new IllegalArgumentException("Cannot filter by both success and failed only");
        }
    }
}
