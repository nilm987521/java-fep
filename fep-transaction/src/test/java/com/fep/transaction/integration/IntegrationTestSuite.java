package com.fep.transaction.integration;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Integration Test Suite.
 * Runs all integration and end-to-end tests.
 *
 * Usage:
 * - Run this test class to execute all integration tests
 * - Or run: mvn test -Dtest=IntegrationTestSuite
 */
@Suite
@SuiteDisplayName("FEP Transaction Integration Test Suite")
@SelectClasses({
        TransactionApiIntegrationTest.class,
        EndToEndTransactionTest.class,
        MessageTransactionIntegrationTest.class
})
public class IntegrationTestSuite {
    // Test suite configuration
}
