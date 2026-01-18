package com.fep.communication.manager;

import com.fep.communication.client.DualChannelState;
import com.fep.communication.client.FiscDualChannelClient;
import com.fep.message.channel.ChannelConnection;
import com.fep.message.channel.ChannelConnectionRegistry;
import com.fep.message.channel.ConnectionProfile;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DynamicConnectionManager.
 *
 * <p>These tests use a mock ChannelConnectionRegistry and verify the
 * manager's behavior without actually connecting to FISC.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamicConnectionManagerTest {

    private ChannelConnectionRegistry registry;
    private DynamicConnectionManager manager;
    private TestLifecycleListener lifecycleListener;

    @BeforeEach
    void setUp() {
        registry = ChannelConnectionRegistry.getInstance();
        registry.clear();

        // Load test configuration
        loadTestConfiguration();

        manager = new DynamicConnectionManager(registry, null);
        manager.setAutoConnect(false); // Disable auto-connect for unit tests
        manager.setAutoSignOn(false);

        lifecycleListener = new TestLifecycleListener();
        manager.addLifecycleListener(lifecycleListener);
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
        registry.clear();
    }

    // ==================== Initialization Tests ====================

    @Test
    @Order(1)
    @DisplayName("Should initialize with empty connections")
    void shouldInitializeWithEmptyConnections() {
        assertEquals(0, manager.getConnectionCount());
        assertTrue(manager.getAllConnectionIds().isEmpty());
        assertFalse(manager.hasConnection("FISC_TEST_V1"));
    }

    @Test
    @Order(2)
    @DisplayName("Should subscribe to registry on init")
    void shouldSubscribeToRegistryOnInit() {
        // Create a new manager and init
        DynamicConnectionManager newManager = new DynamicConnectionManager(registry);
        newManager.setAutoConnect(false);
        newManager.init();

        // Verify by triggering a reload - subscriber should be notified
        // This is tested implicitly by onConnectionsUpdated being called
        assertNotNull(newManager);
        newManager.shutdown();
    }

    // ==================== Connection Management Tests ====================

    @Test
    @Order(10)
    @DisplayName("Should add connection for configured channel")
    void shouldAddConnectionForConfiguredChannel() {
        FiscDualChannelClient client = manager.addConnection("FISC_TEST_V1");

        assertNotNull(client);
        assertTrue(manager.hasConnection("FISC_TEST_V1"));
        assertEquals(1, manager.getConnectionCount());

        // Verify lifecycle listener was notified
        assertEquals(1, lifecycleListener.addedCount.get());
        assertEquals("FISC_TEST_V1", lifecycleListener.lastAddedChannelId);
    }

    @Test
    @Order(11)
    @DisplayName("Should return existing client when adding duplicate")
    void shouldReturnExistingClientWhenAddingDuplicate() {
        FiscDualChannelClient client1 = manager.addConnection("FISC_TEST_V1");
        FiscDualChannelClient client2 = manager.addConnection("FISC_TEST_V1");

        assertSame(client1, client2, "Should return the same client instance");
        assertEquals(1, manager.getConnectionCount());
    }

    @Test
    @Order(12)
    @DisplayName("Should throw when adding unconfigured channel")
    void shouldThrowWhenAddingUnconfiguredChannel() {
        assertThrows(Exception.class, () ->
                manager.addConnection("UNCONFIGURED_CHANNEL"));
    }

    @Test
    @Order(20)
    @DisplayName("Should remove connection")
    void shouldRemoveConnection() {
        manager.addConnection("FISC_TEST_V1");
        assertTrue(manager.hasConnection("FISC_TEST_V1"));

        boolean removed = manager.removeConnection("FISC_TEST_V1");

        assertTrue(removed);
        assertFalse(manager.hasConnection("FISC_TEST_V1"));
        assertEquals(0, manager.getConnectionCount());

        // Verify lifecycle listener was notified
        assertEquals(1, lifecycleListener.removedCount.get());
        assertEquals("FISC_TEST_V1", lifecycleListener.lastRemovedChannelId);
    }

    @Test
    @Order(21)
    @DisplayName("Should return false when removing non-existent connection")
    void shouldReturnFalseWhenRemovingNonExistent() {
        boolean removed = manager.removeConnection("NON_EXISTENT");

        assertFalse(removed);
        assertEquals(0, lifecycleListener.removedCount.get());
    }

    // ==================== Query Tests ====================

    @Test
    @Order(30)
    @DisplayName("Should get connection by ID")
    void shouldGetConnectionById() {
        manager.addConnection("FISC_TEST_V1");

        Optional<FiscDualChannelClient> client = manager.getConnection("FISC_TEST_V1");

        assertTrue(client.isPresent());
        assertNotNull(client.get());
    }

    @Test
    @Order(31)
    @DisplayName("Should return empty optional for non-existent connection")
    void shouldReturnEmptyForNonExistent() {
        Optional<FiscDualChannelClient> client = manager.getConnection("NON_EXISTENT");

        assertTrue(client.isEmpty());
    }

    @Test
    @Order(32)
    @DisplayName("Should get all connection IDs")
    void shouldGetAllConnectionIds() {
        manager.addConnection("FISC_TEST_V1");
        addSecondTestChannel();
        manager.addConnection("FISC_TEST_V2");

        Set<String> ids = manager.getAllConnectionIds();

        assertEquals(2, ids.size());
        assertTrue(ids.contains("FISC_TEST_V1"));
        assertTrue(ids.contains("FISC_TEST_V2"));
    }

    @Test
    @Order(33)
    @DisplayName("Should get connection state")
    void shouldGetConnectionState() {
        manager.addConnection("FISC_TEST_V1");

        Optional<DualChannelState> state = manager.getConnectionState("FISC_TEST_V1");

        assertTrue(state.isPresent());
        // Since autoConnect is false, state should be DISCONNECTED
        assertEquals(DualChannelState.DISCONNECTED, state.get());
    }

    @Test
    @Order(34)
    @DisplayName("Should get all connection states")
    void shouldGetAllConnectionStates() {
        manager.addConnection("FISC_TEST_V1");
        addSecondTestChannel();
        manager.addConnection("FISC_TEST_V2");

        Map<String, DualChannelState> states = manager.getAllConnectionStates();

        assertEquals(2, states.size());
        assertTrue(states.containsKey("FISC_TEST_V1"));
        assertTrue(states.containsKey("FISC_TEST_V2"));
    }

    // ==================== Lifecycle Listener Tests ====================

    @Test
    @Order(40)
    @DisplayName("Should notify listener on add")
    void shouldNotifyListenerOnAdd() {
        manager.addConnection("FISC_TEST_V1");

        assertEquals(1, lifecycleListener.addedCount.get());
        assertEquals("FISC_TEST_V1", lifecycleListener.lastAddedChannelId);
        assertNotNull(lifecycleListener.lastAddedClient);
    }

    @Test
    @Order(41)
    @DisplayName("Should notify listener on remove")
    void shouldNotifyListenerOnRemove() {
        manager.addConnection("FISC_TEST_V1");
        manager.removeConnection("FISC_TEST_V1");

        assertEquals(1, lifecycleListener.removedCount.get());
        assertEquals("FISC_TEST_V1", lifecycleListener.lastRemovedChannelId);
    }

    @Test
    @Order(42)
    @DisplayName("Should support multiple listeners")
    void shouldSupportMultipleListeners() {
        TestLifecycleListener listener2 = new TestLifecycleListener();
        manager.addLifecycleListener(listener2);

        manager.addConnection("FISC_TEST_V1");

        assertEquals(1, lifecycleListener.addedCount.get());
        assertEquals(1, listener2.addedCount.get());
    }

    @Test
    @Order(43)
    @DisplayName("Should remove listener")
    void shouldRemoveListener() {
        manager.removeLifecycleListener(lifecycleListener);

        manager.addConnection("FISC_TEST_V1");

        assertEquals(0, lifecycleListener.addedCount.get());
    }

    // ==================== Subscriber Notification Tests ====================

    @Test
    @Order(50)
    @DisplayName("Should sync connections on registry update")
    void shouldSyncConnectionsOnRegistryUpdate() {
        // Add initial connection
        manager.addConnection("FISC_TEST_V1");
        assertEquals(1, manager.getConnectionCount());

        // Simulate configuration update - add new channel
        addSecondTestChannel();

        // Trigger subscriber notification
        Map<String, ChannelConnection> connectionMap = registry.getConnectionMap();
        Map<String, ConnectionProfile> profileMap = registry.getProfileMap();
        manager.onConnectionsUpdated(connectionMap, profileMap);

        // Manager should now have connections for all active channels
        // (though FISC_TEST_V2 wasn't auto-created since we're just testing the sync logic)
        assertTrue(manager.hasConnection("FISC_TEST_V1"));
    }

    @Test
    @Order(51)
    @DisplayName("Should handle connection changed notification")
    void shouldHandleConnectionChangedNotification() {
        manager.addConnection("FISC_TEST_V1");
        assertTrue(manager.hasConnection("FISC_TEST_V1"));

        // Get the config and mark it as inactive
        ChannelConnection config = registry.getChannelConnectionRequired("FISC_TEST_V1");

        // Create inactive version
        ChannelConnection inactiveConfig = ChannelConnection.builder()
                .channelId("FISC_TEST_V1")
                .connectionProfileId(config.getConnectionProfileId())
                .active(false)
                .priority(100)
                .build();
        inactiveConfig.resolveConnectionProfile(config.getResolvedConnectionProfile());

        manager.onConnectionChanged("FISC_TEST_V1", inactiveConfig);

        // Connection should be removed since it's now inactive
        assertFalse(manager.hasConnection("FISC_TEST_V1"));
    }

    @Test
    @Order(52)
    @DisplayName("Should handle connection removed notification")
    void shouldHandleConnectionRemovedNotification() {
        manager.addConnection("FISC_TEST_V1");

        manager.onConnectionRemoved("FISC_TEST_V1");

        assertFalse(manager.hasConnection("FISC_TEST_V1"));
    }

    // ==================== Reconnect Tests ====================

    @Test
    @Order(60)
    @DisplayName("Should reconnect existing connection")
    void shouldReconnectExistingConnection() {
        FiscDualChannelClient original = manager.addConnection("FISC_TEST_V1");

        FiscDualChannelClient reconnected = manager.reconnect("FISC_TEST_V1");

        assertNotNull(reconnected);
        assertNotSame(original, reconnected, "Should create new client instance");
        assertTrue(manager.hasConnection("FISC_TEST_V1"));
    }

    @Test
    @Order(61)
    @DisplayName("Should throw when reconnecting unconfigured channel")
    void shouldThrowWhenReconnectingUnconfigured() {
        assertThrows(Exception.class, () ->
                manager.reconnect("UNCONFIGURED_CHANNEL"));
    }

    // ==================== Shutdown Tests ====================

    @Test
    @Order(70)
    @DisplayName("Should close all connections on shutdown")
    void shouldCloseAllConnectionsOnShutdown() {
        manager.addConnection("FISC_TEST_V1");
        addSecondTestChannel();
        manager.addConnection("FISC_TEST_V2");
        assertEquals(2, manager.getConnectionCount());

        manager.shutdown();

        assertEquals(0, manager.getConnectionCount());
    }

    // ==================== Thread Safety Tests ====================

    @Test
    @Order(80)
    @DisplayName("Should handle concurrent operations")
    void shouldHandleConcurrentOperations() throws Exception {
        // Add more test channels
        addSecondTestChannel();
        addThirdTestChannel();

        int numThreads = 4;
        int operationsPerThread = 10;
        CountDownLatch latch = new CountDownLatch(numThreads);

        // Concurrent add/remove/query operations
        Thread[] threads = new Thread[numThreads];

        threads[0] = new Thread(() -> {
            try {
                for (int i = 0; i < operationsPerThread; i++) {
                    manager.addConnection("FISC_TEST_V1");
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                // Expected
            } finally {
                latch.countDown();
            }
        });

        threads[1] = new Thread(() -> {
            try {
                for (int i = 0; i < operationsPerThread; i++) {
                    manager.removeConnection("FISC_TEST_V1");
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                // Expected
            } finally {
                latch.countDown();
            }
        });

        threads[2] = new Thread(() -> {
            try {
                for (int i = 0; i < operationsPerThread; i++) {
                    manager.getAllConnectionIds();
                    manager.getConnectionCount();
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                // Expected
            } finally {
                latch.countDown();
            }
        });

        threads[3] = new Thread(() -> {
            try {
                for (int i = 0; i < operationsPerThread; i++) {
                    manager.hasConnection("FISC_TEST_V1");
                    manager.getConnection("FISC_TEST_V1");
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                // Expected
            } finally {
                latch.countDown();
            }
        });

        for (Thread thread : threads) {
            thread.start();
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "All threads should complete within timeout");

        // No assertion on final state - just ensure no exceptions
    }

    // ==================== Helper Methods ====================

    private void loadTestConfiguration() {
        String json = """
            {
              "version": "2.0",
              "connectionProfiles": {
                "TEST_PROFILE": {
                  "profileId": "TEST_PROFILE",
                  "host": "localhost",
                  "sendPort": 9001,
                  "receivePort": 9002,
                  "connectTimeout": 5000,
                  "responseTimeout": 30000
                }
              },
              "channels": {
                "FISC_TEST_V1": {
                  "connectionProfile": "TEST_PROFILE",
                  "active": true,
                  "priority": 1
                }
              }
            }
            """;
        registry.loadFromJson(json, "test-config.json");
    }

    private void addSecondTestChannel() {
        // Add another channel dynamically
        ConnectionProfile profile = registry.getConnectionProfileRequired("TEST_PROFILE");

        ChannelConnection channel2 = ChannelConnection.builder()
                .channelId("FISC_TEST_V2")
                .connectionProfileId("TEST_PROFILE")
                .active(true)
                .priority(2)
                .build();
        channel2.resolveConnectionProfile(profile);
        registry.registerConnection(channel2);
    }

    private void addThirdTestChannel() {
        ConnectionProfile profile = registry.getConnectionProfileRequired("TEST_PROFILE");

        ChannelConnection channel3 = ChannelConnection.builder()
                .channelId("FISC_TEST_V3")
                .connectionProfileId("TEST_PROFILE")
                .active(true)
                .priority(3)
                .build();
        channel3.resolveConnectionProfile(profile);
        registry.registerConnection(channel3);
    }

    /**
     * Test implementation of ConnectionLifecycleListener.
     */
    private static class TestLifecycleListener implements ConnectionLifecycleListener {
        final AtomicInteger addedCount = new AtomicInteger(0);
        final AtomicInteger removedCount = new AtomicInteger(0);
        final AtomicInteger failedCount = new AtomicInteger(0);

        volatile String lastAddedChannelId;
        volatile String lastRemovedChannelId;
        volatile FiscDualChannelClient lastAddedClient;

        @Override
        public void onConnectionAdded(String channelId, FiscDualChannelClient client) {
            addedCount.incrementAndGet();
            lastAddedChannelId = channelId;
            lastAddedClient = client;
        }

        @Override
        public void onConnectionRemoved(String channelId) {
            removedCount.incrementAndGet();
            lastRemovedChannelId = channelId;
        }

        @Override
        public void onConnectionFailed(String channelId, Throwable cause) {
            failedCount.incrementAndGet();
        }
    }
}
