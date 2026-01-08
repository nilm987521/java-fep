package com.fep.settlement.clearing;

import com.fep.settlement.domain.*;
import com.fep.settlement.repository.SettlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating and managing clearing amounts.
 */
public class ClearingService {

    private static final Logger log = LoggerFactory.getLogger(ClearingService.class);

    private final SettlementRepository repository;
    private final String ourBankCode;

    public ClearingService(SettlementRepository repository, String ourBankCode) {
        this.repository = repository;
        this.ourBankCode = ourBankCode;
    }

    /**
     * Calculate clearing amounts for a settlement date.
     */
    public List<ClearingRecord> calculateClearing(LocalDate settlementDate) {
        log.info("Calculating clearing amounts for date: {}", settlementDate);

        List<SettlementFile> files = repository.findFilesByDate(settlementDate);
        if (files.isEmpty()) {
            log.warn("No settlement files found for date: {}", settlementDate);
            return Collections.emptyList();
        }

        // Collect all matched records
        List<SettlementRecord> allRecords = files.stream()
                .flatMap(f -> f.getRecords().stream())
                .filter(r -> r.getStatus() == SettlementStatus.MATCHED ||
                            r.getStatus() == SettlementStatus.CLEARED)
                .toList();

        // Group by counterparty bank and transaction category
        Map<String, Map<String, List<SettlementRecord>>> grouped = groupRecords(allRecords);

        List<ClearingRecord> clearingRecords = new ArrayList<>();
        String batchNumber = generateBatchNumber(settlementDate);

        for (Map.Entry<String, Map<String, List<SettlementRecord>>> bankEntry : grouped.entrySet()) {
            String counterpartyBank = bankEntry.getKey();

            for (Map.Entry<String, List<SettlementRecord>> categoryEntry : bankEntry.getValue().entrySet()) {
                String category = categoryEntry.getKey();
                List<SettlementRecord> records = categoryEntry.getValue();

                ClearingRecord clearingRecord = calculateClearingRecord(
                        settlementDate, batchNumber, counterpartyBank, category, records
                );

                clearingRecords.add(clearingRecord);
            }
        }

        // Save all clearing records
        for (ClearingRecord record : clearingRecords) {
            repository.saveClearingRecord(record);
        }

        log.info("Created {} clearing records for date {}", clearingRecords.size(), settlementDate);

        return clearingRecords;
    }

    /**
     * Calculate a single clearing record.
     */
    private ClearingRecord calculateClearingRecord(LocalDate settlementDate,
                                                    String batchNumber,
                                                    String counterpartyBank,
                                                    String category,
                                                    List<SettlementRecord> records) {
        int debitCount = 0;
        int creditCount = 0;
        BigDecimal debitAmount = BigDecimal.ZERO;
        BigDecimal creditAmount = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;

        for (SettlementRecord record : records) {
            BigDecimal amount = record.getAmount() != null ? record.getAmount() : BigDecimal.ZERO;
            BigDecimal fee = record.getFeeAmount() != null ? record.getFeeAmount() : BigDecimal.ZERO;

            // Determine if we are paying (debit) or receiving (credit)
            // If issuing bank is us and acquiring bank is counterparty -> we pay (debit)
            // If acquiring bank is us and issuing bank is counterparty -> we receive (credit)
            boolean isDebit = ourBankCode.equals(record.getIssuingBankCode());

            if (record.isReversal()) {
                isDebit = !isDebit; // Reversals flip the direction
            }

            if (isDebit) {
                debitCount++;
                debitAmount = debitAmount.add(amount);
            } else {
                creditCount++;
                creditAmount = creditAmount.add(amount);
            }

            totalFees = totalFees.add(fee);
        }

        BigDecimal netAmount = creditAmount.subtract(debitAmount);

        return ClearingRecord.builder()
                .clearingId(generateClearingId())
                .settlementDate(settlementDate)
                .batchNumber(batchNumber)
                .ourBankCode(ourBankCode)
                .counterpartyBankCode(counterpartyBank)
                .transactionCategory(category)
                .debitCount(debitCount)
                .debitAmount(debitAmount)
                .creditCount(creditCount)
                .creditAmount(creditAmount)
                .netAmount(netAmount)
                .feeAmount(totalFees)
                .currencyCode("TWD")
                .status(ClearingStatus.CALCULATED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Group records by counterparty bank and transaction category.
     */
    private Map<String, Map<String, List<SettlementRecord>>> groupRecords(List<SettlementRecord> records) {
        Map<String, Map<String, List<SettlementRecord>>> result = new HashMap<>();

        for (SettlementRecord record : records) {
            // Determine counterparty bank
            String counterparty;
            if (ourBankCode.equals(record.getIssuingBankCode())) {
                counterparty = record.getAcquiringBankCode();
            } else if (ourBankCode.equals(record.getAcquiringBankCode())) {
                counterparty = record.getIssuingBankCode();
            } else {
                // Neither matches our bank - skip
                continue;
            }

            if (counterparty == null || counterparty.isBlank()) {
                continue;
            }

            String category = categorizeTransaction(record);

            result.computeIfAbsent(counterparty, k -> new HashMap<>())
                    .computeIfAbsent(category, k -> new ArrayList<>())
                    .add(record);
        }

        return result;
    }

    /**
     * Categorize transaction for clearing purposes.
     */
    private String categorizeTransaction(SettlementRecord record) {
        String type = record.getTransactionType();
        if (type == null) {
            return "OTHER";
        }

        if (type.startsWith("01")) {
            return "ATM_WITHDRAWAL";
        } else if (type.startsWith("02")) {
            return "TRANSFER";
        } else if (type.startsWith("03")) {
            return "DEPOSIT";
        } else if (type.startsWith("04")) {
            return "BILL_PAYMENT";
        } else if (type.startsWith("05")) {
            return "POS";
        } else if (type.startsWith("06")) {
            return "E_PAYMENT";
        } else {
            return "OTHER";
        }
    }

    /**
     * Get clearing record by ID.
     */
    public Optional<ClearingRecord> getClearingRecord(String clearingId) {
        return repository.findClearingRecordById(clearingId);
    }

    /**
     * Get clearing records by date.
     */
    public List<ClearingRecord> getClearingRecordsByDate(LocalDate date) {
        return repository.findClearingRecordsByDate(date);
    }

    /**
     * Get clearing records by counterparty.
     */
    public List<ClearingRecord> getClearingRecordsByCounterparty(String bankCode) {
        return repository.findClearingRecordsByCounterparty(bankCode);
    }

    /**
     * Get clearing records by status.
     */
    public List<ClearingRecord> getClearingRecordsByStatus(ClearingStatus status) {
        return repository.findClearingRecordsByStatus(status);
    }

    /**
     * Confirm a clearing record.
     */
    public ClearingRecord confirm(String clearingId, String userId) {
        ClearingRecord record = repository.findClearingRecordById(clearingId)
                .orElseThrow(() -> new IllegalArgumentException("Clearing record not found: " + clearingId));

        if (!record.getStatus().isModifiable()) {
            throw new IllegalStateException("Clearing record cannot be confirmed in status: " + record.getStatus());
        }

        record.confirm(userId);
        return repository.saveClearingRecord(record);
    }

    /**
     * Submit clearing records to clearing house.
     */
    public List<ClearingRecord> submitForClearing(LocalDate settlementDate) {
        List<ClearingRecord> records = repository.findClearingRecordsByDate(settlementDate).stream()
                .filter(r -> r.getStatus() == ClearingStatus.CONFIRMED)
                .toList();

        for (ClearingRecord record : records) {
            record.setStatus(ClearingStatus.SUBMITTED);
            repository.saveClearingRecord(record);
        }

        log.info("Submitted {} clearing records for date {}", records.size(), settlementDate);

        return records;
    }

    /**
     * Mark clearing record as settled.
     */
    public ClearingRecord markSettled(String clearingId) {
        ClearingRecord record = repository.findClearingRecordById(clearingId)
                .orElseThrow(() -> new IllegalArgumentException("Clearing record not found: " + clearingId));

        record.settle();
        return repository.saveClearingRecord(record);
    }

    /**
     * Get clearing summary for a date.
     */
    public ClearingSummary getClearingSummary(LocalDate date) {
        List<ClearingRecord> records = repository.findClearingRecordsByDate(date);

        int totalRecords = records.size();
        int pendingCount = 0;
        int confirmedCount = 0;
        int settledCount = 0;
        BigDecimal totalNetPayable = BigDecimal.ZERO;
        BigDecimal totalNetReceivable = BigDecimal.ZERO;

        Map<String, BigDecimal> byCounterparty = new HashMap<>();

        for (ClearingRecord record : records) {
            switch (record.getStatus()) {
                case PENDING, CALCULATED -> pendingCount++;
                case CONFIRMED, SUBMITTED, SETTLING -> confirmedCount++;
                case SETTLED -> settledCount++;
            }

            if (record.isNetPayer()) {
                totalNetPayable = totalNetPayable.add(record.getAbsoluteNetAmount());
            } else if (record.isNetReceiver()) {
                totalNetReceivable = totalNetReceivable.add(record.getAbsoluteNetAmount());
            }

            byCounterparty.merge(record.getCounterpartyBankCode(),
                    record.getNetAmount(), BigDecimal::add);
        }

        return ClearingSummary.builder()
                .settlementDate(date)
                .totalRecords(totalRecords)
                .pendingCount(pendingCount)
                .confirmedCount(confirmedCount)
                .settledCount(settledCount)
                .totalNetPayable(totalNetPayable)
                .totalNetReceivable(totalNetReceivable)
                .netPosition(totalNetReceivable.subtract(totalNetPayable))
                .byCounterparty(byCounterparty)
                .build();
    }

    /**
     * Get clearing statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        List<ClearingRecord> pending = repository.findClearingRecordsByStatus(ClearingStatus.PENDING);
        pending.addAll(repository.findClearingRecordsByStatus(ClearingStatus.CALCULATED));
        List<ClearingRecord> confirmed = repository.findClearingRecordsByStatus(ClearingStatus.CONFIRMED);
        List<ClearingRecord> settled = repository.findClearingRecordsByStatus(ClearingStatus.SETTLED);

        stats.put("pendingCount", pending.size());
        stats.put("confirmedCount", confirmed.size());
        stats.put("settledCount", settled.size());

        BigDecimal pendingAmount = pending.stream()
                .map(ClearingRecord::getAbsoluteNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("pendingAmount", pendingAmount);

        BigDecimal settledAmount = settled.stream()
                .map(ClearingRecord::getAbsoluteNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("settledAmount", settledAmount);

        return stats;
    }

    private String generateClearingId() {
        return "CLR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateBatchNumber(LocalDate date) {
        return "B" + date.toString().replace("-", "") + "-" +
               String.format("%04d", (int) (Math.random() * 10000));
    }

    /**
     * Clearing summary for a date.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ClearingSummary {
        private LocalDate settlementDate;
        private int totalRecords;
        private int pendingCount;
        private int confirmedCount;
        private int settledCount;
        private BigDecimal totalNetPayable;
        private BigDecimal totalNetReceivable;
        private BigDecimal netPosition;
        private Map<String, BigDecimal> byCounterparty;
    }
}
