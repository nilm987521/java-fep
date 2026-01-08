package com.fep.transaction.stats;

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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionStatsService.
 */
class TransactionStatsServiceTest {

    private TransactionRepository repository;
    private TransactionStatsService statsService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        statsService = new TransactionStatsService(repository);
    }

    @Nested
    @DisplayName("Record Transaction Tests")
    class RecordTransactionTests {

        @Test
        @DisplayName("Should record transaction by type")
        void shouldRecordTransactionByType() {
            TransactionRecord record = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            statsService.recordTransaction(record);

            TransactionStatsService.StatsSummary summary = statsService.getOverallStats();
            assertEquals(1, summary.getTotalTransactions());
            assertEquals(1, summary.getSuccessfulTransactions());
        }

        @Test
        @DisplayName("Should record failed transaction")
        void shouldRecordFailedTransaction() {
            TransactionRecord record = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.FAILED);
            statsService.recordTransaction(record);

            TransactionStatsService.StatsSummary summary = statsService.getOverallStats();
            assertEquals(1, summary.getTotalTransactions());
            assertEquals(0, summary.getSuccessfulTransactions());
            assertEquals(1, summary.getFailedTransactions());
        }

        @Test
        @DisplayName("Should handle null record gracefully")
        void shouldHandleNullRecord() {
            statsService.recordTransaction(null);
            TransactionStatsService.StatsSummary summary = statsService.getOverallStats();
            assertEquals(0, summary.getTotalTransactions());
        }

        @Test
        @DisplayName("Should track by channel")
        void shouldTrackByChannel() {
            TransactionRecord record = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            record.setChannel("ATM");
            statsService.recordTransaction(record);

            Map<String, Long> byChannel = statsService.getStatsByChannel();
            assertEquals(1L, byChannel.get("ATM"));
        }

        @Test
        @DisplayName("Should track response time")
        void shouldTrackResponseTime() {
            TransactionRecord record = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            record.setProcessingTimeMs(150L);
            statsService.recordTransaction(record);

            TransactionStatsService.ResponseTimePercentiles percentiles =
                    statsService.getResponseTimePercentiles(TransactionType.WITHDRAWAL);
            assertEquals(150L, percentiles.getP50());
        }
    }

    @Nested
    @DisplayName("Overall Stats Tests")
    class OverallStatsTests {

        @Test
        @DisplayName("Should calculate success rate correctly")
        void shouldCalculateSuccessRateCorrectly() {
            statsService.recordTransaction(createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED));
            statsService.recordTransaction(createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED));
            statsService.recordTransaction(createRecord(TransactionType.WITHDRAWAL, TransactionStatus.FAILED));
            statsService.recordTransaction(createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED));

            TransactionStatsService.StatsSummary summary = statsService.getOverallStats();
            assertEquals(4, summary.getTotalTransactions());
            assertEquals(3, summary.getSuccessfulTransactions());
            assertEquals(1, summary.getFailedTransactions());
            assertEquals(new BigDecimal("75.00"), summary.getSuccessRate());
        }

        @Test
        @DisplayName("Should handle zero transactions")
        void shouldHandleZeroTransactions() {
            TransactionStatsService.StatsSummary summary = statsService.getOverallStats();
            assertEquals(0, summary.getTotalTransactions());
            assertEquals(BigDecimal.ZERO.setScale(2), summary.getSuccessRate());
        }
    }

    @Nested
    @DisplayName("Stats By Type Tests")
    class StatsByTypeTests {

        @Test
        @DisplayName("Should return stats grouped by transaction type")
        void shouldReturnStatsGroupedByType() {
            statsService.recordTransaction(createRecordWithTime(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED, 100L));
            statsService.recordTransaction(createRecordWithTime(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED, 200L));
            statsService.recordTransaction(createRecordWithTime(TransactionType.TRANSFER, TransactionStatus.COMPLETED, 150L));

            Map<TransactionType, TransactionStatsService.TypeStats> statsByType = statsService.getStatsByType();

            assertTrue(statsByType.containsKey(TransactionType.WITHDRAWAL));
            assertTrue(statsByType.containsKey(TransactionType.TRANSFER));
            assertEquals(2L, statsByType.get(TransactionType.WITHDRAWAL).getTotalCount());
            assertEquals(1L, statsByType.get(TransactionType.TRANSFER).getTotalCount());
        }

        @Test
        @DisplayName("Should calculate average response time by type")
        void shouldCalculateAverageResponseTimeByType() {
            statsService.recordTransaction(createRecordWithTime(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED, 100L));
            statsService.recordTransaction(createRecordWithTime(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED, 200L));

            Map<TransactionType, TransactionStatsService.TypeStats> statsByType = statsService.getStatsByType();
            TransactionStatsService.TypeStats withdrawalStats = statsByType.get(TransactionType.WITHDRAWAL);

            assertEquals(new BigDecimal("150.00"), withdrawalStats.getAverageResponseTimeMs());
        }

        @Test
        @DisplayName("Should calculate success rate by type")
        void shouldCalculateSuccessRateByType() {
            statsService.recordTransaction(createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED));
            statsService.recordTransaction(createRecord(TransactionType.WITHDRAWAL, TransactionStatus.FAILED));

            Map<TransactionType, TransactionStatsService.TypeStats> statsByType = statsService.getStatsByType();
            TransactionStatsService.TypeStats withdrawalStats = statsByType.get(TransactionType.WITHDRAWAL);

            assertEquals(new BigDecimal("50.00"), withdrawalStats.getSuccessRate());
        }
    }

    @Nested
    @DisplayName("Stats By Channel Tests")
    class StatsByChannelTests {

        @Test
        @DisplayName("Should return stats grouped by channel")
        void shouldReturnStatsGroupedByChannel() {
            TransactionRecord atmRecord = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            atmRecord.setChannel("ATM");
            TransactionRecord posRecord = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            posRecord.setChannel("POS");

            statsService.recordTransaction(atmRecord);
            statsService.recordTransaction(atmRecord);
            statsService.recordTransaction(posRecord);

            Map<String, Long> byChannel = statsService.getStatsByChannel();
            assertEquals(2L, byChannel.get("ATM"));
            assertEquals(1L, byChannel.get("POS"));
        }
    }

    @Nested
    @DisplayName("Hourly Distribution Tests")
    class HourlyDistributionTests {

        @Test
        @DisplayName("Should track hourly distribution")
        void shouldTrackHourlyDistribution() {
            statsService.recordTransaction(createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED));
            statsService.recordTransaction(createRecord(TransactionType.TRANSFER, TransactionStatus.COMPLETED));

            Map<String, Long> hourly = statsService.getHourlyDistribution();
            assertFalse(hourly.isEmpty());
            // Current hour should have 2 transactions
            assertTrue(hourly.values().stream().mapToLong(Long::longValue).sum() >= 2);
        }
    }

    @Nested
    @DisplayName("Date Range Stats Tests")
    class DateRangeStatsTests {

        @Test
        @DisplayName("Should return stats for date range")
        void shouldReturnStatsForDateRange() {
            // Add records to repository with proper time
            LocalDateTime now = LocalDateTime.now();
            TransactionRecord record1 = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            record1.setTransactionTime(now);
            record1.setAmount(new BigDecimal("1000"));

            TransactionRecord record2 = createRecord(TransactionType.TRANSFER, TransactionStatus.COMPLETED);
            record2.setTransactionTime(now);
            record2.setAmount(new BigDecimal("2000"));

            repository.save(record1);
            repository.save(record2);

            TransactionStatsService.DateRangeStats stats = statsService.getStatsForDateRange(
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            assertEquals(2, stats.getTotalTransactions());
            assertEquals(2, stats.getSuccessfulTransactions());
            assertEquals(new BigDecimal("3000"), stats.getTotalAmount());
        }

        @Test
        @DisplayName("Should filter records outside date range")
        void shouldFilterRecordsOutsideDateRange() {
            LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
            TransactionRecord oldRecord = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            oldRecord.setTransactionTime(lastMonth);
            repository.save(oldRecord);

            TransactionStatsService.DateRangeStats stats = statsService.getStatsForDateRange(
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

            assertEquals(0, stats.getTotalTransactions());
        }

        @Test
        @DisplayName("Should group by type in date range stats")
        void shouldGroupByTypeInDateRangeStats() {
            LocalDateTime now = LocalDateTime.now();

            TransactionRecord withdrawalRecord = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            withdrawalRecord.setTransactionTime(now);
            repository.save(withdrawalRecord);

            TransactionRecord transferRecord = createRecord(TransactionType.TRANSFER, TransactionStatus.COMPLETED);
            transferRecord.setTransactionTime(now);
            repository.save(transferRecord);

            TransactionStatsService.DateRangeStats stats = statsService.getStatsForDateRange(
                    LocalDate.now(), LocalDate.now());

            Map<TransactionType, Long> byType = stats.getTransactionsByType();
            assertEquals(1L, byType.get(TransactionType.WITHDRAWAL));
            assertEquals(1L, byType.get(TransactionType.TRANSFER));
        }
    }

    @Nested
    @DisplayName("Top Accounts Tests")
    class TopAccountsTests {

        @Test
        @DisplayName("Should return top accounts by volume")
        void shouldReturnTopAccountsByVolume() {
            TransactionRecord record1 = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            record1.setSourceAccount("1234567890");
            record1.setAmount(new BigDecimal("5000"));
            repository.save(record1);

            TransactionRecord record2 = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            record2.setSourceAccount("1234567890");
            record2.setAmount(new BigDecimal("3000"));
            repository.save(record2);

            TransactionRecord record3 = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            record3.setSourceAccount("0987654321");
            record3.setAmount(new BigDecimal("2000"));
            repository.save(record3);

            List<TransactionStatsService.AccountStats> topAccounts = statsService.getTopAccountsByVolume(10);

            assertFalse(topAccounts.isEmpty());
            // First account should have highest volume (8000)
            assertEquals(new BigDecimal("8000"), topAccounts.get(0).getTotalVolume());
            assertEquals(2L, topAccounts.get(0).getTransactionCount());
        }

        @Test
        @DisplayName("Should mask account numbers")
        void shouldMaskAccountNumbers() {
            TransactionRecord record = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
            record.setSourceAccount("1234567890123456");
            record.setAmount(new BigDecimal("1000"));
            repository.save(record);

            List<TransactionStatsService.AccountStats> topAccounts = statsService.getTopAccountsByVolume(10);

            assertFalse(topAccounts.isEmpty());
            String maskedAccount = topAccounts.get(0).getAccountId();
            assertTrue(maskedAccount.contains("****"));
            assertEquals("1234****3456", maskedAccount);
        }

        @Test
        @DisplayName("Should limit results")
        void shouldLimitResults() {
            for (int i = 0; i < 20; i++) {
                TransactionRecord record = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
                record.setSourceAccount("ACCT" + String.format("%012d", i));
                record.setAmount(new BigDecimal(1000 + i * 100));
                repository.save(record);
            }

            List<TransactionStatsService.AccountStats> topAccounts = statsService.getTopAccountsByVolume(5);
            assertEquals(5, topAccounts.size());
        }
    }

    @Nested
    @DisplayName("Response Time Percentiles Tests")
    class ResponseTimePercentilesTests {

        @Test
        @DisplayName("Should calculate percentiles correctly")
        void shouldCalculatePercentilesCorrectly() {
            // Add 100 records with sequential response times
            for (long i = 1; i <= 100; i++) {
                TransactionRecord record = createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED);
                record.setProcessingTimeMs(i * 10);
                statsService.recordTransaction(record);
            }

            TransactionStatsService.ResponseTimePercentiles percentiles =
                    statsService.getResponseTimePercentiles(TransactionType.WITHDRAWAL);

            assertEquals(TransactionType.WITHDRAWAL, percentiles.getTransactionType());
            assertTrue(percentiles.getP50() > 0);
            assertTrue(percentiles.getP90() > percentiles.getP50());
            assertTrue(percentiles.getP95() > percentiles.getP90());
            assertTrue(percentiles.getP99() >= percentiles.getP95());
            assertEquals(1000L, percentiles.getMax()); // 100 * 10 = 1000
        }

        @Test
        @DisplayName("Should return zero percentiles for empty data")
        void shouldReturnZeroPercentilesForEmptyData() {
            TransactionStatsService.ResponseTimePercentiles percentiles =
                    statsService.getResponseTimePercentiles(TransactionType.BALANCE_INQUIRY);

            assertEquals(0, percentiles.getP50());
            assertEquals(0, percentiles.getP90());
            assertEquals(0, percentiles.getP95());
            assertEquals(0, percentiles.getP99());
            assertEquals(0, percentiles.getMax());
        }
    }

    @Nested
    @DisplayName("Reset Counters Tests")
    class ResetCountersTests {

        @Test
        @DisplayName("Should reset all counters")
        void shouldResetAllCounters() {
            statsService.recordTransaction(createRecord(TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED));
            statsService.recordTransaction(createRecord(TransactionType.TRANSFER, TransactionStatus.COMPLETED));

            statsService.resetCounters();

            TransactionStatsService.StatsSummary summary = statsService.getOverallStats();
            assertEquals(0, summary.getTotalTransactions());
            assertTrue(statsService.getStatsByChannel().isEmpty());
            assertTrue(statsService.getHourlyDistribution().isEmpty());
        }
    }

    // Helper methods

    private TransactionRecord createRecord(TransactionType type, TransactionStatus status) {
        return TransactionRecord.builder()
                .transactionId("TXN-" + System.nanoTime())
                .transactionType(type)
                .status(status)
                .amount(new BigDecimal("1000"))
                .currencyCode("TWD")
                .build();
    }

    private TransactionRecord createRecordWithTime(TransactionType type, TransactionStatus status, long processingTimeMs) {
        TransactionRecord record = createRecord(type, status);
        record.setProcessingTimeMs(processingTimeMs);
        return record;
    }
}
