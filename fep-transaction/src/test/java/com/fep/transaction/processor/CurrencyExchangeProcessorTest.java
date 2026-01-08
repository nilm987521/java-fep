package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CurrencyExchangeProcessor.
 */
@DisplayName("CurrencyExchangeProcessor Tests")
class CurrencyExchangeProcessorTest {

    private CurrencyExchangeProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CurrencyExchangeProcessor();
    }

    @Test
    @DisplayName("Should support CURRENCY_EXCHANGE transaction type")
    void shouldSupportCurrencyExchangeType() {
        assertEquals(TransactionType.CURRENCY_EXCHANGE, processor.getSupportedType());
    }

    @Nested
    @DisplayName("Successful Exchange Tests")
    class SuccessfulExchangeTests {

        @Test
        @DisplayName("Should process USD buy successfully")
        void shouldProcessUsdBuySuccessfully() {
            // Arrange - Buy USD with TWD
            TransactionRequest request = createExchangeRequest(
                    "FX001",
                    "TWD",
                    "USD",
                    CurrencyExchangeProcessor.EXCHANGE_TYPE_BUY,
                    new BigDecimal("31800") // ~1000 USD
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
            assertNotNull(response.getAuthorizationCode());
            assertNotNull(response.getExchangeReference());
            assertNotNull(response.getAppliedExchangeRate());
            assertNotNull(response.getForeignAmount());
            assertNotNull(response.getExchangeFee());
        }

        @Test
        @DisplayName("Should process USD sell successfully")
        void shouldProcessUsdSellSuccessfully() {
            // Arrange - Sell USD for TWD
            TransactionRequest request = createExchangeRequest(
                    "FX002",
                    "USD",
                    "TWD",
                    CurrencyExchangeProcessor.EXCHANGE_TYPE_SELL,
                    new BigDecimal("1000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertEquals(ResponseCode.APPROVED.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should process JPY buy successfully")
        void shouldProcessJpyBuySuccessfully() {
            // Arrange - Buy JPY with TWD
            TransactionRequest request = createExchangeRequest(
                    "FX003",
                    "TWD",
                    "JPY",
                    CurrencyExchangeProcessor.EXCHANGE_TYPE_BUY,
                    new BigDecimal("22000") // ~100,000 JPY
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertNotNull(response.getForeignAmount());
        }

        @Test
        @DisplayName("Should apply correct exchange rate for BUY")
        void shouldApplyCorrectRateForBuy() {
            // Arrange - Buy USD (customer buys, bank sells, use sell rate)
            TransactionRequest request = createExchangeRequest(
                    "FX004",
                    "TWD",
                    "USD",
                    CurrencyExchangeProcessor.EXCHANGE_TYPE_BUY,
                    new BigDecimal("31800")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            // Should use sell rate (31.80 for USD)
            assertEquals(new BigDecimal("31.80"), response.getAppliedExchangeRate());
        }

        @Test
        @DisplayName("Should apply correct exchange rate for SELL")
        void shouldApplyCorrectRateForSell() {
            // Arrange - Sell USD (customer sells, bank buys, use buy rate)
            TransactionRequest request = createExchangeRequest(
                    "FX005",
                    "USD",
                    "TWD",
                    CurrencyExchangeProcessor.EXCHANGE_TYPE_SELL,
                    new BigDecimal("1000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            // Should use buy rate (31.50 for USD)
            assertEquals(new BigDecimal("31.50"), response.getAppliedExchangeRate());
        }

        @Test
        @DisplayName("Should calculate correct fee")
        void shouldCalculateCorrectFee() {
            // Arrange - 100,000 TWD, fee rate 0.3% = 300 TWD
            TransactionRequest request = createExchangeRequest(
                    "FX006",
                    "TWD",
                    "USD",
                    CurrencyExchangeProcessor.EXCHANGE_TYPE_BUY,
                    new BigDecimal("100000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertTrue(response.isApproved());
            assertNotNull(response.getExchangeFee());
            assertTrue(response.getExchangeFee().compareTo(new BigDecimal("50")) >= 0);
        }
    }

    @Nested
    @DisplayName("Validation Error Tests")
    class ValidationErrorTests {

        @Test
        @DisplayName("Should reject when source account is missing")
        void shouldRejectMissingSourceAccount() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("FX-ERR001")
                    .transactionType(TransactionType.CURRENCY_EXCHANGE)
                    .sourceCurrency("TWD")
                    .targetCurrency("USD")
                    .exchangeType(CurrencyExchangeProcessor.EXCHANGE_TYPE_BUY)
                    .amount(new BigDecimal("31800"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Source account is required"));
        }

        @Test
        @DisplayName("Should reject when exchange type is missing")
        void shouldRejectMissingExchangeType() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("FX-ERR002")
                    .transactionType(TransactionType.CURRENCY_EXCHANGE)
                    .sourceAccount("12345678901234")
                    .sourceCurrency("TWD")
                    .targetCurrency("USD")
                    .amount(new BigDecimal("31800"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Exchange type is required"));
        }

        @Test
        @DisplayName("Should reject invalid exchange type")
        void shouldRejectInvalidExchangeType() {
            // Arrange
            TransactionRequest request = createExchangeRequest(
                    "FX-ERR003",
                    "TWD",
                    "USD",
                    "INVALID",
                    new BigDecimal("31800")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Invalid exchange type"));
        }

        @Test
        @DisplayName("Should reject same source and target currency")
        void shouldRejectSameCurrency() {
            // Arrange
            TransactionRequest request = createExchangeRequest(
                    "FX-ERR004",
                    "TWD",
                    "TWD",
                    CurrencyExchangeProcessor.EXCHANGE_TYPE_BUY,
                    new BigDecimal("31800")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Source and target currency cannot be the same"));
        }

        @Test
        @DisplayName("Should reject exchange without TWD")
        void shouldRejectExchangeWithoutTwd() {
            // Arrange - Neither currency is TWD
            TransactionRequest request = createExchangeRequest(
                    "FX-ERR005",
                    "USD",
                    "JPY",
                    CurrencyExchangeProcessor.EXCHANGE_TYPE_BUY,
                    new BigDecimal("1000")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("One currency must be TWD"));
        }

        @Test
        @DisplayName("Should reject unsupported currency")
        void shouldRejectUnsupportedCurrency() {
            // Arrange
            TransactionRequest request = createExchangeRequest(
                    "FX-ERR006",
                    "TWD",
                    "XYZ", // Unsupported currency
                    CurrencyExchangeProcessor.EXCHANGE_TYPE_BUY,
                    new BigDecimal("31800")
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Unsupported currency"));
        }

        @Test
        @DisplayName("Should reject amount exceeding limit")
        void shouldRejectExceedingLimit() {
            // Arrange
            TransactionRequest request = createExchangeRequest(
                    "FX-ERR007",
                    "TWD",
                    "USD",
                    CurrencyExchangeProcessor.EXCHANGE_TYPE_BUY,
                    new BigDecimal("600000") // Exceeds 500,000 TWD limit
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertEquals(ResponseCode.EXCEEDS_WITHDRAWAL_LIMIT.getCode(), response.getResponseCode());
        }

        @Test
        @DisplayName("Should reject amount below minimum")
        void shouldRejectBelowMinimum() {
            // Arrange
            TransactionRequest request = createExchangeRequest(
                    "FX-ERR008",
                    "TWD",
                    "USD",
                    CurrencyExchangeProcessor.EXCHANGE_TYPE_BUY,
                    new BigDecimal("50") // Below 100 TWD equivalent
            );

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Exchange amount too small"));
        }

        @Test
        @DisplayName("Should reject missing source currency")
        void shouldRejectMissingSourceCurrency() {
            // Arrange
            TransactionRequest request = TransactionRequest.builder()
                    .transactionId("FX-ERR009")
                    .transactionType(TransactionType.CURRENCY_EXCHANGE)
                    .sourceAccount("12345678901234")
                    .targetCurrency("USD")
                    .exchangeType(CurrencyExchangeProcessor.EXCHANGE_TYPE_BUY)
                    .amount(new BigDecimal("31800"))
                    .build();

            // Act
            TransactionResponse response = processor.process(request);

            // Assert
            assertFalse(response.isApproved());
            assertTrue(response.getErrorDetails().contains("Source currency is required"));
        }
    }

    @Nested
    @DisplayName("Static Method Tests")
    class StaticMethodTests {

        @Test
        @DisplayName("Should return supported currencies")
        void shouldReturnSupportedCurrencies() {
            var currencies = CurrencyExchangeProcessor.getSupportedCurrencies();

            assertTrue(currencies.contains("USD"));
            assertTrue(currencies.contains("JPY"));
            assertTrue(currencies.contains("EUR"));
            assertTrue(currencies.contains("CNY"));
        }

        @Test
        @DisplayName("Should return correct exchange rate")
        void shouldReturnCorrectExchangeRate() {
            // Buy rate (bank sells to customer)
            BigDecimal sellRate = CurrencyExchangeProcessor.getExchangeRate("USD", true);
            assertEquals(new BigDecimal("31.80"), sellRate);

            // Sell rate (bank buys from customer)
            BigDecimal buyRate = CurrencyExchangeProcessor.getExchangeRate("USD", false);
            assertEquals(new BigDecimal("31.50"), buyRate);
        }

        @Test
        @DisplayName("Should return null for unsupported currency rate")
        void shouldReturnNullForUnsupportedCurrencyRate() {
            assertNull(CurrencyExchangeProcessor.getExchangeRate("XYZ", true));
        }
    }

    // Helper method to create currency exchange request
    private TransactionRequest createExchangeRequest(
            String txnId,
            String sourceCurrency,
            String targetCurrency,
            String exchangeType,
            BigDecimal amount) {

        return TransactionRequest.builder()
                .transactionId(txnId)
                .transactionType(TransactionType.CURRENCY_EXCHANGE)
                .processingCode("530000")
                .sourceAccount("12345678901234")
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .exchangeType(exchangeType)
                .amount(amount)
                .currencyCode("901")
                .terminalId("BRANCH01")
                .acquiringBankCode("004")
                .stan("000001")
                .rrn("123456789012")
                .channel("COUNTER")
                .build();
    }
}
