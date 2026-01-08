package com.fep.transaction.validator;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DesignatedAccountValidator.
 */
@DisplayName("DesignatedAccountValidator Tests")
class DesignatedAccountValidatorTest {

    private DesignatedAccountValidator validator;

    private static final String SOURCE_ACCOUNT = "12345678901234";
    private static final String DEST_ACCOUNT_1 = "98765432109876";
    private static final String DEST_ACCOUNT_2 = "11111111111111";

    @BeforeEach
    void setUp() {
        validator = new DesignatedAccountValidator();
    }

    @Nested
    @DisplayName("Designated Account Registration Tests")
    class DesignatedAccountRegistrationTests {

        @Test
        @DisplayName("Should register designated account")
        void shouldRegisterDesignatedAccount() {
            // Act
            validator.registerDesignatedAccount(SOURCE_ACCOUNT, DEST_ACCOUNT_1);

            // Assert
            assertTrue(validator.isDesignatedAccount(SOURCE_ACCOUNT, DEST_ACCOUNT_1));
        }

        @Test
        @DisplayName("Should remove designated account")
        void shouldRemoveDesignatedAccount() {
            // Arrange
            validator.registerDesignatedAccount(SOURCE_ACCOUNT, DEST_ACCOUNT_1);

            // Act
            validator.removeDesignatedAccount(SOURCE_ACCOUNT, DEST_ACCOUNT_1);

            // Assert
            assertFalse(validator.isDesignatedAccount(SOURCE_ACCOUNT, DEST_ACCOUNT_1));
        }

        @Test
        @DisplayName("Should get all designated accounts")
        void shouldGetAllDesignatedAccounts() {
            // Arrange
            validator.registerDesignatedAccount(SOURCE_ACCOUNT, DEST_ACCOUNT_1);
            validator.registerDesignatedAccount(SOURCE_ACCOUNT, DEST_ACCOUNT_2);

            // Act
            var accounts = validator.getDesignatedAccounts(SOURCE_ACCOUNT);

            // Assert
            assertEquals(2, accounts.size());
            assertTrue(accounts.contains(DEST_ACCOUNT_1));
            assertTrue(accounts.contains(DEST_ACCOUNT_2));
        }

        @Test
        @DisplayName("Should return empty set for unknown account")
        void shouldReturnEmptySetForUnknownAccount() {
            // Act
            var accounts = validator.getDesignatedAccounts("UNKNOWN");

            // Assert
            assertTrue(accounts.isEmpty());
        }
    }

    @Nested
    @DisplayName("Designated Transfer Validation Tests")
    class DesignatedTransferValidationTests {

        @BeforeEach
        void setUpDesignatedAccount() {
            validator.registerDesignatedAccount(SOURCE_ACCOUNT, DEST_ACCOUNT_1);
        }

        @Test
        @DisplayName("Should allow designated transfer within limit")
        void shouldAllowDesignatedTransferWithinLimit() {
            // Arrange
            TransactionRequest request = createTransferRequest(
                    "TXN001",
                    SOURCE_ACCOUNT,
                    DEST_ACCOUNT_1,
                    new BigDecimal("1000000")
            );
            request.setIsDesignatedAccount(true);

            // Act & Assert - Should not throw
            assertDoesNotThrow(() -> validator.validate(request));
        }

        @Test
        @DisplayName("Should reject designated transfer exceeding limit")
        void shouldRejectDesignatedTransferExceedingLimit() {
            // Arrange
            TransactionRequest request = createTransferRequest(
                    "TXN002",
                    SOURCE_ACCOUNT,
                    DEST_ACCOUNT_1,
                    new BigDecimal("3000000") // Exceeds 2,000,000 limit
            );
            request.setIsDesignatedAccount(true);

            // Act & Assert
            assertThrows(TransactionException.class, () -> validator.validate(request));
        }

        @Test
        @DisplayName("Should auto-detect designated account")
        void shouldAutoDetectDesignatedAccount() {
            // Arrange - No explicit flag set, should detect from registry
            TransactionRequest request = createTransferRequest(
                    "TXN003",
                    SOURCE_ACCOUNT,
                    DEST_ACCOUNT_1,
                    new BigDecimal("500000")
            );

            // Act & Assert - Should not throw for designated account
            assertDoesNotThrow(() -> validator.validate(request));
        }
    }

    @Nested
    @DisplayName("Non-Designated Transfer Validation Tests")
    class NonDesignatedTransferValidationTests {

        @Test
        @DisplayName("Should allow non-designated ATM transfer within night time limit")
        void shouldAllowNonDesignatedAtmTransferWithinLimit() {
            // Arrange - Use 5000 which is within night time limit (10,000)
            TransactionRequest request = createTransferRequest(
                    "TXN004",
                    SOURCE_ACCOUNT,
                    DEST_ACCOUNT_2, // Not designated
                    new BigDecimal("5000")
            );
            request.setChannel("ATM");

            // Act & Assert - Should pass even during night time
            assertDoesNotThrow(() -> validator.validate(request));
        }

        @Test
        @DisplayName("Should reject non-designated ATM transfer exceeding limit")
        void shouldRejectNonDesignatedAtmTransferExceedingLimit() {
            // Arrange - 50,000 exceeds both regular (30,000) and night (10,000) limits
            TransactionRequest request = createTransferRequest(
                    "TXN005",
                    SOURCE_ACCOUNT,
                    DEST_ACCOUNT_2,
                    new BigDecimal("50000") // Exceeds ATM 30,000 limit
            );
            request.setChannel("ATM");

            // Act & Assert
            assertThrows(TransactionException.class, () -> validator.validate(request));
        }

        @Test
        @DisplayName("Should allow non-designated Internet transfer within night time limit")
        void shouldAllowNonDesignatedInternetTransferWithinLimit() {
            // Arrange - Use 8000 which is within night time limit (10,000)
            TransactionRequest request = createTransferRequest(
                    "TXN006",
                    SOURCE_ACCOUNT,
                    DEST_ACCOUNT_2,
                    new BigDecimal("8000") // Within night time limit
            );
            request.setChannel("INTERNET");

            // Act & Assert - Should pass even during night time
            assertDoesNotThrow(() -> validator.validate(request));
        }

        @Test
        @DisplayName("Should reject non-designated Internet transfer exceeding limit")
        void shouldRejectNonDesignatedInternetTransferExceedingLimit() {
            // Arrange - 60,000 exceeds both regular (50,000) and night (10,000) limits
            TransactionRequest request = createTransferRequest(
                    "TXN007",
                    SOURCE_ACCOUNT,
                    DEST_ACCOUNT_2,
                    new BigDecimal("60000") // Exceeds Internet 50,000 limit
            );
            request.setChannel("INTERNET");

            // Act & Assert
            assertThrows(TransactionException.class, () -> validator.validate(request));
        }
    }

    @Nested
    @DisplayName("Daily Limit Tracking Tests")
    class DailyLimitTrackingTests {

        @Test
        @DisplayName("Should track daily transfers")
        void shouldTrackDailyTransfers() {
            // Act
            validator.recordTransfer(SOURCE_ACCOUNT, new BigDecimal("10000"), false);
            validator.recordTransfer(SOURCE_ACCOUNT, new BigDecimal("15000"), false);

            // Assert
            var tracker = validator.getDailyTransferStats(SOURCE_ACCOUNT);
            assertNotNull(tracker);
            assertEquals(new BigDecimal("25000"), tracker.getNonDesignatedTotal());
            assertEquals(2, tracker.getNonDesignatedCount());
        }

        @Test
        @DisplayName("Should reject when daily non-designated limit exceeded")
        void shouldRejectWhenDailyNonDesignatedLimitExceeded() {
            // Arrange - Record previous transfers totaling 90,000
            validator.recordTransfer(SOURCE_ACCOUNT, new BigDecimal("50000"), false);
            validator.recordTransfer(SOURCE_ACCOUNT, new BigDecimal("40000"), false);

            // Try to transfer 20,000 more (total would be 110,000, exceeds 100,000 limit)
            TransactionRequest request = createTransferRequest(
                    "TXN008",
                    SOURCE_ACCOUNT,
                    DEST_ACCOUNT_2,
                    new BigDecimal("20000")
            );
            request.setChannel("ATM");

            // Act & Assert
            assertThrows(TransactionException.class, () -> validator.validate(request));
        }

        @Test
        @DisplayName("Should reset daily limits")
        void shouldResetDailyLimits() {
            // Arrange
            validator.recordTransfer(SOURCE_ACCOUNT, new BigDecimal("50000"), false);

            // Act
            validator.resetDailyLimits();

            // Assert
            var tracker = validator.getDailyTransferStats(SOURCE_ACCOUNT);
            assertEquals(BigDecimal.ZERO, tracker.getNonDesignatedTotal());
        }
    }

    @Nested
    @DisplayName("Limit Information Tests")
    class LimitInformationTests {

        @Test
        @DisplayName("Should return correct channel limits")
        void shouldReturnCorrectChannelLimits() {
            // ATM limit
            assertEquals(new BigDecimal("30000"),
                    validator.getNonDesignatedLimit("ATM", false));

            // Internet limit
            assertEquals(new BigDecimal("50000"),
                    validator.getNonDesignatedLimit("INTERNET", false));

            // Counter limit
            assertEquals(new BigDecimal("100000"),
                    validator.getNonDesignatedLimit("COUNTER", false));
        }

        @Test
        @DisplayName("Should return night time limit")
        void shouldReturnNightTimeLimit() {
            assertEquals(new BigDecimal("10000"),
                    validator.getNonDesignatedLimit("ATM", true));
        }

        @Test
        @DisplayName("Should return designated limit")
        void shouldReturnDesignatedLimit() {
            assertEquals(new BigDecimal("2000000"), validator.getDesignatedLimit());
        }

        @Test
        @DisplayName("Should return daily limits")
        void shouldReturnDailyLimits() {
            assertEquals(new BigDecimal("100000"), validator.getDailyNonDesignatedLimit());
            assertEquals(new BigDecimal("10000000"), validator.getDailyDesignatedLimit());
        }
    }

    @Nested
    @DisplayName("Non-Applicable Transaction Tests")
    class NonApplicableTransactionTests {

        @Test
        @DisplayName("Should skip validation for non-transfer transactions")
        void shouldSkipValidationForNonTransferTransactions() {
            // Arrange - Withdrawal doesn't need designated account validation
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("TXN009")
                    .transactionType(TransactionType.WITHDRAWAL)
                    .sourceAccount(SOURCE_ACCOUNT)
                    .amount(new BigDecimal("100000"))
                    .build();

            // Act & Assert - Should not throw even with large amount
            assertDoesNotThrow(() -> validator.validate(request));
        }
    }

    // Helper method to create transfer request
    private TransactionRequest createTransferRequest(
            String txnId,
            String sourceAccount,
            String destAccount,
            BigDecimal amount) {

        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.TRANSFER)
                .sourceAccount(sourceAccount)
                .destinationAccount(destAccount)
                .amount(amount)
                .build();
    }
}
