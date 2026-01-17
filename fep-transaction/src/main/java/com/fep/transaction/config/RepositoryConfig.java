package com.fep.transaction.config;

import com.fep.transaction.repository.InMemoryTransactionRepository;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.scheduled.InMemoryScheduledTransferRepository;
import com.fep.transaction.scheduled.ScheduledTransferRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Repository configuration for transaction module.
 * Provides InMemory implementations for non-Oracle profiles.
 */
@Configuration("transactionRepositoryConfig")
public class RepositoryConfig {

    /**
     * InMemory TransactionRepository for development and testing.
     * Active when 'oracle' profile is NOT active.
     */
    @Bean
    @Profile("!oracle & !oracle-prod")
    public TransactionRepository inMemoryTransactionRepository() {
        return new InMemoryTransactionRepository();
    }

    /**
     * InMemory ScheduledTransferRepository for development and testing.
     * Active when 'oracle' profile is NOT active.
     */
    @Bean
    @Profile("!oracle & !oracle-prod")
    public ScheduledTransferRepository inMemoryScheduledTransferRepository() {
        return new InMemoryScheduledTransferRepository();
    }
}
