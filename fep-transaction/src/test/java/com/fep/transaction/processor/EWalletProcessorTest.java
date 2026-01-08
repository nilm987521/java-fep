package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EWalletProcessor.
 */
class EWalletProcessorTest {

    private EWalletProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new EWalletProcessor();
    }

    @Nested
    @DisplayName("Transaction Type Support Tests")
    class TransactionTypeSupportTests {

        @Test
        @DisplayName("Should support E_WALLET_TOPUP transaction type")
        void shouldSupportEWalletTopup() {
            assertTrue(processor.supports(TransactionType.E_WALLET_TOPUP));
        }

        @Test
        @DisplayName("Should support E_WALLET_PAYMENT transaction type")
        void shouldSupportEWalletPayment() {
            assertTrue(processor.supports(TransactionType.E_WALLET_PAYMENT));
        }

        @Test
        @DisplayName("Should support E_WALLET_TRANSFER transaction type")
        void shouldSupportEWalletTransfer() {
            assertTrue(processor.supports(TransactionType.E_WALLET_TRANSFER));
        }

        @Test
        @DisplayName("Should not support unrelated transaction types")
        void shouldNotSupportUnrelatedTypes() {
            assertFalse(processor.supports(TransactionType.WITHDRAWAL));
            assertFalse(processor.supports(TransactionType.DEPOSIT));
            assertFalse(processor.supports(TransactionType.TRANSFER));
        }

        @Test
        @DisplayName("Should return E_WALLET_TOPUP as primary supported type")
        void shouldReturnPrimarySupportedType() {
            assertEquals(TransactionType.E_WALLET_TOPUP, processor.getSupportedType());
        }
    }

    @Nested
    @DisplayName("Provider Tests")
    class ProviderTests {

        @Test
        @DisplayName("Should return all supported providers")
        void shouldReturnSupportedProviders() {
            Set<String> providers = EWalletProcessor.getSupportedProviders();
            assertTrue(providers.contains("LINE"));
            assertTrue(providers.contains("JKOPAY"));
            assertTrue(providers.contains("PXPAY"));
            assertTrue(providers.contains("ICASH"));
            assertTrue(providers.contains("EASY"));
            assertTrue(providers.contains("PI"));
            assertTrue(providers.contains("GAMAPAY"));
        }

        @Test
        @DisplayName("Should get provider by code")
        void shouldGetProviderByCode() {
            EWalletProcessor.EWalletProvider provider = EWalletProcessor.getProvider("LINE");
            assertNotNull(provider);
            assertEquals("LINE", provider.getCode());
            assertEquals("LINE Pay", provider.getName());
            assertEquals("LINE Pay", provider.getChineseName());
            assertTrue(provider.isSupportsTopup());
            assertTrue(provider.isSupportsPayment());
            assertTrue(provider.isSupportsTransfer());
        }

        @Test
        @DisplayName("Should get provider by code case insensitive")
        void shouldGetProviderCaseInsensitive() {
            EWalletProcessor.EWalletProvider provider = EWalletProcessor.getProvider("line");
            assertNotNull(provider);
            assertEquals("LINE", provider.getCode());
        }

        @Test
        @DisplayName("Should return null for unknown provider")
        void shouldReturnNullForUnknownProvider() {
            assertNull(EWalletProcessor.getProvider("UNKNOWN"));
        }

        @Test
        @DisplayName("Should return null for null provider code")
        void shouldReturnNullForNullCode() {
            assertNull(EWalletProcessor.getProvider(null));
        }
    }

    @Nested
    @DisplayName("Top-up Validation Tests")
    class TopupValidationTests {

        @Test
        @DisplayName("Should reject top-up without provider")
        void shouldRejectTopupWithoutProvider() {
            TransactionRequest request = createTopupRequest();
            request.setEWalletProvider(null);

            TransactionException ex = assertThrows(TransactionException.class,
                    () -> processor.validate(request));
            assertTrue(ex.getMessage().contains("provider is required"));
        }

        @Test
        @DisplayName("Should reject top-up with unsupported provider")
        void shouldRejectTopupWithUnsupportedProvider() {
            TransactionRequest request = createTopupRequest();
            request.setEWalletProvider("UNKNOWN");

            TransactionException ex = assertThrows(TransactionException.class,
                    () -> processor.validate(request));
            assertTrue(ex.getMessage().contains("Unsupported e-wallet provider"));
        }

        @Test
        @DisplayName("Should reject top-up without wallet account ID")
        void shouldRejectTopupWithoutAccountId() {
            TransactionRequest request = createTopupRequest();
            request.setEWalletAccountId(null);

            TransactionException ex = assertThrows(TransactionException.class,
                    () -> processor.validate(request));
            assertTrue(ex.getMessage().contains("account ID is required"));
        }

        @Test
        @DisplayName("Should reject top-up with short wallet account ID")
        void shouldRejectTopupWithShortAccountId() {
            TransactionRequest request = createTopupRequest();
            request.setEWalletAccountId("1234567"); // Less than 8 chars

            TransactionException ex = assertThrows(TransactionException.class,
                    () -> processor.validate(request));
            assertTrue(ex.getMessage().contains("Invalid e-wallet account format"));
        }

        @Test
        @DisplayName("Should reject top-up without source account")
        void shouldRejectTopupWithoutSourceAccount() {
            TransactionRequest request = createTopupRequest();
            request.setSourceAccount(null);

            TransactionException ex = assertThrows(TransactionException.class,
                    () -> processor.validate(request));
            assertTrue(ex.getMessage().contains("Source account is required for top-up"));
        }

        @Test
        @DisplayName("Should reject top-up without amount")
        void shouldRejectTopupWithoutAmount() {
            TransactionRequest request = createTopupRequest();
            request.setAmount(null);

            TransactionException ex = assertThrows(TransactionException.class,
                    () -> processor.validate(request));
            assertTrue(ex.getMessage().contains("amount is required"));
        }

        @Test
        @DisplayName("Should reject top-up with amount below minimum")
        void shouldRejectTopupBelowMinimum() {
            TransactionRequest request = createTopupRequest();
            request.setAmount(new BigDecimal("0.5")); // Less than 1 TWD

            TransactionException ex = assertThrows(TransactionException.class,
                    () -> processor.validate(request));
            assertTrue(ex.getMessage().contains("at least"));
        }

        @Test
        @DisplayName("Should reject top-up exceeding maximum")
        void shouldRejectTopupExceedingMaximum() {
            TransactionRequest request = createTopupRequest();
            request.setAmount(new BigDecimal("60000")); // Exceeds 50,000 TWD

            TransactionException ex = assertThrows(TransactionException.class,
                    () -> processor.validate(request));
            assertTrue(ex.getMessage().contains("limit") || ex.getMessage().contains("exceed"));
        }
    }

    @Nested
    @DisplayName("Payment Validation Tests")
    class PaymentValidationTests {

        @Test
        @DisplayName("Should validate payment successfully")
        void shouldValidatePaymentSuccessfully() {
            TransactionRequest request = createPaymentRequest();
            assertDoesNotThrow(() -> processor.validate(request));
        }

        @Test
        @DisplayName("Should reject payment with provider not supporting payment")
        void shouldRejectPaymentWithUnsupportedProvider() {
            // Note: All current providers support payment, so this test is for future-proofing
            TransactionRequest request = createPaymentRequest();
            request.setEWalletProvider("LINE"); // LINE supports payment
            assertDoesNotThrow(() -> processor.validate(request));
        }
    }

    @Nested
    @DisplayName("Transfer Validation Tests")
    class TransferValidationTests {

        @Test
        @DisplayName("Should reject transfer without recipient")
        void shouldRejectTransferWithoutRecipient() {
            TransactionRequest request = createTransferRequest();
            request.setEWalletRecipient(null);

            TransactionException ex = assertThrows(TransactionException.class,
                    () -> processor.validate(request));
            assertTrue(ex.getMessage().contains("Recipient wallet ID is required"));
        }

        @Test
        @DisplayName("Should reject transfer with provider not supporting transfer")
        void shouldRejectTransferWithUnsupportedProvider() {
            TransactionRequest request = createTransferRequest();
            request.setEWalletProvider("PXPAY"); // PX Pay does not support transfer

            TransactionException ex = assertThrows(TransactionException.class,
                    () -> processor.validate(request));
            assertTrue(ex.getMessage().contains("does not support wallet transfer"));
        }

        @Test
        @DisplayName("Should validate transfer with supported provider")
        void shouldValidateTransferWithSupportedProvider() {
            TransactionRequest request = createTransferRequest();
            request.setEWalletProvider("LINE"); // LINE supports transfer
            assertDoesNotThrow(() -> processor.validate(request));
        }
    }

    @Nested
    @DisplayName("Processing Tests")
    class ProcessingTests {

        @Test
        @DisplayName("Should process top-up successfully")
        void shouldProcessTopupSuccessfully() {
            TransactionRequest request = createTopupRequest();
            TransactionResponse response = processor.process(request);

            assertNotNull(response);
            assertTrue(response.isApproved());
            assertEquals("00", response.getResponseCode());
            assertNotNull(response.getAuthorizationCode());
            assertNotNull(response.getEWalletReference());
            assertTrue(response.getEWalletReference().startsWith("EWLI")); // LINE prefix
            assertNotNull(response.getEWalletBalance());
            assertEquals("LINE Pay", response.getEWalletProviderName());
        }

        @Test
        @DisplayName("Should process payment successfully")
        void shouldProcessPaymentSuccessfully() {
            TransactionRequest request = createPaymentRequest();
            TransactionResponse response = processor.process(request);

            assertNotNull(response);
            assertTrue(response.isApproved());
            assertEquals("00", response.getResponseCode());
            assertNotNull(response.getEWalletReference());
            assertTrue(response.getEWalletReference().startsWith("EWJK")); // JKOPAY prefix
            assertEquals("街口支付", response.getEWalletProviderName());
        }

        @Test
        @DisplayName("Should process transfer successfully")
        void shouldProcessTransferSuccessfully() {
            TransactionRequest request = createTransferRequest();
            TransactionResponse response = processor.process(request);

            assertNotNull(response);
            assertTrue(response.isApproved());
            assertEquals("00", response.getResponseCode());
            assertNotNull(response.getEWalletReference());
        }

        @Test
        @DisplayName("Should calculate correct balance after top-up")
        void shouldCalculateCorrectBalanceAfterTopup() {
            TransactionRequest request = createTopupRequest();
            request.setAmount(new BigDecimal("1000"));
            TransactionResponse response = processor.process(request);

            // Simulated starting balance is 5000, add 1000 = 6000
            assertEquals(new BigDecimal("6000"), response.getEWalletBalance());
        }

        @Test
        @DisplayName("Should calculate correct balance after payment")
        void shouldCalculateCorrectBalanceAfterPayment() {
            TransactionRequest request = createPaymentRequest();
            request.setAmount(new BigDecimal("500"));
            TransactionResponse response = processor.process(request);

            // Simulated starting balance is 5000, subtract 500 = 4500
            assertEquals(new BigDecimal("4500"), response.getEWalletBalance());
        }

        @Test
        @DisplayName("Should calculate correct balance after transfer")
        void shouldCalculateCorrectBalanceAfterTransfer() {
            TransactionRequest request = createTransferRequest();
            request.setAmount(new BigDecimal("200"));
            TransactionResponse response = processor.process(request);

            // Simulated starting balance is 5000, subtract 200 = 4800
            assertEquals(new BigDecimal("4800"), response.getEWalletBalance());
        }
    }

    @Nested
    @DisplayName("Provider Feature Tests")
    class ProviderFeatureTests {

        @Test
        @DisplayName("JKOPay should support all features")
        void jkoPayShouldSupportAllFeatures() {
            EWalletProcessor.EWalletProvider provider = EWalletProcessor.getProvider("JKOPAY");
            assertNotNull(provider);
            assertTrue(provider.isSupportsTopup());
            assertTrue(provider.isSupportsPayment());
            assertTrue(provider.isSupportsTransfer());
            assertEquals("街口支付", provider.getChineseName());
        }

        @Test
        @DisplayName("PX Pay should not support transfer")
        void pxPayShouldNotSupportTransfer() {
            EWalletProcessor.EWalletProvider provider = EWalletProcessor.getProvider("PXPAY");
            assertNotNull(provider);
            assertTrue(provider.isSupportsTopup());
            assertTrue(provider.isSupportsPayment());
            assertFalse(provider.isSupportsTransfer());
            assertEquals("全聯支付", provider.getChineseName());
        }

        @Test
        @DisplayName("Gama Pay should not support transfer")
        void gamaPayShouldNotSupportTransfer() {
            EWalletProcessor.EWalletProvider provider = EWalletProcessor.getProvider("GAMAPAY");
            assertNotNull(provider);
            assertTrue(provider.isSupportsTopup());
            assertTrue(provider.isSupportsPayment());
            assertFalse(provider.isSupportsTransfer());
            assertEquals("橘子支付", provider.getChineseName());
        }

        @Test
        @DisplayName("Easy Wallet should support all features")
        void easyWalletShouldSupportAllFeatures() {
            EWalletProcessor.EWalletProvider provider = EWalletProcessor.getProvider("EASY");
            assertNotNull(provider);
            assertTrue(provider.isSupportsTopup());
            assertTrue(provider.isSupportsPayment());
            assertTrue(provider.isSupportsTransfer());
            assertEquals("悠遊付", provider.getChineseName());
        }

        @Test
        @DisplayName("icash Pay should support all features")
        void icashPayShouldSupportAllFeatures() {
            EWalletProcessor.EWalletProvider provider = EWalletProcessor.getProvider("ICASH");
            assertNotNull(provider);
            assertTrue(provider.isSupportsTopup());
            assertTrue(provider.isSupportsPayment());
            assertTrue(provider.isSupportsTransfer());
            assertEquals("愛金卡", provider.getChineseName());
        }

        @Test
        @DisplayName("Pi Wallet should support all features")
        void piWalletShouldSupportAllFeatures() {
            EWalletProcessor.EWalletProvider provider = EWalletProcessor.getProvider("PI");
            assertNotNull(provider);
            assertTrue(provider.isSupportsTopup());
            assertTrue(provider.isSupportsPayment());
            assertTrue(provider.isSupportsTransfer());
            assertEquals("Pi 拍錢包", provider.getChineseName());
        }
    }

    @Nested
    @DisplayName("Different Provider Processing Tests")
    class DifferentProviderProcessingTests {

        @Test
        @DisplayName("Should process with icash Pay provider")
        void shouldProcessWithIcashPay() {
            TransactionRequest request = createTopupRequest();
            request.setEWalletProvider("ICASH");
            TransactionResponse response = processor.process(request);

            assertTrue(response.isApproved());
            assertTrue(response.getEWalletReference().startsWith("EWIC"));
            assertEquals("愛金卡", response.getEWalletProviderName());
        }

        @Test
        @DisplayName("Should process with Easy Wallet provider")
        void shouldProcessWithEasyWallet() {
            TransactionRequest request = createTopupRequest();
            request.setEWalletProvider("EASY");
            TransactionResponse response = processor.process(request);

            assertTrue(response.isApproved());
            assertTrue(response.getEWalletReference().startsWith("EWEA"));
            assertEquals("悠遊付", response.getEWalletProviderName());
        }

        @Test
        @DisplayName("Should process with Pi Wallet provider")
        void shouldProcessWithPiWallet() {
            TransactionRequest request = createTopupRequest();
            request.setEWalletProvider("PI");
            TransactionResponse response = processor.process(request);

            assertTrue(response.isApproved());
            assertTrue(response.getEWalletReference().startsWith("EWPI"));
            assertEquals("Pi 拍錢包", response.getEWalletProviderName());
        }

        @Test
        @DisplayName("Should process with Gama Pay provider")
        void shouldProcessWithGamaPay() {
            TransactionRequest request = createPaymentRequest();
            request.setEWalletProvider("GAMAPAY");
            TransactionResponse response = processor.process(request);

            assertTrue(response.isApproved());
            assertTrue(response.getEWalletReference().startsWith("EWGA"));
            assertEquals("橘子支付", response.getEWalletProviderName());
        }

        @Test
        @DisplayName("Should process with PX Pay provider")
        void shouldProcessWithPxPay() {
            TransactionRequest request = createPaymentRequest();
            request.setEWalletProvider("PXPAY");
            TransactionResponse response = processor.process(request);

            assertTrue(response.isApproved());
            assertTrue(response.getEWalletReference().startsWith("EWPX"));
            assertEquals("全聯支付", response.getEWalletProviderName());
        }
    }

    // Helper methods to create test requests

    private TransactionRequest createTopupRequest() {
        return TransactionRequest.builder()
                .transactionId("EWALLET-TOPUP-001")
                .transactionType(TransactionType.E_WALLET_TOPUP)
                .eWalletProvider("LINE")
                .eWalletAccountId("LINE12345678")
                .eWalletTxnType("TOPUP")
                .sourceAccount("123456789012")
                .amount(new BigDecimal("1000"))
                .currencyCode("TWD")
                .build();
    }

    private TransactionRequest createPaymentRequest() {
        return TransactionRequest.builder()
                .transactionId("EWALLET-PAYMENT-001")
                .transactionType(TransactionType.E_WALLET_PAYMENT)
                .eWalletProvider("JKOPAY")
                .eWalletAccountId("JKO123456789")
                .eWalletTxnType("PAYMENT")
                .amount(new BigDecimal("500"))
                .currencyCode("TWD")
                .build();
    }

    private TransactionRequest createTransferRequest() {
        return TransactionRequest.builder()
                .transactionId("EWALLET-TRANSFER-001")
                .transactionType(TransactionType.E_WALLET_TRANSFER)
                .eWalletProvider("LINE")
                .eWalletAccountId("LINE12345678")
                .eWalletTxnType("TRANSFER")
                .eWalletRecipient("LINE87654321")
                .amount(new BigDecimal("200"))
                .currencyCode("TWD")
                .build();
    }
}
