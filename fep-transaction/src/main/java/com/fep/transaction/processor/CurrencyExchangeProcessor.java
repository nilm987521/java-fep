package com.fep.transaction.processor;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.ResponseCode;
import com.fep.transaction.enums.TransactionType;
import com.fep.transaction.exception.TransactionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Processor for foreign currency exchange transactions (外幣兌換).
 * Handles buying and selling foreign currency.
 */
public class CurrencyExchangeProcessor extends AbstractTransactionProcessor {

    /** Maximum single exchange amount in TWD equivalent */
    private static final BigDecimal MAX_EXCHANGE_AMOUNT_TWD = new BigDecimal("500000");

    /** Minimum exchange amount in TWD equivalent */
    private static final BigDecimal MIN_EXCHANGE_AMOUNT_TWD = new BigDecimal("100");

    /** Daily exchange limit per customer */
    private static final BigDecimal MAX_DAILY_LIMIT_TWD = new BigDecimal("5000000");

    /** Exchange type: Buy foreign currency */
    public static final String EXCHANGE_TYPE_BUY = "BUY";

    /** Exchange type: Sell foreign currency */
    public static final String EXCHANGE_TYPE_SELL = "SELL";

    /** Supported currencies with sample rates (TWD based) */
    private static final Map<String, CurrencyInfo> SUPPORTED_CURRENCIES = Map.of(
            "USD", new CurrencyInfo("USD", "US Dollar", "美金", new BigDecimal("31.50"), new BigDecimal("31.80")),
            "JPY", new CurrencyInfo("JPY", "Japanese Yen", "日圓", new BigDecimal("0.21"), new BigDecimal("0.22")),
            "EUR", new CurrencyInfo("EUR", "Euro", "歐元", new BigDecimal("34.20"), new BigDecimal("34.60")),
            "GBP", new CurrencyInfo("GBP", "British Pound", "英鎊", new BigDecimal("39.80"), new BigDecimal("40.30")),
            "AUD", new CurrencyInfo("AUD", "Australian Dollar", "澳幣", new BigDecimal("20.50"), new BigDecimal("20.80")),
            "CNY", new CurrencyInfo("CNY", "Chinese Yuan", "人民幣", new BigDecimal("4.35"), new BigDecimal("4.45")),
            "HKD", new CurrencyInfo("HKD", "Hong Kong Dollar", "港幣", new BigDecimal("4.00"), new BigDecimal("4.10")),
            "SGD", new CurrencyInfo("SGD", "Singapore Dollar", "新幣", new BigDecimal("23.50"), new BigDecimal("23.80")),
            "KRW", new CurrencyInfo("KRW", "Korean Won", "韓元", new BigDecimal("0.023"), new BigDecimal("0.024")),
            "THB", new CurrencyInfo("THB", "Thai Baht", "泰銖", new BigDecimal("0.90"), new BigDecimal("0.93"))
    );

    /** Fee rate for exchange (0.3%) */
    private static final BigDecimal FEE_RATE = new BigDecimal("0.003");

    /** Minimum fee */
    private static final BigDecimal MIN_FEE = new BigDecimal("50");

    /**
     * Currency information holder.
     */
    public static class CurrencyInfo {
        private final String code;
        private final String name;
        private final String chineseName;
        private final BigDecimal buyRate;  // Bank buys foreign currency (customer sells)
        private final BigDecimal sellRate; // Bank sells foreign currency (customer buys)

        public CurrencyInfo(String code, String name, String chineseName,
                           BigDecimal buyRate, BigDecimal sellRate) {
            this.code = code;
            this.name = name;
            this.chineseName = chineseName;
            this.buyRate = buyRate;
            this.sellRate = sellRate;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getChineseName() { return chineseName; }
        public BigDecimal getBuyRate() { return buyRate; }
        public BigDecimal getSellRate() { return sellRate; }
    }

    @Override
    public TransactionType getSupportedType() {
        return TransactionType.CURRENCY_EXCHANGE;
    }

    /**
     * Overrides base validation to skip PAN validation.
     * Currency exchange uses account number instead of card.
     */
    @Override
    public void validate(TransactionRequest request) {
        validateTransactionId(request);
        doValidate(request);
    }

    @Override
    protected void doValidate(TransactionRequest request) {
        // Validate source account
        validateSourceAccount(request);

        // Validate exchange type
        validateExchangeType(request);

        // Validate currencies
        validateCurrencies(request);

        // Validate amount
        validateExchangeAmount(request);
    }

    /**
     * Validates the source account.
     */
    private void validateSourceAccount(TransactionRequest request) {
        if (request.getSourceAccount() == null || request.getSourceAccount().isBlank()) {
            throw TransactionException.invalidRequest("Source account is required for currency exchange");
        }
    }

    /**
     * Validates exchange type (BUY or SELL).
     */
    private void validateExchangeType(TransactionRequest request) {
        String exchangeType = request.getExchangeType();
        if (exchangeType == null || exchangeType.isBlank()) {
            throw TransactionException.invalidRequest("Exchange type is required (BUY or SELL)");
        }

        if (!EXCHANGE_TYPE_BUY.equals(exchangeType) && !EXCHANGE_TYPE_SELL.equals(exchangeType)) {
            throw TransactionException.invalidRequest("Invalid exchange type: " + exchangeType);
        }
    }

    /**
     * Validates source and target currencies.
     */
    private void validateCurrencies(TransactionRequest request) {
        String sourceCurrency = request.getSourceCurrency();
        String targetCurrency = request.getTargetCurrency();

        if (sourceCurrency == null || sourceCurrency.isBlank()) {
            throw TransactionException.invalidRequest("Source currency is required");
        }

        if (targetCurrency == null || targetCurrency.isBlank()) {
            throw TransactionException.invalidRequest("Target currency is required");
        }

        if (sourceCurrency.equals(targetCurrency)) {
            throw TransactionException.invalidRequest("Source and target currency cannot be the same");
        }

        // One of the currencies must be TWD
        boolean hasTwd = "TWD".equals(sourceCurrency) || "TWD".equals(targetCurrency);
        if (!hasTwd) {
            throw TransactionException.invalidRequest("One currency must be TWD");
        }

        // The foreign currency must be supported
        String foreignCurrency = "TWD".equals(sourceCurrency) ? targetCurrency : sourceCurrency;
        if (!SUPPORTED_CURRENCIES.containsKey(foreignCurrency)) {
            throw TransactionException.invalidRequest("Unsupported currency: " + foreignCurrency);
        }
    }

    /**
     * Validates exchange amount.
     */
    private void validateExchangeAmount(TransactionRequest request) {
        BigDecimal amount = request.getAmount();

        if (amount == null) {
            throw TransactionException.invalidRequest("Exchange amount is required");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw TransactionException.invalidRequest("Exchange amount must be positive");
        }

        // Calculate TWD equivalent for limit check
        BigDecimal twdEquivalent = calculateTwdEquivalent(request);

        if (twdEquivalent.compareTo(MIN_EXCHANGE_AMOUNT_TWD) < 0) {
            throw TransactionException.invalidRequest(
                    "Exchange amount too small (minimum TWD equivalent: " + MIN_EXCHANGE_AMOUNT_TWD + ")");
        }

        if (twdEquivalent.compareTo(MAX_EXCHANGE_AMOUNT_TWD) > 0) {
            throw TransactionException.exceedsLimit("currency exchange");
        }
    }

    /**
     * Calculates TWD equivalent for limit checking.
     */
    private BigDecimal calculateTwdEquivalent(TransactionRequest request) {
        String sourceCurrency = request.getSourceCurrency();
        BigDecimal amount = request.getAmount();

        if ("TWD".equals(sourceCurrency)) {
            return amount;
        }

        // Convert foreign currency to TWD using buy rate
        CurrencyInfo currencyInfo = SUPPORTED_CURRENCIES.get(sourceCurrency);
        if (currencyInfo != null) {
            return amount.multiply(currencyInfo.getBuyRate());
        }

        return amount;
    }

    @Override
    protected void preProcess(TransactionRequest request) {
        log.debug("[{}] Pre-processing currency exchange: {} -> {}, amount={}",
                request.getTransactionId(),
                request.getSourceCurrency(),
                request.getTargetCurrency(),
                request.getAmount());

        // Here we would typically:
        // 1. Get real-time exchange rate from treasury system
        // 2. Lock the rate for the transaction
        // 3. Verify source account balance
        // 4. Check customer's daily exchange limit
    }

    @Override
    protected TransactionResponse doProcess(TransactionRequest request) {
        String exchangeType = request.getExchangeType();
        String foreignCurrency = "TWD".equals(request.getSourceCurrency()) ?
                request.getTargetCurrency() : request.getSourceCurrency();

        CurrencyInfo currencyInfo = SUPPORTED_CURRENCIES.get(foreignCurrency);

        // Determine the rate based on exchange type
        // BUY: Customer buys foreign currency (uses sell rate)
        // SELL: Customer sells foreign currency (uses buy rate)
        BigDecimal rate = EXCHANGE_TYPE_BUY.equals(exchangeType) ?
                currencyInfo.getSellRate() : currencyInfo.getBuyRate();

        // Use pre-agreed rate if provided
        if (request.getExchangeRate() != null) {
            rate = request.getExchangeRate();
        }

        // Calculate converted amount
        BigDecimal sourceAmount = request.getAmount();
        BigDecimal convertedAmount;
        BigDecimal twdAmount;
        BigDecimal foreignAmount;

        if ("TWD".equals(request.getSourceCurrency())) {
            // Buying foreign currency with TWD
            twdAmount = sourceAmount;
            foreignAmount = sourceAmount.divide(rate, 2, RoundingMode.DOWN);
            convertedAmount = foreignAmount;
        } else {
            // Selling foreign currency for TWD
            foreignAmount = sourceAmount;
            twdAmount = sourceAmount.multiply(rate).setScale(0, RoundingMode.DOWN);
            convertedAmount = twdAmount;
        }

        // Calculate fee (based on TWD amount)
        BigDecimal fee = calculateFee(twdAmount);

        log.info("[{}] Processing currency exchange: {} {} {} -> {} {}, rate={}, fee={}",
                request.getTransactionId(),
                exchangeType,
                sourceAmount,
                request.getSourceCurrency(),
                convertedAmount,
                request.getTargetCurrency(),
                rate,
                fee);

        String exchangeRef = generateExchangeReference();

        return TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .responseCode(ResponseCode.APPROVED.getCode())
                .responseCodeEnum(ResponseCode.APPROVED)
                .responseDescription(ResponseCode.APPROVED.getDescription())
                .responseDescriptionChinese(ResponseCode.APPROVED.getChineseDescription())
                .approved(true)
                .authorizationCode(generateAuthorizationCode())
                .amount(twdAmount)
                .currencyCode("TWD")
                .rrn(request.getRrn())
                .stan(request.getStan())
                .exchangeReference(exchangeRef)
                .appliedExchangeRate(rate)
                .foreignAmount(foreignAmount)
                .exchangeFee(fee)
                .rateExpiryTime(LocalDateTime.now().plusMinutes(5))
                .build();
    }

    @Override
    protected void postProcess(TransactionRequest request, TransactionResponse response) {
        if (response.isApproved()) {
            log.info("[{}] Currency exchange approved: {} {} @ {}, Foreign: {}, Fee: {}, Ref: {}",
                    request.getTransactionId(),
                    request.getExchangeType(),
                    response.getAmount() + " TWD",
                    response.getAppliedExchangeRate(),
                    response.getForeignAmount(),
                    response.getExchangeFee(),
                    response.getExchangeReference());

            // Here we would typically:
            // 1. Update foreign currency account
            // 2. Report to Central Bank (for regulatory compliance)
            // 3. Send confirmation to customer
            // 4. Update treasury position
        } else {
            log.warn("[{}] Currency exchange declined: {}",
                    request.getTransactionId(),
                    response.getResponseCode());
        }
    }

    /**
     * Calculates the exchange fee.
     */
    private BigDecimal calculateFee(BigDecimal twdAmount) {
        BigDecimal fee = twdAmount.multiply(FEE_RATE).setScale(0, RoundingMode.CEILING);
        return fee.compareTo(MIN_FEE) < 0 ? MIN_FEE : fee;
    }

    /**
     * Generates an exchange reference number.
     */
    private String generateExchangeReference() {
        return "FX" + System.currentTimeMillis() + String.format("%04d", (int) (Math.random() * 10000));
    }

    /**
     * Gets the current exchange rate for a currency.
     *
     * @param currencyCode the currency code
     * @param isBuy true if customer is buying foreign currency
     * @return the exchange rate
     */
    public static BigDecimal getExchangeRate(String currencyCode, boolean isBuy) {
        CurrencyInfo info = SUPPORTED_CURRENCIES.get(currencyCode);
        if (info == null) {
            return null;
        }
        return isBuy ? info.getSellRate() : info.getBuyRate();
    }

    /**
     * Gets the list of supported currencies.
     */
    public static Set<String> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES.keySet();
    }
}
