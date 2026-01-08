package com.fep.settlement.repository;

import com.fep.settlement.domain.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of SettlementRepository for development and testing.
 */
public class InMemorySettlementRepository implements SettlementRepository {

    private final Map<String, SettlementFile> files = new ConcurrentHashMap<>();
    private final Map<String, SettlementRecord> records = new ConcurrentHashMap<>();
    private final Map<String, Discrepancy> discrepancies = new ConcurrentHashMap<>();
    private final Map<String, ClearingRecord> clearingRecords = new ConcurrentHashMap<>();

    // Settlement File operations

    @Override
    public SettlementFile saveFile(SettlementFile file) {
        if (file.getFileId() == null) {
            file.setFileId("SF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        files.put(file.getFileId(), file);

        // Also save all records
        if (file.getRecords() != null) {
            for (SettlementRecord record : file.getRecords()) {
                saveRecord(record);
            }
        }

        return file;
    }

    @Override
    public Optional<SettlementFile> findFileById(String fileId) {
        return Optional.ofNullable(files.get(fileId));
    }

    @Override
    public List<SettlementFile> findFilesByDate(LocalDate date) {
        return files.values().stream()
                .filter(f -> date.equals(f.getSettlementDate()))
                .collect(Collectors.toList());
    }

    @Override
    public List<SettlementFile> findFilesByStatus(FileProcessingStatus status) {
        return files.values().stream()
                .filter(f -> f.getProcessingStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<SettlementFile> findFilesByDateRange(LocalDate startDate, LocalDate endDate) {
        return files.values().stream()
                .filter(f -> {
                    LocalDate d = f.getSettlementDate();
                    return d != null && !d.isBefore(startDate) && !d.isAfter(endDate);
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteFile(String fileId) {
        SettlementFile removed = files.remove(fileId);
        if (removed != null && removed.getRecords() != null) {
            for (SettlementRecord record : removed.getRecords()) {
                records.remove(record.getTransactionRefNo());
            }
        }
        return removed != null;
    }

    // Settlement Record operations

    @Override
    public SettlementRecord saveRecord(SettlementRecord record) {
        String key = record.getTransactionRefNo();
        if (key == null) {
            key = "REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            record.setTransactionRefNo(key);
        }
        records.put(key, record);
        return record;
    }

    @Override
    public List<SettlementRecord> saveRecords(List<SettlementRecord> recordList) {
        for (SettlementRecord record : recordList) {
            saveRecord(record);
        }
        return recordList;
    }

    @Override
    public Optional<SettlementRecord> findRecordByRef(String transactionRefNo) {
        return Optional.ofNullable(records.get(transactionRefNo));
    }

    @Override
    public List<SettlementRecord> findRecordsByFileId(String fileId) {
        return files.values().stream()
                .filter(f -> fileId.equals(f.getFileId()))
                .findFirst()
                .map(SettlementFile::getRecords)
                .orElse(Collections.emptyList());
    }

    @Override
    public List<SettlementRecord> findRecordsByStatus(SettlementStatus status) {
        return records.values().stream()
                .filter(r -> r.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<SettlementRecord> findUnmatchedRecords(LocalDate date) {
        return files.values().stream()
                .filter(f -> date.equals(f.getSettlementDate()))
                .flatMap(f -> f.getRecords().stream())
                .filter(r -> r.getStatus() != SettlementStatus.MATCHED)
                .collect(Collectors.toList());
    }

    // Discrepancy operations

    @Override
    public Discrepancy saveDiscrepancy(Discrepancy discrepancy) {
        if (discrepancy.getDiscrepancyId() == null) {
            discrepancy.setDiscrepancyId("DISC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        discrepancies.put(discrepancy.getDiscrepancyId(), discrepancy);
        return discrepancy;
    }

    @Override
    public Optional<Discrepancy> findDiscrepancyById(String discrepancyId) {
        return Optional.ofNullable(discrepancies.get(discrepancyId));
    }

    @Override
    public List<Discrepancy> findDiscrepanciesByDate(LocalDate date) {
        return discrepancies.values().stream()
                .filter(d -> date.equals(d.getSettlementDate()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Discrepancy> findDiscrepanciesByStatus(DiscrepancyStatus status) {
        return discrepancies.values().stream()
                .filter(d -> d.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<Discrepancy> findOpenDiscrepancies() {
        return discrepancies.values().stream()
                .filter(Discrepancy::isOpen)
                .collect(Collectors.toList());
    }

    @Override
    public List<Discrepancy> findDiscrepanciesByType(DiscrepancyType type) {
        return discrepancies.values().stream()
                .filter(d -> d.getType() == type)
                .collect(Collectors.toList());
    }

    @Override
    public List<Discrepancy> findDiscrepanciesByPriority(DiscrepancyPriority priority) {
        return discrepancies.values().stream()
                .filter(d -> d.getPriority() == priority)
                .collect(Collectors.toList());
    }

    // Clearing Record operations

    @Override
    public ClearingRecord saveClearingRecord(ClearingRecord record) {
        if (record.getClearingId() == null) {
            record.setClearingId("CLR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        clearingRecords.put(record.getClearingId(), record);
        return record;
    }

    @Override
    public Optional<ClearingRecord> findClearingRecordById(String clearingId) {
        return Optional.ofNullable(clearingRecords.get(clearingId));
    }

    @Override
    public List<ClearingRecord> findClearingRecordsByDate(LocalDate date) {
        return clearingRecords.values().stream()
                .filter(c -> date.equals(c.getSettlementDate()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ClearingRecord> findClearingRecordsByStatus(ClearingStatus status) {
        return clearingRecords.values().stream()
                .filter(c -> c.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<ClearingRecord> findClearingRecordsByCounterparty(String bankCode) {
        return clearingRecords.values().stream()
                .filter(c -> bankCode.equals(c.getCounterpartyBankCode()))
                .collect(Collectors.toList());
    }

    // Additional utility methods

    public void clear() {
        files.clear();
        records.clear();
        discrepancies.clear();
        clearingRecords.clear();
    }

    public int getFileCount() {
        return files.size();
    }

    public int getRecordCount() {
        return records.size();
    }

    public int getDiscrepancyCount() {
        return discrepancies.size();
    }

    public int getClearingRecordCount() {
        return clearingRecords.size();
    }
}
