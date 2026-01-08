package com.fep.settlement.report;

import com.fep.settlement.clearing.ClearingService;
import com.fep.settlement.domain.*;
import com.fep.settlement.reconciliation.DiscrepancyService;
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

@DisplayName("SettlementReportService Tests")
class SettlementReportServiceTest {

    private SettlementReportService reportService;
    private SettlementRepository repository;
    private ClearingService clearingService;
    private DiscrepancyService discrepancyService;
    private static final String OUR_BANK_CODE = "0040000";

    @BeforeEach
    void setUp() {
        repository = new InMemorySettlementRepository();
        clearingService = new ClearingService(repository, OUR_BANK_CODE);
        discrepancyService = new DiscrepancyService(repository);
        reportService = new SettlementReportService(repository, clearingService, discrepancyService);
    }

    @Nested
    @DisplayName("Daily Report")
    class DailyReportTests {

        @BeforeEach
        void setupTestData() {
            LocalDate today = LocalDate.now();
            SettlementFile file = createTestFile(today, 10);

            // Mark some as matched, some as discrepancy
            for (int i = 0; i < 7; i++) {
                file.getRecords().get(i).setStatus(SettlementStatus.MATCHED);
            }
            for (int i = 7; i < 10; i++) {
                file.getRecords().get(i).setStatus(SettlementStatus.AMOUNT_MISMATCH);
            }

            repository.saveFile(file);
            clearingService.calculateClearing(today);
        }

        @Test
        @DisplayName("Should generate daily report")
        void shouldGenerateDailyReport() {
            SettlementReportService.SettlementReport report =
                    reportService.generateDailyReport(LocalDate.now());

            assertNotNull(report);
            assertNotNull(report.getReportId());
            assertEquals(SettlementReportService.ReportType.DAILY, report.getReportType());
            assertEquals(LocalDate.now(), report.getReportDate());
        }

        @Test
        @DisplayName("Should calculate statistics correctly")
        void shouldCalculateStatisticsCorrectly() {
            SettlementReportService.SettlementReport report =
                    reportService.generateDailyReport(LocalDate.now());

            assertEquals(10, report.getTotalRecords());
            assertEquals(7, report.getMatchedRecords());
            assertEquals(3, report.getDiscrepancyRecords());
            assertEquals(70.0, report.getMatchRate(), 0.01);
        }

        @Test
        @DisplayName("Should include sections")
        void shouldIncludeSections() {
            SettlementReportService.SettlementReport report =
                    reportService.generateDailyReport(LocalDate.now());

            assertNotNull(report.getSections());
            assertNotNull(report.getSections().get("byChannel"));
            assertNotNull(report.getSections().get("byTransactionType"));
            assertNotNull(report.getSections().get("byStatus"));
        }
    }

    @Nested
    @DisplayName("Reconciliation Report")
    class ReconciliationReportTests {

        @BeforeEach
        void setupTestData() {
            LocalDate today = LocalDate.now();
            SettlementFile file = createTestFile(today, 5);
            repository.saveFile(file);

            // Create some discrepancies
            discrepancyService.createDiscrepancy(
                    DiscrepancyType.AMOUNT_MISMATCH,
                    today, "SF-001", "TXN-001", "INT-001",
                    new BigDecimal("1000.00"), new BigDecimal("900.00"),
                    "Amount mismatch"
            );
            discrepancyService.createDiscrepancy(
                    DiscrepancyType.MISSING_INTERNAL,
                    today, "SF-001", "TXN-002", null,
                    new BigDecimal("500.00"), null,
                    "Missing internal"
            );
        }

        @Test
        @DisplayName("Should generate reconciliation report")
        void shouldGenerateReconciliationReport() {
            SettlementReportService.SettlementReport report =
                    reportService.generateReconciliationReport(LocalDate.now());

            assertNotNull(report);
            assertEquals(SettlementReportService.ReportType.RECONCILIATION, report.getReportType());
        }

        @Test
        @DisplayName("Should include discrepancy details")
        void shouldIncludeDiscrepancyDetails() {
            SettlementReportService.SettlementReport report =
                    reportService.generateReconciliationReport(LocalDate.now());

            assertNotNull(report.getSections().get("discrepancies"));
            assertNotNull(report.getSections().get("discrepancyByType"));
        }
    }

    @Nested
    @DisplayName("Clearing Report")
    class ClearingReportTests {

        @BeforeEach
        void setupTestData() {
            LocalDate today = LocalDate.now();
            List<SettlementRecord> records = new ArrayList<>();
            records.add(createRecord("TXN-001", OUR_BANK_CODE, "0050000",
                    new BigDecimal("1000.00")));
            records.add(createRecord("TXN-002", "0050000", OUR_BANK_CODE,
                    new BigDecimal("500.00")));

            SettlementFile file = SettlementFile.builder()
                    .fileId("SF-001")
                    .settlementDate(today)
                    .records(records)
                    .build();
            repository.saveFile(file);
            clearingService.calculateClearing(today);
        }

        @Test
        @DisplayName("Should generate clearing report")
        void shouldGenerateClearingReport() {
            SettlementReportService.SettlementReport report =
                    reportService.generateClearingReport(LocalDate.now());

            assertNotNull(report);
            assertEquals(SettlementReportService.ReportType.CLEARING, report.getReportType());
        }

        @Test
        @DisplayName("Should include net position")
        void shouldIncludeNetPosition() {
            SettlementReportService.SettlementReport report =
                    reportService.generateClearingReport(LocalDate.now());

            assertNotNull(report.getNetPayable());
            assertNotNull(report.getNetReceivable());
            assertNotNull(report.getNetPosition());
        }

        @Test
        @DisplayName("Should include clearing records")
        void shouldIncludeClearingRecords() {
            SettlementReportService.SettlementReport report =
                    reportService.generateClearingReport(LocalDate.now());

            assertNotNull(report.getSections().get("clearingRecords"));
            assertNotNull(report.getSections().get("summary"));
        }
    }

    @Nested
    @DisplayName("Discrepancy Aging Report")
    class DiscrepancyAgingReportTests {

        @BeforeEach
        void setupTestData() {
            LocalDate today = LocalDate.now();
            discrepancyService.createDiscrepancy(
                    DiscrepancyType.AMOUNT_MISMATCH,
                    today, "SF-001", "TXN-001", "INT-001",
                    new BigDecimal("1000.00"), new BigDecimal("900.00"),
                    "Amount mismatch"
            );
        }

        @Test
        @DisplayName("Should generate aging report")
        void shouldGenerateAgingReport() {
            SettlementReportService.SettlementReport report =
                    reportService.generateDiscrepancyAgingReport();

            assertNotNull(report);
            assertEquals(SettlementReportService.ReportType.DISCREPANCY_AGING, report.getReportType());
        }

        @Test
        @DisplayName("Should include aging buckets")
        void shouldIncludeAgingBuckets() {
            SettlementReportService.SettlementReport report =
                    reportService.generateDiscrepancyAgingReport();

            assertNotNull(report.getSections().get("aging"));
            assertNotNull(report.getSections().get("statistics"));
        }
    }

    @Nested
    @DisplayName("Monthly Report")
    class MonthlyReportTests {

        @BeforeEach
        void setupTestData() {
            LocalDate today = LocalDate.now();
            for (int i = 0; i < 5; i++) {
                LocalDate date = today.minusDays(i);
                SettlementFile file = createTestFile(date, 10);
                for (SettlementRecord r : file.getRecords()) {
                    r.setStatus(SettlementStatus.MATCHED);
                }
                repository.saveFile(file);
            }
        }

        @Test
        @DisplayName("Should generate monthly report")
        void shouldGenerateMonthlyReport() {
            LocalDate today = LocalDate.now();
            SettlementReportService.SettlementReport report =
                    reportService.generateMonthlyReport(today.getYear(), today.getMonthValue());

            assertNotNull(report);
            assertEquals(SettlementReportService.ReportType.MONTHLY, report.getReportType());
        }

        @Test
        @DisplayName("Should aggregate monthly data")
        void shouldAggregateMonthlyData() {
            LocalDate today = LocalDate.now();
            SettlementReportService.SettlementReport report =
                    reportService.generateMonthlyReport(today.getYear(), today.getMonthValue());

            assertTrue(report.getTotalFiles() > 0);
            assertTrue(report.getTotalRecords() > 0);
        }

        @Test
        @DisplayName("Should include daily breakdown")
        void shouldIncludeDailyBreakdown() {
            LocalDate today = LocalDate.now();
            SettlementReportService.SettlementReport report =
                    reportService.generateMonthlyReport(today.getYear(), today.getMonthValue());

            assertNotNull(report.getSections().get("dailyBreakdown"));
        }
    }

    @Nested
    @DisplayName("Export")
    class ExportTests {

        @Test
        @DisplayName("Should export report to CSV")
        void shouldExportReportToCsv() {
            LocalDate today = LocalDate.now();
            SettlementFile file = createTestFile(today, 5);
            repository.saveFile(file);

            SettlementReportService.SettlementReport report =
                    reportService.generateDailyReport(today);

            String csv = reportService.exportToCsv(report);

            assertNotNull(csv);
            assertTrue(csv.contains("Settlement Report"));
            assertTrue(csv.contains("Report ID"));
            assertTrue(csv.contains("Total Records"));
        }
    }

    // Helper methods

    private SettlementFile createTestFile(LocalDate date, int recordCount) {
        List<SettlementRecord> records = new ArrayList<>();
        for (int i = 0; i < recordCount; i++) {
            records.add(SettlementRecord.builder()
                    .transactionRefNo("TXN-" + i)
                    .amount(new BigDecimal("1000.00"))
                    .transactionType("0100")
                    .channel("ATM")
                    .status(SettlementStatus.PENDING)
                    .build());
        }

        return SettlementFile.builder()
                .fileId("SF-" + date.toString())
                .settlementDate(date)
                .records(records)
                .build();
    }

    private SettlementRecord createRecord(String txnRef, String issuingBank,
                                          String acquiringBank, BigDecimal amount) {
        return SettlementRecord.builder()
                .transactionRefNo(txnRef)
                .issuingBankCode(issuingBank)
                .acquiringBankCode(acquiringBank)
                .amount(amount)
                .transactionType("0100")
                .status(SettlementStatus.MATCHED)
                .build();
    }
}
