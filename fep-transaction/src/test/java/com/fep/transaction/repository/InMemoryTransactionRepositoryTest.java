package com.fep.transaction.repository;

import com.fep.transaction.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryTransactionRepository.
 */
class InMemoryTransactionRepositoryTest {

    private InMemoryTransactionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
    }

    @Test
    @DisplayName("Should save and retrieve transaction by ID")
    void testSaveAndFindByTransactionId() {
        TransactionRecord record = createTestRecord("TXN001");

        TransactionRecord saved = repository.save(record);

        assertNotNull(saved.getId());
        assertEquals("TXN001", saved.getTransactionId());

        Optional<TransactionRecord> found = repository.findByTransactionId("TXN001");
        assertTrue(found.isPresent());
        assertEquals("TXN001", found.get().getTransactionId());
    }

    @Test
    @DisplayName("Should find by RRN and STAN")
    void testFindByRrnAndStan() {
        TransactionRecord record = createTestRecord("TXN001");
        record.setRrn("123456789012");
        record.setStan("000001");
        repository.save(record);

        Optional<TransactionRecord> found = repository.findByRrnAndStan("123456789012", "000001");

        assertTrue(found.isPresent());
        assertEquals("TXN001", found.get().getTransactionId());
    }

    @Test
    @DisplayName("Should find by terminal ID and date range")
    void testFindByTerminalIdAndDateRange() {
        TransactionRecord record1 = createTestRecord("TXN001");
        record1.setTerminalId("ATM00001");
        record1.setRequestTime(LocalDateTime.now().minusHours(1));
        repository.save(record1);

        TransactionRecord record2 = createTestRecord("TXN002");
        record2.setTerminalId("ATM00001");
        record2.setRequestTime(LocalDateTime.now());
        repository.save(record2);

        TransactionRecord record3 = createTestRecord("TXN003");
        record3.setTerminalId("ATM00002");
        record3.setRequestTime(LocalDateTime.now());
        repository.save(record3);

        List<TransactionRecord> found = repository.findByTerminalIdAndDateRange(
                "ATM00001",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1));

        assertEquals(2, found.size());
    }

    @Test
    @DisplayName("Should find by status")
    void testFindByStatus() {
        TransactionRecord record1 = createTestRecord("TXN001");
        record1.setStatus(TransactionStatus.APPROVED);
        repository.save(record1);

        TransactionRecord record2 = createTestRecord("TXN002");
        record2.setStatus(TransactionStatus.DECLINED);
        repository.save(record2);

        TransactionRecord record3 = createTestRecord("TXN003");
        record3.setStatus(TransactionStatus.APPROVED);
        repository.save(record3);

        List<TransactionRecord> approved = repository.findByStatus(TransactionStatus.APPROVED);

        assertEquals(2, approved.size());
    }

    @Test
    @DisplayName("Should update status")
    void testUpdateStatus() {
        TransactionRecord record = createTestRecord("TXN001");
        record.setStatus(TransactionStatus.PENDING);
        repository.save(record);

        boolean updated = repository.updateStatus("TXN001", TransactionStatus.PROCESSING);

        assertTrue(updated);
        Optional<TransactionRecord> found = repository.findByTransactionId("TXN001");
        assertTrue(found.isPresent());
        assertEquals(TransactionStatus.PROCESSING, found.get().getStatus());
    }

    @Test
    @DisplayName("Should update response")
    void testUpdateResponse() {
        TransactionRecord record = createTestRecord("TXN001");
        record.setStatus(TransactionStatus.PROCESSING);
        repository.save(record);

        boolean updated = repository.updateResponse("TXN001", "00", "123456", TransactionStatus.APPROVED);

        assertTrue(updated);
        Optional<TransactionRecord> found = repository.findByTransactionId("TXN001");
        assertTrue(found.isPresent());
        assertEquals("00", found.get().getResponseCode());
        assertEquals("123456", found.get().getAuthorizationCode());
        assertEquals(TransactionStatus.APPROVED, found.get().getStatus());
    }

    @Test
    @DisplayName("Should check for existence")
    void testExistsByTransactionId() {
        TransactionRecord record = createTestRecord("TXN001");
        repository.save(record);

        assertTrue(repository.existsByTransactionId("TXN001"));
        assertFalse(repository.existsByTransactionId("TXN999"));
    }

    @Test
    @DisplayName("Should detect duplicates within time window")
    void testIsDuplicate() {
        TransactionRecord record = createTestRecord("TXN001");
        record.setRrn("123456789012");
        record.setStan("000001");
        record.setTerminalId("ATM00001");
        record.setRequestTime(LocalDateTime.now());
        record.setStatus(TransactionStatus.APPROVED);
        repository.save(record);

        boolean isDuplicate = repository.isDuplicate("123456789012", "000001", "ATM00001", 5);

        assertTrue(isDuplicate);
    }

    @Test
    @DisplayName("Should not detect duplicate for different terminal")
    void testNotDuplicateDifferentTerminal() {
        TransactionRecord record = createTestRecord("TXN001");
        record.setRrn("123456789012");
        record.setStan("000001");
        record.setTerminalId("ATM00001");
        record.setRequestTime(LocalDateTime.now());
        record.setStatus(TransactionStatus.APPROVED);
        repository.save(record);

        boolean isDuplicate = repository.isDuplicate("123456789012", "000001", "ATM00002", 5);

        assertFalse(isDuplicate);
    }

    @Test
    @DisplayName("Should clear all records")
    void testClear() {
        repository.save(createTestRecord("TXN001"));
        repository.save(createTestRecord("TXN002"));

        assertEquals(2, repository.size());

        repository.clear();

        assertEquals(0, repository.size());
    }

    private TransactionRecord createTestRecord(String transactionId) {
        return TransactionRecord.builder()
                .transactionId(transactionId)
                .transactionType(TransactionType.WITHDRAWAL)
                .maskedPan("411111******1111")
                .amount(new BigDecimal("5000"))
                .currencyCode("901")
                .terminalId("ATM00001")
                .acquiringBankCode("004")
                .stan("000001")
                .rrn("123456789012")
                .status(TransactionStatus.PENDING)
                .requestTime(LocalDateTime.now())
                .transactionDate(java.time.LocalDate.now().toString())
                .build();
    }
}
