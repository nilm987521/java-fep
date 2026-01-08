package com.fep.settlement.reconciliation;

import com.fep.settlement.domain.*;
import com.fep.settlement.repository.SettlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for reconciling settlement files with internal transactions.
 */
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final SettlementRepository settlementRepository;
    private final InternalTransactionProvider transactionProvider;
    private final Map<String, ReconciliationResult> resultCache = new ConcurrentHashMap<>();

    public ReconciliationService(SettlementRepository settlementRepository,
                                 InternalTransactionProvider transactionProvider) {
        this.settlementRepository = settlementRepository;
        this.transactionProvider = transactionProvider;
    }

    /**
     * Reconcile a settlement file with internal transactions.
     *
     * @param settlementFile the settlement file to reconcile
     * @param config reconciliation configuration
     * @return reconciliation result
     */
    public ReconciliationResult reconcile(SettlementFile settlementFile, ReconciliationConfig config) {
        log.info("Starting reconciliation for file: {} with {} records",
                settlementFile.getFileId(), settlementFile.getTotalRecordCount());

        String resultId = generateResultId();
        ReconciliationResult result = ReconciliationResult.builder()
                .resultId(resultId)
                .settlementDate(settlementFile.getSettlementDate())
                .settlementFileId(settlementFile.getFileId())
                .startTime(LocalDateTime.now())
                .status(ReconciliationStatus.IN_PROGRESS)
                .totalSettlementRecords(settlementFile.getTotalRecordCount())
                .matchedAmount(BigDecimal.ZERO)
                .discrepancyAmount(BigDecimal.ZERO)
                .build();

        try {
            // Get internal transactions for the settlement date
            LocalDate settlementDate = settlementFile.getSettlementDate();
            List<InternalTransaction> internalTransactions = transactionProvider.getTransactions(
                    settlementDate,
                    config.getMatchingTimeWindowHours()
            );
            result.setTotalInternalTransactions(internalTransactions.size());

            log.info("Found {} internal transactions for date {}", internalTransactions.size(), settlementDate);

            // Build lookup maps for matching
            Map<String, InternalTransaction> rrnMap = new HashMap<>();
            Map<String, InternalTransaction> stanMap = new HashMap<>();
            Map<String, InternalTransaction> refMap = new HashMap<>();

            for (InternalTransaction txn : internalTransactions) {
                if (txn.getRrn() != null && !txn.getRrn().isBlank()) {
                    rrnMap.put(txn.getRrn(), txn);
                }
                if (txn.getStan() != null && !txn.getStan().isBlank()) {
                    stanMap.put(txn.getStan(), txn);
                }
                if (txn.getTransactionId() != null) {
                    refMap.put(txn.getTransactionId(), txn);
                }
            }

            Set<String> matchedInternalTxnIds = new HashSet<>();

            // Process each settlement record
            for (SettlementRecord record : settlementFile.getRecords()) {
                try {
                    MatchResult matchResult = findMatch(record, rrnMap, stanMap, refMap, config);

                    if (matchResult.isMatched()) {
                        handleMatch(record, matchResult, result, config);
                        matchedInternalTxnIds.add(matchResult.getInternalTransactionId());
                    } else {
                        handleUnmatched(record, matchResult, result, config);
                    }

                    // Check max discrepancies
                    if (result.getDiscrepancyCount() >= config.getMaxDiscrepancies()) {
                        log.warn("Max discrepancies reached: {}", config.getMaxDiscrepancies());
                        if (!config.isContinueOnError()) {
                            break;
                        }
                    }

                } catch (Exception e) {
                    log.error("Error processing record {}: {}", record.getTransactionRefNo(), e.getMessage());
                    if (!config.isContinueOnError()) {
                        throw e;
                    }
                }
            }

            // Find internal transactions not in settlement file
            for (InternalTransaction txn : internalTransactions) {
                if (!matchedInternalTxnIds.contains(txn.getTransactionId())) {
                    result.addUnmatchedInternalTransaction(txn.getTransactionId());

                    if (config.isAutoCreateDiscrepancies()) {
                        Discrepancy discrepancy = createDiscrepancy(
                                null, txn, DiscrepancyType.MISSING_SETTLEMENT,
                                settlementFile.getSettlementDate(), settlementFile.getFileId()
                        );
                        result.addDiscrepancy(discrepancy);
                    }
                }
            }

            // Update status summary
            Map<SettlementStatus, Integer> statusSummary = settlementFile.getRecords().stream()
                    .collect(Collectors.groupingBy(
                            SettlementRecord::getStatus,
                            Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                    ));
            result.setStatusSummary(statusSummary);

            // Complete reconciliation
            result.complete();

            // Update settlement file status
            settlementFile.setProcessingStatus(
                    result.hasDiscrepancies() ?
                            FileProcessingStatus.RECONCILED_WITH_DISCREPANCIES :
                            FileProcessingStatus.RECONCILED
            );

            log.info("Reconciliation completed: {} matched, {} discrepancies, match rate: {:.2f}%",
                    result.getMatchedCount(), result.getDiscrepancyCount(), result.getMatchRate());

        } catch (Exception e) {
            log.error("Reconciliation failed: {}", e.getMessage(), e);
            result.fail(e.getMessage());
            settlementFile.setProcessingStatus(FileProcessingStatus.FAILED);
        }

        // Cache result
        resultCache.put(resultId, result);

        return result;
    }

    /**
     * Reconcile a settlement file using default configuration.
     */
    public ReconciliationResult reconcile(SettlementFile settlementFile) {
        return reconcile(settlementFile, ReconciliationConfig.defaultConfig());
    }

    /**
     * Get reconciliation result by ID.
     */
    public Optional<ReconciliationResult> getResult(String resultId) {
        return Optional.ofNullable(resultCache.get(resultId));
    }

    /**
     * Get all results for a settlement date.
     */
    public List<ReconciliationResult> getResultsForDate(LocalDate date) {
        return resultCache.values().stream()
                .filter(r -> date.equals(r.getSettlementDate()))
                .toList();
    }

    /**
     * Find matching internal transaction for a settlement record.
     */
    private MatchResult findMatch(SettlementRecord record,
                                  Map<String, InternalTransaction> rrnMap,
                                  Map<String, InternalTransaction> stanMap,
                                  Map<String, InternalTransaction> refMap,
                                  ReconciliationConfig config) {

        InternalTransaction matched = null;
        String matchKey = null;

        // Try RRN first
        if (config.isMatchByRrn() && record.getRrn() != null) {
            matched = rrnMap.get(record.getRrn());
            if (matched != null) {
                matchKey = "RRN:" + record.getRrn();
            }
        }

        // Try STAN if not matched
        if (matched == null && config.isMatchByStan() && record.getStan() != null) {
            matched = stanMap.get(record.getStan());
            if (matched != null) {
                matchKey = "STAN:" + record.getStan();
            }
        }

        // Try transaction reference if not matched
        if (matched == null && config.isMatchByTxnRef() && record.getTransactionRefNo() != null) {
            matched = refMap.get(record.getTransactionRefNo());
            if (matched != null) {
                matchKey = "REF:" + record.getTransactionRefNo();
            }
        }

        if (matched == null) {
            return MatchResult.noMatch();
        }

        // Validate amount if configured
        if (config.isValidateAmount()) {
            BigDecimal settlementAmount = record.getAmount() != null ? record.getAmount() : BigDecimal.ZERO;
            BigDecimal internalAmount = matched.getAmount() != null ? matched.getAmount() : BigDecimal.ZERO;
            BigDecimal difference = settlementAmount.subtract(internalAmount).abs();

            if (difference.compareTo(config.getAmountTolerance()) > 0) {
                return MatchResult.amountMismatch(matched.getTransactionId(), matchKey, difference);
            }
        }

        return MatchResult.matched(matched.getTransactionId(), matchKey);
    }

    /**
     * Handle a matched record.
     */
    private void handleMatch(SettlementRecord record, MatchResult matchResult,
                            ReconciliationResult result, ReconciliationConfig config) {
        record.setStatus(SettlementStatus.MATCHED);
        record.setMatchedTransactionId(matchResult.getInternalTransactionId());
        record.setMatchedAt(LocalDateTime.now());

        if (config.isIncludeMatchedRecords()) {
            result.addMatchedPair(record, matchResult.getInternalTransactionId());
        } else {
            result.setMatchedCount(result.getMatchedCount() + 1);
            if (record.getAmount() != null) {
                result.setMatchedAmount(
                        result.getMatchedAmount().add(record.getAmount())
                );
            }
        }
    }

    /**
     * Handle an unmatched record.
     */
    private void handleUnmatched(SettlementRecord record, MatchResult matchResult,
                                 ReconciliationResult result, ReconciliationConfig config) {

        if (matchResult.isAmountMismatch()) {
            record.setStatus(SettlementStatus.AMOUNT_MISMATCH);

            if (config.isAutoCreateDiscrepancies()) {
                Discrepancy discrepancy = Discrepancy.builder()
                        .discrepancyId(generateDiscrepancyId())
                        .type(DiscrepancyType.AMOUNT_MISMATCH)
                        .settlementDate(result.getSettlementDate())
                        .settlementFileId(result.getSettlementFileId())
                        .settlementRecordRef(record.getTransactionRefNo())
                        .internalTransactionRef(matchResult.getInternalTransactionId())
                        .settlementAmount(record.getAmount())
                        .differenceAmount(matchResult.getAmountDifference())
                        .cardNumber(record.getMaskedCardNumber())
                        .transactionType(record.getTransactionType())
                        .status(DiscrepancyStatus.OPEN)
                        .priority(DiscrepancyPriority.HIGH)
                        .description("Amount mismatch: difference of " + matchResult.getAmountDifference())
                        .createdAt(LocalDateTime.now())
                        .build();
                result.addDiscrepancy(discrepancy);
            }

        } else {
            record.setStatus(SettlementStatus.NOT_FOUND);
            result.addUnmatchedSettlementRecord(record);

            if (config.isAutoCreateDiscrepancies()) {
                Discrepancy discrepancy = Discrepancy.builder()
                        .discrepancyId(generateDiscrepancyId())
                        .type(DiscrepancyType.MISSING_INTERNAL)
                        .settlementDate(result.getSettlementDate())
                        .settlementFileId(result.getSettlementFileId())
                        .settlementRecordRef(record.getTransactionRefNo())
                        .settlementAmount(record.getAmount())
                        .cardNumber(record.getMaskedCardNumber())
                        .transactionType(record.getTransactionType())
                        .status(DiscrepancyStatus.OPEN)
                        .priority(DiscrepancyType.MISSING_INTERNAL.getDefaultPriority())
                        .description("Transaction not found in internal system")
                        .createdAt(LocalDateTime.now())
                        .build();
                result.addDiscrepancy(discrepancy);
            }
        }

        if (record.getAmount() != null) {
            result.setDiscrepancyAmount(
                    (result.getDiscrepancyAmount() != null ? result.getDiscrepancyAmount() : BigDecimal.ZERO)
                            .add(record.getAmount())
            );
        }
    }

    /**
     * Create a discrepancy for missing settlement record.
     */
    private Discrepancy createDiscrepancy(SettlementRecord settlementRecord,
                                          InternalTransaction internalTxn,
                                          DiscrepancyType type,
                                          LocalDate settlementDate,
                                          String settlementFileId) {
        return Discrepancy.builder()
                .discrepancyId(generateDiscrepancyId())
                .type(type)
                .settlementDate(settlementDate)
                .settlementFileId(settlementFileId)
                .settlementRecordRef(settlementRecord != null ? settlementRecord.getTransactionRefNo() : null)
                .internalTransactionRef(internalTxn != null ? internalTxn.getTransactionId() : null)
                .settlementAmount(settlementRecord != null ? settlementRecord.getAmount() : null)
                .internalAmount(internalTxn != null ? internalTxn.getAmount() : null)
                .status(DiscrepancyStatus.OPEN)
                .priority(type.getDefaultPriority())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String generateResultId() {
        return "REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateDiscrepancyId() {
        return "DISC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Match result holder.
     */
    private static class MatchResult {
        private final boolean matched;
        private final boolean amountMismatch;
        private final String internalTransactionId;
        private final String matchKey;
        private final BigDecimal amountDifference;

        private MatchResult(boolean matched, boolean amountMismatch,
                           String internalTransactionId, String matchKey,
                           BigDecimal amountDifference) {
            this.matched = matched;
            this.amountMismatch = amountMismatch;
            this.internalTransactionId = internalTransactionId;
            this.matchKey = matchKey;
            this.amountDifference = amountDifference;
        }

        static MatchResult matched(String txnId, String matchKey) {
            return new MatchResult(true, false, txnId, matchKey, null);
        }

        static MatchResult noMatch() {
            return new MatchResult(false, false, null, null, null);
        }

        static MatchResult amountMismatch(String txnId, String matchKey, BigDecimal difference) {
            return new MatchResult(false, true, txnId, matchKey, difference);
        }

        boolean isMatched() { return matched; }
        boolean isAmountMismatch() { return amountMismatch; }
        String getInternalTransactionId() { return internalTransactionId; }
        String getMatchKey() { return matchKey; }
        BigDecimal getAmountDifference() { return amountDifference; }
    }

    /**
     * Interface for providing internal transactions.
     */
    public interface InternalTransactionProvider {
        List<InternalTransaction> getTransactions(LocalDate date, int timeWindowHours);
    }

    /**
     * Internal transaction representation.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class InternalTransaction {
        private String transactionId;
        private String rrn;
        private String stan;
        private BigDecimal amount;
        private LocalDateTime transactionTime;
        private String transactionType;
        private String status;
    }
}
