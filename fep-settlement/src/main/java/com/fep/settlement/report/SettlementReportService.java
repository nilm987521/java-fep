package com.fep.settlement.report;

import com.fep.settlement.clearing.ClearingService;
import com.fep.settlement.domain.*;
import com.fep.settlement.reconciliation.DiscrepancyService;
import com.fep.settlement.reconciliation.ReconciliationResult;
import com.fep.settlement.repository.SettlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating settlement reports.
 */
public class SettlementReportService {

    private static final Logger log = LoggerFactory.getLogger(SettlementReportService.class);

    private final SettlementRepository repository;
    private final ClearingService clearingService;
    private final DiscrepancyService discrepancyService;

    public SettlementReportService(SettlementRepository repository,
                                   ClearingService clearingService,
                                   DiscrepancyService discrepancyService) {
        this.repository = repository;
        this.clearingService = clearingService;
        this.discrepancyService = discrepancyService;
    }

    /**
     * Generate daily settlement report.
     */
    public SettlementReport generateDailyReport(LocalDate date) {
        log.info("Generating daily settlement report for {}", date);

        SettlementReport report = SettlementReport.builder()
                .reportId(generateReportId())
                .reportType(ReportType.DAILY)
                .reportDate(date)
                .generatedAt(LocalDateTime.now())
                .build();

        // Get settlement files for the date
        List<SettlementFile> files = repository.findFilesByDate(date);
        report.setTotalFiles(files.size());

        // Aggregate statistics
        int totalRecords = 0;
        int matchedRecords = 0;
        int discrepancyRecords = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal matchedAmount = BigDecimal.ZERO;

        Map<String, Integer> byChannel = new HashMap<>();
        Map<String, Integer> byTransactionType = new HashMap<>();
        Map<SettlementStatus, Integer> byStatus = new EnumMap<>(SettlementStatus.class);

        for (SettlementFile file : files) {
            for (SettlementRecord record : file.getRecords()) {
                totalRecords++;
                if (record.getAmount() != null) {
                    totalAmount = totalAmount.add(record.getAmount());
                }

                if (record.getStatus() == SettlementStatus.MATCHED) {
                    matchedRecords++;
                    if (record.getAmount() != null) {
                        matchedAmount = matchedAmount.add(record.getAmount());
                    }
                } else if (record.getStatus().isDiscrepancy()) {
                    discrepancyRecords++;
                }

                // Count by channel
                String channel = record.getChannel() != null ? record.getChannel() : "UNKNOWN";
                byChannel.merge(channel, 1, Integer::sum);

                // Count by transaction type
                String txnType = record.getTransactionType() != null ?
                        record.getTransactionType() : "UNKNOWN";
                byTransactionType.merge(txnType, 1, Integer::sum);

                // Count by status
                byStatus.merge(record.getStatus(), 1, Integer::sum);
            }
        }

        report.setTotalRecords(totalRecords);
        report.setMatchedRecords(matchedRecords);
        report.setDiscrepancyRecords(discrepancyRecords);
        report.setTotalAmount(totalAmount);
        report.setMatchedAmount(matchedAmount);

        // Calculate match rate
        double matchRate = totalRecords > 0 ?
                (matchedRecords * 100.0) / totalRecords : 0.0;
        report.setMatchRate(matchRate);

        // Get clearing summary
        ClearingService.ClearingSummary clearingSummary = clearingService.getClearingSummary(date);
        report.setNetPayable(clearingSummary.getTotalNetPayable());
        report.setNetReceivable(clearingSummary.getTotalNetReceivable());
        report.setNetPosition(clearingSummary.getNetPosition());

        // Get discrepancy summary
        List<Discrepancy> discrepancies = discrepancyService.getDiscrepanciesByDate(date);
        Map<DiscrepancyType, Long> discrepancyByType = discrepancies.stream()
                .collect(Collectors.groupingBy(Discrepancy::getType, Collectors.counting()));

        // Build report sections
        report.setSections(new LinkedHashMap<>());
        report.getSections().put("byChannel", byChannel);
        report.getSections().put("byTransactionType", byTransactionType);
        report.getSections().put("byStatus", byStatus);
        report.getSections().put("discrepancyByType", discrepancyByType);
        report.getSections().put("clearingSummary", Map.of(
                "totalRecords", clearingSummary.getTotalRecords(),
                "pendingCount", clearingSummary.getPendingCount(),
                "confirmedCount", clearingSummary.getConfirmedCount(),
                "settledCount", clearingSummary.getSettledCount()
        ));

        log.info("Daily report generated: {} records, {:.2f}% match rate",
                totalRecords, matchRate);

        return report;
    }

    /**
     * Generate reconciliation report.
     */
    public SettlementReport generateReconciliationReport(LocalDate date) {
        log.info("Generating reconciliation report for {}", date);

        SettlementReport report = SettlementReport.builder()
                .reportId(generateReportId())
                .reportType(ReportType.RECONCILIATION)
                .reportDate(date)
                .generatedAt(LocalDateTime.now())
                .build();

        List<SettlementFile> files = repository.findFilesByDate(date);
        List<Discrepancy> discrepancies = discrepancyService.getDiscrepanciesByDate(date);

        // Summary
        int totalRecords = files.stream().mapToInt(SettlementFile::getTotalRecordCount).sum();
        long matchedRecords = files.stream()
                .flatMap(f -> f.getRecords().stream())
                .filter(r -> r.getStatus() == SettlementStatus.MATCHED)
                .count();

        report.setTotalRecords(totalRecords);
        report.setMatchedRecords((int) matchedRecords);
        report.setDiscrepancyRecords(discrepancies.size());
        report.setMatchRate(totalRecords > 0 ? (matchedRecords * 100.0) / totalRecords : 0.0);

        // Discrepancy details
        List<Map<String, Object>> discrepancyDetails = new ArrayList<>();
        for (Discrepancy d : discrepancies) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("discrepancyId", d.getDiscrepancyId());
            detail.put("type", d.getType().getChineseName());
            detail.put("priority", d.getPriority().getChineseName());
            detail.put("status", d.getStatus().getChineseDescription());
            detail.put("settlementAmount", d.getSettlementAmount());
            detail.put("internalAmount", d.getInternalAmount());
            detail.put("difference", d.getDifferenceAmount());
            detail.put("settlementRef", d.getSettlementRecordRef());
            detail.put("internalRef", d.getInternalTransactionRef());
            discrepancyDetails.add(detail);
        }

        report.setSections(new LinkedHashMap<>());
        report.getSections().put("discrepancies", discrepancyDetails);

        // Summary by type
        Map<String, Long> byType = discrepancies.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getType().getChineseName(),
                        Collectors.counting()
                ));
        report.getSections().put("discrepancyByType", byType);

        // Summary by priority
        Map<String, Long> byPriority = discrepancies.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getPriority().getChineseName(),
                        Collectors.counting()
                ));
        report.getSections().put("discrepancyByPriority", byPriority);

        return report;
    }

    /**
     * Generate clearing report.
     */
    public SettlementReport generateClearingReport(LocalDate date) {
        log.info("Generating clearing report for {}", date);

        SettlementReport report = SettlementReport.builder()
                .reportId(generateReportId())
                .reportType(ReportType.CLEARING)
                .reportDate(date)
                .generatedAt(LocalDateTime.now())
                .build();

        List<ClearingRecord> clearingRecords = clearingService.getClearingRecordsByDate(date);
        ClearingService.ClearingSummary summary = clearingService.getClearingSummary(date);

        report.setNetPayable(summary.getTotalNetPayable());
        report.setNetReceivable(summary.getTotalNetReceivable());
        report.setNetPosition(summary.getNetPosition());

        // Clearing record details
        List<Map<String, Object>> clearingDetails = new ArrayList<>();
        for (ClearingRecord cr : clearingRecords) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("clearingId", cr.getClearingId());
            detail.put("counterparty", cr.getCounterpartyBankCode());
            detail.put("category", cr.getTransactionCategory());
            detail.put("debitCount", cr.getDebitCount());
            detail.put("debitAmount", cr.getDebitAmount());
            detail.put("creditCount", cr.getCreditCount());
            detail.put("creditAmount", cr.getCreditAmount());
            detail.put("netAmount", cr.getNetAmount());
            detail.put("feeAmount", cr.getFeeAmount());
            detail.put("status", cr.getStatus().getChineseDescription());
            clearingDetails.add(detail);
        }

        report.setSections(new LinkedHashMap<>());
        report.getSections().put("clearingRecords", clearingDetails);
        report.getSections().put("byCounterparty", summary.getByCounterparty());
        report.getSections().put("summary", Map.of(
                "totalRecords", summary.getTotalRecords(),
                "pendingCount", summary.getPendingCount(),
                "confirmedCount", summary.getConfirmedCount(),
                "settledCount", summary.getSettledCount(),
                "netPayable", summary.getTotalNetPayable(),
                "netReceivable", summary.getTotalNetReceivable(),
                "netPosition", summary.getNetPosition()
        ));

        return report;
    }

    /**
     * Generate discrepancy aging report.
     */
    public SettlementReport generateDiscrepancyAgingReport() {
        log.info("Generating discrepancy aging report");

        SettlementReport report = SettlementReport.builder()
                .reportId(generateReportId())
                .reportType(ReportType.DISCREPANCY_AGING)
                .reportDate(LocalDate.now())
                .generatedAt(LocalDateTime.now())
                .build();

        Map<String, List<Discrepancy>> aging = discrepancyService.getAgingReport();
        Map<String, Object> stats = discrepancyService.getStatistics();

        report.setDiscrepancyRecords(
                aging.values().stream().mapToInt(List::size).sum()
        );

        // Convert to report format
        Map<String, Object> agingSection = new LinkedHashMap<>();
        for (Map.Entry<String, List<Discrepancy>> entry : aging.entrySet()) {
            List<Map<String, Object>> items = new ArrayList<>();
            BigDecimal totalAmount = BigDecimal.ZERO;

            for (Discrepancy d : entry.getValue()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("discrepancyId", d.getDiscrepancyId());
                item.put("type", d.getType().getChineseName());
                item.put("priority", d.getPriority().getChineseName());
                item.put("amount", d.getAbsoluteDifference());
                item.put("createdAt", d.getCreatedAt());
                item.put("assignedTo", d.getAssignedTo());
                items.add(item);

                totalAmount = totalAmount.add(d.getAbsoluteDifference());
            }

            agingSection.put(entry.getKey(), Map.of(
                    "count", entry.getValue().size(),
                    "totalAmount", totalAmount,
                    "items", items
            ));
        }

        report.setSections(new LinkedHashMap<>());
        report.getSections().put("aging", agingSection);
        report.getSections().put("statistics", stats);

        return report;
    }

    /**
     * Generate monthly summary report.
     */
    public SettlementReport generateMonthlyReport(int year, int month) {
        log.info("Generating monthly report for {}-{}", year, month);

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        SettlementReport report = SettlementReport.builder()
                .reportId(generateReportId())
                .reportType(ReportType.MONTHLY)
                .reportDate(startDate)
                .generatedAt(LocalDateTime.now())
                .build();

        List<SettlementFile> files = repository.findFilesByDateRange(startDate, endDate);

        // Aggregate monthly statistics
        int totalRecords = 0;
        int matchedRecords = 0;
        int discrepancyRecords = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<LocalDate, Integer> dailyRecordCounts = new TreeMap<>();
        Map<LocalDate, BigDecimal> dailyAmounts = new TreeMap<>();

        for (SettlementFile file : files) {
            LocalDate fileDate = file.getSettlementDate();
            int fileRecords = file.getTotalRecordCount();
            BigDecimal fileAmount = file.getTotalAmount();

            totalRecords += fileRecords;
            totalAmount = totalAmount.add(fileAmount);

            dailyRecordCounts.merge(fileDate, fileRecords, Integer::sum);
            dailyAmounts.merge(fileDate, fileAmount, BigDecimal::add);

            for (SettlementRecord record : file.getRecords()) {
                if (record.getStatus() == SettlementStatus.MATCHED) {
                    matchedRecords++;
                } else if (record.getStatus().isDiscrepancy()) {
                    discrepancyRecords++;
                }
            }
        }

        report.setTotalFiles(files.size());
        report.setTotalRecords(totalRecords);
        report.setMatchedRecords(matchedRecords);
        report.setDiscrepancyRecords(discrepancyRecords);
        report.setTotalAmount(totalAmount);
        report.setMatchRate(totalRecords > 0 ? (matchedRecords * 100.0) / totalRecords : 0.0);

        // Daily breakdown
        List<Map<String, Object>> dailyBreakdown = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date);
            day.put("recordCount", dailyRecordCounts.getOrDefault(date, 0));
            day.put("amount", dailyAmounts.getOrDefault(date, BigDecimal.ZERO));
            dailyBreakdown.add(day);
        }

        report.setSections(new LinkedHashMap<>());
        report.getSections().put("dailyBreakdown", dailyBreakdown);
        report.getSections().put("averageDaily", Map.of(
                "records", totalRecords / Math.max(1, files.size()),
                "amount", files.isEmpty() ? BigDecimal.ZERO :
                        totalAmount.divide(BigDecimal.valueOf(files.size()), 2, RoundingMode.HALF_UP)
        ));

        return report;
    }

    /**
     * Export report to CSV format.
     */
    public String exportToCsv(SettlementReport report) {
        StringBuilder csv = new StringBuilder();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Header
        csv.append("Settlement Report\n");
        csv.append("Report ID,").append(report.getReportId()).append("\n");
        csv.append("Report Type,").append(report.getReportType()).append("\n");
        csv.append("Report Date,").append(report.getReportDate().format(dateFormatter)).append("\n");
        csv.append("Generated At,").append(report.getGeneratedAt()).append("\n");
        csv.append("\n");

        // Summary
        csv.append("Summary\n");
        csv.append("Total Records,").append(report.getTotalRecords()).append("\n");
        csv.append("Matched Records,").append(report.getMatchedRecords()).append("\n");
        csv.append("Discrepancy Records,").append(report.getDiscrepancyRecords()).append("\n");
        csv.append("Match Rate,").append(String.format("%.2f%%", report.getMatchRate())).append("\n");
        if (report.getTotalAmount() != null) {
            csv.append("Total Amount,").append(report.getTotalAmount()).append("\n");
        }
        csv.append("\n");

        // Sections
        if (report.getSections() != null) {
            for (Map.Entry<String, Object> section : report.getSections().entrySet()) {
                csv.append(section.getKey()).append("\n");
                appendSectionToCsv(csv, section.getValue());
                csv.append("\n");
            }
        }

        return csv.toString();
    }

    private void appendSectionToCsv(StringBuilder csv, Object value) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toString() : "";
                String val = entry.getValue() != null ? entry.getValue().toString() : "";
                csv.append(key).append(",").append(val).append("\n");
            }
        } else if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<?> rawList = (List<?>) value;
            if (!rawList.isEmpty() && rawList.get(0) instanceof Map) {
                @SuppressWarnings("unchecked")
                List<Map<?, ?>> list = (List<Map<?, ?>>) rawList;
                // Header row - convert keys to strings
                csv.append(list.get(0).keySet().stream()
                        .map(k -> k != null ? k.toString() : "")
                        .collect(Collectors.joining(","))).append("\n");
                // Data rows
                for (Map<?, ?> item : list) {
                    csv.append(item.values().stream()
                            .map(v -> v != null ? v.toString() : "")
                            .collect(Collectors.joining(","))).append("\n");
                }
            }
        }
    }

    private String generateReportId() {
        return "RPT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Report types.
     */
    public enum ReportType {
        DAILY("日報表"),
        RECONCILIATION("對帳報表"),
        CLEARING("清算報表"),
        DISCREPANCY_AGING("差異帳齡報表"),
        MONTHLY("月報表");

        private final String chineseName;

        ReportType(String chineseName) {
            this.chineseName = chineseName;
        }

        public String getChineseName() {
            return chineseName;
        }
    }

    /**
     * Settlement report.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SettlementReport {
        private String reportId;
        private ReportType reportType;
        private LocalDate reportDate;
        private LocalDateTime generatedAt;
        private int totalFiles;
        private int totalRecords;
        private int matchedRecords;
        private int discrepancyRecords;
        private double matchRate;
        private BigDecimal totalAmount;
        private BigDecimal matchedAmount;
        private BigDecimal netPayable;
        private BigDecimal netReceivable;
        private BigDecimal netPosition;
        private Map<String, Object> sections;
    }
}
