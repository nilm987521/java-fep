package com.fep.settlement.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Settlement file container holding all records from a FISC settlement file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementFile {

    /** Unique file identifier */
    private String fileId;

    /** File name */
    private String fileName;

    /** Settlement date */
    private LocalDate settlementDate;

    /** File type (daily, monthly, etc.) */
    private SettlementFileType fileType;

    /** Source system (FISC, internal, etc.) */
    private String source;

    /** Bank code this file belongs to */
    private String bankCode;

    /** File header information */
    private FileHeader header;

    /** File trailer information */
    private FileTrailer trailer;

    /** All settlement records */
    @Builder.Default
    private List<SettlementRecord> records = new ArrayList<>();

    /** Processing status */
    @Builder.Default
    private FileProcessingStatus processingStatus = FileProcessingStatus.RECEIVED;

    /** File received timestamp */
    private LocalDateTime receivedAt;

    /** Processing started timestamp */
    private LocalDateTime processingStartedAt;

    /** Processing completed timestamp */
    private LocalDateTime processingCompletedAt;

    /** Error message if processing failed */
    private String errorMessage;

    /** File checksum */
    private String checksum;

    /** Raw file size in bytes */
    private long fileSizeBytes;

    /**
     * Get total record count.
     */
    public int getTotalRecordCount() {
        return records != null ? records.size() : 0;
    }

    /**
     * Get total amount of all records.
     */
    public BigDecimal getTotalAmount() {
        if (records == null || records.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return records.stream()
                .map(SettlementRecord::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total net amount after fees.
     */
    public BigDecimal getTotalNetAmount() {
        if (records == null || records.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return records.stream()
                .map(SettlementRecord::calculateNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get records by status.
     */
    public List<SettlementRecord> getRecordsByStatus(SettlementStatus status) {
        if (records == null) {
            return List.of();
        }
        return records.stream()
                .filter(r -> r.getStatus() == status)
                .toList();
    }

    /**
     * Get count of matched records.
     */
    public long getMatchedCount() {
        return countByStatus(SettlementStatus.MATCHED);
    }

    /**
     * Get count of discrepancy records.
     */
    public long getDiscrepancyCount() {
        if (records == null) {
            return 0;
        }
        return records.stream()
                .filter(r -> r.getStatus().isDiscrepancy())
                .count();
    }

    /**
     * Get count by status.
     */
    public long countByStatus(SettlementStatus status) {
        if (records == null) {
            return 0;
        }
        return records.stream()
                .filter(r -> r.getStatus() == status)
                .count();
    }

    /**
     * Get status summary.
     */
    public Map<SettlementStatus, Long> getStatusSummary() {
        if (records == null) {
            return Map.of();
        }
        return records.stream()
                .collect(Collectors.groupingBy(
                        SettlementRecord::getStatus,
                        Collectors.counting()
                ));
    }

    /**
     * Get match rate as percentage.
     */
    public double getMatchRate() {
        int total = getTotalRecordCount();
        if (total == 0) {
            return 0.0;
        }
        return (getMatchedCount() * 100.0) / total;
    }

    /**
     * Validate file integrity.
     */
    public boolean validateIntegrity() {
        if (header == null || trailer == null) {
            return false;
        }

        // Check record count matches trailer
        if (trailer.getRecordCount() != getTotalRecordCount()) {
            return false;
        }

        // Check total amount matches trailer
        if (trailer.getTotalAmount() != null &&
            trailer.getTotalAmount().compareTo(getTotalAmount()) != 0) {
            return false;
        }

        return true;
    }

    /**
     * Add a record to the file.
     */
    public void addRecord(SettlementRecord record) {
        if (records == null) {
            records = new ArrayList<>();
        }
        records.add(record);
    }

    /**
     * File header information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileHeader {
        private String fileId;
        private String version;
        private LocalDate creationDate;
        private String creatingBank;
        private String receivingBank;
        private String fileType;
        private String rawData;
    }

    /**
     * File trailer information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileTrailer {
        private int recordCount;
        private BigDecimal totalAmount;
        private BigDecimal totalDebitAmount;
        private BigDecimal totalCreditAmount;
        private int debitCount;
        private int creditCount;
        private String checksum;
        private String rawData;
    }
}
