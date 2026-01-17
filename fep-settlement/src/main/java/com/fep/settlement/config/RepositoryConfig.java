package com.fep.settlement.config;

import com.fep.settlement.repository.InMemorySettlementRepository;
import com.fep.settlement.repository.SettlementRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Repository configuration for settlement module.
 * Provides InMemory implementation for non-Oracle profiles.
 */
@Configuration("settlementRepositoryConfig")
public class RepositoryConfig {

    /**
     * InMemory SettlementRepository for development and testing.
     * Active when 'oracle' profile is NOT active.
     */
    @Bean
    @Profile("!oracle & !oracle-prod")
    public SettlementRepository inMemorySettlementRepository() {
        return new InMemorySettlementRepository();
    }
}
