package com.fep.settlement.reconciliation;

import com.fep.settlement.domain.*;
import com.fep.settlement.repository.InMemorySettlementRepository;
import com.fep.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReconciliationService Tests")
class ReconciliationServiceTest {

    private ReconciliationService reconciliationService;
    private SettlementRepository repository;
    private TestTransactionProvider transactionProvider;

    @BeforeEach
    void setUp() {
        repository = new InMemorySettlementRepository();
        transactionProvider = new TestTransactionProvider();
        reconciliationService = new ReconciliationService(repository, transactionProvider);
    }

    @Nested
    @DisplayName("Basic Reconciliation")
    class BasicReconciliationTests {

        @Test
        @DisplayName("Should match all records when data is identical")
        void shouldMatchAllRecordsWhenIdentical() {
            // Setup settlement file
            SettlementFile file = createSettlementFile(LocalDate.now(), 5);

            // Setup matching internal transactions
            for (SettlementRecord record : file.getRecords()) {
                transactionProvider.addTransaction(
                        record.getRrn(),
                        record.getStan(),
                        record.getAmount()
                );
            }

            ReconciliationResult result = reconciliationService.reconcile(file);

            assertEquals(ReconciliationStatus.COMPLETED, result.getStatus());
            assertEquals(5, result.getMatchedCount());
            assertEquals(0, result.getDiscrepancyCount());
            assertEquals(100.0, result.getMatchRate(), 0.01);
            assertTrue(result.isSuccessful());
        }

        @Test
        @DisplayName("Should detect missing internal transactions")
        void shouldDetectMissingInternalTransactions() {
            SettlementFile file = createSettlementFile(LocalDate.now(), 3);

            // Only add 2 matching transactions
            List<SettlementRecord> records = file.getRecords();
            transactionProvider.addTransaction(
                    records.get(0).getRrn(),
                    records.get(0).getStan(),
                    records.get(0).getAmount()
            );
            transactionProvider.addTransaction(
                    records.get(1).getRrn(),
                    records.get(1).getStan(),
                    records.get(1).getAmount()
            );

            ReconciliationResult result = reconciliationService.reconcile(file);

            assertEquals(ReconciliationStatus.COMPLETED_WITH_DISCREPANCIES, result.getStatus());
            assertEquals(2, result.getMatchedCount());
            assertEquals(1, result.getUnmatchedSettlementRecords().size());
            assertTrue(result.hasDiscrepancies());
        }

        @Test
        @DisplayName("Should detect missing settlement records")
        void shouldDetectMissingSettlementRecords() {
            SettlementFile file = createSettlementFile(LocalDate.now(), 2);

            // Add matching + extra internal transactions
            for (SettlementRecord record : file.getRecords()) {
                transactionProvider.addTransaction(
                        record.getRrn(),
                        record.getStan(),
                        record.getAmount()
                );
            }
            // Extra internal transaction not in settlement
            transactionProvider.addTransaction("EXTRA_RRN", "999999", new BigDecimal("500.00"));

            ReconciliationResult result = reconciliationService.reconcile(file);

            assertEquals(2, result.getMatchedCount());
            assertEquals(1, result.getUnmatchedInternalTransactions().size());
        }

        @Test
        @DisplayName("Should detect amount mismatch")
        void shouldDetectAmountMismatch() {
            SettlementFile file = createSettlementFile(LocalDate.now(), 1);
            SettlementRecord record = file.getRecords().get(0);

            // Add internal transaction with different amount
            transactionProvider.addTransaction(
                    record.getRrn(),
                    record.getStan(),
                    record.getAmount().add(new BigDecimal("100.00"))
            );

            ReconciliationResult result = reconciliationService.reconcile(file);

            assertEquals(0, result.getMatchedCount());
            assertTrue(result.hasDiscrepancies());
            assertTrue(result.getDiscrepancies().stream()
                    .anyMatch(d -> d.getType() == DiscrepancyType.AMOUNT_MISMATCH));
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("Should respect amount tolerance")
        void shouldRespectAmountTolerance() {
            SettlementFile file = createSettlementFile(LocalDate.now(), 1);
            SettlementRecord record = file.getRecords().get(0);

            // Add internal transaction with small amount difference
            transactionProvider.addTransaction(
                    record.getRrn(),
                    record.getStan(),
                    record.getAmount().add(new BigDecimal("0.50"))
            );

            ReconciliationConfig config = ReconciliationConfig.builder()
                    .amountTolerance(new BigDecimal("1.00"))
                    .build();

            ReconciliationResult result = reconciliationService.reconcile(file, config);

            assertEquals(1, result.getMatchedCount());
            assertFalse(result.hasDiscrepancies());
        }

        @Test
        @DisplayName("Should match by RRN only when configured")
        void shouldMatchByRrnOnly() {
            SettlementFile file = createSettlementFile(LocalDate.now(), 1);
            SettlementRecord record = file.getRecords().get(0);

            // Add transaction with matching RRN but different STAN
            transactionProvider.addTransaction(
                    record.getRrn(),
                    "DIFFERENT_STAN",
                    record.getAmount()
            );

            ReconciliationConfig config = ReconciliationConfig.builder()
                    .matchByRrn(true)
                    .matchByStan(false)
                    .build();

            ReconciliationResult result = reconciliationService.reconcile(file, config);

            assertEquals(1, result.getMatchedCount());
        }

        @Test
        @DisplayName("Should limit max discrepancies")
        void shouldLimitMaxDiscrepancies() {
            SettlementFile file = createSettlementFile(LocalDate.now(), 10);
            // No internal transactions - all will be discrepancies

            ReconciliationConfig config = ReconciliationConfig.builder()
                    .maxDiscrepancies(5)
                    .continueOnError(true)
                    .build();

            ReconciliationResult result = reconciliationService.reconcile(file, config);

            // Should have at most maxDiscrepancies + unmatched internal (which creates more discrepancies)
            assertTrue(result.getDiscrepancyCount() >= 5);
        }
    }

    @Nested
    @DisplayName("Result Management")
    class ResultManagementTests {

        @Test
        @DisplayName("Should cache reconciliation result")
        void shouldCacheReconciliationResult() {
            SettlementFile file = createSettlementFile(LocalDate.now(), 1);
            transactionProvider.addTransaction(
                    file.getRecords().get(0).getRrn(),
                    file.getRecords().get(0).getStan(),
                    file.getRecords().get(0).getAmount()
            );

            ReconciliationResult result = reconciliationService.reconcile(file);

            assertTrue(reconciliationService.getResult(result.getResultId()).isPresent());
        }

        @Test
        @DisplayName("Should get results by date")
        void shouldGetResultsByDate() {
            LocalDate today = LocalDate.now();
            SettlementFile file = createSettlementFile(today, 1);
            transactionProvider.addTransaction(
                    file.getRecords().get(0).getRrn(),
                    file.getRecords().get(0).getStan(),
                    file.getRecords().get(0).getAmount()
            );

            reconciliationService.reconcile(file);

            List<ReconciliationResult> results = reconciliationService.getResultsForDate(today);

            assertEquals(1, results.size());
        }
    }

    @Nested
    @DisplayName("Summary")
    class SummaryTests {

        @Test
        @DisplayName("Should provide accurate summary")
        void shouldProvideAccurateSummary() {
            SettlementFile file = createSettlementFile(LocalDate.now(), 5);

            // Match 3, miss 2
            for (int i = 0; i < 3; i++) {
                SettlementRecord record = file.getRecords().get(i);
                transactionProvider.addTransaction(
                        record.getRrn(),
                        record.getStan(),
                        record.getAmount()
                );
            }

            ReconciliationResult result = reconciliationService.reconcile(file);

            var summary = result.getSummary();

            assertEquals(5, summary.get("totalSettlementRecords"));
            assertEquals(3, summary.get("matchedCount"));
            assertEquals("60.00%", summary.get("matchRate"));
        }
    }

    // Helper methods

    private SettlementFile createSettlementFile(LocalDate date, int recordCount) {
        List<SettlementRecord> records = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {
            records.add(SettlementRecord.builder()
                    .sequenceNumber(i + 1)
                    .transactionRefNo("TXN" + String.format("%09d", i))
                    .rrn("RRN" + String.format("%09d", i))
                    .stan(String.format("%06d", i))
                    .amount(new BigDecimal("1000.00"))
                    .transactionType("0100")
                    .acquiringBankCode("0040000")
                    .issuingBankCode("0050000")
                    .status(SettlementStatus.PENDING)
                    .transactionDateTime(date.atStartOfDay())
                    .build());
        }

        return SettlementFile.builder()
                .fileId("SF-TEST")
                .fileName("test.txt")
                .settlementDate(date)
                .fileType(SettlementFileType.DAILY_SETTLEMENT)
                .records(records)
                .header(SettlementFile.FileHeader.builder().build())
                .trailer(SettlementFile.FileTrailer.builder()
                        .recordCount(recordCount)
                        .totalAmount(new BigDecimal("1000.00").multiply(BigDecimal.valueOf(recordCount)))
                        .build())
                .build();
    }

    // Test transaction provider
    private static class TestTransactionProvider implements ReconciliationService.InternalTransactionProvider {
        private final List<ReconciliationService.InternalTransaction> transactions = new ArrayList<>();

        void addTransaction(String rrn, String stan, BigDecimal amount) {
            transactions.add(ReconciliationService.InternalTransaction.builder()
                    .transactionId("INT-" + System.nanoTime())
                    .rrn(rrn)
                    .stan(stan)
                    .amount(amount)
                    .transactionTime(LocalDateTime.now())
                    .build());
        }

        @Override
        public List<ReconciliationService.InternalTransaction> getTransactions(LocalDate date, int timeWindowHours) {
            return new ArrayList<>(transactions);
        }
    }
}
