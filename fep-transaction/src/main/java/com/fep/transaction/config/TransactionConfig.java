package com.fep.transaction.config;

import com.fep.transaction.converter.MessageToRequestConverter;
import com.fep.transaction.converter.ResponseToMessageConverter;
import com.fep.transaction.limit.LimitManager;
import com.fep.transaction.logging.DefaultTransactionLogger;
import com.fep.transaction.logging.TransactionLogger;
import com.fep.transaction.processor.*;
import com.fep.transaction.repository.InMemoryTransactionRepository;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.service.TransactionService;
import com.fep.transaction.service.TransactionServiceImpl;
import com.fep.transaction.validator.AmountValidator;
import com.fep.transaction.validator.CardValidator;
import com.fep.transaction.validator.TransactionValidator;
import com.fep.transaction.validator.ValidationChain;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring configuration for transaction processing components.
 */
@Configuration
public class TransactionConfig {

    // ==================== Processors ====================

    @Bean
    public WithdrawalProcessor withdrawalProcessor() {
        return new WithdrawalProcessor();
    }

    @Bean
    public TransferProcessor transferProcessor() {
        return new TransferProcessor();
    }

    @Bean
    public BalanceInquiryProcessor balanceInquiryProcessor() {
        return new BalanceInquiryProcessor();
    }

    @Bean
    public DepositProcessor depositProcessor() {
        return new DepositProcessor();
    }

    @Bean
    public BillPaymentProcessor billPaymentProcessor() {
        return new BillPaymentProcessor();
    }

    @Bean
    public ReversalProcessor reversalProcessor() {
        return new ReversalProcessor();
    }

    @Bean
    public QrPaymentProcessor qrPaymentProcessor() {
        return new QrPaymentProcessor();
    }

    // ==================== Validators ====================

    @Bean
    public CardValidator cardValidator() {
        return new CardValidator();
    }

    @Bean
    public AmountValidator amountValidator() {
        return new AmountValidator();
    }

    @Bean
    public ValidationChain validationChain(List<TransactionValidator> validators) {
        return new ValidationChain(validators);
    }

    // ==================== Logging ====================

    @Bean
    public TransactionLogger transactionLogger() {
        return new DefaultTransactionLogger();
    }

    // ==================== Limit Management ====================

    @Bean
    public LimitManager limitManager() {
        return new LimitManager();
    }

    // ==================== Repository ====================

    @Bean
    public TransactionRepository transactionRepository() {
        return new InMemoryTransactionRepository();
    }

    // ==================== Converters ====================

    @Bean
    public MessageToRequestConverter messageToRequestConverter() {
        return new MessageToRequestConverter();
    }

    @Bean
    public ResponseToMessageConverter responseToMessageConverter() {
        return new ResponseToMessageConverter();
    }

    // ==================== Services ====================

    @Bean
    public TransactionService transactionService(List<TransactionProcessor> processors) {
        return new TransactionServiceImpl(processors);
    }
}
