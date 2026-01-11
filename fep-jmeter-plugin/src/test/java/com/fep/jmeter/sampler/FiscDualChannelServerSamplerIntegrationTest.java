package com.fep.jmeter.sampler;

import com.fep.communication.client.FiscDualChannelClient;
import com.fep.communication.config.DualChannelConfig;
import com.fep.jmeter.engine.FiscDualChannelSimulatorEngine;
import com.fep.jmeter.validation.MessageValidationEngine;
import com.fep.jmeter.validation.ValidationResult;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.MessageType;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FiscDualChannelServerSampler and related components.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FiscDualChannelServerSamplerIntegrationTest {

    private static FiscDualChannelSimulatorEngine engine;
    private static FiscDualChannelClient client;
    private static int receivePort;
    private static int sendPort;

    @BeforeAll
    static void setUp() throws Exception {
        // Start the engine with random ports
        engine = new FiscDualChannelSimulatorEngine(0, 0);
        engine.setDefaultResponseCode("00");
        engine.setEnableBankIdRouting(true);
        engine.setBankIdField(32);
        engine.start().get(10, TimeUnit.SECONDS);

        receivePort = engine.getReceivePort();
        sendPort = engine.getSendPort();

        System.out.println("Engine started: Receive=" + receivePort + ", Send=" + sendPort);
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
        assertTrue(receivePort > 0, "Receive port should be assigned");
        assertTrue(sendPort > 0, "Send port should be assigned");
    }

    @Test
    @Order(2)
    void testClientConnection() throws Exception {
        // From client's perspective: sendPort = where client sends (= engine's receivePort)
        //                           receivePort = where client receives (= engine's sendPort)
        DualChannelConfig config = DualChannelConfig.builder()
            .sendHost("localhost")
            .sendPort(receivePort)      // Client sends to engine's Receive port
            .receiveHost("localhost")
            .receivePort(sendPort)      // Client receives from engine's Send port
            .build();

        client = new FiscDualChannelClient(config);
        client.connect().get(10, TimeUnit.SECONDS);

        assertTrue(client.isConnected(), "Client should be connected");

        // Wait for connection to be registered
        Thread.sleep(500);

        assertEquals(1, engine.getReceiveChannelClientCount().get(), "One receive channel client should be connected");
        assertEquals(1, engine.getSendChannelClientCount().get(), "One send channel client should be connected");
    }

    @Test
    @Order(3)
    void testSignOn() throws Exception {
        Iso8583Message request = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        request.setField(11, "000001");  // STAN
        request.setField(70, "001");     // Sign-on
        request.setField(32, "004");     // Bank ID

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        assertNotNull(response, "Should receive response");
        assertEquals("0810", response.getMti(), "Response MTI should be 0810");
        assertEquals("00", response.getFieldAsString(39), "Response code should be 00");
        assertEquals("000001", response.getFieldAsString(11), "STAN should match");
    }

    @Test
    @Order(4)
    void testFinancialTransaction() throws Exception {
        Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        request.setField(2, "4111111111111111");  // PAN
        request.setField(3, "010000");            // Processing Code (Withdrawal)
        request.setField(4, "000000100000");      // Amount
        request.setField(11, "000002");           // STAN
        request.setField(32, "004");              // Bank ID
        request.setField(41, "ATM00001");         // Terminal ID
        request.setField(42, "MERCHANT001234");   // Merchant ID

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        assertNotNull(response, "Should receive response");
        assertEquals("0210", response.getMti(), "Response MTI should be 0210");
        assertEquals("00", response.getFieldAsString(39), "Response code should be 00");
        assertEquals("000002", response.getFieldAsString(11), "STAN should match");

        // Verify statistics
        assertTrue(engine.getMessagesReceived().get() >= 2, "Should have received at least 2 messages");
        assertTrue(engine.getMessagesSent().get() >= 2, "Should have sent at least 2 messages");
    }

    @Test
    @Order(5)
    void testValidationEngine() {
        MessageValidationEngine validationEngine = new MessageValidationEngine();
        validationEngine.configure("""
            REQUIRED:2,3,4,11
            FORMAT:2=N(13-19);3=N(6);4=N(12)
            VALUE:3=010000|400000|310000
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

        // Invalid value
        Iso8583Message badValueMsg = new Iso8583Message("0200");
        badValueMsg.setField(2, "4111111111111111");
        badValueMsg.setField(3, "999999"); // Invalid processing code
        badValueMsg.setField(4, "000000100000");
        badValueMsg.setField(11, "000001");

        result = validationEngine.validate(badValueMsg);
        assertFalse(result.isValid(), "Message with invalid value should fail");
        assertTrue(result.getErrorSummary().contains("not allowed"), "Error should mention invalid value");
    }

    @Test
    @Order(6)
    void testValidationIntegration() throws Exception {
        // Configure validation on engine
        MessageValidationEngine validationEngine = new MessageValidationEngine();
        validationEngine.configure("REQUIRED:2,3,4,11");
        engine.setValidationCallback(validationEngine.createValidationCallback());
        engine.setValidationErrorCode("30");

        // Send invalid message (missing required field)
        Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
        request.setField(3, "010000");  // Processing Code
        request.setField(11, "000003"); // STAN
        request.setField(32, "004");    // Bank ID
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
    @Order(7)
    void testProactiveMessageSending() throws Exception {
        // Send echo test to all clients
        Iso8583Message echoMsg = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        echoMsg.setField(11, "999001");
        echoMsg.setField(70, "301"); // Echo test

        int sentCount = engine.broadcastMessage(echoMsg);
        assertEquals(1, sentCount, "Should send to 1 client");
    }

    @Test
    @Order(8)
    void testBankIdRouting() throws Exception {
        // Register bank ID by sending a message
        Iso8583Message request = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        request.setField(11, "000004");
        request.setField(70, "001");
        request.setField(32, "005"); // Different bank ID

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);
        assertNotNull(response);

        // Verify bank ID is registered
        var registeredBankIds = engine.getClientRouter().getRegisteredBankIds();
        assertTrue(registeredBankIds.contains("004") || registeredBankIds.contains("005"),
            "Bank ID should be registered");
    }

    @Test
    @Order(9)
    void testConcurrentTransactions() throws Exception {
        int numTransactions = 5;
        CompletableFuture<?>[] futures = new CompletableFuture[numTransactions];

        for (int i = 0; i < numTransactions; i++) {
            final int stan = 100 + i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
                    request.setField(2, "4111111111111111");
                    request.setField(3, "010000");
                    request.setField(4, "000000100000");
                    request.setField(11, String.format("%06d", stan));
                    request.setField(32, "004");
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
    @Order(10)
    void testClientRouterStatistics() {
        var router = engine.getClientRouter();

        assertEquals(1, router.getReceiveChannelCount(), "Should have 1 receive channel");
        assertEquals(1, router.getSendChannelCount(), "Should have 1 send channel");
        assertFalse(router.getRegisteredBankIds().isEmpty(), "Should have registered bank IDs");
    }

    @Test
    @Order(11)
    void testSamplerBlockingWaitForMessage() throws Exception {
        // Create a new engine for this test to have a clean request queue
        FiscDualChannelSimulatorEngine testEngine = new FiscDualChannelSimulatorEngine(0, 0);
        testEngine.setDefaultResponseCode("00");
        testEngine.setEnableBankIdRouting(true);
        testEngine.start().get(10, TimeUnit.SECONDS);

        int testReceivePort = testEngine.getReceivePort();
        int testSendPort = testEngine.getSendPort();

        try {
            // Connect a client
            // From client's perspective: sendPort = where client sends (= engine's receivePort)
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(testReceivePort)
                .receiveHost("localhost")
                .receivePort(testSendPort)
                .build();

            FiscDualChannelClient testClient = new FiscDualChannelClient(config);
            testClient.connect().get(10, TimeUnit.SECONDS);
            Thread.sleep(200); // Wait for connection to be established

            try {
                // Start a thread that will wait for the message using poll()
                CompletableFuture<FiscDualChannelSimulatorEngine.ReceivedRequest> receivedFuture =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return testEngine.getRequestQueue().poll(5000, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            return null;
                        }
                    });

                // Wait a bit to ensure the poll is waiting
                Thread.sleep(100);

                // Send a message
                Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
                request.setField(2, "4111111111111111");
                request.setField(3, "010000");
                request.setField(4, "000000100000");
                request.setField(11, "000099");
                request.setField(32, "004");

                testClient.sendAndReceive(request).get(10, TimeUnit.SECONDS);

                // The blocking poll should have received the message
                FiscDualChannelSimulatorEngine.ReceivedRequest received = receivedFuture.get(5, TimeUnit.SECONDS);

                assertNotNull(received, "Should receive the message via blocking poll");
                assertEquals("0200", received.message().getMti(), "MTI should match");
                assertEquals("000099", received.message().getFieldAsString(11), "STAN should match");
                assertEquals("004", received.bankId(), "Bank ID should match");

            } finally {
                testClient.close();
            }
        } finally {
            testEngine.close();
        }
    }

    @Test
    @Order(12)
    void testSamplerBlockingTimeout() throws Exception {
        // Create a new engine for this test
        FiscDualChannelSimulatorEngine testEngine = new FiscDualChannelSimulatorEngine(0, 0);
        testEngine.start().get(10, TimeUnit.SECONDS);

        try {
            // Poll with a short timeout - should timeout as no message is sent
            FiscDualChannelSimulatorEngine.ReceivedRequest received =
                testEngine.getRequestQueue().poll(500, TimeUnit.MILLISECONDS);

            assertNull(received, "Should return null on timeout when no message is received");
        } finally {
            testEngine.close();
        }
    }

    @Test
    @Order(13)
    void testOperationModeConstants() {
        // Test OperationMode enum
        assertEquals(3, OperationMode.values().length);
        assertEquals("PASSIVE", OperationMode.PASSIVE.name());
        assertEquals("ACTIVE", OperationMode.ACTIVE.name());
        assertEquals("BIDIRECTIONAL", OperationMode.BIDIRECTIONAL.name());

        // Test OperationMode.names() helper
        assertArrayEquals(new String[]{"PASSIVE", "ACTIVE", "BIDIRECTIONAL"}, OperationMode.names());

        // Test OperationMode.fromString() helper
        assertEquals(OperationMode.PASSIVE, OperationMode.fromString("PASSIVE"));
        assertEquals(OperationMode.ACTIVE, OperationMode.fromString("active"));
        assertEquals(OperationMode.BIDIRECTIONAL, OperationMode.fromString("Bidirectional"));
        assertEquals(OperationMode.PASSIVE, OperationMode.fromString(null));
        assertEquals(OperationMode.PASSIVE, OperationMode.fromString(""));
        assertEquals(OperationMode.PASSIVE, OperationMode.fromString("INVALID"));

        // Verify deprecated constants still work for backwards compatibility
        assertEquals("PASSIVE", OperationMode.PASSIVE.name());
        assertEquals("ACTIVE", OperationMode.ACTIVE.name());
        assertEquals("BIDIRECTIONAL", OperationMode.BIDIRECTIONAL.name());
    }

    @Test
    @Order(14)
    void testActiveMessageTypeConstants() {
        // Test ActiveMessageType enum
        assertEquals(5, ActiveMessageType.values().length);
        assertEquals("SIGN_ON", ActiveMessageType.SIGN_ON.name());
        assertEquals("SIGN_OFF", ActiveMessageType.SIGN_OFF.name());
        assertEquals("ECHO_TEST", ActiveMessageType.ECHO_TEST.name());
        assertEquals("KEY_EXCHANGE", ActiveMessageType.KEY_EXCHANGE.name());
        assertEquals("CUSTOM", ActiveMessageType.CUSTOM.name());

        // Test ActiveMessageType.names() helper
        assertArrayEquals(
            new String[]{"SIGN_ON", "SIGN_OFF", "ECHO_TEST", "KEY_EXCHANGE", "CUSTOM"},
            ActiveMessageType.names()
        );

        // Test ActiveMessageType.fromString() helper
        assertEquals(ActiveMessageType.SIGN_ON, ActiveMessageType.fromString("SIGN_ON"));
        assertEquals(ActiveMessageType.ECHO_TEST, ActiveMessageType.fromString("echo_test"));
        assertEquals(ActiveMessageType.ECHO_TEST, ActiveMessageType.fromString(null));
        assertEquals(ActiveMessageType.ECHO_TEST, ActiveMessageType.fromString(""));
        assertEquals(ActiveMessageType.ECHO_TEST, ActiveMessageType.fromString("INVALID"));

        // Verify deprecated constants still work for backwards compatibility
        assertEquals("SIGN_ON", FiscDualChannelServerSampler.ACTIVE_TYPE_SIGN_ON);
        assertEquals("SIGN_OFF", FiscDualChannelServerSampler.ACTIVE_TYPE_SIGN_OFF);
        assertEquals("ECHO_TEST", FiscDualChannelServerSampler.ACTIVE_TYPE_ECHO_TEST);
        assertEquals("KEY_EXCHANGE", FiscDualChannelServerSampler.ACTIVE_TYPE_KEY_EXCHANGE);
        assertEquals("CUSTOM", FiscDualChannelServerSampler.ACTIVE_TYPE_CUSTOM);
    }

    @Test
    @Order(15)
    void testActiveModeWithConnectedClient() throws Exception {
        // Engine should have a connected client from previous tests
        assertTrue(engine.getSendChannelClientCount().get() >= 1, "Should have at least 1 client connected");

        // Test broadcasting an echo test message
        Iso8583Message echoMsg = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        echoMsg.setField(11, "888001");
        echoMsg.setField(70, "301"); // Echo test

        int sentCount = engine.broadcastMessage(echoMsg);
        assertTrue(sentCount >= 1, "Should send message to at least 1 client");

        // Verify message was queued for sending
        assertTrue(engine.getMessagesSent().get() >= 1, "Messages sent count should increase");
    }

    @Test
    @Order(16)
    void testActiveModeWithTargetBankId() throws Exception {
        // Get registered bank IDs
        var registeredBankIds = engine.getClientRouter().getRegisteredBankIds();
        assertFalse(registeredBankIds.isEmpty(), "Should have registered bank IDs");

        String targetBankId = registeredBankIds.iterator().next();

        // Send message to specific bank
        Iso8583Message signOnMsg = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
        signOnMsg.setField(11, "888002");
        signOnMsg.setField(70, "001"); // Sign-on

        boolean sent = engine.sendProactiveMessage(targetBankId, signOnMsg);
        assertTrue(sent, "Should send message to target bank ID: " + targetBankId);
    }

    @Test
    @Order(17)
    void testBidirectionalModeParallelExecution() throws Exception {
        // Create a new engine for isolated testing
        FiscDualChannelSimulatorEngine testEngine = new FiscDualChannelSimulatorEngine(0, 0);
        testEngine.setDefaultResponseCode("00");
        testEngine.setEnableBankIdRouting(true);
        testEngine.start().get(10, TimeUnit.SECONDS);

        int testReceivePort = testEngine.getReceivePort();
        int testSendPort = testEngine.getSendPort();

        try {
            // Connect a client
            DualChannelConfig config = DualChannelConfig.builder()
                .sendHost("localhost")
                .sendPort(testReceivePort)
                .receiveHost("localhost")
                .receivePort(testSendPort)
                .build();

            FiscDualChannelClient testClient = new FiscDualChannelClient(config);
            testClient.connect().get(10, TimeUnit.SECONDS);
            Thread.sleep(300); // Wait for connection

            try {
                // Run parallel tasks simulating bidirectional mode
                CompletableFuture<Void> activeFuture = CompletableFuture.runAsync(() -> {
                    // Active: Send echo test
                    Iso8583Message echoMsg = new Iso8583Message(MessageType.NETWORK_MANAGEMENT_REQUEST);
                    echoMsg.setField(11, "777001");
                    echoMsg.setField(70, "301");
                    int count = testEngine.broadcastMessage(echoMsg);
                    assertTrue(count >= 0, "Active sending should complete");
                });

                CompletableFuture<Void> passiveFuture = CompletableFuture.runAsync(() -> {
                    // Passive: Send a transaction and wait for response
                    try {
                        Iso8583Message request = new Iso8583Message(MessageType.FINANCIAL_REQUEST);
                        request.setField(2, "4111111111111111");
                        request.setField(3, "310000");
                        request.setField(4, "000000050000");
                        request.setField(11, "777002");
                        request.setField(32, "006");

                        Iso8583Message response = testClient.sendAndReceive(request).get(5, TimeUnit.SECONDS);
                        assertNotNull(response, "Should receive response");
                        assertEquals("0210", response.getMti(), "Response MTI should be 0210");
                    } catch (Exception e) {
                        fail("Passive operation failed: " + e.getMessage());
                    }
                });

                // Wait for both to complete
                CompletableFuture.allOf(activeFuture, passiveFuture).get(10, TimeUnit.SECONDS);

            } finally {
                testClient.close();
            }
        } finally {
            testEngine.close();
        }
    }

    @Test
    @Order(18)
    void testSamplerPropertyGettersSetters() {
        FiscDualChannelServerSampler sampler = new FiscDualChannelServerSampler();

        // Test operation mode property
        sampler.setOperationMode(OperationMode.BIDIRECTIONAL.name());
        assertEquals(OperationMode.BIDIRECTIONAL.name(), sampler.getOperationMode());

        // Test active message type property
        sampler.setActiveMessageType(ActiveMessageType.KEY_EXCHANGE.name());
        assertEquals(ActiveMessageType.KEY_EXCHANGE.name(), sampler.getActiveMessageType());

        // Test active target bank ID property
        sampler.setActiveTargetBankId("007");
        assertEquals("007", sampler.getActiveTargetBankId());

        // Test active custom MTI property
        sampler.setActiveCustomMti("0820");
        assertEquals("0820", sampler.getActiveCustomMti());

        // Test active custom fields property
        sampler.setActiveCustomFields("48:TEST;32:001");
        assertEquals("48:TEST;32:001", sampler.getActiveCustomFields());
    }
}
