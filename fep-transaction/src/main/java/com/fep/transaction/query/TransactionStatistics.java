package com.fep.transaction.query;

import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Transaction statistics summary.
 */
@Data
@Builder
public class TransactionStatistics {

    /** Total number of transactions */
    private long totalCount;

    /** Number of approved transactions */
    private long approvedCount;

    /** Number of declined transactions */
    private long declinedCount;

    /** Number of reversed transactions */
    private long reversedCount;

    /** Number of pending transactions */
    private long pendingCount;

    /** Total amount of all transactions */
    private BigDecimal totalAmount;

    /** Total amount of approved transactions */
    private BigDecimal approvedAmount;

    /** Average transaction amount */
    private BigDecimal averageAmount;

    /** Approval rate (percentage) */
    private BigDecimal approvalRate;

    /** Count by transaction type */
    private Map<TransactionType, Long> countByType;

    /** Amount by transaction type */
    private Map<TransactionType, BigDecimal> amountByType;

    /**
     * Calculates statistics from a list of records.
     */
    public static TransactionStatistics calculate(List<TransactionRecord> records) {
        if (records == null || records.isEmpty()) {
            return empty();
        }

        long total = records.size();
        long approved = 0;
        long declined = 0;
        long reversed = 0;
        long pending = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal approvedAmount = BigDecimal.ZERO;

        Map<TransactionType, Long> countByType = new EnumMap<>(TransactionType.class);
        Map<TransactionType, BigDecimal> amountByType = new EnumMap<>(TransactionType.class);

        for (TransactionRecord record : records) {
            // Count by status
            if (record.getStatus() == TransactionStatus.APPROVED) {
                approved++;
                if (record.getAmount() != null) {
                    approvedAmount = approvedAmount.add(record.getAmount());
                }
            } else if (record.getStatus() == TransactionStatus.DECLINED) {
                declined++;
            } else if (record.getStatus() == TransactionStatus.REVERSED) {
                reversed++;
            } else if (record.getStatus() == TransactionStatus.PENDING ||
                       record.getStatus() == TransactionStatus.PROCESSING) {
                pending++;
            }

            // Sum amounts
            if (record.getAmount() != null) {
                totalAmount = totalAmount.add(record.getAmount());
            }

            // Count by type
            if (record.getTransactionType() != null) {
                countByType.merge(record.getTransactionType(), 1L, Long::sum);
                if (record.getAmount() != null) {
                    amountByType.merge(record.getTransactionType(), record.getAmount(), BigDecimal::add);
                }
            }
        }

        BigDecimal averageAmount = total > 0 ?
                totalAmount.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        BigDecimal approvalRate = total > 0 ?
                BigDecimal.valueOf(approved * 100.0 / total).setScale(2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        return TransactionStatistics.builder()
                .totalCount(total)
                .approvedCount(approved)
                .declinedCount(declined)
                .reversedCount(reversed)
                .pendingCount(pending)
                .totalAmount(totalAmount)
                .approvedAmount(approvedAmount)
                .averageAmount(averageAmount)
                .approvalRate(approvalRate)
                .countByType(countByType)
                .amountByType(amountByType)
                .build();
    }

    /**
     * Creates empty statistics.
     */
    public static TransactionStatistics empty() {
        return TransactionStatistics.builder()
                .totalCount(0)
                .approvedCount(0)
                .declinedCount(0)
                .reversedCount(0)
                .pendingCount(0)
                .totalAmount(BigDecimal.ZERO)
                .approvedAmount(BigDecimal.ZERO)
                .averageAmount(BigDecimal.ZERO)
                .approvalRate(BigDecimal.ZERO)
                .countByType(new EnumMap<>(TransactionType.class))
                .amountByType(new EnumMap<>(TransactionType.class))
                .build();
    }
}
