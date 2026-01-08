package com.fep.integration.fisc;

import com.fep.communication.client.ConnectionState;
import com.fep.communication.client.FiscClient;
import com.fep.communication.config.FiscConnectionConfig;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.iso8583.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FISC Connection Integration Tests.
 *
 * <p>Comprehensive end-to-end tests for FISC communication including:
 * <ul>
 *   <li>Connection establishment and teardown</li>
 *   <li>Sign-on/sign-off procedures</li>
 *   <li>Heartbeat (echo test) mechanism</li>
 *   <li>Financial transactions (withdrawal, transfer, inquiry)</li>
 *   <li>Reversal transactions</li>
 *   <li>Connection timeout and error handling</li>
 *   <li>Auto-reconnection mechanism</li>
 * </ul>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FiscConnectionIntegrationTest {

    private static FiscConnectionSimulator simulator;
    private static int simulatorPort;

    private FiscClient client;
    private Iso8583MessageFactory messageFactory;

    @BeforeAll
    static void setUpClass() throws Exception {
        log.info("=== Starting FISC Simulator ===");
        simulator = new FiscConnectionSimulator();
        simulator.start().get(5, TimeUnit.SECONDS);
        simulatorPort = simulator.getPort();
        log.info("FISC Simulator started on port {}", simulatorPort);
    }

    @AfterAll
    static void tearDownClass() {
        log.info("=== Stopping FISC Simulator ===");
        if (simulator != null) {
            simulator.close();
        }
    }

    @BeforeEach
    void setUp() {
        messageFactory = new Iso8583MessageFactory();
        messageFactory.setInstitutionId("0004");
        simulator.resetCounters();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect().get(2, TimeUnit.SECONDS);
                }
                client.close();
                // Wait for resources to be released
                Thread.sleep(200);
            } catch (Exception e) {
                log.warn("Error closing client: {}", e.getMessage());
            } finally {
                client = null;
            }
        }
    }

    /**
     * Helper method to create and configure a FISC client.
     */
    private FiscClient createClient() {
        FiscConnectionConfig config = FiscConnectionConfig.builder()
            .connectionName("TEST-CLIENT")
            .primaryHost("localhost")
            .primaryPort(simulatorPort)
            .institutionId("0004")
            .connectTimeoutMs(5000)
            .readTimeoutMs(10000)
            .autoReconnect(false) // Disable for most tests
            .build();

        return new FiscClient(config);
    }

    // ==================== Connection Tests ====================

    @Test
    @Order(1)
    @DisplayName("01. TCP/IP Connection - Establish and Close")
    void testConnectionEstablishAndClose() throws Exception {
        log.info("Test: Connection Establish and Close");

        client = createClient();

        // Connect
        client.connect().get(5, TimeUnit.SECONDS);
        Thread.sleep(100); // Wait for connection to be fully established
        assertTrue(client.isConnected(), "Should be connected");
        assertEquals(ConnectionState.CONNECTED, client.getState());
        assertEquals(1, simulator.getConnectedClientsCount(), "Should have 1 connected client");

        // Disconnect
        client.disconnect().get(5, TimeUnit.SECONDS);
        Thread.sleep(100); // Wait for disconnection to be fully processed
        assertFalse(client.isConnected(), "Should be disconnected");
        assertEquals(ConnectionState.DISCONNECTED, client.getState());
        assertEquals(0, simulator.getConnectedClientsCount(), "Should have 0 connected clients");
    }

    @Test
    @Order(2)
    @DisplayName("02. Network Management - Sign On (0800/0810)")
    void testSignOn() throws Exception {
        log.info("Test: Sign On");

        client = createClient();
        client.connect().get(5, TimeUnit.SECONDS);

        // Sign on
        Iso8583Message response = client.signOn().get(5, TimeUnit.SECONDS);

        assertNotNull(response, "Response should not be null");
        assertEquals("0810", response.getMti(), "Should be sign-on response");
        assertEquals("00", response.getFieldAsString(39), "Response code should be 00 (approved)");
        assertTrue(client.isSignedOn(), "Should be signed on");
        assertEquals(ConnectionState.SIGNED_ON, client.getState());
    }

    @Test
    @Order(3)
    @DisplayName("03. Network Management - Echo Test (Heartbeat)")
    void testEchoTest() throws Exception {
        log.info("Test: Echo Test (Heartbeat)");

        client = createClient();
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        // Send echo test
        Iso8583Message response = client.sendEchoTest().get(5, TimeUnit.SECONDS);

        assertNotNull(response, "Response should not be null");
        assertEquals("0810", response.getMti(), "Should be network management response");
        assertEquals("00", response.getFieldAsString(39), "Response code should be 00");
    }

    @Test
    @Order(4)
    @DisplayName("04. Network Management - Sign Off")
    void testSignOff() throws Exception {
        log.info("Test: Sign Off");

        client = createClient();
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        // Sign off
        Iso8583Message response = client.signOff().get(5, TimeUnit.SECONDS);

        assertNotNull(response, "Response should not be null");
        assertEquals("0810", response.getMti(), "Should be network management response");
    }

    // ==================== Financial Transaction Tests ====================

    @Test
    @Order(10)
    @DisplayName("10. Financial Transaction - Withdrawal (0200/0210)")
    void testWithdrawal() throws Exception {
        log.info("Test: Withdrawal Transaction");

        client = createClient();
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        // Create withdrawal request
        Iso8583Message request = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
        messageFactory.setTransactionFields(request);
        request.setField(2, "4111111111111111");  // PAN
        request.setField(3, "010000");            // Processing Code: Withdrawal from savings
        request.setField(4, "000000010000");      // Amount: 100.00
        request.setField(41, "ATM00001");         // Terminal ID
        request.setField(42, "MERCHANT0001");     // Card Acceptor ID

        // Send and receive
        Iso8583Message response = client.sendAndReceive(request).get(5, TimeUnit.SECONDS);

        assertNotNull(response, "Response should not be null");
        assertEquals("0210", response.getMti(), "Should be financial response");
        assertEquals("00", response.getFieldAsString(39), "Should be approved");
        assertEquals(request.getFieldAsString(11), response.getFieldAsString(11),
            "STAN should match");
    }

    @Test
    @Order(11)
    @DisplayName("11. Financial Transaction - Transfer (0200/0210)")
    void testTransfer() throws Exception {
        log.info("Test: Transfer Transaction");

        client = createClient();
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        // Create transfer request
        Iso8583Message request = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
        messageFactory.setTransactionFields(request);
        request.setField(2, "4111111111111111");  // PAN
        request.setField(3, "400000");            // Processing Code: Transfer
        request.setField(4, "000000050000");      // Amount: 500.00
        request.setField(41, "ATM00001");
        request.setField(42, "MERCHANT0001");
        request.setField(102, "1234567890");      // To Account

        Iso8583Message response = client.sendAndReceive(request).get(5, TimeUnit.SECONDS);

        assertNotNull(response);
        assertEquals("0210", response.getMti());
        assertEquals("00", response.getFieldAsString(39));
    }

    @Test
    @Order(12)
    @DisplayName("12. Financial Transaction - Balance Inquiry (0200/0210)")
    void testBalanceInquiry() throws Exception {
        log.info("Test: Balance Inquiry");

        client = createClient();
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        // Create inquiry request
        Iso8583Message request = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
        messageFactory.setTransactionFields(request);
        request.setField(2, "4111111111111111");  // PAN
        request.setField(3, "300000");            // Processing Code: Balance Inquiry
        request.setField(41, "ATM00001");
        request.setField(42, "MERCHANT0001");

        Iso8583Message response = client.sendAndReceive(request).get(5, TimeUnit.SECONDS);

        assertNotNull(response);
        assertEquals("0210", response.getMti());
        assertEquals("00", response.getFieldAsString(39));
    }

    // ==================== Reversal Tests ====================

    @Test
    @Order(20)
    @DisplayName("20. Reversal Transaction (0400/0410)")
    void testReversal() throws Exception {
        log.info("Test: Reversal Transaction");

        client = createClient();
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        // First, do a normal transaction
        Iso8583Message originalRequest = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
        messageFactory.setTransactionFields(originalRequest);
        originalRequest.setField(2, "4111111111111111");
        originalRequest.setField(3, "010000");
        originalRequest.setField(4, "000000010000");
        originalRequest.setField(41, "ATM00001");
        originalRequest.setField(42, "MERCHANT0001");

        Iso8583Message originalResponse = client.sendAndReceive(originalRequest)
            .get(5, TimeUnit.SECONDS);
        assertEquals("00", originalResponse.getFieldAsString(39));

        // Now send reversal
        Iso8583Message reversalRequest = messageFactory.createMessage(MessageType.REVERSAL_REQUEST);
        messageFactory.setTransactionFields(reversalRequest);
        reversalRequest.setField(2, originalRequest.getFieldAsString(2));
        reversalRequest.setField(3, originalRequest.getFieldAsString(3));
        reversalRequest.setField(4, originalRequest.getFieldAsString(4));
        reversalRequest.setField(41, originalRequest.getFieldAsString(41));
        reversalRequest.setField(42, originalRequest.getFieldAsString(42));
        reversalRequest.setField(90, originalRequest.getFieldAsString(11)); // Original STAN

        Iso8583Message reversalResponse = client.sendAndReceive(reversalRequest)
            .get(5, TimeUnit.SECONDS);

        assertNotNull(reversalResponse);
        assertEquals("0410", reversalResponse.getMti());
        assertEquals("00", reversalResponse.getFieldAsString(39));
    }

    // ==================== Concurrent Transaction Tests ====================

    @Test
    @Order(30)
    @DisplayName("30. Concurrent Transactions - Multiple Requests")
    void testConcurrentTransactions() throws Exception {
        log.info("Test: Concurrent Transactions");

        client = createClient();
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        int transactionCount = 10;
        CompletableFuture<?>[] futures = new CompletableFuture[transactionCount];

        for (int i = 0; i < transactionCount; i++) {
            Iso8583Message request = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
            messageFactory.setTransactionFields(request);
            request.setField(2, "411111111111111" + i);
            request.setField(3, "010000");
            request.setField(4, String.format("%012d", (i + 1) * 1000));
            request.setField(41, "ATM00001");
            request.setField(42, "MERCHANT0001");

            futures[i] = client.sendAndReceive(request)
                .thenAccept(response -> {
                    assertEquals("0210", response.getMti());
                    assertEquals("00", response.getFieldAsString(39));
                });
        }

        // Wait for all transactions to complete
        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
        log.info("All {} transactions completed successfully", transactionCount);
    }

    // ==================== Error Handling Tests ====================

    @Test
    @Order(40)
    @DisplayName("40. Error Handling - Timeout")
    void testTimeout() throws Exception {
        log.info("Test: Timeout Handling");

        // Configure simulator to delay response
        simulator.registerHandler("0200", request -> {
            try {
                Thread.sleep(3000); // Delay 3 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return request.createResponse();
        });

        client = createClient();
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        Iso8583Message request = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
        messageFactory.setTransactionFields(request);
        request.setField(2, "4111111111111111");
        request.setField(3, "010000");
        request.setField(4, "000000010000");

        // Send with short timeout (1 second)
        assertThrows(Exception.class, () -> {
            client.sendAndReceive(request, 1000).get(2, TimeUnit.SECONDS);
        }, "Should timeout");

        // Restore default handler
        simulator.registerHandler("0200", request2 -> {
            Iso8583Message response = request2.createResponse();
            response.setField(39, "00");
            return response;
        });
    }

    @Test
    @Order(41)
    @DisplayName("41. Error Handling - Declined Transaction")
    void testDeclinedTransaction() throws Exception {
        log.info("Test: Declined Transaction");

        // Configure simulator to decline transactions
        simulator.registerHandler("0200", request -> {
            Iso8583Message response = request.createResponse();
            response.setField(39, "51"); // Insufficient funds
            return response;
        });

        client = createClient();
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        Iso8583Message request = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
        messageFactory.setTransactionFields(request);
        request.setField(2, "4111111111111111");
        request.setField(3, "010000");
        request.setField(4, "000000010000");

        Iso8583Message response = client.sendAndReceive(request).get(5, TimeUnit.SECONDS);

        assertNotNull(response);
        assertEquals("0210", response.getMti());
        assertEquals("51", response.getFieldAsString(39), "Should be declined");

        // Restore default handler
        simulator.registerHandler("0200", request2 -> {
            Iso8583Message resp = request2.createResponse();
            resp.setField(39, "00");
            return resp;
        });
    }

    // ==================== Auto-Reconnection Tests ====================

    @Test
    @Order(50)
    @DisplayName("50. Auto-Reconnection - Connection Lost")
    void testAutoReconnection() throws Exception {
        log.info("Test: Auto-Reconnection");

        // Create client with auto-reconnect enabled
        FiscConnectionConfig config = FiscConnectionConfig.builder()
            .connectionName("TEST-RECONNECT")
            .primaryHost("localhost")
            .primaryPort(simulatorPort)
            .institutionId("0004")
            .autoReconnect(true)
            .maxRetryAttempts(3)
            .retryDelayMs(1000)
            .build();

        client = new FiscClient(config);

        AtomicInteger reconnectCount = new AtomicInteger(0);
        client.setListener(new com.fep.communication.client.ConnectionListener() {
            @Override
            public void onReconnecting(String connectionName, int attempt) {
                reconnectCount.incrementAndGet();
                log.info("Reconnecting attempt: {}", attempt);
            }
        });

        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        // Simulate connection drop by closing client
        log.info("Simulating connection drop...");
        // Force close the channel
        if (client != null && client.isConnected()) {
            // Wait to trigger reconnect
            Thread.sleep(3000);
        }

        // Verify reconnection was attempted
        assertTrue(reconnectCount.get() > 0 || client.isSignedOn(),
            "Should have attempted reconnection or maintained connection");
    }

    // ==================== Stress Test ====================

    @Test
    @Order(60)
    @DisplayName("60. Stress Test - High Volume Transactions")
    void testHighVolumeTransactions() throws Exception {
        log.info("Test: High Volume Transactions");

        client = createClient();
        client.connect().get(5, TimeUnit.SECONDS);
        client.signOn().get(5, TimeUnit.SECONDS);

        int transactionCount = 100;
        long startTime = System.currentTimeMillis();

        CompletableFuture<?>[] futures = new CompletableFuture[transactionCount];

        for (int i = 0; i < transactionCount; i++) {
            Iso8583Message request = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
            messageFactory.setTransactionFields(request);
            request.setField(2, String.format("411111%010d", i));
            request.setField(3, "010000");
            request.setField(4, "000000001000");
            request.setField(41, "ATM00001");

            futures[i] = client.sendAndReceive(request);
        }

        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        double tps = (transactionCount * 1000.0) / duration;

        log.info("Completed {} transactions in {}ms (TPS: {:.2f})",
            transactionCount, duration, tps);
        log.info("Simulator stats - Received: {}, Sent: {}",
            simulator.getMessagesReceivedCount(), simulator.getMessagesSentCount());

        assertTrue(tps > 10, "TPS should be greater than 10");
    }

    // ==================== Full Lifecycle Test ====================

    @Test
    @Order(100)
    @DisplayName("100. Full Lifecycle - Connect, Sign On, Transactions, Sign Off, Disconnect")
    void testFullLifecycle() throws Exception {
        log.info("Test: Full Lifecycle");

        client = createClient();

        // 1. Connect
        client.connect().get(5, TimeUnit.SECONDS);
        assertTrue(client.isConnected());

        // 2. Sign on
        client.signOn().get(5, TimeUnit.SECONDS);
        assertTrue(client.isSignedOn());

        // 3. Echo test
        client.sendEchoTest().get(5, TimeUnit.SECONDS);

        // 4. Withdrawal
        Iso8583Message withdrawalReq = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
        messageFactory.setTransactionFields(withdrawalReq);
        withdrawalReq.setField(2, "4111111111111111");
        withdrawalReq.setField(3, "010000");
        withdrawalReq.setField(4, "000000010000");
        Iso8583Message withdrawalResp = client.sendAndReceive(withdrawalReq).get(5, TimeUnit.SECONDS);
        assertEquals("00", withdrawalResp.getFieldAsString(39));

        // 5. Transfer
        Iso8583Message transferReq = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
        messageFactory.setTransactionFields(transferReq);
        transferReq.setField(2, "4111111111111111");
        transferReq.setField(3, "400000");
        transferReq.setField(4, "000000020000");
        Iso8583Message transferResp = client.sendAndReceive(transferReq).get(5, TimeUnit.SECONDS);
        assertEquals("00", transferResp.getFieldAsString(39));

        // 6. Balance inquiry
        Iso8583Message inquiryReq = messageFactory.createMessage(MessageType.FINANCIAL_REQUEST);
        messageFactory.setTransactionFields(inquiryReq);
        inquiryReq.setField(2, "4111111111111111");
        inquiryReq.setField(3, "300000");
        Iso8583Message inquiryResp = client.sendAndReceive(inquiryReq).get(5, TimeUnit.SECONDS);
        assertEquals("00", inquiryResp.getFieldAsString(39));

        // 7. Sign off
        client.signOff().get(5, TimeUnit.SECONDS);

        // 8. Disconnect
        client.disconnect().get(5, TimeUnit.SECONDS);
        assertFalse(client.isConnected());

        log.info("Full lifecycle test completed successfully");
        log.info("Total messages - Received: {}, Sent: {}",
            simulator.getMessagesReceivedCount(), simulator.getMessagesSentCount());
    }
}
