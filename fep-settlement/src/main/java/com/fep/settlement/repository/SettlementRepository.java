package com.fep.settlement.repository;

import com.fep.settlement.domain.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for settlement data.
 */
public interface SettlementRepository {

    // Settlement File operations

    /**
     * Save a settlement file.
     */
    SettlementFile saveFile(SettlementFile file);

    /**
     * Find settlement file by ID.
     */
    Optional<SettlementFile> findFileById(String fileId);

    /**
     * Find settlement files by date.
     */
    List<SettlementFile> findFilesByDate(LocalDate date);

    /**
     * Find settlement files by status.
     */
    List<SettlementFile> findFilesByStatus(FileProcessingStatus status);

    /**
     * Find settlement files by date range.
     */
    List<SettlementFile> findFilesByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * Delete settlement file.
     */
    boolean deleteFile(String fileId);

    // Settlement Record operations

    /**
     * Save a settlement record.
     */
    SettlementRecord saveRecord(SettlementRecord record);

    /**
     * Save multiple records.
     */
    List<SettlementRecord> saveRecords(List<SettlementRecord> records);

    /**
     * Find record by transaction reference.
     */
    Optional<SettlementRecord> findRecordByRef(String transactionRefNo);

    /**
     * Find records by file ID.
     */
    List<SettlementRecord> findRecordsByFileId(String fileId);

    /**
     * Find records by status.
     */
    List<SettlementRecord> findRecordsByStatus(SettlementStatus status);

    /**
     * Find unmatched records for a date.
     */
    List<SettlementRecord> findUnmatchedRecords(LocalDate date);

    // Discrepancy operations

    /**
     * Save a discrepancy.
     */
    Discrepancy saveDiscrepancy(Discrepancy discrepancy);

    /**
     * Find discrepancy by ID.
     */
    Optional<Discrepancy> findDiscrepancyById(String discrepancyId);

    /**
     * Find discrepancies by date.
     */
    List<Discrepancy> findDiscrepanciesByDate(LocalDate date);

    /**
     * Find discrepancies by status.
     */
    List<Discrepancy> findDiscrepanciesByStatus(DiscrepancyStatus status);

    /**
     * Find open discrepancies.
     */
    List<Discrepancy> findOpenDiscrepancies();

    /**
     * Find discrepancies by type.
     */
    List<Discrepancy> findDiscrepanciesByType(DiscrepancyType type);

    /**
     * Find discrepancies by priority.
     */
    List<Discrepancy> findDiscrepanciesByPriority(DiscrepancyPriority priority);

    // Clearing Record operations

    /**
     * Save a clearing record.
     */
    ClearingRecord saveClearingRecord(ClearingRecord record);

    /**
     * Find clearing record by ID.
     */
    Optional<ClearingRecord> findClearingRecordById(String clearingId);

    /**
     * Find clearing records by date.
     */
    List<ClearingRecord> findClearingRecordsByDate(LocalDate date);

    /**
     * Find clearing records by status.
     */
    List<ClearingRecord> findClearingRecordsByStatus(ClearingStatus status);

    /**
     * Find clearing records by counterparty.
     */
    List<ClearingRecord> findClearingRecordsByCounterparty(String bankCode);
}
