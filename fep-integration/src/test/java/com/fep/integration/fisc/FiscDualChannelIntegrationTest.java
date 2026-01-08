package com.fep.integration.fisc;

import com.fep.communication.client.DualChannelState;
import com.fep.communication.client.FiscDualChannelClient;
import com.fep.communication.config.ChannelFailureStrategy;
import com.fep.communication.config.DualChannelConfig;
import com.fep.communication.manager.PendingRequestManager;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FISC dual-channel client.
 *
 * <p>Tests the complete flow of:
 * <ul>
 *   <li>Dual-channel connection (Send + Receive)</li>
 *   <li>Sign-on/Sign-off</li>
 *   <li>Request/Response via separate channels</li>
 *   <li>STAN matching across channels</li>
 *   <li>Concurrent transactions</li>
 *   <li>Timeout handling</li>
 *   <li>Reconnection</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FiscDualChannelIntegrationTest {

    private static FiscDualChannelSimulator simulator;
    private FiscDualChannelClient client;
    private DualChannelConfig config;

    @BeforeAll
    static void startSimulator() throws Exception {
        simulator = new FiscDualChannelSimulator();
        simulator.start().get(10, TimeUnit.SECONDS);
        System.out.println("Simulator started: Send=" + simulator.getSendPort() +
            ", Receive=" + simulator.getReceivePort());
    }

    @AfterAll
    static void stopSimulator() {
        if (simulator != null) {
            simulator.close();
        }
    }

    @BeforeEach
    void setUp() {
        config = DualChannelConfig.builder()
            .sendHost("localhost")
            .sendPort(simulator.getSendPort())
            .receiveHost("localhost")
            .receivePort(simulator.getReceivePort())
            .connectionName("TEST-DUAL")
            .connectTimeoutMs(5000)
            .readTimeoutMs(10000)
            .heartbeatIntervalMs(30000)
            .autoReconnect(false) // Disable for most tests
            .failureStrategy(ChannelFailureStrategy.FAIL_WHEN_BOTH_DOWN)
            .build();

        simulator.resetCounters();
        simulator.setResponseDelayMs(0);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    // ==================== Connection Tests ====================

    @Test
    @Order(1)
    @DisplayName("Should connect both channels successfully")
    void shouldConnectBothChannels() throws Exception {
        client = new FiscDualChannelClient(config);

        client.connect().get(5, TimeUnit.SECONDS);

        assertTrue(client.isSendChannelConnected(), "Send channel should be connected");
        assertTrue(client.isReceiveChannelConnected(), "Receive channel should be connected");
        assertTrue(client.isConnected(), "Both channels should be connected");
        assertEquals(DualChannelState.BOTH_CONNECTED, client.getState());
    }

    @Test
    @Order(2)
    @DisplayName("Should sign on successfully")
    void shouldSignOnSuccessfully() throws Exception {
        client = new FiscDualChannelClient(config);
        client.connect().get(5, TimeUnit.SECONDS);

        Iso8583Message response = client.signOn().get(5, TimeUnit.SECONDS);

        assertEquals("00", response.getFieldAsString(39), "Sign-on should be approved");
        assertTrue(client.isSignedOn(), "Client should be signed on");
        assertEquals(DualChannelState.SIGNED_ON, client.getState());
    }

    @Test
    @Order(3)
    @DisplayName("Should sign off successfully")
    void shouldSignOffSuccessfully() throws Exception {
        client = new FiscDualChannelClient(config);
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        Iso8583Message response = client.signOff().get(5, TimeUnit.SECONDS);

        assertEquals("00", response.getFieldAsString(39), "Sign-off should be approved");
    }

    // ==================== Transaction Tests ====================

    @Test
    @Order(10)
    @DisplayName("Should send financial transaction and receive response")
    void shouldSendFinancialTransaction() throws Exception {
        client = new FiscDualChannelClient(config);
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        // Create withdrawal request
        Iso8583MessageFactory factory = client.getMessageFactory();
        Iso8583Message request = factory.createMessage("0200");
        request.setField(2, "4111111111111111");
        request.setField(3, "010000"); // Withdrawal
        request.setField(4, "000000010000"); // Amount: 100.00
        factory.setTransactionFields(request);

        // Send and receive
        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        assertNotNull(response, "Response should not be null");
        assertEquals("0210", response.getMti(), "Response MTI should be 0210");
        assertEquals("00", response.getFieldAsString(39), "Should be approved");
        assertEquals(request.getFieldAsString(11), response.getFieldAsString(11),
            "STAN should match");
    }

    @Test
    @Order(11)
    @DisplayName("Should handle reversal transaction")
    void shouldHandleReversalTransaction() throws Exception {
        client = new FiscDualChannelClient(config);
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        Iso8583MessageFactory factory = client.getMessageFactory();
        Iso8583Message request = factory.createMessage("0400");
        request.setField(2, "4111111111111111");
        request.setField(3, "010000");
        request.setField(4, "000000010000");
        factory.setTransactionFields(request);

        Iso8583Message response = client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        assertEquals("0410", response.getMti(), "Response MTI should be 0410");
        assertEquals("00", response.getFieldAsString(39));
    }

    @Test
    @Order(12)
    @DisplayName("Should send heartbeat successfully")
    void shouldSendHeartbeat() throws Exception {
        client = new FiscDualChannelClient(config);
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        Iso8583Message response = client.sendHeartbeat().get(5, TimeUnit.SECONDS);

        assertEquals("0810", response.getMti());
        assertEquals("00", response.getFieldAsString(39));
    }

    // ==================== Concurrent Transaction Tests ====================

    @Test
    @Order(20)
    @DisplayName("Should handle concurrent transactions with correct STAN matching")
    void shouldHandleConcurrentTransactions() throws Exception {
        client = new FiscDualChannelClient(config);
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        int numTransactions = 10;
        List<CompletableFuture<Iso8583Message>> futures = new ArrayList<>();
        List<String> stans = new ArrayList<>();

        Iso8583MessageFactory factory = client.getMessageFactory();

        // Send multiple transactions concurrently
        for (int i = 0; i < numTransactions; i++) {
            Iso8583Message request = factory.createMessage("0200");
            request.setField(2, "4111111111111111");
            request.setField(3, "010000");
            request.setField(4, String.format("%012d", (i + 1) * 100)); // Different amounts
            factory.setTransactionFields(request);

            stans.add(request.getFieldAsString(11));
            futures.add(client.sendAndReceive(request));
        }

        // Wait for all responses
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(30, TimeUnit.SECONDS);

        // Verify each response matches its request
        for (int i = 0; i < numTransactions; i++) {
            Iso8583Message response = futures.get(i).get();
            assertEquals(stans.get(i), response.getFieldAsString(11),
                "STAN should match for transaction " + i);
            assertEquals("00", response.getFieldAsString(39),
                "Transaction " + i + " should be approved");
        }

        assertEquals(numTransactions + 1, simulator.getMessagesReceived().get(),
            "Simulator should receive all transactions (+ sign-on)");
    }

    // ==================== Timeout Tests ====================

    @Test
    @Order(30)
    @DisplayName("Should timeout when response is delayed")
    void shouldTimeoutWhenResponseDelayed() throws Exception {
        // Configure simulator to delay responses
        simulator.setResponseDelayMs(5000); // 5 second delay

        config = DualChannelConfig.builder()
            .sendHost("localhost")
            .sendPort(simulator.getSendPort())
            .receiveHost("localhost")
            .receivePort(simulator.getReceivePort())
            .connectionName("TEST-TIMEOUT")
            .readTimeoutMs(1000) // 1 second timeout
            .build();

        client = new FiscDualChannelClient(config);
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(10, TimeUnit.SECONDS); // Allow longer for sign-on

        simulator.setResponseDelayMs(5000); // Set delay for next request

        Iso8583MessageFactory factory = client.getMessageFactory();
        Iso8583Message request = factory.createMessage("0200");
        request.setField(3, "010000");
        factory.setTransactionFields(request);

        assertThrows(TimeoutException.class, () ->
            client.sendAndReceive(request, 1000).get(3, TimeUnit.SECONDS),
            "Should throw TimeoutException");
    }

    // ==================== STAN Matching Tests ====================

    @Test
    @Order(40)
    @DisplayName("Should correctly match responses by STAN")
    void shouldCorrectlyMatchResponsesByStan() throws Exception {
        client = new FiscDualChannelClient(config);
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        PendingRequestManager pendingManager = client.getPendingRequestManager();
        Iso8583MessageFactory factory = client.getMessageFactory();

        // Send a transaction
        Iso8583Message request = factory.createMessage("0200");
        request.setField(3, "010000");
        factory.setTransactionFields(request);

        String expectedStan = request.getFieldAsString(11);

        CompletableFuture<Iso8583Message> future = client.sendAndReceive(request);

        // Verify pending request is registered
        assertTrue(pendingManager.isPending(expectedStan),
            "STAN should be in pending requests");

        Iso8583Message response = future.get(10, TimeUnit.SECONDS);

        // Verify STAN matches
        assertEquals(expectedStan, response.getFieldAsString(11));

        // Verify pending request is cleared
        assertFalse(pendingManager.isPending(expectedStan),
            "STAN should be cleared from pending requests");
    }

    // ==================== State Tests ====================

    @Test
    @Order(50)
    @DisplayName("Should track state correctly through lifecycle")
    void shouldTrackStateCorrectly() throws Exception {
        client = new FiscDualChannelClient(config);

        assertEquals(DualChannelState.DISCONNECTED, client.getState());

        client.connect().get(5, TimeUnit.SECONDS);
        assertEquals(DualChannelState.BOTH_CONNECTED, client.getState());

        client.signOn().get(5, TimeUnit.SECONDS);
        assertEquals(DualChannelState.SIGNED_ON, client.getState());

        client.disconnect().get(5, TimeUnit.SECONDS);
        assertEquals(DualChannelState.DISCONNECTED, client.getState());
    }

    // ==================== Disconnect Tests ====================

    @Test
    @Order(60)
    @DisplayName("Should disconnect gracefully")
    void shouldDisconnectGracefully() throws Exception {
        client = new FiscDualChannelClient(config);
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        client.disconnect().get(5, TimeUnit.SECONDS);

        assertFalse(client.isSendChannelConnected());
        assertFalse(client.isReceiveChannelConnected());
        assertEquals(DualChannelState.DISCONNECTED, client.getState());
    }

    // ==================== Statistics Tests ====================

    @Test
    @Order(70)
    @DisplayName("Should track pending request statistics")
    void shouldTrackPendingRequestStatistics() throws Exception {
        client = new FiscDualChannelClient(config);
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        PendingRequestManager pendingManager = client.getPendingRequestManager();
        PendingRequestManager.Statistics stats = pendingManager.getStatistics();

        long initialRegistered = stats.totalRegistered();

        // Send a transaction
        Iso8583MessageFactory factory = client.getMessageFactory();
        Iso8583Message request = factory.createMessage("0200");
        factory.setTransactionFields(request);

        client.sendAndReceive(request).get(10, TimeUnit.SECONDS);

        stats = pendingManager.getStatistics();
        assertTrue(stats.totalRegistered() > initialRegistered,
            "Total registered should increase");
        assertTrue(stats.totalCompleted() > 0,
            "Total completed should increase");
        assertEquals(0, stats.currentPending(),
            "No pending requests should remain");
    }

    // ==================== Error Handling Tests ====================

    @Test
    @Order(80)
    @DisplayName("Should handle send channel not connected")
    void shouldHandleSendChannelNotConnected() {
        client = new FiscDualChannelClient(config);
        // Don't connect

        Iso8583MessageFactory factory = client.getMessageFactory();
        Iso8583Message request = factory.createMessage("0200");
        factory.setTransactionFields(request);

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
            client.sendAndReceive(request).get(5, TimeUnit.SECONDS));

        assertTrue(ex.getCause().getMessage().contains("not connected") ||
                   ex.getCause().getMessage().contains("closed"),
            "Should indicate channel not connected");
    }
}
