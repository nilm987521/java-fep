package com.fep.transaction.stats;

import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.repository.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Service for collecting and reporting transaction statistics.
 * Provides real-time metrics and historical analysis.
 */
public class TransactionStatsService {

    private static final Logger log = LoggerFactory.getLogger(TransactionStatsService.class);

    private final TransactionRepository repository;

    // Real-time counters
    private final Map<TransactionType, AtomicLong> typeCounters = new ConcurrentHashMap<>();
    private final Map<TransactionType, AtomicLong> typeSuccessCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> channelCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> hourlyCounters = new ConcurrentHashMap<>();

    // Response time tracking
    private final Map<TransactionType, List<Long>> responseTimesByType = new ConcurrentHashMap<>();
    private static final int MAX_RESPONSE_TIMES = 1000;

    public TransactionStatsService(TransactionRepository repository) {
        this.repository = repository;
        initializeCounters();
    }

    private void initializeCounters() {
        for (TransactionType type : TransactionType.values()) {
            typeCounters.put(type, new AtomicLong(0));
            typeSuccessCounters.put(type, new AtomicLong(0));
            responseTimesByType.put(type, Collections.synchronizedList(new ArrayList<>()));
        }
    }

    /**
     * Records a transaction for statistics.
     */
    public void recordTransaction(TransactionRecord record) {
        if (record == null) return;

        TransactionType type = record.getTransactionType();
        if (type != null) {
            typeCounters.get(type).incrementAndGet();
            if (record.getStatus() == TransactionStatus.COMPLETED) {
                typeSuccessCounters.get(type).incrementAndGet();
            }

            // Track response time
            if (record.getProcessingTimeMs() != null && record.getProcessingTimeMs() > 0) {
                List<Long> times = responseTimesByType.get(type);
                synchronized (times) {
                    if (times.size() >= MAX_RESPONSE_TIMES) {
                        times.remove(0);
                    }
                    times.add(record.getProcessingTimeMs());
                }
            }
        }

        // Track by channel
        String channel = record.getChannel();
        if (channel != null) {
            channelCounters.computeIfAbsent(channel, k -> new AtomicLong(0)).incrementAndGet();
        }

        // Track by hour
        String hourKey = LocalDateTime.now().getHour() + ":00";
        hourlyCounters.computeIfAbsent(hourKey, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Gets overall statistics summary.
     */
    public StatsSummary getOverallStats() {
        long totalCount = typeCounters.values().stream().mapToLong(AtomicLong::get).sum();
        long successCount = typeSuccessCounters.values().stream().mapToLong(AtomicLong::get).sum();

        double successRate = totalCount > 0 ? (double) successCount / totalCount * 100 : 0;

        return StatsSummary.builder()
                .totalTransactions(totalCount)
                .successfulTransactions(successCount)
                .failedTransactions(totalCount - successCount)
                .successRate(BigDecimal.valueOf(successRate).setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    /**
     * Gets statistics by transaction type.
     */
    public Map<TransactionType, TypeStats> getStatsByType() {
        Map<TransactionType, TypeStats> stats = new EnumMap<>(TransactionType.class);

        for (TransactionType type : TransactionType.values()) {
            long count = typeCounters.get(type).get();
            long success = typeSuccessCounters.get(type).get();

            if (count > 0) {
                List<Long> times = responseTimesByType.get(type);
                double avgTime = times.isEmpty() ? 0 :
                        times.stream().mapToLong(Long::longValue).average().orElse(0);

                stats.put(type, TypeStats.builder()
                        .transactionType(type)
                        .totalCount(count)
                        .successCount(success)
                        .failureCount(count - success)
                        .successRate(BigDecimal.valueOf((double) success / count * 100)
                                .setScale(2, RoundingMode.HALF_UP))
                        .averageResponseTimeMs(BigDecimal.valueOf(avgTime)
                                .setScale(2, RoundingMode.HALF_UP))
                        .build());
            }
        }

        return stats;
    }

    /**
     * Gets statistics by channel.
     */
    public Map<String, Long> getStatsByChannel() {
        return channelCounters.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    /**
     * Gets hourly distribution.
     */
    public Map<String, Long> getHourlyDistribution() {
        return hourlyCounters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * Gets statistics for a specific date range from repository.
     */
    public DateRangeStats getStatsForDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        // Filter records by date range from all records
        List<TransactionRecord> records = repository.findAll().stream()
                .filter(r -> r.getTransactionTime() != null)
                .filter(r -> !r.getTransactionTime().isBefore(start) &&
                            !r.getTransactionTime().isAfter(end))
                .collect(Collectors.toList());

        long total = records.size();
        long success = records.stream()
                .filter(r -> r.getStatus() == TransactionStatus.COMPLETED)
                .count();

        BigDecimal totalAmount = records.stream()
                .filter(r -> r.getAmount() != null)
                .map(TransactionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<TransactionType, Long> byType = records.stream()
                .filter(r -> r.getTransactionType() != null)
                .collect(Collectors.groupingBy(
                        TransactionRecord::getTransactionType,
                        Collectors.counting()
                ));

        Map<String, Long> byChannel = records.stream()
                .filter(r -> r.getChannel() != null)
                .collect(Collectors.groupingBy(
                        TransactionRecord::getChannel,
                        Collectors.counting()
                ));

        return DateRangeStats.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalTransactions(total)
                .successfulTransactions(success)
                .failedTransactions(total - success)
                .totalAmount(totalAmount)
                .transactionsByType(byType)
                .transactionsByChannel(byChannel)
                .build();
    }

    /**
     * Gets top N accounts by transaction volume.
     */
    public List<AccountStats> getTopAccountsByVolume(int limit) {
        Map<String, BigDecimal> volumeByAccount = new HashMap<>();
        Map<String, Long> countByAccount = new HashMap<>();

        for (TransactionRecord record : repository.findAll()) {
            String account = record.getSourceAccount();
            if (account != null && record.getAmount() != null) {
                volumeByAccount.merge(account, record.getAmount(), BigDecimal::add);
                countByAccount.merge(account, 1L, Long::sum);
            }
        }

        return volumeByAccount.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(limit)
                .map(e -> AccountStats.builder()
                        .accountId(maskAccount(e.getKey()))
                        .totalVolume(e.getValue())
                        .transactionCount(countByAccount.getOrDefault(e.getKey(), 0L))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Gets average response time percentiles.
     */
    public ResponseTimePercentiles getResponseTimePercentiles(TransactionType type) {
        List<Long> times = new ArrayList<>(responseTimesByType.get(type));
        if (times.isEmpty()) {
            return ResponseTimePercentiles.builder()
                    .transactionType(type)
                    .p50(0)
                    .p90(0)
                    .p95(0)
                    .p99(0)
                    .max(0)
                    .build();
        }

        Collections.sort(times);
        int size = times.size();

        return ResponseTimePercentiles.builder()
                .transactionType(type)
                .p50(times.get((int) (size * 0.50)))
                .p90(times.get((int) (size * 0.90)))
                .p95(times.get((int) (size * 0.95)))
                .p99(times.get(Math.min((int) (size * 0.99), size - 1)))
                .max(times.get(size - 1))
                .build();
    }

    /**
     * Resets all counters.
     */
    public void resetCounters() {
        typeCounters.values().forEach(c -> c.set(0));
        typeSuccessCounters.values().forEach(c -> c.set(0));
        channelCounters.clear();
        hourlyCounters.clear();
        responseTimesByType.values().forEach(List::clear);
        log.info("Statistics counters reset");
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 8) {
            return "****";
        }
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }

    // Inner classes for statistics results

    @lombok.Builder
    @lombok.Data
    public static class StatsSummary {
        private long totalTransactions;
        private long successfulTransactions;
        private long failedTransactions;
        private BigDecimal successRate;
    }

    @lombok.Builder
    @lombok.Data
    public static class TypeStats {
        private TransactionType transactionType;
        private long totalCount;
        private long successCount;
        private long failureCount;
        private BigDecimal successRate;
        private BigDecimal averageResponseTimeMs;
    }

    @lombok.Builder
    @lombok.Data
    public static class DateRangeStats {
        private LocalDate startDate;
        private LocalDate endDate;
        private long totalTransactions;
        private long successfulTransactions;
        private long failedTransactions;
        private BigDecimal totalAmount;
        private Map<TransactionType, Long> transactionsByType;
        private Map<String, Long> transactionsByChannel;
    }

    @lombok.Builder
    @lombok.Data
    public static class AccountStats {
        private String accountId;
        private BigDecimal totalVolume;
        private long transactionCount;
    }

    @lombok.Builder
    @lombok.Data
    public static class ResponseTimePercentiles {
        private TransactionType transactionType;
        private long p50;
        private long p90;
        private long p95;
        private long p99;
        private long max;
    }
}
