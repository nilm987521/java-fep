package com.fep.transaction.risk.blacklist;

import com.fep.transaction.domain.TransactionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BlacklistService Tests")
class BlacklistServiceTest {

    private BlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        blacklistService = new BlacklistService();
    }

    @Nested
    @DisplayName("Add to Blacklist")
    class AddToBlacklistTests {

        @Test
        @DisplayName("Should add card to blacklist")
        void shouldAddCardToBlacklist() {
            BlacklistEntry entry = blacklistService.addToBlacklist(
                    BlacklistType.CARD,
                    "4111111111111111",
                    BlacklistReason.STOLEN,
                    "Reported stolen",
                    "admin"
            );

            assertNotNull(entry);
            assertNotNull(entry.getEntryId());
            assertTrue(entry.getEntryId().startsWith("CARD-"));
            assertEquals(BlacklistType.CARD, entry.getType());
            assertEquals("4111111111111111", entry.getValue());
            assertEquals(BlacklistReason.STOLEN, entry.getReason());
            assertTrue(entry.isActive());
            assertTrue(entry.isEffective());
        }

        @Test
        @DisplayName("Should add account to blacklist with expiration")
        void shouldAddAccountWithExpiration() {
            BlacklistEntry entry = blacklistService.addToBlacklist(
                    BlacklistType.ACCOUNT,
                    "1234567890",
                    BlacklistReason.TEMPORARY_HOLD,
                    "Temporary hold for investigation",
                    "fraud_team",
                    Duration.ofHours(24)
            );

            assertNotNull(entry);
            assertNotNull(entry.getExpiresAt());
            assertTrue(entry.isEffective());
            assertFalse(entry.isExpired());
        }

        @Test
        @DisplayName("Should mask card number correctly")
        void shouldMaskCardNumber() {
            BlacklistEntry entry = blacklistService.addToBlacklist(
                    BlacklistType.CARD,
                    "4111111111111111",
                    BlacklistReason.LOST,
                    "Lost card",
                    "admin"
            );

            assertEquals("4111****1111", entry.getMaskedValue());
        }

        @Test
        @DisplayName("Should normalize card number")
        void shouldNormalizeCardNumber() {
            BlacklistEntry entry = blacklistService.addToBlacklist(
                    BlacklistType.CARD,
                    "4111-1111-1111-1111",
                    BlacklistReason.LOST,
                    "Lost card",
                    "admin"
            );

            assertEquals("4111111111111111", entry.getValue());
        }
    }

    @Nested
    @DisplayName("Check Blacklist")
    class CheckBlacklistTests {

        @Test
        @DisplayName("Should detect blacklisted card")
        void shouldDetectBlacklistedCard() {
            blacklistService.addToBlacklist(
                    BlacklistType.CARD,
                    "4111111111111111",
                    BlacklistReason.STOLEN,
                    "Stolen card",
                    "admin"
            );

            BlacklistCheckResult result = blacklistService.checkCard("4111111111111111");

            assertTrue(result.isBlocked());
            assertEquals(1, result.getMatchedEntries().size());
            assertEquals(BlacklistCheckResult.RecommendedAction.CAPTURE_CARD, result.getAction());
        }

        @Test
        @DisplayName("Should not block non-blacklisted card")
        void shouldNotBlockNonBlacklistedCard() {
            BlacklistCheckResult result = blacklistService.checkCard("4222222222222222");

            assertFalse(result.isBlocked());
            assertEquals(BlacklistCheckResult.RecommendedAction.ALLOW, result.getAction());
            assertTrue(result.getMatchedEntries().isEmpty());
        }

        @Test
        @DisplayName("Should check transaction against multiple blacklists")
        void shouldCheckTransactionAgainstMultipleBlacklists() {
            // Add to blacklists
            blacklistService.addToBlacklist(BlacklistType.CARD, "4111111111111111",
                    BlacklistReason.STOLEN, "Stolen", "admin");
            blacklistService.addToBlacklist(BlacklistType.MERCHANT, "MERCH001",
                    BlacklistReason.MERCHANT_FRAUD, "Fraudulent merchant", "admin");

            TransactionRequest request = TransactionRequest.builder()
                    .pan("4111111111111111")
                    .merchantId("MERCH001")
                    .amount(new BigDecimal("1000"))
                    .build();

            BlacklistCheckResult result = blacklistService.checkTransaction(request);

            assertTrue(result.isBlocked());
            assertEquals(2, result.getMatchedEntries().size());
        }

        @Test
        @DisplayName("Should use cache for repeated checks")
        void shouldUseCacheForRepeatedChecks() {
            blacklistService.addToBlacklist(BlacklistType.CARD, "4111111111111111",
                    BlacklistReason.LOST, "Lost", "admin");

            // First check - cache miss
            BlacklistCheckResult result1 = blacklistService.checkCard("4111111111111111");
            // Second check - cache hit
            BlacklistCheckResult result2 = blacklistService.checkCard("4111111111111111");

            assertTrue(result1.isBlocked());
            assertTrue(result2.isBlocked());
            assertTrue(result2.getCheckDurationMs() <= result1.getCheckDurationMs());
        }
    }

    @Nested
    @DisplayName("Remove and Deactivate")
    class RemoveAndDeactivateTests {

        @Test
        @DisplayName("Should remove entry from blacklist")
        void shouldRemoveFromBlacklist() {
            blacklistService.addToBlacklist(BlacklistType.CARD, "4111111111111111",
                    BlacklistReason.LOST, "Lost", "admin");

            boolean removed = blacklistService.removeFromBlacklist(BlacklistType.CARD, "4111111111111111");
            BlacklistCheckResult result = blacklistService.checkCard("4111111111111111");

            assertTrue(removed);
            assertFalse(result.isBlocked());
        }

        @Test
        @DisplayName("Should deactivate entry without removing")
        void shouldDeactivateEntry() {
            BlacklistEntry entry = blacklistService.addToBlacklist(BlacklistType.CARD, "4111111111111111",
                    BlacklistReason.LOST, "Lost", "admin");

            boolean deactivated = blacklistService.deactivateEntry(entry.getEntryId());
            BlacklistCheckResult result = blacklistService.checkCard("4111111111111111");

            assertTrue(deactivated);
            assertFalse(result.isBlocked());
        }
    }

    @Nested
    @DisplayName("Search and Query")
    class SearchAndQueryTests {

        @BeforeEach
        void setupTestData() {
            blacklistService.addToBlacklist(BlacklistType.CARD, "4111111111111111",
                    BlacklistReason.STOLEN, "Stolen 1", "admin");
            blacklistService.addToBlacklist(BlacklistType.CARD, "4222222222222222",
                    BlacklistReason.STOLEN, "Stolen 2", "admin");
            blacklistService.addToBlacklist(BlacklistType.ACCOUNT, "1234567890",
                    BlacklistReason.FRAUD_CONFIRMED, "Fraud", "admin");
        }

        @Test
        @DisplayName("Should get entries by type")
        void shouldGetEntriesByType() {
            List<BlacklistEntry> cardEntries = blacklistService.getEntries(BlacklistType.CARD);
            List<BlacklistEntry> accountEntries = blacklistService.getEntries(BlacklistType.ACCOUNT);

            assertEquals(2, cardEntries.size());
            assertEquals(1, accountEntries.size());
        }

        @Test
        @DisplayName("Should search by reason")
        void shouldSearchByReason() {
            List<BlacklistEntry> stolenCards = blacklistService.searchByReason(BlacklistReason.STOLEN);

            assertEquals(2, stolenCards.size());
        }

        @Test
        @DisplayName("Should get entry by ID")
        void shouldGetEntryById() {
            BlacklistEntry entry = blacklistService.addToBlacklist(BlacklistType.CARD, "5555555555555555",
                    BlacklistReason.COUNTERFEIT, "Counterfeit", "admin");

            Optional<BlacklistEntry> found = blacklistService.getEntryById(entry.getEntryId());

            assertTrue(found.isPresent());
            assertEquals(entry.getValue(), found.get().getValue());
        }

        @Test
        @DisplayName("Should get active entries only")
        void shouldGetActiveEntriesOnly() {
            BlacklistEntry entry = blacklistService.addToBlacklist(BlacklistType.CARD, "6666666666666666",
                    BlacklistReason.LOST, "Lost", "admin");
            blacklistService.deactivateEntry(entry.getEntryId());

            List<BlacklistEntry> activeEntries = blacklistService.getActiveEntries(BlacklistType.CARD);

            assertEquals(2, activeEntries.size()); // Only the original 2
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should track check and hit counts")
        void shouldTrackStatistics() {
            blacklistService.addToBlacklist(BlacklistType.CARD, "4111111111111111",
                    BlacklistReason.STOLEN, "Stolen", "admin");

            // Perform some checks
            blacklistService.checkCard("4111111111111111"); // Hit
            blacklistService.checkCard("4111111111111111"); // Hit
            blacklistService.checkCard("4222222222222222"); // Miss

            Map<String, Object> stats = blacklistService.getStatistics();
            @SuppressWarnings("unchecked")
            Map<String, Object> cardStats = (Map<String, Object>) stats.get("CARD");

            assertEquals(1, ((Number) cardStats.get("totalEntries")).intValue());
            assertEquals(3, ((Number) cardStats.get("checksPerformed")).intValue());
            assertEquals(2, ((Number) cardStats.get("hitsRecorded")).intValue());
        }

        @Test
        @DisplayName("Should get top hit entries")
        void shouldGetTopHitEntries() {
            blacklistService.addToBlacklist(BlacklistType.CARD, "4111111111111111",
                    BlacklistReason.STOLEN, "Stolen 1", "admin");
            blacklistService.addToBlacklist(BlacklistType.CARD, "4222222222222222",
                    BlacklistReason.STOLEN, "Stolen 2", "admin");

            // Create hits
            for (int i = 0; i < 5; i++) {
                blacklistService.checkCard("4111111111111111");
            }
            for (int i = 0; i < 2; i++) {
                blacklistService.checkCard("4222222222222222");
            }

            List<BlacklistEntry> topHits = blacklistService.getTopHitEntries(10);

            assertEquals(2, topHits.size());
            assertEquals("4111111111111111", topHits.get(0).getValue());
        }
    }

    @Nested
    @DisplayName("Import/Export")
    class ImportExportTests {

        @Test
        @DisplayName("Should export all entries")
        void shouldExportAllEntries() {
            blacklistService.addToBlacklist(BlacklistType.CARD, "4111111111111111",
                    BlacklistReason.STOLEN, "Stolen", "admin");
            blacklistService.addToBlacklist(BlacklistType.ACCOUNT, "1234567890",
                    BlacklistReason.FRAUD_CONFIRMED, "Fraud", "admin");

            List<BlacklistEntry> exported = blacklistService.exportEntries();

            assertEquals(2, exported.size());
        }

        @Test
        @DisplayName("Should import entries")
        void shouldImportEntries() {
            BlacklistEntry entry1 = BlacklistEntry.builder()
                    .type(BlacklistType.CARD)
                    .value("4111111111111111")
                    .reason(BlacklistReason.STOLEN)
                    .active(true)
                    .build();

            BlacklistEntry entry2 = BlacklistEntry.builder()
                    .type(BlacklistType.MERCHANT)
                    .value("MERCH001")
                    .reason(BlacklistReason.MERCHANT_FRAUD)
                    .active(true)
                    .build();

            int imported = blacklistService.importEntries(List.of(entry1, entry2));

            assertEquals(2, imported);
            assertTrue(blacklistService.checkCard("4111111111111111").isBlocked());
            assertTrue(blacklistService.checkMerchant("MERCH001").isBlocked());
        }
    }

    @Nested
    @DisplayName("Risk Score Calculation")
    class RiskScoreTests {

        @Test
        @DisplayName("Should calculate high risk score for fraud")
        void shouldCalculateHighRiskScoreForFraud() {
            blacklistService.addToBlacklist(BlacklistType.CARD, "4111111111111111",
                    BlacklistReason.FRAUD_CONFIRMED, "Confirmed fraud", "admin");

            BlacklistCheckResult result = blacklistService.checkCard("4111111111111111");

            assertTrue(result.isBlocked());
            assertTrue(result.getRiskScore() >= 90);
            assertEquals(BlacklistCheckResult.RecommendedAction.BLOCK_AND_ALERT, result.getAction());
        }

        @Test
        @DisplayName("Should calculate appropriate risk for temporary hold")
        void shouldCalculateAppropriateRiskForTemporaryHold() {
            blacklistService.addToBlacklist(BlacklistType.CARD, "4111111111111111",
                    BlacklistReason.TEMPORARY_HOLD, "Under investigation", "admin");

            BlacklistCheckResult result = blacklistService.checkCard("4111111111111111");

            assertTrue(result.isBlocked());
            assertTrue(result.getRiskScore() < 90);
            assertEquals(BlacklistCheckResult.RecommendedAction.DECLINE, result.getAction());
        }
    }
}
