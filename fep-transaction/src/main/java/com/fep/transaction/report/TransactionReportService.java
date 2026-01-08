package com.fep.transaction.report;

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
import java.util.stream.Collectors;

/**
 * Service for generating transaction reports.
 * Provides various analytical reports for transaction data.
 */
public class TransactionReportService {

    private static final Logger log = LoggerFactory.getLogger(TransactionReportService.class);

    private final TransactionRepository repository;

    public TransactionReportService(TransactionRepository repository) {
        this.repository = repository;
    }

    /**
     * Generates a report based on the request.
     */
    public ReportResult generateReport(ReportRequest request) {
        request.validate();

        log.info("Generating {} report for {} to {}",
                request.getReportType(), request.getStartDate(), request.getEndDate());

        long startTime = System.currentTimeMillis();

        // Fetch and filter records
        List<TransactionRecord> records = fetchRecords(request);

        // Generate report based on type
        ReportResult result = switch (request.getReportType()) {
            case DAILY_SUMMARY, MONTHLY_SUMMARY -> generateSummaryReport(request, records);
            case TRANSACTION_DETAIL -> generateDetailReport(request, records);
            case SUCCESS_RATE -> generateSuccessRateReport(request, records);
            case CHANNEL_DISTRIBUTION -> generateChannelReport(request, records);
            case TYPE_DISTRIBUTION -> generateTypeReport(request, records);
            case PEAK_HOUR_ANALYSIS -> generatePeakHourReport(request, records);
            case RESPONSE_TIME -> generateResponseTimeReport(request, records);
            case ERROR_ANALYSIS -> generateErrorReport(request, records);
            default -> generateSummaryReport(request, records);
        };

        result.setReportId(generateReportId(request.getReportType()));
        result.setReportType(request.getReportType());
        result.setFormat(request.getFormat());
        result.setStartDate(request.getStartDate());
        result.setEndDate(request.getEndDate());

        log.info("Report generated in {}ms: {} records processed",
                System.currentTimeMillis() - startTime, records.size());

        return result;
    }

    /**
     * Fetches and filters records based on request criteria.
     */
    private List<TransactionRecord> fetchRecords(ReportRequest request) {
        LocalDateTime start = request.getStartDate().atStartOfDay();
        LocalDateTime end = request.getEndDate().atTime(LocalTime.MAX);

        return repository.findAll().stream()
                .filter(r -> r.getTransactionTime() != null)
                .filter(r -> !r.getTransactionTime().isBefore(start) &&
                            !r.getTransactionTime().isAfter(end))
                .filter(r -> filterByType(r, request.getTransactionTypes()))
                .filter(r -> filterByChannel(r, request.getChannel()))
                .filter(r -> filterByStatus(r, request.isSuccessOnly(), request.isFailedOnly()))
                .filter(r -> filterByTerminal(r, request.getTerminalId()))
                .filter(r -> filterByMerchant(r, request.getMerchantId()))
                .collect(Collectors.toList());
    }

    private boolean filterByType(TransactionRecord record, Set<TransactionType> types) {
        return types == null || types.isEmpty() || types.contains(record.getTransactionType());
    }

    private boolean filterByChannel(TransactionRecord record, String channel) {
        return channel == null || channel.isBlank() || channel.equals(record.getChannel());
    }

    private boolean filterByStatus(TransactionRecord record, boolean successOnly, boolean failedOnly) {
        if (successOnly) {
            return record.getStatus() == TransactionStatus.COMPLETED;
        }
        if (failedOnly) {
            return record.getStatus() == TransactionStatus.FAILED;
        }
        return true;
    }

    private boolean filterByTerminal(TransactionRecord record, String terminalId) {
        return terminalId == null || terminalId.isBlank() || terminalId.equals(record.getTerminalId());
    }

    private boolean filterByMerchant(TransactionRecord record, String merchantId) {
        return merchantId == null || merchantId.isBlank() || merchantId.equals(record.getMerchantId());
    }

    /**
     * Generates summary report.
     */
    private ReportResult generateSummaryReport(ReportRequest request, List<TransactionRecord> records) {
        ReportResult.ReportSummary summary = calculateSummary(records);

        Map<TransactionType, ReportResult.TypeSummary> typeDistribution = calculateTypeDistribution(records);
        Map<String, ReportResult.ChannelSummary> channelDistribution = calculateChannelDistribution(records);

        ReportResult.ReportResultBuilder builder = ReportResult.builder()
                .summary(summary)
                .typeDistribution(typeDistribution)
                .channelDistribution(channelDistribution)
                .totalRecords(records.size());

        if (request.isIncludeDetails()) {
            builder.records(convertToReportRecords(records, request.getPage(), request.getPageSize()));
            builder.currentPage(request.getPage());
            builder.totalPages((int) Math.ceil((double) records.size() / request.getPageSize()));
        }

        return builder.build();
    }

    /**
     * Generates detail report with all transactions.
     */
    private ReportResult generateDetailReport(ReportRequest request, List<TransactionRecord> records) {
        List<ReportResult.TransactionRecord> reportRecords =
                convertToReportRecords(records, request.getPage(), request.getPageSize());

        return ReportResult.builder()
                .summary(calculateSummary(records))
                .records(reportRecords)
                .currentPage(request.getPage())
                .totalPages((int) Math.ceil((double) records.size() / request.getPageSize()))
                .totalRecords(records.size())
                .build();
    }

    /**
     * Generates success rate report.
     */
    private ReportResult generateSuccessRateReport(ReportRequest request, List<TransactionRecord> records) {
        Map<TransactionType, ReportResult.TypeSummary> typeDistribution = calculateTypeDistribution(records);
        Map<String, ReportResult.ChannelSummary> channelDistribution = calculateChannelDistribution(records);

        // Calculate daily success rates
        Map<LocalDate, BigDecimal> dailySuccessRates = records.stream()
                .filter(r -> r.getTransactionTime() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getTransactionTime().toLocalDate(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    long total = list.size();
                                    long success = list.stream()
                                            .filter(r -> r.getStatus() == TransactionStatus.COMPLETED)
                                            .count();
                                    return total > 0 ?
                                            BigDecimal.valueOf((double) success / total * 100)
                                                    .setScale(2, RoundingMode.HALF_UP) :
                                            BigDecimal.ZERO;
                                }
                        )
                ));

        return ReportResult.builder()
                .summary(calculateSummary(records))
                .typeDistribution(typeDistribution)
                .channelDistribution(channelDistribution)
                .totalRecords(records.size())
                .build();
    }

    /**
     * Generates channel distribution report.
     */
    private ReportResult generateChannelReport(ReportRequest request, List<TransactionRecord> records) {
        Map<String, ReportResult.ChannelSummary> channelDistribution = calculateChannelDistribution(records);

        return ReportResult.builder()
                .summary(calculateSummary(records))
                .channelDistribution(channelDistribution)
                .totalRecords(records.size())
                .build();
    }

    /**
     * Generates type distribution report.
     */
    private ReportResult generateTypeReport(ReportRequest request, List<TransactionRecord> records) {
        Map<TransactionType, ReportResult.TypeSummary> typeDistribution = calculateTypeDistribution(records);

        return ReportResult.builder()
                .summary(calculateSummary(records))
                .typeDistribution(typeDistribution)
                .totalRecords(records.size())
                .build();
    }

    /**
     * Generates peak hour analysis report.
     */
    private ReportResult generatePeakHourReport(ReportRequest request, List<TransactionRecord> records) {
        Map<Integer, ReportResult.HourlySummary> hourlyDistribution = new HashMap<>();

        for (int hour = 0; hour < 24; hour++) {
            final int h = hour;
            List<TransactionRecord> hourRecords = records.stream()
                    .filter(r -> r.getTransactionTime() != null &&
                                r.getTransactionTime().getHour() == h)
                    .collect(Collectors.toList());

            BigDecimal totalAmount = hourRecords.stream()
                    .filter(r -> r.getAmount() != null)
                    .map(TransactionRecord::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            double avgResponseTime = hourRecords.stream()
                    .filter(r -> r.getProcessingTimeMs() != null)
                    .mapToLong(TransactionRecord::getProcessingTimeMs)
                    .average()
                    .orElse(0);

            hourlyDistribution.put(hour, ReportResult.HourlySummary.builder()
                    .hour(hour)
                    .count(hourRecords.size())
                    .amount(totalAmount)
                    .averageResponseTime(BigDecimal.valueOf(avgResponseTime).setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        return ReportResult.builder()
                .summary(calculateSummary(records))
                .hourlyDistribution(hourlyDistribution)
                .totalRecords(records.size())
                .build();
    }

    /**
     * Generates response time analysis report.
     */
    private ReportResult generateResponseTimeReport(ReportRequest request, List<TransactionRecord> records) {
        List<Long> responseTimes = records.stream()
                .filter(r -> r.getProcessingTimeMs() != null && r.getProcessingTimeMs() > 0)
                .map(TransactionRecord::getProcessingTimeMs)
                .sorted()
                .collect(Collectors.toList());

        ReportResult.ReportSummary summary = calculateSummary(records);

        // Calculate percentiles
        if (!responseTimes.isEmpty()) {
            int size = responseTimes.size();
            summary.setP95ResponseTime(responseTimes.get((int) (size * 0.95)));
            summary.setP99ResponseTime(responseTimes.get(Math.min((int) (size * 0.99), size - 1)));
        }

        // Group by type for response time comparison
        Map<TransactionType, ReportResult.TypeSummary> typeDistribution = calculateTypeDistribution(records);

        return ReportResult.builder()
                .summary(summary)
                .typeDistribution(typeDistribution)
                .totalRecords(records.size())
                .build();
    }

    /**
     * Generates error analysis report.
     */
    private ReportResult generateErrorReport(ReportRequest request, List<TransactionRecord> records) {
        // Only include failed transactions
        List<TransactionRecord> failedRecords = records.stream()
                .filter(r -> r.getStatus() == TransactionStatus.FAILED)
                .collect(Collectors.toList());

        // Group by response code
        Map<String, Long> errorDistribution = failedRecords.stream()
                .filter(r -> r.getResponseCode() != null)
                .collect(Collectors.groupingBy(
                        TransactionRecord::getResponseCode,
                        Collectors.counting()
                ));

        return ReportResult.builder()
                .summary(calculateSummary(failedRecords))
                .errorDistribution(errorDistribution)
                .records(convertToReportRecords(failedRecords, request.getPage(), request.getPageSize()))
                .totalRecords(failedRecords.size())
                .build();
    }

    /**
     * Calculates summary statistics.
     */
    private ReportResult.ReportSummary calculateSummary(List<TransactionRecord> records) {
        long totalCount = records.size();
        long successCount = records.stream()
                .filter(r -> r.getStatus() == TransactionStatus.COMPLETED)
                .count();

        BigDecimal totalAmount = records.stream()
                .filter(r -> r.getAmount() != null)
                .map(TransactionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgAmount = totalCount > 0 ?
                totalAmount.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        BigDecimal minAmount = records.stream()
                .filter(r -> r.getAmount() != null)
                .map(TransactionRecord::getAmount)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxAmount = records.stream()
                .filter(r -> r.getAmount() != null)
                .map(TransactionRecord::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        double avgResponseTime = records.stream()
                .filter(r -> r.getProcessingTimeMs() != null)
                .mapToLong(TransactionRecord::getProcessingTimeMs)
                .average()
                .orElse(0);

        BigDecimal successRate = totalCount > 0 ?
                BigDecimal.valueOf((double) successCount / totalCount * 100)
                        .setScale(2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        return ReportResult.ReportSummary.builder()
                .totalCount(totalCount)
                .successCount(successCount)
                .failedCount(totalCount - successCount)
                .successRate(successRate)
                .totalAmount(totalAmount)
                .averageAmount(avgAmount)
                .minAmount(minAmount)
                .maxAmount(maxAmount)
                .averageResponseTime(BigDecimal.valueOf(avgResponseTime).setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    /**
     * Calculates type distribution.
     */
    private Map<TransactionType, ReportResult.TypeSummary> calculateTypeDistribution(List<TransactionRecord> records) {
        Map<TransactionType, ReportResult.TypeSummary> distribution = new EnumMap<>(TransactionType.class);

        Map<TransactionType, List<TransactionRecord>> byType = records.stream()
                .filter(r -> r.getTransactionType() != null)
                .collect(Collectors.groupingBy(TransactionRecord::getTransactionType));

        for (Map.Entry<TransactionType, List<TransactionRecord>> entry : byType.entrySet()) {
            List<TransactionRecord> typeRecords = entry.getValue();
            long count = typeRecords.size();
            long success = typeRecords.stream()
                    .filter(r -> r.getStatus() == TransactionStatus.COMPLETED)
                    .count();

            BigDecimal totalAmount = typeRecords.stream()
                    .filter(r -> r.getAmount() != null)
                    .map(TransactionRecord::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            double avgResponseTime = typeRecords.stream()
                    .filter(r -> r.getProcessingTimeMs() != null)
                    .mapToLong(TransactionRecord::getProcessingTimeMs)
                    .average()
                    .orElse(0);

            distribution.put(entry.getKey(), ReportResult.TypeSummary.builder()
                    .type(entry.getKey())
                    .count(count)
                    .amount(totalAmount)
                    .successRate(count > 0 ?
                            BigDecimal.valueOf((double) success / count * 100)
                                    .setScale(2, RoundingMode.HALF_UP) :
                            BigDecimal.ZERO)
                    .averageResponseTime(BigDecimal.valueOf(avgResponseTime).setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        return distribution;
    }

    /**
     * Calculates channel distribution.
     */
    private Map<String, ReportResult.ChannelSummary> calculateChannelDistribution(List<TransactionRecord> records) {
        Map<String, ReportResult.ChannelSummary> distribution = new HashMap<>();

        long totalCount = records.size();

        Map<String, List<TransactionRecord>> byChannel = records.stream()
                .filter(r -> r.getChannel() != null)
                .collect(Collectors.groupingBy(TransactionRecord::getChannel));

        for (Map.Entry<String, List<TransactionRecord>> entry : byChannel.entrySet()) {
            List<TransactionRecord> channelRecords = entry.getValue();
            long count = channelRecords.size();
            long success = channelRecords.stream()
                    .filter(r -> r.getStatus() == TransactionStatus.COMPLETED)
                    .count();

            BigDecimal totalAmount = channelRecords.stream()
                    .filter(r -> r.getAmount() != null)
                    .map(TransactionRecord::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            distribution.put(entry.getKey(), ReportResult.ChannelSummary.builder()
                    .channel(entry.getKey())
                    .count(count)
                    .amount(totalAmount)
                    .successRate(count > 0 ?
                            BigDecimal.valueOf((double) success / count * 100)
                                    .setScale(2, RoundingMode.HALF_UP) :
                            BigDecimal.ZERO)
                    .percentage(totalCount > 0 ?
                            BigDecimal.valueOf((double) count / totalCount * 100)
                                    .setScale(2, RoundingMode.HALF_UP) :
                            BigDecimal.ZERO)
                    .build());
        }

        return distribution;
    }

    /**
     * Converts repository records to report records with pagination.
     */
    private List<ReportResult.TransactionRecord> convertToReportRecords(
            List<TransactionRecord> records, int page, int pageSize) {
        return records.stream()
                .skip((long) page * pageSize)
                .limit(pageSize)
                .map(this::convertToReportRecord)
                .collect(Collectors.toList());
    }

    private ReportResult.TransactionRecord convertToReportRecord(TransactionRecord record) {
        return ReportResult.TransactionRecord.builder()
                .transactionId(record.getTransactionId())
                .type(record.getTransactionType())
                .transactionTime(record.getTransactionTime())
                .maskedAccount(record.getMaskedPan())
                .amount(record.getAmount())
                .currencyCode(record.getCurrencyCode())
                .channel(record.getChannel())
                .responseCode(record.getResponseCode())
                .success(record.getStatus() == TransactionStatus.COMPLETED)
                .responseTimeMs(record.getProcessingTimeMs() != null ? record.getProcessingTimeMs() : 0)
                .terminalId(record.getTerminalId())
                .merchantId(record.getMerchantId())
                .build();
    }

    private String generateReportId(ReportType type) {
        return "RPT" + type.name().substring(0, Math.min(3, type.name().length())) +
               System.currentTimeMillis() +
               String.format("%04d", (int) (Math.random() * 10000));
    }
}
