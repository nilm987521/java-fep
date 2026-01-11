package com.fep.jmeter.sampler;

import com.fep.communication.client.FiscDualChannelClient;
import com.fep.communication.config.DualChannelConfig;
import com.fep.jmeter.engine.BankCoreSimulatorEngine;
import com.fep.jmeter.validation.MessageValidationEngine;
import com.fep.jmeter.validation.ValidationResult;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.MessageType;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BankCoreServerSampler and related components.
 *
 * <p>Tests the Bank Core System Simulator which models a bank's core system
 * with dual-channel architecture for FEP integration testing.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BankCoreServerSamplerIntegrationTest {

    private static BankCoreSimulatorEngine engine;
    private static FiscDualChannelClient client;
    private static int sendPort;
    private static int receivePort;

    @BeforeAll
    static void setUp() throws Exception {
        // Start the engine with random ports
        engine = new BankCoreSimulatorEngine(0, 0);
        engine.setDefaultResponseCode("00");
        engine.setEnableFepIdRouting(true);
        engine.setFepIdField(32);
        engine.setDefaultAvailableBalance("000000100000"); // 1,000.00
        engine.setDefaultLedgerBalance("000000150000");    // 1,500.00
        engine.start().get(10, TimeUnit.SECONDS);

        sendPort = engine.getSendPort();
        receivePort = engine.getReceivePort();

        System.out.println("BankCore Engine started: Send=" + sendPort + ", Receive=" + receivePort);
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @Order(1)
    void testEngineStarted() {
        assertTrue(engine.isRunning(), "Engine should be running");
        assertTrue(sendPort > 0, "Send port should be assigned");
        assertTrue(receivePort > 0, "Receive port should be assigned");
    }

    @Test
    @Order(2)
    void testClientConnection() throws Exception {
        // From FEP client's perspective:
        // - FEP sends to BankCore's Send port (where BankCore receives requests)
        // - FEP receives from BankCore's Receive port (where BankCore sends responses)
        DualChannelConfig config = DualChannelConfig.builder()
            .sendHost("localhost")
            .sendPort(sendPort)       // FEP sends to BankCore's Send port
            .receiveHost("localhost")
            .receivePort(receivePort) // FEP receives from BankCore's Receive port
            .build();

        client = new FiscDualChannelClient(config);
        client.connect().get(10, TimeUnit.SECONDS);

        assertTrue(client.isConnected(), "Client should be connected");

        // Wait for connection to be registered
        Thread.sleep(500);

        assertEquals(1, engine.getSendChannelClientCount().get(), "One send channel client should be connected");
        assertEquals(1, engine.getReceiveChannelClientCount().get(), "One receive channel client should be connected");
    }

    @Test
    @Order(3)
    void testNetworkManagementSignOn() throws Exception {
        Iso8583Message request = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        request.setField(11, "000001");  // STAN
        request.setField(70, "001");     // Sign-on
        request.setField(32, "001");     // FEP ID

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        assertNotNull(response, "Should receive response");
        assertEquals("0810", response.getMti(), "Response MTI should be 0810");
        assertEquals("00", response.getFieldAsString(39), "Response code should be 00");
        assertEquals("000001", response.getFieldAsString(11), "STAN should match");
        assertEquals("001", response.getFieldAsString(70), "Network code should match");
    }

    @Test
    @Order(4)
    void testNetworkManagementEchoTest() throws Exception {
        Iso8583Message request = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        request.setField(11, "000002");  // STAN
        request.setField(70, "301");     // Echo test
        request.setField(32, "001");     // FEP ID

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        assertNotNull(response, "Should receive response");
        assertEquals("0810", response.getMti(), "Response MTI should be 0810");
        assertEquals("00", response.getFieldAsString(39), "Response code should be 00");
    }

    @Test
    @Order(5)
    void testAuthorizationRequest() throws Exception {
        Iso8583Message request = new Iso8583Message(MessageType.AUTH_REQUEST);
        request.setField(2, "4111111111111111");  // PAN
        request.setField(3, "000000");            // Processing Code (Purchase)
        request.setField(4, "000000050000");      // Amount (500.00)
        request.setField(11, "000003");           // STAN
        request.setField(32, "001");              // FEP ID
        request.setField(41, "POS00001");         // Terminal ID
        request.setField(42, "MERCHANT001234");   // Merchant ID

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        assertNotNull(response, "Should receive response");
        assertEquals("0110", response.getMti(), "Response MTI should be 0110");
        assertEquals("00", response.getFieldAsString(39), "Response code should be 00");
        assertNotNull(response.getFieldAsString(38), "Should have authorization code");
        assertEquals(6, response.getFieldAsString(38).length(), "Auth code should be 6 digits");
    }

    @Test
    @Order(6)
    void testFinancialWithdrawal() throws Exception {
        Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        request.setField(2, "4111111111111111");  // PAN
        request.setField(3, "010000");            // Processing Code (Withdrawal)
        request.setField(4, "000000100000");      // Amount (1,000.00)
        request.setField(11, "000004");           // STAN
        request.setField(32, "001");              // FEP ID
        request.setField(41, "ATM00001");         // Terminal ID
        request.setField(102, "1234567890");      // From Account

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        assertNotNull(response, "Should receive response");
        assertEquals("0210", response.getMti(), "Response MTI should be 0210");
        assertEquals("00", response.getFieldAsString(39), "Response code should be 00");
        assertEquals("000004", response.getFieldAsString(11), "STAN should match");

        // Verify statistics
        assertTrue(engine.getMessagesReceived().get() >= 4, "Should have received messages");
        assertTrue(engine.getMessagesSent().get() >= 4, "Should have sent messages");
    }

    @Test
    @Order(7)
    void testBalanceInquiry() throws Exception {
        Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        request.setField(2, "4111111111111111");  // PAN
        request.setField(3, "310000");            // Processing Code (Balance Inquiry)
        request.setField(4, "000000000000");      // Amount (0)
        request.setField(11, "000005");           // STAN
        request.setField(32, "001");              // FEP ID
        request.setField(102, "1234567890");      // From Account

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        assertNotNull(response, "Should receive response");
        assertEquals("0210", response.getMti(), "Response MTI should be 0210");
        assertEquals("00", response.getFieldAsString(39), "Response code should be 00");

        // Should have balance info in F54
        String balance = response.getFieldAsString(54);
        assertNotNull(balance, "Should have balance info");
        assertTrue(balance.length() >= 12, "Balance should contain available and ledger balance");
    }

    @Test
    @Order(8)
    void testReversalRequest() throws Exception {
        Iso8583Message request = new Iso8583Message(MessageType.REVERSAL_REQUEST);
        request.setField(2, "4111111111111111");  // PAN
        request.setField(3, "010000");            // Processing Code
        request.setField(4, "000000100000");      // Amount
        request.setField(11, "000006");           // STAN
        request.setField(32, "001");              // FEP ID
        request.setField(90, "0200000004");       // Original Data (MTI + STAN)

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        assertNotNull(response, "Should receive response");
        assertEquals("0410", response.getMti(), "Response MTI should be 0410");
        assertEquals("00", response.getFieldAsString(39), "Response code should be 00");
    }

    @Test
    @Order(9)
    void testValidationEngine() {
        MessageValidationEngine validationEngine = new MessageValidationEngine();
        validationEngine.configure("""
            {
                "globalRules": {
                    "required": [2, 3, 4, 11],
                    "format": {"2": "N(13-19)", "3": "N(6)", "4": "N(12)"},
                    "value": {"3": ["010000", "310000", "400000"]}
                }
            }
            """);

        // Valid message
        Iso8583Message validMsg = new Iso8583Message("0200");
        validMsg.setField(2, "4111111111111111");
        validMsg.setField(3, "010000");
        validMsg.setField(4, "000000100000");
        validMsg.setField(11, "000001");

        ValidationResult result = validationEngine.validate(validMsg);
        assertTrue(result.isValid(), "Valid message should pass: " + result.getErrorSummary());

        // Invalid message (missing required field)
        Iso8583Message invalidMsg = new Iso8583Message("0200");
        invalidMsg.setField(2, "4111111111111111");
        invalidMsg.setField(3, "010000");
        // Missing field 4 and 11

        result = validationEngine.validate(invalidMsg);
        assertFalse(result.isValid(), "Invalid message should fail");
        assertEquals(2, result.getErrorCount(), "Should have 2 errors (missing F4 and F11)");
    }

    @Test
    @Order(10)
    void testValidationIntegration() throws Exception {
        // Configure validation on engine
        MessageValidationEngine validationEngine = new MessageValidationEngine();
        validationEngine.configure("{\"globalRules\": {\"required\": [2, 3, 4, 11]}}");
        engine.setValidationCallback(validationEngine.createValidationCallback());
        engine.setValidationErrorCode("30");

        // Send invalid message (missing required field)
        Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        request.setField(3, "010000");  // Processing Code
        request.setField(11, "000007"); // STAN
        request.setField(32, "001");    // FEP ID
        // Missing F2 and F4

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        assertNotNull(response, "Should receive error response");
        assertEquals("30", response.getFieldAsString(39), "Should get validation error code 30");

        // Verify validation error count increased
        assertTrue(engine.getValidationErrors().get() >= 1, "Validation error count should increase");

        // Clear validation callback for remaining tests
        engine.setValidationCallback(null);
    }

    @Test
    @Order(11)
    void testProactiveMessageBroadcast() throws Exception {
        // Broadcast echo test to all FEPs
        Iso8583Message echoMsg = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        echoMsg.setField(11, "999001");
        echoMsg.setField(70, "301"); // Echo test

        int sentCount = engine.broadcastMessage(echoMsg);
        assertEquals(1, sentCount, "Should send to 1 FEP client");
    }

    @Test
    @Order(12)
    void testProactiveMessageToSpecificFep() throws Exception {
        // Get registered FEP IDs
        var registeredFepIds = engine.getClientRouter().getRegisteredBankIds();
        assertFalse(registeredFepIds.isEmpty(), "Should have registered FEP IDs");

        String targetFepId = registeredFepIds.iterator().next();

        // Send message to specific FEP
        Iso8583Message signOnMsg = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        signOnMsg.setField(11, "999002");
        signOnMsg.setField(70, "001"); // Sign-on notification

        boolean sent = engine.sendProactiveMessage(targetFepId, signOnMsg);
        assertTrue(sent, "Should send message to target FEP ID: " + targetFepId);
    }

    @Test
    @Order(13)
    void testReconciliationNotification() throws Exception {
        // Get a registered FEP ID
        var registeredFepIds = engine.getClientRouter().getRegisteredBankIds();
        assertFalse(registeredFepIds.isEmpty(), "Should have registered FEP IDs");

        String targetFepId = registeredFepIds.iterator().next();

        // Send reconciliation notification
        boolean sent = engine.sendReconciliationNotification(targetFepId, "0111"); // MMDD format
        assertTrue(sent, "Should send reconciliation notification");
    }

    @Test
    @Order(14)
    void testSystemStatusNotification() throws Exception {
        // Send system status notification to all FEPs
        int sentCount = engine.sendSystemStatusNotification("001"); // System available
        assertTrue(sentCount >= 1, "Should send to at least 1 FEP");
    }

    @Test
    @Order(15)
    void testFepIdRouting() throws Exception {
        // Register FEP ID by sending a message with different ID
        Iso8583Message request = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        request.setField(11, "000008");
        request.setField(70, "001");
        request.setField(32, "002"); // Different FEP ID

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);
        assertNotNull(response);

        // Verify FEP ID is registered
        var registeredFepIds = engine.getClientRouter().getRegisteredBankIds();
        assertTrue(registeredFepIds.contains("001") || registeredFepIds.contains("002"),
            "FEP ID should be registered");
    }

    @Test
    @Order(16)
    void testConcurrentTransactions() throws Exception {
        int numTransactions = 5;
        CompletableFuture<?>[] futures = new CompletableFuture[numTransactions];

        for (int i = 0; i < numTransactions; i++) {
            final int stan = 200 + i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
                    request.setField(2, "4111111111111111");
                    request.setField(3, "010000");
                    request.setField(4, "000000010000");
                    request.setField(11, String.format("%06d", stan));
                    request.setField(32, "001");
                    request.setField(41, "ATM00001");

                    Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

                    assertNotNull(response);
                    assertEquals(String.format("%06d", stan), response.getFieldAsString(11),
                        "STAN should match for concurrent transactions");
                } catch (Exception e) {
                    fail("Concurrent transaction failed: " + e.getMessage());
                }
            });
        }

        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
    }

    @Test
    @Order(17)
    void testClientRouterStatistics() {
        var router = engine.getClientRouter();

        assertEquals(1, router.getReceiveChannelCount(), "Should have 1 receive channel");
        assertEquals(1, router.getSendChannelCount(), "Should have 1 send channel");
        assertFalse(router.getRegisteredBankIds().isEmpty(), "Should have registered FEP IDs");
    }

    @Test
    @Order(18)
    void testBankCoreMessageTypeEnum() {
        // Test BankCoreMessageType enum values
        assertEquals(7, BankCoreMessageType.values().length);
        assertEquals("ECHO_TEST", BankCoreMessageType.ECHO_TEST.name());
        assertEquals("RECONCILIATION_NOTIFY", BankCoreMessageType.RECONCILIATION_NOTIFY.name());
        assertEquals("ACCOUNT_UPDATE", BankCoreMessageType.ACCOUNT_UPDATE.name());
        assertEquals("SYSTEM_STATUS", BankCoreMessageType.SYSTEM_STATUS.name());
        assertEquals("SIGN_ON_NOTIFY", BankCoreMessageType.SIGN_ON_NOTIFY.name());
        assertEquals("SIGN_OFF_NOTIFY", BankCoreMessageType.SIGN_OFF_NOTIFY.name());
        assertEquals("CUSTOM", BankCoreMessageType.CUSTOM.name());

        // Test BankCoreMessageType.names() helper
        String[] expectedNames = {
            "ECHO_TEST", "RECONCILIATION_NOTIFY", "ACCOUNT_UPDATE",
            "SYSTEM_STATUS", "SIGN_ON_NOTIFY", "SIGN_OFF_NOTIFY", "CUSTOM"
        };
        assertArrayEquals(expectedNames, BankCoreMessageType.names());

        // Test BankCoreMessageType.fromString() helper
        assertEquals(BankCoreMessageType.ECHO_TEST, BankCoreMessageType.fromString("ECHO_TEST"));
        assertEquals(BankCoreMessageType.RECONCILIATION_NOTIFY, BankCoreMessageType.fromString("reconciliation_notify"));
        assertEquals(BankCoreMessageType.ECHO_TEST, BankCoreMessageType.fromString(null));
        assertEquals(BankCoreMessageType.ECHO_TEST, BankCoreMessageType.fromString(""));
        assertEquals(BankCoreMessageType.ECHO_TEST, BankCoreMessageType.fromString("INVALID"));
    }

    @Test
    @Order(19)
    void testSamplerPropertyGettersSetters() {
        BankCoreServerSampler sampler = new BankCoreServerSampler();

        // Test port properties
        sampler.setSendPort(9100);
        assertEquals(9100, sampler.getSendPort());

        sampler.setReceivePort(9101);
        assertEquals(9101, sampler.getReceivePort());

        // Test response properties
        sampler.setDefaultResponseCode("00");
        assertEquals("00", sampler.getDefaultResponseCode());

        sampler.setResponseDelay(100);
        assertEquals(100, sampler.getResponseDelay());

        // Test balance properties
        sampler.setAvailableBalance("200000");
        assertEquals("200000", sampler.getAvailableBalance());

        sampler.setLedgerBalance("300000");
        assertEquals("300000", sampler.getLedgerBalance());

        // Test validation properties
        sampler.setEnableValidation(true);
        assertTrue(sampler.isEnableValidation());

        sampler.setValidationErrorCode("30");
        assertEquals("30", sampler.getValidationErrorCode());

        // Test routing properties
        sampler.setEnableFepIdRouting(true);
        assertTrue(sampler.isEnableFepIdRouting());

        sampler.setFepIdField(32);
        assertEquals(32, sampler.getFepIdField());

        // Test response rules
        sampler.setResponseRules("{\"010000\": \"00\"}");
        assertEquals("{\"010000\": \"00\"}", sampler.getResponseRules());

        sampler.setCustomResponseFields("38:AUTH01;44:Additional");
        assertEquals("38:AUTH01;44:Additional", sampler.getCustomResponseFields());
    }

    @Test
    @Order(20)
    void testMessageSenderSamplerProperties() {
        BankCoreMessageSenderSampler sampler = new BankCoreMessageSenderSampler();

        // Test message type property
        sampler.setMessageType(BankCoreMessageType.RECONCILIATION_NOTIFY.name());
        assertEquals(BankCoreMessageType.RECONCILIATION_NOTIFY.name(), sampler.getMessageType());

        // Test target FEP ID property
        sampler.setTargetFepId("001");
        assertEquals("001", sampler.getTargetFepId());

        // Test custom MTI property
        sampler.setCustomMti("0820");
        assertEquals("0820", sampler.getCustomMti());

        // Test settlement date property
        sampler.setSettlementDate("0111");
        assertEquals("0111", sampler.getSettlementDate());

        // Test account number property
        sampler.setAccountNumber("1234567890");
        assertEquals("1234567890", sampler.getAccountNumber());
    }

    @Test
    @Order(21)
    void testDeprecatedConstants() {
        // Verify deprecated constants still work for backwards compatibility
        assertEquals("RECONCILIATION_NOTIFY", BankCoreMessageSenderSampler.TYPE_RECONCILIATION);
        assertEquals("ACCOUNT_UPDATE", BankCoreMessageSenderSampler.TYPE_ACCOUNT_UPDATE);
        assertEquals("SYSTEM_STATUS", BankCoreMessageSenderSampler.TYPE_SYSTEM_STATUS);
        assertEquals("ECHO_TEST", BankCoreMessageSenderSampler.TYPE_ECHO_TEST);
        assertEquals("SIGN_ON_NOTIFY", BankCoreMessageSenderSampler.TYPE_SIGN_ON);
        assertEquals("SIGN_OFF_NOTIFY", BankCoreMessageSenderSampler.TYPE_SIGN_OFF);
        assertEquals("CUSTOM", BankCoreMessageSenderSampler.TYPE_CUSTOM);
    }

    @Test
    @Order(22)
    void testEngineResetCounters() {
        // Store current counts
        int receivedBefore = engine.getMessagesReceived().get();
        int sentBefore = engine.getMessagesSent().get();

        assertTrue(receivedBefore > 0, "Should have received messages before reset");
        assertTrue(sentBefore > 0, "Should have sent messages before reset");

        // Reset counters
        engine.resetCounters();

        assertEquals(0, engine.getMessagesReceived().get(), "Messages received should be reset");
        assertEquals(0, engine.getMessagesSent().get(), "Messages sent should be reset");
        assertEquals(0, engine.getValidationErrors().get(), "Validation errors should be reset");
    }

    @Test
    @Order(23)
    void testEngineLastRequestInfo() throws Exception {
        // Send a transaction to update last request info
        Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        request.setField(2, "4111111111111111");
        request.setField(3, "310000");
        request.setField(4, "000000000000");
        request.setField(11, "000999");
        request.setField(32, "001");

        client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        // Verify last request info
        assertEquals("0200", engine.getLastRequestMti(), "Last MTI should be 0200");
        assertEquals("000999", engine.getLastRequestStan(), "Last STAN should be 000999");
        assertNotNull(engine.getLastValidationResult(), "Last validation result should not be null");
    }

    @Test
    @Order(24)
    void testRequestQueue() throws Exception {
        // Clear the queue first
        engine.getRequestQueue().clear();

        // Send a request
        Iso8583Message request = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        request.setField(11, "000888");
        request.setField(70, "301");
        request.setField(32, "001");

        client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        // Check request queue
        BankCoreSimulatorEngine.ReceivedRequest received = engine.getRequestQueue().poll(1, TimeUnit.SECONDS);

        assertNotNull(received, "Should have request in queue");
        assertEquals("0800", received.message().getMti(), "MTI should match");
        assertEquals("000888", received.message().getFieldAsString(11), "STAN should match");
        assertEquals("001", received.fepId(), "FEP ID should match");
        assertTrue(received.timestamp() > 0, "Timestamp should be set");
    }
}
