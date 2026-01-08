package com.fep.transaction.query;

import com.fep.transaction.repository.TransactionRecord;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of a transaction query.
 */
@Data
@Builder
public class TransactionQueryResult {

    /** List of matching transactions */
    private List<TransactionRecord> records;

    /** Total count of matching records */
    private long totalCount;

    /** Current page number */
    private int page;

    /** Page size */
    private int pageSize;

    /** Total number of pages */
    private int totalPages;

    /** Whether there are more pages */
    private boolean hasMore;

    /**
     * Creates an empty result.
     */
    public static TransactionQueryResult empty() {
        return TransactionQueryResult.builder()
                .records(List.of())
                .totalCount(0)
                .page(0)
                .pageSize(0)
                .totalPages(0)
                .hasMore(false)
                .build();
    }

    /**
     * Creates a result with records.
     */
    public static TransactionQueryResult of(List<TransactionRecord> records, long totalCount,
                                             int page, int pageSize) {
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        return TransactionQueryResult.builder()
                .records(records)
                .totalCount(totalCount)
                .page(page)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .hasMore(page < totalPages - 1)
                .build();
    }

    /**
     * Gets the number of records in this page.
     */
    public int getRecordCount() {
        return records != null ? records.size() : 0;
    }
}
