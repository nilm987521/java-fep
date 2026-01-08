package com.fep.transaction.report;

import com.fep.transaction.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of report generation.
 */
@Data
@Builder
public class ReportResult {

    /** Report ID */
    private String reportId;

    /** Report type */
    private ReportType reportType;

    /** Report format */
    private ReportFormat format;

    /** Date range */
    private LocalDate startDate;
    private LocalDate endDate;

    /** Generation timestamp */
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();

    /** Summary statistics */
    private ReportSummary summary;

    /** Detailed records (if requested) */
    @Builder.Default
    private List<TransactionRecord> records = new ArrayList<>();

    /** Distribution by type */
    @Builder.Default
    private Map<TransactionType, TypeSummary> typeDistribution = new HashMap<>();

    /** Distribution by channel */
    @Builder.Default
    private Map<String, ChannelSummary> channelDistribution = new HashMap<>();

    /** Hourly distribution */
    @Builder.Default
    private Map<Integer, HourlySummary> hourlyDistribution = new HashMap<>();

    /** Error distribution */
    @Builder.Default
    private Map<String, Long> errorDistribution = new HashMap<>();

    /** Pagination info */
    private int currentPage;
    private int totalPages;
    private long totalRecords;

    /** Raw data bytes (for file downloads) */
    private byte[] rawData;

    /**
     * Summary statistics.
     */
    @Data
    @Builder
    public static class ReportSummary {
        /** Total transaction count */
        private long totalCount;

        /** Successful transaction count */
        private long successCount;

        /** Failed transaction count */
        private long failedCount;

        /** Success rate percentage */
        private BigDecimal successRate;

        /** Total amount */
        private BigDecimal totalAmount;

        /** Average amount */
        private BigDecimal averageAmount;

        /** Minimum amount */
        private BigDecimal minAmount;

        /** Maximum amount */
        private BigDecimal maxAmount;

        /** Average response time (ms) */
        private BigDecimal averageResponseTime;

        /** P95 response time (ms) */
        private long p95ResponseTime;

        /** P99 response time (ms) */
        private long p99ResponseTime;
    }

    /**
     * Transaction type summary.
     */
    @Data
    @Builder
    public static class TypeSummary {
        private TransactionType type;
        private long count;
        private BigDecimal amount;
        private BigDecimal successRate;
        private BigDecimal averageResponseTime;
    }

    /**
     * Channel summary.
     */
    @Data
    @Builder
    public static class ChannelSummary {
        private String channel;
        private long count;
        private BigDecimal amount;
        private BigDecimal successRate;
        private BigDecimal percentage;
    }

    /**
     * Hourly summary.
     */
    @Data
    @Builder
    public static class HourlySummary {
        private int hour;
        private long count;
        private BigDecimal amount;
        private BigDecimal averageResponseTime;
    }

    /**
     * Transaction record for detailed report.
     */
    @Data
    @Builder
    public static class TransactionRecord {
        private String transactionId;
        private TransactionType type;
        private LocalDateTime transactionTime;
        private String maskedAccount;
        private BigDecimal amount;
        private String currencyCode;
        private String channel;
        private String responseCode;
        private boolean success;
        private long responseTimeMs;
        private String terminalId;
        private String merchantId;
    }
}
