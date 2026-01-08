package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Processor for e-wallet transactions (電子錢包).
 * Supports top-up, payment, and wallet-to-wallet transfers.
 */
public class EWalletProcessor extends AbstractTransactionProcessor {

    /** Maximum single e-wallet transaction (default 50,000 TWD) */
    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("50000");

    /** Minimum transaction amount */
    private static final BigDecimal MIN_TRANSACTION_AMOUNT = new BigDecimal("1");

    /** Maximum wallet balance */
    private static final BigDecimal MAX_WALLET_BALANCE = new BigDecimal("100000");

    /** Daily transaction limit */
    private static final BigDecimal DAILY_LIMIT = new BigDecimal("200000");

    /** E-wallet transaction types */
    public static final String TXN_TYPE_TOPUP = "TOPUP";
    public static final String TXN_TYPE_PAYMENT = "PAYMENT";
    public static final String TXN_TYPE_TRANSFER = "TRANSFER";

    /** Supported e-wallet providers */
    private static final Map<String, EWalletProvider> PROVIDERS = Map.of(
            "LINE", new EWalletProvider("LINE", "LINE Pay", "LINE Pay", true, true, true),
            "JKOPAY", new EWalletProvider("JKOPAY", "JKOPay", "街口支付", true, true, true),
            "PXPAY", new EWalletProvider("PXPAY", "PX Pay", "全聯支付", true, true, false),
            "ICASH", new EWalletProvider("ICASH", "icash Pay", "愛金卡", true, true, true),
            "EASY", new EWalletProvider("EASY", "Easy Wallet", "悠遊付", true, true, true),
            "PI", new EWalletProvider("PI", "Pi Wallet", "Pi 拍錢包", true, true, true),
            "GAMAPAY", new EWalletProvider("GAMAPAY", "Gama Pay", "橘子支付", true, true, false)
    );

    /**
     * E-wallet provider information.
     */
    public static class EWalletProvider {
        private final String code;
        private final String name;
        private final String chineseName;
        private final boolean supportsTopup;
        private final boolean supportsPayment;
        private final boolean supportsTransfer;

        public EWalletProvider(String code, String name, String chineseName,
                              boolean supportsTopup, boolean supportsPayment, boolean supportsTransfer) {
            this.code = code;
            this.name = name;
            this.chineseName = chineseName;
            this.supportsTopup = supportsTopup;
            this.supportsPayment = supportsPayment;
            this.supportsTransfer = supportsTransfer;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getChineseName() { return chineseName; }
        public boolean isSupportsTopup() { return supportsTopup; }
        public boolean isSupportsPayment() { return supportsPayment; }
        public boolean isSupportsTransfer() { return supportsTransfer; }
    }

    @Override
    public TransactionType getSupportedType() {
        // This processor handles multiple e-wallet transaction types
        return TransactionType.E_WALLET_TOPUP;
    }

    /**
     * Checks if this processor supports the given transaction type.
     */
    @Override
    public boolean supports(TransactionType type) {
        return type == TransactionType.E_WALLET_TOPUP ||
               type == TransactionType.E_WALLET_PAYMENT ||
               type == TransactionType.E_WALLET_TRANSFER;
    }

    /**
     * Overrides base validation to skip PAN validation.
     * E-wallet uses wallet account instead of card.
     */
    @Override
    public void validate(TransactionRequest request) {
        validateTransactionId(request);
        doValidate(request);
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate e-wallet provider
        validateProvider(request);

        // Validate wallet account
        validateWalletAccount(request);

        // Validate transaction type specific requirements
        validateTransactionTypeRequirements(request);

        // Validate amount
        validateTransactionAmount(request);
    }

    /**
     * Validates the e-wallet provider.
     */
    private void validateProvider(TransactionRequest request) {
        String providerCode = request.getEWalletProvider();
        if (providerCode == null || providerCode.isBlank()) {
            throw TransactionException.invalidRequest("E-wallet provider is required");
        }

        EWalletProvider provider = PROVIDERS.get(providerCode.toUpperCase());
        if (provider == null) {
            throw TransactionException.invalidRequest("Unsupported e-wallet provider: " + providerCode);
        }
    }

    /**
     * Validates the wallet account.
     */
    private void validateWalletAccount(TransactionRequest request) {
        String accountId = request.getEWalletAccountId();
        if (accountId == null || accountId.isBlank()) {
            throw TransactionException.invalidRequest("E-wallet account ID is required");
        }

        // Account ID format validation (provider-specific, simplified here)
        if (accountId.length() < 8) {
            throw TransactionException.invalidRequest("Invalid e-wallet account format");
        }
    }

    /**
     * Validates transaction type specific requirements.
     */
    private void validateTransactionTypeRequirements(TransactionRequest request) {
        String txnType = request.getEWalletTxnType();
        if (txnType == null || txnType.isBlank()) {
            // Infer from TransactionType
            txnType = inferTxnType(request.getTransactionType());
        }

        if (txnType == null) {
            throw TransactionException.invalidRequest("E-wallet transaction type is required");
        }

        EWalletProvider provider = PROVIDERS.get(request.getEWalletProvider().toUpperCase());

        switch (txnType.toUpperCase()) {
            case TXN_TYPE_TOPUP:
                if (!provider.isSupportsTopup()) {
                    throw TransactionException.invalidRequest(
                            provider.getName() + " does not support top-up");
                }
                // Top-up requires source account (bank account to debit)
                if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
                    throw TransactionException.invalidRequest("Source account is required for top-up");
                }
                break;

            case TXN_TYPE_PAYMENT:
                if (!provider.isSupportsPayment()) {
                    throw TransactionException.invalidRequest(
                            provider.getName() + " does not support payment");
                }
                break;

            case TXN_TYPE_TRANSFER:
                if (!provider.isSupportsTransfer()) {
                    throw TransactionException.invalidRequest(
                            provider.getName() + " does not support wallet transfer");
                }
                // Transfer requires recipient
                if (request.getEWalletRecipient() == null || request.getEWalletRecipient().isBlank()) {
                    throw TransactionException.invalidRequest("Recipient wallet ID is required for transfer");
                }
                break;

            default:
                throw TransactionException.invalidRequest("Invalid e-wallet transaction type: " + txnType);
        }
    }

    /**
     * Infers transaction type from TransactionType enum.
     */
    private String inferTxnType(TransactionType type) {
        if (type == null) return null;
        return switch (type) {
            case E_WALLET_TOPUP -> TXN_TYPE_TOPUP;
            case E_WALLET_PAYMENT -> TXN_TYPE_PAYMENT;
            case E_WALLET_TRANSFER -> TXN_TYPE_TRANSFER;
            default -> null;
        };
    }

    /**
     * Validates transaction amount.
     */
    private void validateTransactionAmount(TransactionRequest request) {
        BigDecimal amount = request.getAmount();

        if (amount == null) {
            throw TransactionException.invalidRequest("Transaction amount is required");
        }

        if (amount.compareTo(MIN_TRANSACTION_AMOUNT) < 0) {
            throw TransactionException.invalidRequest(
                    "Transaction amount must be at least " + MIN_TRANSACTION_AMOUNT + " TWD");
        }

        if (amount.compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
            throw TransactionException.exceedsLimit("e-wallet transaction");
        }
    }

    @Override
    protected void preProcess(TransactionRequest request) {
        String txnType = request.getEWalletTxnType();
        if (txnType == null) {
            txnType = inferTxnType(request.getTransactionType());
        }

        log.debug("[{}] Pre-processing e-wallet {}: provider={}, amount={}",
                request.getTransactionId(),
                txnType,
                request.getEWalletProvider(),
                request.getAmount());

        // Here we would typically:
        // 1. Verify wallet account status with provider
        // 2. Check wallet balance (for payment/transfer)
        // 3. Verify daily transaction limits
        // 4. Anti-fraud checks
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        String txnType = request.getEWalletTxnType();
        if (txnType == null) {
            txnType = inferTxnType(request.getTransactionType());
        }

        EWalletProvider provider = PROVIDERS.get(request.getEWalletProvider().toUpperCase());

        log.info("[{}] Processing e-wallet {}: provider={}, account={}, amount={} TWD",
                request.getTransactionId(),
                txnType,
                provider.getName(),
                maskWalletId(request.getEWalletAccountId()),
                request.getAmount());

        // Simulate processing based on transaction type
        BigDecimal newBalance = simulateTransaction(request, txnType);
        String walletRef = generateWalletReference(provider.getCode());

        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(ResponseCode.APPROVED.getCode())
                .responseCodeEnum(ResponseCode.APPROVED)
                .responseDescription(ResponseCode.APPROVED.getDescription())
                .responseDescriptionChinese(ResponseCode.APPROVED.getChineseDescription())
                .approved(true)
                .authorizationCode(generateAuthorizationCode())
                .amount(request.getAmount())
                .currencyCode(request.getCurrencyCode())
                .rrn(request.getRrn())
                .stan(request.getStan())
                .eWalletReference(walletRef)
                .eWalletBalance(newBalance)
                .eWalletProviderName(provider.getChineseName())
                .build();
    }

    /**
     * Simulates the transaction and returns new balance.
     * In real implementation, this would integrate with e-wallet provider.
     */
    private BigDecimal simulateTransaction(TransactionRequest request, String txnType) {
        // Simulate a wallet balance (in real system, query from provider)
        BigDecimal currentBalance = new BigDecimal("5000");
        BigDecimal amount = request.getAmount();

        return switch (txnType.toUpperCase()) {
            case TXN_TYPE_TOPUP -> currentBalance.add(amount);
            case TXN_TYPE_PAYMENT, TXN_TYPE_TRANSFER -> currentBalance.subtract(amount);
            default -> currentBalance;
        };
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        String txnType = request.getEWalletTxnType();
        if (txnType == null) {
            txnType = inferTxnType(request.getTransactionType());
        }

        if (response.isApproved()) {
            log.info("[{}] E-wallet {} approved: {} TWD, Provider: {}, Balance: {}, Ref: {}",
                    request.getTransactionId(),
                    txnType,
                    response.getAmount(),
                    response.getEWalletProviderName(),
                    response.getEWalletBalance(),
                    response.getEWalletReference());

            // Here we would typically:
            // 1. Confirm transaction with e-wallet provider
            // 2. Update transaction log
            // 3. Send notification to user
            // 4. Update loyalty points (if applicable)
        } else {
            log.warn("[{}] E-wallet {} declined: {}",
                    request.getTransactionId(),
                    txnType,
                    response.getResponseCode());
        }
    }

    /**
     * Generates a wallet transaction reference.
     */
    private String generateWalletReference(String providerCode) {
        return "EW" + providerCode.substring(0, Math.min(2, providerCode.length())) +
               System.currentTimeMillis() +
               String.format("%04d", (int) (Math.random() * 10000));
    }

    /**
     * Masks wallet ID for logging.
     */
    private String maskWalletId(String walletId) {
        if (walletId == null || walletId.length() < 6) {
            return "****";
        }
        return walletId.substring(0, 3) + "****" + walletId.substring(walletId.length() - 3);
    }

    /**
     * Gets supported e-wallet providers.
     */
    public static Set<String> getSupportedProviders() {
        return PROVIDERS.keySet();
    }

    /**
     * Gets provider information by code.
     */
    public static EWalletProvider getProvider(String code) {
        if (code == null) {
            return null;
        }
        return PROVIDERS.get(code.toUpperCase());
    }
}
