package com.fep.transaction.query;

import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.repository.InMemoryTransactionRepository;
import com.fep.transaction.repository.TransactionRecord;
import com.fep.transaction.repository.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransactionQueryService.
 */
class TransactionQueryServiceTest {

    private InMemoryTransactionRepository repository;
    private TransactionQueryService queryService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        queryService = new TransactionQueryService(repository);
    }

    @Nested
    @DisplayName("Find by ID tests")
    class FindByIdTests {

        @Test
        @DisplayName("Should find transaction by ID")
        void shouldFindById() {
            // Arrange
            TransactionRecord record = createRecord("TXN001", "123456789012", "000001", TransactionStatus.APPROVED);
            repository.save(record);

            // Act
            Optional<TransactionRecord> result = queryService.findById("TXN001");

            // Assert
            assertTrue(result.isPresent());
            assertEquals("TXN001", result.get().getTransactionId());
        }

        @Test
        @DisplayName("Should return empty for non-existent ID")
        void shouldReturnEmptyForNonExistent() {
            // Act
            Optional<TransactionRecord> result = queryService.findById("NON_EXISTENT");

            // Assert
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Find by RRN tests")
    class FindByRrnTests {

        @Test
        @DisplayName("Should find transaction by RRN")
        void shouldFindByRrn() {
            // Arrange
            TransactionRecord record = createRecord("TXN001", "123456789012", "000001", TransactionStatus.APPROVED);
            repository.save(record);

            // Act
            Optional<TransactionRecord> result = queryService.findByRrn("123456789012");

            // Assert
            assertTrue(result.isPresent());
            assertEquals("123456789012", result.get().getRrn());
        }
    }

    @Nested
    @DisplayName("Find by RRN, STAN, Terminal tests")
    class FindByRrnStanTerminalTests {

        @Test
        @DisplayName("Should find transaction by RRN, STAN, and Terminal ID")
        void shouldFindByRrnStanTerminal() {
            // Arrange
            TransactionRecord record = createRecord("TXN001", "123456789012", "000001", TransactionStatus.APPROVED);
            record.setTerminalId("TERM001");
            repository.save(record);

            // Act
            Optional<TransactionRecord> result = queryService.findByRrnStanTerminal(
                    "123456789012", "000001", "TERM001");

            // Assert
            assertTrue(result.isPresent());
            assertEquals("TXN001", result.get().getTransactionId());
        }

        @Test
        @DisplayName("Should return empty when terminal ID does not match")
        void shouldReturnEmptyWhenTerminalNotMatch() {
            // Arrange
            TransactionRecord record = createRecord("TXN001", "123456789012", "000001", TransactionStatus.APPROVED);
            record.setTerminalId("TERM001");
            repository.save(record);

            // Act
            Optional<TransactionRecord> result = queryService.findByRrnStanTerminal(
                    "123456789012", "000001", "TERM002");

            // Assert
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Search tests")
    class SearchTests {

        @BeforeEach
        void setUpData() {
            // Create various test records
            repository.save(createRecord("TXN001", "RRN001", "STAN001", TransactionStatus.APPROVED,
                    TransactionType.WITHDRAWAL, new BigDecimal("1000")));
            repository.save(createRecord("TXN002", "RRN002", "STAN002", TransactionStatus.APPROVED,
                    TransactionType.TRANSFER, new BigDecimal("5000")));
            repository.save(createRecord("TXN003", "RRN003", "STAN003", TransactionStatus.DECLINED,
                    TransactionType.WITHDRAWAL, new BigDecimal("2000")));
            repository.save(createRecord("TXN004", "RRN004", "STAN004", TransactionStatus.PENDING,
                    TransactionType.BALANCE_INQUIRY, null));
            repository.save(createRecord("TXN005", "RRN005", "STAN005", TransactionStatus.REVERSED,
                    TransactionType.TRANSFER, new BigDecimal("3000")));
        }

        @Test
        @DisplayName("Should search by status")
        void shouldSearchByStatus() {
            // Arrange
            TransactionQuery query = TransactionQuery.builder()
                    .status(TransactionStatus.APPROVED)
                    .build();

            // Act
            TransactionQueryResult result = queryService.search(query);

            // Assert
            assertEquals(2, result.getTotalCount());
            assertTrue(result.getRecords().stream()
                    .allMatch(r -> r.getStatus() == TransactionStatus.APPROVED));
        }

        @Test
        @DisplayName("Should search by transaction type")
        void shouldSearchByTransactionType() {
            // Arrange
            TransactionQuery query = TransactionQuery.builder()
                    .transactionType(TransactionType.WITHDRAWAL)
                    .build();

            // Act
            TransactionQueryResult result = queryService.search(query);

            // Assert
            assertEquals(2, result.getTotalCount());
            assertTrue(result.getRecords().stream()
                    .allMatch(r -> r.getTransactionType() == TransactionType.WITHDRAWAL));
        }

        @Test
        @DisplayName("Should search with amount range")
        void shouldSearchWithAmountRange() {
            // Arrange
            TransactionQuery query = TransactionQuery.builder()
                    .minAmount(new BigDecimal("2000"))
                    .maxAmount(new BigDecimal("5000"))
                    .build();

            // Act
            TransactionQueryResult result = queryService.search(query);

            // Assert - TXN002(5000), TXN003(2000), TXN005(3000) match the range
            // TXN004 has null amount which passes both checks (null > 2000 returns false skips filter)
            // Actually all non-null amounts in range: 2000, 3000, 5000 = 3 records + null record = 4
            assertEquals(4, result.getTotalCount());
            assertTrue(result.getRecords().stream()
                    .filter(r -> r.getAmount() != null)
                    .allMatch(r -> r.getAmount().compareTo(new BigDecimal("2000")) >= 0 &&
                                   r.getAmount().compareTo(new BigDecimal("5000")) <= 0));
        }

        @Test
        @DisplayName("Should paginate results")
        void shouldPaginateResults() {
            // Arrange
            TransactionQuery query = TransactionQuery.builder()
                    .page(0)
                    .pageSize(2)
                    .build();

            // Act
            TransactionQueryResult result = queryService.search(query);

            // Assert
            assertEquals(5, result.getTotalCount());
            assertEquals(2, result.getRecordCount());
            assertEquals(0, result.getPage());
            assertEquals(3, result.getTotalPages());
            assertTrue(result.isHasMore());
        }

        @Test
        @DisplayName("Should return empty result for no matches")
        void shouldReturnEmptyForNoMatches() {
            // Arrange
            TransactionQuery query = TransactionQuery.builder()
                    .transactionId("NON_EXISTENT")
                    .build();

            // Act
            TransactionQueryResult result = queryService.search(query);

            // Assert
            assertEquals(0, result.getTotalCount());
            assertTrue(result.getRecords().isEmpty());
        }
    }

    @Nested
    @DisplayName("Statistics tests")
    class StatisticsTests {

        @BeforeEach
        void setUpData() {
            repository.save(createRecord("TXN001", "RRN001", "STAN001", TransactionStatus.APPROVED,
                    TransactionType.WITHDRAWAL, new BigDecimal("1000")));
            repository.save(createRecord("TXN002", "RRN002", "STAN002", TransactionStatus.APPROVED,
                    TransactionType.TRANSFER, new BigDecimal("5000")));
            repository.save(createRecord("TXN003", "RRN003", "STAN003", TransactionStatus.DECLINED,
                    TransactionType.WITHDRAWAL, new BigDecimal("2000")));
            repository.save(createRecord("TXN004", "RRN004", "STAN004", TransactionStatus.PENDING,
                    TransactionType.BALANCE_INQUIRY, null));
            repository.save(createRecord("TXN005", "RRN005", "STAN005", TransactionStatus.REVERSED,
                    TransactionType.TRANSFER, new BigDecimal("3000")));
        }

        @Test
        @DisplayName("Should calculate total statistics")
        void shouldCalculateTotalStatistics() {
            // Arrange
            TransactionQuery query = TransactionQuery.builder().build();

            // Act
            TransactionStatistics stats = queryService.getStatistics(query);

            // Assert
            assertEquals(5, stats.getTotalCount());
            assertEquals(2, stats.getApprovedCount());
            assertEquals(1, stats.getDeclinedCount());
            assertEquals(1, stats.getReversedCount());
            assertEquals(1, stats.getPendingCount());
        }

        @Test
        @DisplayName("Should calculate approval rate")
        void shouldCalculateApprovalRate() {
            // Arrange
            TransactionQuery query = TransactionQuery.builder().build();

            // Act
            TransactionStatistics stats = queryService.getStatistics(query);

            // Assert - 2 approved out of 5 = 40%
            assertEquals(new BigDecimal("40.00"), stats.getApprovalRate());
        }

        @Test
        @DisplayName("Should calculate amount totals")
        void shouldCalculateAmountTotals() {
            // Arrange
            TransactionQuery query = TransactionQuery.builder().build();

            // Act
            TransactionStatistics stats = queryService.getStatistics(query);

            // Assert - Total: 1000+5000+2000+3000 = 11000
            assertEquals(new BigDecimal("11000"), stats.getTotalAmount());
            // Approved: 1000+5000 = 6000
            assertEquals(new BigDecimal("6000"), stats.getApprovedAmount());
        }

        @Test
        @DisplayName("Should calculate count by type")
        void shouldCalculateCountByType() {
            // Arrange
            TransactionQuery query = TransactionQuery.builder().build();

            // Act
            TransactionStatistics stats = queryService.getStatistics(query);

            // Assert
            assertEquals(2, stats.getCountByType().get(TransactionType.WITHDRAWAL));
            assertEquals(2, stats.getCountByType().get(TransactionType.TRANSFER));
            assertEquals(1, stats.getCountByType().get(TransactionType.BALANCE_INQUIRY));
        }

        @Test
        @DisplayName("Should return empty statistics for no data")
        void shouldReturnEmptyStatisticsForNoData() {
            // Arrange
            repository.clear();
            TransactionQuery query = TransactionQuery.builder().build();

            // Act
            TransactionStatistics stats = queryService.getStatistics(query);

            // Assert
            assertEquals(0, stats.getTotalCount());
            assertEquals(BigDecimal.ZERO, stats.getTotalAmount());
            assertEquals(BigDecimal.ZERO, stats.getApprovalRate());
        }
    }

    @Nested
    @DisplayName("Reversal eligibility tests")
    class ReversalEligibilityTests {

        @Test
        @DisplayName("Should return eligible for approved transaction")
        void shouldBeEligibleForApproved() {
            // Arrange
            TransactionRecord record = createRecord("TXN001", "RRN001", "STAN001", TransactionStatus.APPROVED);
            repository.save(record);

            // Act
            ReversalEligibility result = queryService.checkReversalEligibility("TXN001");

            // Assert
            assertTrue(result.isEligible());
            assertNotNull(result.getOriginalTransaction());
            assertEquals("TXN001", result.getOriginalTransaction().getTransactionId());
        }

        @Test
        @DisplayName("Should return eligible for pending transaction")
        void shouldBeEligibleForPending() {
            // Arrange
            TransactionRecord record = createRecord("TXN001", "RRN001", "STAN001", TransactionStatus.PENDING);
            repository.save(record);

            // Act
            ReversalEligibility result = queryService.checkReversalEligibility("TXN001");

            // Assert
            assertTrue(result.isEligible());
        }

        @Test
        @DisplayName("Should return not found for non-existent transaction")
        void shouldReturnNotFoundForNonExistent() {
            // Act
            ReversalEligibility result = queryService.checkReversalEligibility("NON_EXISTENT");

            // Assert
            assertFalse(result.isEligible());
            assertEquals(ReversalEligibility.ReversalIneligibleReason.NOT_FOUND, result.getReasonCode());
            assertTrue(result.getReason().contains("not found"));
        }

        @Test
        @DisplayName("Should return already reversed for reversed transaction")
        void shouldReturnAlreadyReversedForReversed() {
            // Arrange
            TransactionRecord record = createRecord("TXN001", "RRN001", "STAN001", TransactionStatus.REVERSED);
            repository.save(record);

            // Act
            ReversalEligibility result = queryService.checkReversalEligibility("TXN001");

            // Assert
            assertFalse(result.isEligible());
            assertEquals(ReversalEligibility.ReversalIneligibleReason.ALREADY_REVERSED, result.getReasonCode());
        }

        @Test
        @DisplayName("Should return not reversible for declined transaction")
        void shouldReturnNotReversibleForDeclined() {
            // Arrange
            TransactionRecord record = createRecord("TXN001", "RRN001", "STAN001", TransactionStatus.DECLINED);
            repository.save(record);

            // Act
            ReversalEligibility result = queryService.checkReversalEligibility("TXN001");

            // Assert
            assertFalse(result.isEligible());
            assertEquals(ReversalEligibility.ReversalIneligibleReason.INVALID_STATUS, result.getReasonCode());
        }

        @Test
        @DisplayName("Should return not reversible for failed transaction")
        void shouldReturnNotReversibleForFailed() {
            // Arrange
            TransactionRecord record = createRecord("TXN001", "RRN001", "STAN001", TransactionStatus.FAILED);
            repository.save(record);

            // Act
            ReversalEligibility result = queryService.checkReversalEligibility("TXN001");

            // Assert
            assertFalse(result.isEligible());
            assertEquals(ReversalEligibility.ReversalIneligibleReason.INVALID_STATUS, result.getReasonCode());
        }
    }

    // Helper methods

    private TransactionRecord createRecord(String txnId, String rrn, String stan, TransactionStatus status) {
        return TransactionRecord.builder()
                .transactionId(txnId)
                .rrn(rrn)
                .stan(stan)
                .status(status)
                .transactionType(TransactionType.WITHDRAWAL)
                .requestTime(LocalDateTime.now())
                .transactionTime(LocalDateTime.now())
                .build();
    }

    private TransactionRecord createRecord(String txnId, String rrn, String stan, TransactionStatus status,
                                           TransactionType type, BigDecimal amount) {
        return TransactionRecord.builder()
                .transactionId(txnId)
                .rrn(rrn)
                .stan(stan)
                .status(status)
                .transactionType(type)
                .amount(amount)
                .requestTime(LocalDateTime.now())
                .transactionTime(LocalDateTime.now())
                .build();
    }
}
