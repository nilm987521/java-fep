package com.fep.transaction.query;

import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.repository.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Query criteria for searching transactions.
 */
@Data
@Builder
public class TransactionQuery {

    /** Transaction ID (exact match) */
    private String transactionId;

    /** RRN for matching */
    private String rrn;

    /** STAN for matching */
    private String stan;

    /** Terminal ID */
    private String terminalId;

    /** PAN (masked or full) */
    private String pan;

    /** Transaction type filter */
    private TransactionType transactionType;

    /** Transaction status filter */
    private TransactionStatus status;

    /** Acquiring bank code */
    private String acquiringBankCode;

    /** Minimum amount */
    private BigDecimal minAmount;

    /** Maximum amount */
    private BigDecimal maxAmount;

    /** Start date/time */
    private LocalDateTime startTime;

    /** End date/time */
    private LocalDateTime endTime;

    /** Channel filter */
    private String channel;

    /** Page number (0-based) */
    @Builder.Default
    private int page = 0;

    /** Page size */
    @Builder.Default
    private int pageSize = 20;

    /** Maximum page size allowed */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Creates a query to find by transaction ID.
     */
    public static TransactionQuery byTransactionId(String transactionId) {
        return TransactionQuery.builder()
                .transactionId(transactionId)
                .build();
    }

    /**
     * Creates a query to find by RRN.
     */
    public static TransactionQuery byRrn(String rrn) {
        return TransactionQuery.builder()
                .rrn(rrn)
                .build();
    }

    /**
     * Creates a query to find by RRN, STAN, and Terminal ID.
     */
    public static TransactionQuery byRrnStanTerminal(String rrn, String stan, String terminalId) {
        return TransactionQuery.builder()
                .rrn(rrn)
                .stan(stan)
                .terminalId(terminalId)
                .build();
    }

    /**
     * Creates a query for today's transactions.
     */
    public static TransactionQuery today() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        return TransactionQuery.builder()
                .startTime(startOfDay)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * Validates and normalizes the query.
     */
    public void normalize() {
        if (pageSize <= 0) {
            pageSize = 20;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        if (page < 0) {
            page = 0;
        }
    }
}
