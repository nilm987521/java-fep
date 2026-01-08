package com.fep.transaction.integration;

import com.fep.transaction.config.TransactionModuleConfig;
import com.fep.transaction.repository.TransactionRepository;
import com.fep.transaction.service.TransactionService;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Integration Test Configuration.
 * Provides shared configuration and utilities for integration tests.
 */
public class IntegrationTestConfig implements BeforeAllCallback {

    private static boolean initialized = false;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!initialized) {
            // Initialize test environment
            System.out.println("Initializing integration test environment...");
            initialized = true;
        }
    }

    /**
     * Creates a test transaction service with default configuration.
     */
    public static TransactionService createTestTransactionService() {
        return TransactionModuleConfig.createTransactionService();
    }

    /**
     * Creates a test transaction service with custom repository.
     */
    public static TransactionService createTestTransactionService(TransactionRepository repository) {
        return TransactionModuleConfig.createTransactionService(repository);
    }

    /**
     * Creates a test transaction repository.
     */
    public static TransactionRepository createTestRepository() {
        return TransactionModuleConfig.createRepository();
    }
}
