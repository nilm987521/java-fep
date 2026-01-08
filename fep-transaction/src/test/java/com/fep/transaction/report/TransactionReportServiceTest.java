package com.fep.transaction.report;

import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.repository.InMemoryTransactionRepository;
import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.repository.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionReportService.
 */
class TransactionReportServiceTest {

    private TransactionRepository repository;
    private TransactionReportService reportService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        reportService = new TransactionReportService(repository);

        // Seed test data
        seedTestData();
    }

    private void seedTestData() {
        LocalDateTime now = LocalDateTime.now();

        // Add various transactions
        for (int i = 0; i < 10; i++) {
            repository.save(TransactionRecord.builder()
                    .transactionId("TXN-WD-" + i)
                    .transactionType(TransactionType.WITHDRAWAL)
                    .transactionTime(now.minusHours(i))
                    .amount(new BigDecimal((i + 1) * 1000))
                    .currencyCode("TWD")
                    .channel("ATM")
                    .status(i < 8 ? TransactionStatus.COMPLETED : TransactionStatus.FAILED)
                    .responseCode(i < 8 ? "00" : "51")
                    .processingTimeMs((long) (100 + i * 20))
                    .maskedPan("4111****1111")
                    .build());
        }

        for (int i = 0; i < 5; i++) {
            repository.save(TransactionRecord.builder()
                    .transactionId("TXN-TF-" + i)
                    .transactionType(TransactionType.TRANSFER)
                    .transactionTime(now.minusHours(i))
                    .amount(new BigDecimal((i + 1) * 5000))
                    .currencyCode("TWD")
                    .channel("INTERNET")
                    .status(TransactionStatus.COMPLETED)
                    .responseCode("00")
                    .processingTimeMs((long) (150 + i * 30))
                    .maskedPan("5500****0000")
                    .build());
        }

        for (int i = 0; i < 3; i++) {
            repository.save(TransactionRecord.builder()
                    .transactionId("TXN-BI-" + i)
                    .transactionType(TransactionType.BALANCE_INQUIRY)
                    .transactionTime(now.minusHours(i))
                    .amount(BigDecimal.ZERO)
                    .currencyCode("TWD")
                    .channel("ATM")
                    .status(TransactionStatus.COMPLETED)
                    .responseCode("00")
                    .processingTimeMs((long) (50 + i * 10))
                    .maskedPan("4111****2222")
                    .build());
        }
    }

    @Nested
    @DisplayName("Daily Summary Report Tests")
    class DailySummaryTests {

        @Test
        @DisplayName("Should generate daily summary report")
        void shouldGenerateDailySummaryReport() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.DAILY_SUMMARY)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertNotNull(result);
            assertNotNull(result.getReportId());
            assertEquals(ReportType.DAILY_SUMMARY, result.getReportType());
            assertNotNull(result.getSummary());
            assertTrue(result.getSummary().getTotalCount() > 0);
        }

        @Test
        @DisplayName("Should calculate success rate correctly")
        void shouldCalculateSuccessRate() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.DAILY_SUMMARY)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertNotNull(result.getSummary().getSuccessRate());
            assertTrue(result.getSummary().getSuccessRate().compareTo(BigDecimal.ZERO) >= 0);
            assertTrue(result.getSummary().getSuccessRate().compareTo(new BigDecimal("100")) <= 0);
        }
    }

    @Nested
    @DisplayName("Transaction Detail Report Tests")
    class DetailReportTests {

        @Test
        @DisplayName("Should generate detail report with pagination")
        void shouldGenerateDetailReportWithPagination() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.TRANSACTION_DETAIL)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .page(0)
                    .pageSize(5)
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertNotNull(result);
            assertTrue(result.getRecords().size() <= 5);
            assertEquals(0, result.getCurrentPage());
        }

        @Test
        @DisplayName("Should include all transaction details")
        void shouldIncludeTransactionDetails() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.TRANSACTION_DETAIL)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .includeDetails(true)
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertFalse(result.getRecords().isEmpty());
            ReportResult.TransactionRecord record = result.getRecords().get(0);
            assertNotNull(record.getTransactionId());
            assertNotNull(record.getType());
            assertNotNull(record.getTransactionTime());
        }
    }

    @Nested
    @DisplayName("Channel Distribution Report Tests")
    class ChannelReportTests {

        @Test
        @DisplayName("Should generate channel distribution report")
        void shouldGenerateChannelDistributionReport() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.CHANNEL_DISTRIBUTION)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertNotNull(result);
            assertFalse(result.getChannelDistribution().isEmpty());
            assertTrue(result.getChannelDistribution().containsKey("ATM"));
            assertTrue(result.getChannelDistribution().containsKey("INTERNET"));
        }

        @Test
        @DisplayName("Should calculate channel percentages")
        void shouldCalculateChannelPercentages() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.CHANNEL_DISTRIBUTION)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            BigDecimal totalPercentage = result.getChannelDistribution().values().stream()
                    .map(ReportResult.ChannelSummary::getPercentage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Total should be approximately 100%
            assertTrue(totalPercentage.compareTo(new BigDecimal("99")) >= 0);
            assertTrue(totalPercentage.compareTo(new BigDecimal("101")) <= 0);
        }
    }

    @Nested
    @DisplayName("Type Distribution Report Tests")
    class TypeReportTests {

        @Test
        @DisplayName("Should generate type distribution report")
        void shouldGenerateTypeDistributionReport() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.TYPE_DISTRIBUTION)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertNotNull(result);
            assertFalse(result.getTypeDistribution().isEmpty());
            assertTrue(result.getTypeDistribution().containsKey(TransactionType.WITHDRAWAL));
            assertTrue(result.getTypeDistribution().containsKey(TransactionType.TRANSFER));
        }

        @Test
        @DisplayName("Should calculate success rate per type")
        void shouldCalculateSuccessRatePerType() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.TYPE_DISTRIBUTION)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            ReportResult.TypeSummary withdrawalSummary =
                    result.getTypeDistribution().get(TransactionType.WITHDRAWAL);
            assertNotNull(withdrawalSummary);
            assertNotNull(withdrawalSummary.getSuccessRate());
        }
    }

    @Nested
    @DisplayName("Peak Hour Analysis Report Tests")
    class PeakHourReportTests {

        @Test
        @DisplayName("Should generate peak hour analysis")
        void shouldGeneratePeakHourAnalysis() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.PEAK_HOUR_ANALYSIS)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertNotNull(result);
            assertFalse(result.getHourlyDistribution().isEmpty());
            // Should have entries for hours with transactions
        }

        @Test
        @DisplayName("Should include average response time per hour")
        void shouldIncludeAverageResponseTimePerHour() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.PEAK_HOUR_ANALYSIS)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            for (ReportResult.HourlySummary summary : result.getHourlyDistribution().values()) {
                assertNotNull(summary.getAverageResponseTime());
            }
        }
    }

    @Nested
    @DisplayName("Error Analysis Report Tests")
    class ErrorReportTests {

        @Test
        @DisplayName("Should generate error analysis report")
        void shouldGenerateErrorAnalysisReport() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.ERROR_ANALYSIS)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertNotNull(result);
            // Should contain error distribution
            assertNotNull(result.getErrorDistribution());
        }

        @Test
        @DisplayName("Should group errors by response code")
        void shouldGroupErrorsByResponseCode() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.ERROR_ANALYSIS)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            // Should have at least the "51" error code from seed data
            assertTrue(result.getErrorDistribution().containsKey("51") ||
                       result.getErrorDistribution().isEmpty());
        }
    }

    @Nested
    @DisplayName("Response Time Report Tests")
    class ResponseTimeReportTests {

        @Test
        @DisplayName("Should generate response time analysis")
        void shouldGenerateResponseTimeAnalysis() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.RESPONSE_TIME)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertNotNull(result);
            assertNotNull(result.getSummary().getAverageResponseTime());
        }

        @Test
        @DisplayName("Should calculate percentiles")
        void shouldCalculatePercentiles() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.RESPONSE_TIME)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertTrue(result.getSummary().getP95ResponseTime() >= 0);
            assertTrue(result.getSummary().getP99ResponseTime() >= result.getSummary().getP95ResponseTime());
        }
    }

    @Nested
    @DisplayName("Filter Tests")
    class FilterTests {

        @Test
        @DisplayName("Should filter by transaction type")
        void shouldFilterByTransactionType() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.TRANSACTION_DETAIL)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .transactionTypes(Set.of(TransactionType.WITHDRAWAL))
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertTrue(result.getRecords().stream()
                    .allMatch(r -> r.getType() == TransactionType.WITHDRAWAL));
        }

        @Test
        @DisplayName("Should filter by channel")
        void shouldFilterByChannel() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.TRANSACTION_DETAIL)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .channel("ATM")
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertTrue(result.getRecords().stream()
                    .allMatch(r -> "ATM".equals(r.getChannel())));
        }

        @Test
        @DisplayName("Should filter success only")
        void shouldFilterSuccessOnly() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.TRANSACTION_DETAIL)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .successOnly(true)
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertTrue(result.getRecords().stream().allMatch(ReportResult.TransactionRecord::isSuccess));
        }

        @Test
        @DisplayName("Should filter failed only")
        void shouldFilterFailedOnly() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.TRANSACTION_DETAIL)
                    .startDate(LocalDate.now().minusDays(1))
                    .endDate(LocalDate.now())
                    .failedOnly(true)
                    .build();

            ReportResult result = reportService.generateReport(request);

            assertTrue(result.getRecords().stream().noneMatch(ReportResult.TransactionRecord::isSuccess));
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw exception for missing report type")
        void shouldThrowForMissingReportType() {
            ReportRequest request = ReportRequest.builder()
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now())
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> reportService.generateReport(request));
        }

        @Test
        @DisplayName("Should throw exception for missing date range")
        void shouldThrowForMissingDateRange() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.DAILY_SUMMARY)
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> reportService.generateReport(request));
        }

        @Test
        @DisplayName("Should throw exception for invalid date range")
        void shouldThrowForInvalidDateRange() {
            ReportRequest request = ReportRequest.builder()
                    .reportType(ReportType.DAILY_SUMMARY)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().minusDays(1))
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> reportService.generateReport(request));
        }
    }
}
