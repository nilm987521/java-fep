package com.fep.settlement.reconciliation;

import com.fep.settlement.domain.*;
import com.fep.settlement.repository.InMemorySettlementRepository;
import com.fep.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DiscrepancyService Tests")
class DiscrepancyServiceTest {

    private DiscrepancyService discrepancyService;
    private SettlementRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemorySettlementRepository();
        discrepancyService = new DiscrepancyService(repository);
    }

    @Nested
    @DisplayName("Create Discrepancy")
    class CreateDiscrepancyTests {

        @Test
        @DisplayName("Should create discrepancy with all fields")
        void shouldCreateDiscrepancyWithAllFields() {
            Discrepancy discrepancy = discrepancyService.createDiscrepancy(
                    DiscrepancyType.AMOUNT_MISMATCH,
                    LocalDate.now(),
                    "SF-001",
                    "TXN-001",
                    "INT-001",
                    new BigDecimal("1000.00"),
                    new BigDecimal("900.00"),
                    "Amount mismatch found"
            );

            assertNotNull(discrepancy);
            assertNotNull(discrepancy.getDiscrepancyId());
            assertEquals(DiscrepancyType.AMOUNT_MISMATCH, discrepancy.getType());
            assertEquals(DiscrepancyStatus.OPEN, discrepancy.getStatus());
            assertEquals(new BigDecimal("100.00"), discrepancy.getDifferenceAmount());
        }

        @Test
        @DisplayName("Should set default priority based on type")
        void shouldSetDefaultPriorityBasedOnType() {
            Discrepancy amountMismatch = discrepancyService.createDiscrepancy(
                    DiscrepancyType.AMOUNT_MISMATCH,
                    LocalDate.now(),
                    "SF-001",
                    "TXN-001",
                    null,
                    new BigDecimal("1000.00"),
                    null,
                    "Test"
            );

            assertEquals(DiscrepancyPriority.HIGH, amountMismatch.getPriority());
        }

        @Test
        @DisplayName("Should calculate difference when only settlement amount present")
        void shouldCalculateDifferenceWithSettlementOnly() {
            Discrepancy discrepancy = discrepancyService.createDiscrepancy(
                    DiscrepancyType.MISSING_INTERNAL,
                    LocalDate.now(),
                    "SF-001",
                    "TXN-001",
                    null,
                    new BigDecimal("500.00"),
                    null,
                    "Missing"
            );

            assertEquals(new BigDecimal("500.00"), discrepancy.getDifferenceAmount());
        }
    }

    @Nested
    @DisplayName("Query Discrepancies")
    class QueryDiscrepanciesTests {

        @BeforeEach
        void setupTestData() {
            discrepancyService.createDiscrepancy(
                    DiscrepancyType.AMOUNT_MISMATCH,
                    LocalDate.now(),
                    "SF-001", "TXN-001", "INT-001",
                    new BigDecimal("1000.00"), new BigDecimal("900.00"),
                    "Amount mismatch 1"
            );
            discrepancyService.createDiscrepancy(
                    DiscrepancyType.MISSING_INTERNAL,
                    LocalDate.now(),
                    "SF-001", "TXN-002", null,
                    new BigDecimal("500.00"), null,
                    "Missing internal"
            );
            discrepancyService.createDiscrepancy(
                    DiscrepancyType.AMOUNT_MISMATCH,
                    LocalDate.now().minusDays(1),
                    "SF-002", "TXN-003", "INT-003",
                    new BigDecimal("2000.00"), new BigDecimal("1800.00"),
                    "Amount mismatch 2"
            );
        }

        @Test
        @DisplayName("Should get open discrepancies")
        void shouldGetOpenDiscrepancies() {
            List<Discrepancy> open = discrepancyService.getOpenDiscrepancies();

            assertEquals(3, open.size());
            assertTrue(open.stream().allMatch(d -> d.getStatus() == DiscrepancyStatus.OPEN));
        }

        @Test
        @DisplayName("Should get discrepancies by date")
        void shouldGetDiscrepanciesByDate() {
            List<Discrepancy> today = discrepancyService.getDiscrepanciesByDate(LocalDate.now());

            assertEquals(2, today.size());
        }

        @Test
        @DisplayName("Should get discrepancies by type")
        void shouldGetDiscrepanciesByType() {
            List<Discrepancy> amountMismatch = discrepancyService.getDiscrepanciesByType(
                    DiscrepancyType.AMOUNT_MISMATCH
            );

            assertEquals(2, amountMismatch.size());
        }
    }

    @Nested
    @DisplayName("Discrepancy Workflow")
    class WorkflowTests {

        private String discrepancyId;

        @BeforeEach
        void setUp() {
            Discrepancy d = discrepancyService.createDiscrepancy(
                    DiscrepancyType.AMOUNT_MISMATCH,
                    LocalDate.now(),
                    "SF-001", "TXN-001", "INT-001",
                    new BigDecimal("1000.00"), new BigDecimal("900.00"),
                    "Test discrepancy"
            );
            discrepancyId = d.getDiscrepancyId();
        }

        @Test
        @DisplayName("Should assign discrepancy to investigator")
        void shouldAssignDiscrepancy() {
            Discrepancy assigned = discrepancyService.assign(discrepancyId, "analyst1");

            assertEquals("analyst1", assigned.getAssignedTo());
            assertEquals(DiscrepancyStatus.INVESTIGATING, assigned.getStatus());
            assertFalse(assigned.getInvestigationNotes().isEmpty());
        }

        @Test
        @DisplayName("Should add investigation note")
        void shouldAddInvestigationNote() {
            Discrepancy withNote = discrepancyService.addNote(
                    discrepancyId, "analyst1", "Investigated and found timing issue"
            );

            assertEquals(1, withNote.getInvestigationNotes().size());
            assertEquals("analyst1", withNote.getInvestigationNotes().get(0).getUserId());
        }

        @Test
        @DisplayName("Should set root cause")
        void shouldSetRootCause() {
            Discrepancy withRootCause = discrepancyService.setRootCause(
                    discrepancyId, "Settlement file received before internal posting"
            );

            assertEquals("Settlement file received before internal posting", withRootCause.getRootCause());
        }

        @Test
        @DisplayName("Should escalate discrepancy")
        void shouldEscalateDiscrepancy() {
            Discrepancy escalated = discrepancyService.escalate(
                    discrepancyId, "Large amount requires manager approval"
            );

            assertEquals(DiscrepancyStatus.ESCALATED, escalated.getStatus());
            assertEquals(DiscrepancyPriority.CRITICAL, escalated.getPriority());
        }

        @Test
        @DisplayName("Should request approval")
        void shouldRequestApproval() {
            Discrepancy pending = discrepancyService.requestApproval(
                    discrepancyId,
                    ResolutionAction.ADJUST_INTERNAL,
                    "Adjust internal record to match settlement"
            );

            assertEquals(DiscrepancyStatus.PENDING_APPROVAL, pending.getStatus());
            assertEquals(ResolutionAction.ADJUST_INTERNAL, pending.getResolutionAction());
        }

        @Test
        @DisplayName("Should resolve discrepancy")
        void shouldResolveDiscrepancy() {
            Discrepancy resolved = discrepancyService.resolve(
                    discrepancyId,
                    "manager1",
                    ResolutionAction.ADJUST_INTERNAL,
                    "Adjusted internal record"
            );

            assertEquals(DiscrepancyStatus.RESOLVED, resolved.getStatus());
            assertEquals("manager1", resolved.getResolvedBy());
            assertNotNull(resolved.getResolvedAt());
        }

        @Test
        @DisplayName("Should write off discrepancy")
        void shouldWriteOffDiscrepancy() {
            Discrepancy writtenOff = discrepancyService.writeOff(
                    discrepancyId, "manager1", "Amount below materiality threshold"
            );

            assertEquals(DiscrepancyStatus.WRITTEN_OFF, writtenOff.getStatus());
            assertEquals(ResolutionAction.WRITE_OFF, writtenOff.getResolutionAction());
        }
    }

    @Nested
    @DisplayName("Link Discrepancies")
    class LinkDiscrepanciesTests {

        @Test
        @DisplayName("Should link related discrepancies")
        void shouldLinkRelatedDiscrepancies() {
            Discrepancy d1 = discrepancyService.createDiscrepancy(
                    DiscrepancyType.AMOUNT_MISMATCH,
                    LocalDate.now(), "SF-001", "TXN-001", "INT-001",
                    new BigDecimal("1000.00"), new BigDecimal("900.00"), "D1"
            );

            Discrepancy d2 = discrepancyService.createDiscrepancy(
                    DiscrepancyType.FEE_MISMATCH,
                    LocalDate.now(), "SF-001", "TXN-001", "INT-001",
                    new BigDecimal("10.00"), new BigDecimal("5.00"), "D2"
            );

            discrepancyService.linkDiscrepancies(d1.getDiscrepancyId(), d2.getDiscrepancyId());

            Discrepancy updated1 = discrepancyService.getDiscrepancy(d1.getDiscrepancyId()).orElseThrow();
            Discrepancy updated2 = discrepancyService.getDiscrepancy(d2.getDiscrepancyId()).orElseThrow();

            assertTrue(updated1.getRelatedDiscrepancies().contains(d2.getDiscrepancyId()));
            assertTrue(updated2.getRelatedDiscrepancies().contains(d1.getDiscrepancyId()));
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @BeforeEach
        void setupTestData() {
            // Create various discrepancies
            Discrepancy d1 = discrepancyService.createDiscrepancy(
                    DiscrepancyType.AMOUNT_MISMATCH, LocalDate.now(),
                    "SF-001", "TXN-001", "INT-001",
                    new BigDecimal("1000.00"), new BigDecimal("900.00"), "D1"
            );
            discrepancyService.resolve(d1.getDiscrepancyId(), "user1",
                    ResolutionAction.ADJUST_INTERNAL, "Resolved");

            discrepancyService.createDiscrepancy(
                    DiscrepancyType.MISSING_INTERNAL, LocalDate.now(),
                    "SF-001", "TXN-002", null,
                    new BigDecimal("500.00"), null, "D2"
            );
        }

        @Test
        @DisplayName("Should get statistics")
        void shouldGetStatistics() {
            Map<String, Object> stats = discrepancyService.getStatistics();

            assertNotNull(stats.get("byStatus"));
            assertNotNull(stats.get("byType"));
            assertNotNull(stats.get("byPriority"));
            assertNotNull(stats.get("openCount"));
            assertNotNull(stats.get("resolutionRate"));
        }

        @Test
        @DisplayName("Should get aging report")
        void shouldGetAgingReport() {
            Map<String, List<Discrepancy>> aging = discrepancyService.getAgingReport();

            assertNotNull(aging.get("0-24 hours"));
            assertNotNull(aging.get("1-3 days"));
            assertNotNull(aging.get("3-7 days"));
            assertNotNull(aging.get("7-30 days"));
            assertNotNull(aging.get(">30 days"));

            // New discrepancy should be in 0-24 hours bucket
            assertEquals(1, aging.get("0-24 hours").size());
        }
    }
}
