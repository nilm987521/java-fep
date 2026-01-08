package com.fep.settlement.clearing;

import com.fep.settlement.domain.*;
import com.fep.settlement.repository.InMemorySettlementRepository;
import com.fep.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClearingService Tests")
class ClearingServiceTest {

    private ClearingService clearingService;
    private SettlementRepository repository;
    private static final String OUR_BANK_CODE = "0040000";

    @BeforeEach
    void setUp() {
        repository = new InMemorySettlementRepository();
        clearingService = new ClearingService(repository, OUR_BANK_CODE);
    }

    @Nested
    @DisplayName("Calculate Clearing")
    class CalculateClearingTests {

        @Test
        @DisplayName("Should calculate clearing for matched records")
        void shouldCalculateClearingForMatchedRecords() {
            LocalDate today = LocalDate.now();
            SettlementFile file = createTestFile(today);
            repository.saveFile(file);

            List<ClearingRecord> clearingRecords = clearingService.calculateClearing(today);

            assertFalse(clearingRecords.isEmpty());
        }

        @Test
        @DisplayName("Should separate debit and credit transactions")
        void shouldSeparateDebitAndCreditTransactions() {
            LocalDate today = LocalDate.now();
            List<SettlementRecord> records = new ArrayList<>();

            // Debit: we issue, counterparty acquires (we pay)
            records.add(createRecord("TXN-001", OUR_BANK_CODE, "0050000",
                    new BigDecimal("1000.00"), "0100"));

            // Credit: counterparty issues, we acquire (we receive)
            records.add(createRecord("TXN-002", "0050000", OUR_BANK_CODE,
                    new BigDecimal("500.00"), "0100"));

            SettlementFile file = SettlementFile.builder()
                    .fileId("SF-001")
                    .settlementDate(today)
                    .records(records)
                    .build();
            repository.saveFile(file);

            List<ClearingRecord> clearingRecords = clearingService.calculateClearing(today);

            assertFalse(clearingRecords.isEmpty());
            ClearingRecord cr = clearingRecords.get(0);
            assertEquals(1, cr.getDebitCount());
            assertEquals(1, cr.getCreditCount());
            assertEquals(new BigDecimal("1000.00"), cr.getDebitAmount());
            assertEquals(new BigDecimal("500.00"), cr.getCreditAmount());
            assertEquals(new BigDecimal("-500.00"), cr.getNetAmount());
        }

        @Test
        @DisplayName("Should group by counterparty bank")
        void shouldGroupByCounterpartyBank() {
            LocalDate today = LocalDate.now();
            List<SettlementRecord> records = new ArrayList<>();

            // Transaction with bank A
            records.add(createRecord("TXN-001", OUR_BANK_CODE, "0050000",
                    new BigDecimal("1000.00"), "0100"));

            // Transaction with bank B
            records.add(createRecord("TXN-002", OUR_BANK_CODE, "0060000",
                    new BigDecimal("2000.00"), "0100"));

            SettlementFile file = SettlementFile.builder()
                    .fileId("SF-001")
                    .settlementDate(today)
                    .records(records)
                    .build();
            repository.saveFile(file);

            List<ClearingRecord> clearingRecords = clearingService.calculateClearing(today);

            assertEquals(2, clearingRecords.size());
        }

        @Test
        @DisplayName("Should return empty list when no files found")
        void shouldReturnEmptyListWhenNoFilesFound() {
            List<ClearingRecord> result = clearingService.calculateClearing(LocalDate.now());

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Clearing Workflow")
    class ClearingWorkflowTests {

        private String clearingId;

        @BeforeEach
        void setupClearing() {
            LocalDate today = LocalDate.now();
            SettlementFile file = createTestFile(today);
            repository.saveFile(file);

            List<ClearingRecord> records = clearingService.calculateClearing(today);
            if (!records.isEmpty()) {
                clearingId = records.get(0).getClearingId();
            }
        }

        @Test
        @DisplayName("Should confirm clearing record")
        void shouldConfirmClearingRecord() {
            ClearingRecord confirmed = clearingService.confirm(clearingId, "operator1");

            assertEquals(ClearingStatus.CONFIRMED, confirmed.getStatus());
            assertEquals("operator1", confirmed.getConfirmedBy());
            assertNotNull(confirmed.getConfirmedAt());
        }

        @Test
        @DisplayName("Should mark as settled")
        void shouldMarkAsSettled() {
            clearingService.confirm(clearingId, "operator1");
            ClearingRecord settled = clearingService.markSettled(clearingId);

            assertEquals(ClearingStatus.SETTLED, settled.getStatus());
            assertNotNull(settled.getSettledAt());
        }

        @Test
        @DisplayName("Should submit confirmed records for clearing")
        void shouldSubmitConfirmedRecords() {
            LocalDate today = LocalDate.now();
            clearingService.confirm(clearingId, "operator1");

            List<ClearingRecord> submitted = clearingService.submitForClearing(today);

            assertFalse(submitted.isEmpty());
            assertTrue(submitted.stream().allMatch(r -> r.getStatus() == ClearingStatus.SUBMITTED));
        }
    }

    @Nested
    @DisplayName("Queries")
    class QueryTests {

        @BeforeEach
        void setupTestData() {
            LocalDate today = LocalDate.now();
            SettlementFile file = createTestFile(today);
            repository.saveFile(file);
            clearingService.calculateClearing(today);
        }

        @Test
        @DisplayName("Should get clearing records by date")
        void shouldGetClearingRecordsByDate() {
            List<ClearingRecord> records = clearingService.getClearingRecordsByDate(LocalDate.now());

            assertFalse(records.isEmpty());
        }

        @Test
        @DisplayName("Should get clearing records by status")
        void shouldGetClearingRecordsByStatus() {
            List<ClearingRecord> calculated = clearingService.getClearingRecordsByStatus(
                    ClearingStatus.CALCULATED
            );

            assertFalse(calculated.isEmpty());
        }

        @Test
        @DisplayName("Should get clearing record by ID")
        void shouldGetClearingRecordById() {
            List<ClearingRecord> records = clearingService.getClearingRecordsByDate(LocalDate.now());
            String id = records.get(0).getClearingId();

            assertTrue(clearingService.getClearingRecord(id).isPresent());
        }
    }

    @Nested
    @DisplayName("Summary and Statistics")
    class SummaryTests {

        @BeforeEach
        void setupTestData() {
            LocalDate today = LocalDate.now();
            List<SettlementRecord> records = new ArrayList<>();

            // Multiple transactions for summary
            records.add(createRecord("TXN-001", OUR_BANK_CODE, "0050000",
                    new BigDecimal("1000.00"), "0100"));
            records.add(createRecord("TXN-002", OUR_BANK_CODE, "0050000",
                    new BigDecimal("2000.00"), "0100"));
            records.add(createRecord("TXN-003", "0050000", OUR_BANK_CODE,
                    new BigDecimal("500.00"), "0100"));

            SettlementFile file = SettlementFile.builder()
                    .fileId("SF-001")
                    .settlementDate(today)
                    .records(records)
                    .build();
            repository.saveFile(file);
            clearingService.calculateClearing(today);
        }

        @Test
        @DisplayName("Should get clearing summary")
        void shouldGetClearingSummary() {
            ClearingService.ClearingSummary summary = clearingService.getClearingSummary(LocalDate.now());

            assertNotNull(summary);
            assertTrue(summary.getTotalRecords() > 0);
            assertNotNull(summary.getTotalNetPayable());
            assertNotNull(summary.getTotalNetReceivable());
            assertNotNull(summary.getNetPosition());
        }

        @Test
        @DisplayName("Should calculate net position correctly")
        void shouldCalculateNetPositionCorrectly() {
            ClearingService.ClearingSummary summary = clearingService.getClearingSummary(LocalDate.now());

            // Based on test setup: 2 debits (1000 + 2000) and 1 credit (500)
            // Net payable is absolute value of negative net amounts
            // Net receivable is sum of positive net amounts
            assertNotNull(summary.getTotalNetPayable());
            assertNotNull(summary.getTotalNetReceivable());
            assertNotNull(summary.getNetPosition());
            // Net position should be negative (we pay more than we receive)
            assertTrue(summary.getNetPosition().compareTo(BigDecimal.ZERO) < 0);
        }

        @Test
        @DisplayName("Should get statistics")
        void shouldGetStatistics() {
            var stats = clearingService.getStatistics();

            assertNotNull(stats.get("pendingCount"));
            assertNotNull(stats.get("settledCount"));
        }
    }

    // Helper methods

    private SettlementFile createTestFile(LocalDate date) {
        List<SettlementRecord> records = new ArrayList<>();
        records.add(createRecord("TXN-001", OUR_BANK_CODE, "0050000",
                new BigDecimal("1000.00"), "0100"));

        return SettlementFile.builder()
                .fileId("SF-TEST")
                .settlementDate(date)
                .records(records)
                .build();
    }

    private SettlementRecord createRecord(String txnRef, String issuingBank,
                                          String acquiringBank, BigDecimal amount,
                                          String txnType) {
        return SettlementRecord.builder()
                .transactionRefNo(txnRef)
                .issuingBankCode(issuingBank)
                .acquiringBankCode(acquiringBank)
                .amount(amount)
                .transactionType(txnType)
                .status(SettlementStatus.MATCHED)
                .build();
    }
}
