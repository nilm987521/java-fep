package com.fep.communication.manager;

import com.fep.communication.client.DualChannelState;
import com.fep.communication.client.FiscDualChannelClient;
import com.fep.communication.config.ConnectionMode;
import com.fep.communication.config.DualChannelConfig;
import com.fep.communication.handler.DefaultServerMessageContext;
import com.fep.communication.handler.DefaultServerMessageHandler;
import com.fep.communication.handler.ServerMessageHandler;
import com.fep.communication.server.FiscDualChannelServer;
import com.fep.message.channel.ChannelConnection;
import com.fep.message.channel.ChannelConnectionRegistry;
import com.fep.message.channel.ChannelSchemaRegistry;
import com.fep.message.channel.ConnectionProfile;
import com.fep.message.generic.schema.MessageSchema;
import com.fep.message.interfaces.ConnectionSubscriber;
import com.fep.message.service.ChannelMessageService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Dynamic connection manager that subscribes to configuration changes
 * and manages FiscDualChannelClient instances at runtime.
 *
 * <p>This component bridges the gap between:
 * <ul>
 *   <li>{@link ChannelConnectionRegistry} - configuration management with hot-reload</li>
 *   <li>{@link FiscDualChannelClient} - actual TCP/IP connections to FISC</li>
 * </ul>
 *
 * <p>Key features:
 * <ul>
 *   <li>Subscribes to {@link ChannelConnectionRegistry} for configuration changes</li>
 *   <li>Automatically syncs connections when configuration is hot-reloaded</li>
 *   <li>Provides dynamic add/remove connection APIs</li>
 *   <li>Thread-safe connection management with ReadWriteLock</li>
 *   <li>Graceful shutdown with sign-off before closing connections</li>
 * </ul>
 *
 * <p>Connection lifecycle:
 * <pre>
 * Configuration Change → onConnectionsUpdated() → syncConnections()
 *     ↓
 * For new channels (active=true):
 *     createClient() → connect() → signOn() → add to connections map
 *     ↓
 * For removed channels or active=false:
 *     signOff() → close() → remove from connections map
 * </pre>
 *
 * <p>Usage example:
 * <pre>{@code
 * @Autowired
 * private DynamicConnectionManager connectionManager;
 *
 * // Get an existing connection
 * Optional<FiscDualChannelClient> client = connectionManager.getConnection("FISC_INTERBANK_V1");
 *
 * // Dynamically add a new connection
 * FiscDualChannelClient newClient = connectionManager.addConnection("NEW_CHANNEL");
 *
 * // Dynamically remove a connection
 * connectionManager.removeConnection("OLD_CHANNEL");
 *
 * // List all active connections
 * Set<String> activeIds = connectionManager.getActiveConnectionIds();
 * }</pre>
 */
@Slf4j
public class DynamicConnectionManager implements ConnectionSubscriber {

    /** Client connections (FEP connects to remote servers) */
    private final ConcurrentHashMap<String, FiscDualChannelClient> clientConnections = new ConcurrentHashMap<>();

    /** Server connections (FEP listens for incoming connections) */
    private final ConcurrentHashMap<String, FiscDualChannelServer> serverConnections = new ConcurrentHashMap<>();

    /** Legacy alias for backward compatibility */
    private final ConcurrentHashMap<String, FiscDualChannelClient> connections = clientConnections;

    private final ChannelConnectionRegistry registry;
    private final ChannelMessageService channelMessageService;
    private final List<ConnectionLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile boolean autoConnect = true;
    private volatile boolean autoSignOn = true;
    private volatile long gracefulShutdownTimeoutMs = 10000L;

    /** Handler for processing messages received by servers */
    @Setter
    private ServerMessageHandler serverMessageHandler;

    /**
     * Creates a new DynamicConnectionManager.
     *
     * @param registry the channel connection registry
     */
    public DynamicConnectionManager(ChannelConnectionRegistry registry) {
        this(registry, null);
    }

    /**
     * Creates a new DynamicConnectionManager with ChannelMessageService support.
     *
     * @param registry the channel connection registry
     * @param channelMessageService the channel message service for schema-based processing (may be null)
     */
    public DynamicConnectionManager(ChannelConnectionRegistry registry, ChannelMessageService channelMessageService) {
        this.registry = registry;
        this.channelMessageService = channelMessageService;
    }

    /**
     * Initializes the manager by subscribing to the registry.
     */
    @PostConstruct
    public void init() {
        // Initialize default server message handler BEFORE subscribing
        // because subscribe() may trigger syncConnections() immediately
        if (serverMessageHandler == null) {
            DefaultServerMessageHandler defaultHandler = new DefaultServerMessageHandler();
            // Provide FISC client lookup for forwarding transactions
            defaultHandler.setFiscClientProvider(channelId -> getConnection(channelId).orElse(null));
            serverMessageHandler = defaultHandler;
            log.info("Initialized default ServerMessageHandler");
        }

        // Now subscribe - this may trigger onConnectionsUpdated() → syncConnections()
        registry.subscribe(this);

        log.info("DynamicConnectionManager initialized and subscribed to ChannelConnectionRegistry");
    }

    /**
     * Shuts down the manager by unsubscribing and closing all connections.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down DynamicConnectionManager...");
        registry.unsubscribe(this);

        lock.writeLock().lock();
        try {
            // Close client connections
            List<String> clientIds = new ArrayList<>(clientConnections.keySet());
            for (String channelId : clientIds) {
                try {
                    closeClientConnectionGracefully(channelId);
                } catch (Exception e) {
                    log.warn("Error closing client connection {}: {}", channelId, e.getMessage());
                }
            }
            clientConnections.clear();

            // Close server connections
            List<String> serverIds = new ArrayList<>(serverConnections.keySet());
            for (String channelId : serverIds) {
                try {
                    closeServerConnectionGracefully(channelId);
                } catch (Exception e) {
                    log.warn("Error closing server connection {}: {}", channelId, e.getMessage());
                }
            }
            serverConnections.clear();
        } finally {
            lock.writeLock().unlock();
        }

        log.info("DynamicConnectionManager shutdown complete");
    }

    // ==================== ConnectionSubscriber Implementation ====================

    /**
     * Called when channel connections are updated in the registry.
     * Syncs the active connections with the configuration.
     *
     * @param connectionMap the updated connection map
     * @param profileMap the updated profile map
     */
    @Override
    public void onConnectionsUpdated(Map<String, ChannelConnection> connectionMap,
                                     Map<String, ConnectionProfile> profileMap) {
        log.info("Received connection configuration update: {} channels, {} profiles",
                connectionMap.size(), profileMap.size());
        syncConnections(connectionMap);
    }

    /**
     * Called when a specific channel connection is added or updated.
     *
     * @param channelId the channel ID
     * @param connection the added or updated connection
     */
    @Override
    public void onConnectionChanged(String channelId, ChannelConnection connection) {
        log.debug("Channel connection changed: {}, active={}", channelId, connection.isActive());

        lock.writeLock().lock();
        try {
            if (connection.isActive()) {
                // Add or update connection
                if (connections.containsKey(channelId)) {
                    // Check if configuration actually changed
                    FiscDualChannelClient existing = connections.get(channelId);
                    if (needsRecreation(existing, connection)) {
                        log.info("Configuration changed for {}, recreating connection", channelId);
                        closeConnectionGracefully(channelId);
                        createAndConnectClient(channelId, connection);
                    }
                } else {
                    createAndConnectClient(channelId, connection);
                }
            } else {
                // Remove inactive connection
                if (connections.containsKey(channelId)) {
                    closeConnectionGracefully(channelId);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Called when a specific channel connection is removed.
     *
     * @param channelId the removed channel ID
     */
    @Override
    public void onConnectionRemoved(String channelId) {
        log.debug("Channel connection removed: {}", channelId);
        removeConnection(channelId);
    }

    // ==================== Dynamic Management API ====================

    /**
     * Dynamically adds a new connection for the specified channel.
     *
     * <p>The channel configuration must exist in {@link ChannelConnectionRegistry}.
     * If the connection already exists, returns the existing client.
     *
     * @param channelId the channel ID to connect
     * @return the connected FiscDualChannelClient
     * @throws IllegalArgumentException if channel configuration not found
     * @throws IllegalStateException if connection fails
     */
    public FiscDualChannelClient addConnection(String channelId) {
        lock.writeLock().lock();
        try {
            if (connections.containsKey(channelId)) {
                log.warn("Connection {} already exists, returning existing client", channelId);
                return connections.get(channelId);
            }

            ChannelConnection config = registry.getChannelConnectionRequired(channelId);
            return createAndConnectClient(channelId, config);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Dynamically removes a connection.
     *
     * <p>Performs graceful shutdown:
     * <ol>
     *   <li>Sign-off from FISC</li>
     *   <li>Close TCP connections</li>
     *   <li>Remove from connections map</li>
     *   <li>Notify lifecycle listeners</li>
     * </ol>
     *
     * @param channelId the channel ID to remove
     * @return true if connection was removed, false if not found
     */
    public boolean removeConnection(String channelId) {
        lock.writeLock().lock();
        try {
            if (!connections.containsKey(channelId)) {
                log.warn("Connection {} not found, nothing to remove", channelId);
                return false;
            }

            closeConnectionGracefully(channelId);
            log.info("Dynamically removed connection: {}", channelId);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets a connection by channel ID.
     *
     * @param channelId the channel ID
     * @return Optional containing the client, or empty if not found
     */
    public Optional<FiscDualChannelClient> getConnection(String channelId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(connections.get(channelId));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets a client connection, throwing if not found.
     *
     * @param channelId the channel ID
     * @return the FiscDualChannelClient
     * @throws IllegalArgumentException if not found
     */
    public FiscDualChannelClient getConnectionRequired(String channelId) {
        return getConnection(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Client connection not found: " + channelId));
    }

    /**
     * Gets a server connection by channel ID.
     *
     * @param channelId the channel ID
     * @return Optional containing the server, or empty if not found
     */
    public Optional<FiscDualChannelServer> getServerConnection(String channelId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(serverConnections.get(channelId));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets a server connection, throwing if not found.
     *
     * @param channelId the channel ID
     * @return the FiscDualChannelServer
     * @throws IllegalArgumentException if not found
     */
    public FiscDualChannelServer getServerConnectionRequired(String channelId) {
        return getServerConnection(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Server connection not found: " + channelId));
    }

    /**
     * Gets all connection IDs (both clients and servers).
     *
     * @return unmodifiable set of all connection IDs
     */
    public Set<String> getAllConnectionIds() {
        lock.readLock().lock();
        try {
            Set<String> allIds = new HashSet<>(clientConnections.keySet());
            allIds.addAll(serverConnections.keySet());
            return Collections.unmodifiableSet(allIds);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets all client connection IDs.
     *
     * @return unmodifiable set of client connection IDs
     */
    public Set<String> getClientConnectionIds() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new HashSet<>(clientConnections.keySet()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets all server connection IDs.
     *
     * @return unmodifiable set of server connection IDs
     */
    public Set<String> getServerConnectionIds() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new HashSet<>(serverConnections.keySet()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets all active (connected and signed-on) connection IDs.
     *
     * @return set of active connection IDs
     */
    public Set<String> getActiveConnectionIds() {
        lock.readLock().lock();
        try {
            return connections.entrySet().stream()
                    .filter(e -> e.getValue().isSignedOn())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toUnmodifiableSet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets all connected (may not be signed-on) connection IDs.
     *
     * @return set of connected connection IDs
     */
    public Set<String> getConnectedConnectionIds() {
        lock.readLock().lock();
        try {
            return connections.entrySet().stream()
                    .filter(e -> e.getValue().isConnected())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toUnmodifiableSet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if a connection (client or server) exists.
     *
     * @param channelId the channel ID
     * @return true if connection exists
     */
    public boolean hasConnection(String channelId) {
        lock.readLock().lock();
        try {
            return clientConnections.containsKey(channelId) || serverConnections.containsKey(channelId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if a client connection exists.
     *
     * @param channelId the channel ID
     * @return true if client connection exists
     */
    public boolean hasClientConnection(String channelId) {
        lock.readLock().lock();
        try {
            return clientConnections.containsKey(channelId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if a server connection exists.
     *
     * @param channelId the channel ID
     * @return true if server connection exists
     */
    public boolean hasServerConnection(String channelId) {
        lock.readLock().lock();
        try {
            return serverConnections.containsKey(channelId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the total connection count (clients + servers).
     *
     * @return number of connections
     */
    public int getConnectionCount() {
        lock.readLock().lock();
        try {
            return clientConnections.size() + serverConnections.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the client connection count.
     *
     * @return number of client connections
     */
    public int getClientConnectionCount() {
        lock.readLock().lock();
        try {
            return clientConnections.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the server connection count.
     *
     * @return number of server connections
     */
    public int getServerConnectionCount() {
        lock.readLock().lock();
        try {
            return serverConnections.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the state of a specific connection.
     *
     * @param channelId the channel ID
     * @return Optional containing the state, or empty if not found
     */
    public Optional<DualChannelState> getConnectionState(String channelId) {
        lock.readLock().lock();
        try {
            FiscDualChannelClient client = connections.get(channelId);
            return client != null ? Optional.of(client.getState()) : Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets detailed status of all connections.
     *
     * @return map of channelId to state
     */
    public Map<String, DualChannelState> getAllConnectionStates() {
        lock.readLock().lock();
        try {
            Map<String, DualChannelState> states = new HashMap<>();
            connections.forEach((id, client) -> states.put(id, client.getState()));
            return Collections.unmodifiableMap(states);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Reconnects a specific connection.
     *
     * @param channelId the channel ID to reconnect
     * @return the reconnected client
     * @throws IllegalArgumentException if channel not found
     */
    public FiscDualChannelClient reconnect(String channelId) {
        lock.writeLock().lock();
        try {
            ChannelConnection config = registry.getChannelConnectionRequired(channelId);

            // Close existing if present
            if (connections.containsKey(channelId)) {
                closeConnectionGracefully(channelId);
            }

            return createAndConnectClient(channelId, config);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ==================== Lifecycle Listener Management ====================

    /**
     * Registers a lifecycle listener.
     *
     * @param listener the listener to register
     */
    public void addLifecycleListener(ConnectionLifecycleListener listener) {
        if (listener != null && !lifecycleListeners.contains(listener)) {
            lifecycleListeners.add(listener);
            log.debug("Registered lifecycle listener: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * Removes a lifecycle listener.
     *
     * @param listener the listener to remove
     */
    public void removeLifecycleListener(ConnectionLifecycleListener listener) {
        if (listener != null) {
            lifecycleListeners.remove(listener);
            log.debug("Removed lifecycle listener: {}", listener.getClass().getSimpleName());
        }
    }

    // ==================== Configuration ====================

    /**
     * Sets whether to auto-connect when adding connections.
     *
     * @param autoConnect true to auto-connect
     */
    public void setAutoConnect(boolean autoConnect) {
        this.autoConnect = autoConnect;
    }

    /**
     * Sets whether to auto-sign-on after connecting.
     *
     * @param autoSignOn true to auto-sign-on
     */
    public void setAutoSignOn(boolean autoSignOn) {
        this.autoSignOn = autoSignOn;
    }

    /**
     * Sets the graceful shutdown timeout.
     *
     * @param timeoutMs timeout in milliseconds
     */
    public void setGracefulShutdownTimeoutMs(long timeoutMs) {
        this.gracefulShutdownTimeoutMs = timeoutMs;
    }

    // ==================== Internal Methods ====================

    /**
     * Syncs connections with the current configuration.
     */
    private void syncConnections(Map<String, ChannelConnection> configuredConnections) {
        lock.writeLock().lock();
        try {
            Set<String> configuredIds = configuredConnections.keySet();
            Set<String> currentClientIds = new HashSet<>(clientConnections.keySet());
            Set<String> currentServerIds = new HashSet<>(serverConnections.keySet());

            // Remove client connections not in configuration or marked inactive
            for (String channelId : currentClientIds) {
                ChannelConnection config = configuredConnections.get(channelId);
                if (config == null || !config.isActive()) {
                    log.info("Removing client connection {} (not in config or inactive)", channelId);
                    closeClientConnectionGracefully(channelId);
                }
            }

            // Remove server connections not in configuration or marked inactive
            for (String channelId : currentServerIds) {
                ChannelConnection config = configuredConnections.get(channelId);
                if (config == null || !config.isActive()) {
                    log.info("Removing server connection {} (not in config or inactive)", channelId);
                    closeServerConnectionGracefully(channelId);
                }
            }

            // Add new active connections
            for (Map.Entry<String, ChannelConnection> entry : configuredConnections.entrySet()) {
                String channelId = entry.getKey();
                ChannelConnection config = entry.getValue();

                if (config.isActive() && !hasConnection(channelId)) {
                    ConnectionProfile profile = config.getResolvedConnectionProfile();
                    String mode = profile != null && profile.isServerMode() ? "server" : "client";
                    log.info("Adding new {} connection: {}", mode, channelId);
                    try {
                        createAndConnectEndpoint(channelId, config);
                    } catch (Exception e) {
                        log.error("Failed to create {} connection {}: {}", mode, channelId, e.getMessage());
                        notifyConnectionFailed(channelId, e);
                    }
                }
            }

            log.info("Connection sync complete: {} clients, {} servers",
                    clientConnections.size(), serverConnections.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Creates and connects a new endpoint (client or server based on profile mode).
     */
    private FiscDualChannelClient createAndConnectEndpoint(String channelId, ChannelConnection config) {
        ConnectionProfile profile = config.getResolvedConnectionProfile();
        if (profile == null) {
            throw new IllegalStateException("Connection profile not resolved for channel: " + channelId);
        }

        // Check if this is server mode
        if (profile.isServerMode()) {
            createAndStartServer(channelId, config, profile);
            return null; // Server mode doesn't return a client
        }

        // Client mode
        return createAndConnectClient(channelId, config, profile);
    }

    /**
     * Creates and starts a server for accepting incoming connections.
     */
    private FiscDualChannelServer createAndStartServer(String channelId, ChannelConnection config,
                                                        ConnectionProfile profile) {
        DualChannelConfig dualConfig = buildDualChannelConfig(channelId, config, profile);
        dualConfig.setConnectionMode(ConnectionMode.SERVER);

        FiscDualChannelServer server = new FiscDualChannelServer(dualConfig);
        server.setChannelMessageService(channelMessageService);

        // Set message handler to process incoming requests
        log.info("Setting up messageHandler for server {}: serverMessageHandler is {}",
                channelId, serverMessageHandler != null ? "SET" : "NULL");
        if (serverMessageHandler != null) {
            server.setMessageHandler((clientId, message) -> {
                log.info("MessageHandler lambda invoked for channel={}, client={}, MTI={}",
                        channelId, clientId, message.getMti());
                ServerMessageHandler.ServerMessageContext context = DefaultServerMessageContext.builder()
                        .channelId(channelId)
                        .clientId(clientId)
                        .message(message)
                        .server(server)
                        .build();
                serverMessageHandler.handleMessage(context);
            });
            log.info("Configured message handler for server: {}", channelId);
        } else {
            log.warn("serverMessageHandler is NULL, server {} will not process messages!", channelId);
        }

        boolean startSuccessful = false;

        try {
            log.info("Starting server for channel: {} (ports: {}/{})",
                    channelId, profile.getSendPort(), profile.getEffectiveReceivePort());
            server.start().get(dualConfig.getConnectTimeoutMs() * 2L, TimeUnit.MILLISECONDS);
            log.info("Server started for channel: {} (actual ports: {}/{})",
                    channelId, server.getActualSendPort(), server.getActualReceivePort());

            serverConnections.put(channelId, server);
            notifyServerStarted(channelId, server);
            startSuccessful = true;

            return server;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Server startup interrupted: " + channelId, e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            log.error("Failed to start server {}: {}", channelId, e.getMessage());
            throw new IllegalStateException("Failed to start server: " + channelId, e);
        } finally {
            if (!startSuccessful) {
                try {
                    server.close();
                } catch (Exception e) {
                    log.warn("Error closing failed server {}: {}", channelId, e.getMessage());
                }
                notifyConnectionFailed(channelId, new Exception("Server startup failed for: " + channelId));
            }
        }
    }

    /**
     * Creates and connects a new client (CLIENT mode).
     * Resolves the profile from the config automatically.
     */
    private FiscDualChannelClient createAndConnectClient(String channelId, ChannelConnection config) {
        ConnectionProfile profile = config.getResolvedConnectionProfile();
        if (profile == null) {
            throw new IllegalStateException("Connection profile not resolved for channel: " + channelId);
        }
        return createAndConnectClient(channelId, config, profile);
    }

    /**
     * Creates and connects a new client (CLIENT mode) with explicit profile.
     *
     * <p>Even if initial connection fails, the client is still stored in the connections map
     * because it has auto-reconnect capability and will retry in the background.
     */
    private FiscDualChannelClient createAndConnectClient(String channelId, ChannelConnection config,
                                                          ConnectionProfile profile) {
        DualChannelConfig dualConfig = buildDualChannelConfig(channelId, config, profile);
        FiscDualChannelClient client = new FiscDualChannelClient(dualConfig, channelMessageService);

        // Always store the client first (it has auto-reconnect capability)
        clientConnections.put(channelId, client);
        notifyConnectionAdded(channelId, client);
        log.info("Created client for channel: {} (autoReconnect={})", channelId, dualConfig.isAutoReconnect());

        if (autoConnect) {
            try {
                client.connect().get(dualConfig.getConnectTimeoutMs() * 2L, TimeUnit.MILLISECONDS);
                log.info("Connection established for channel: {}", channelId);

                if (autoSignOn) {
                    client.signOn().get(dualConfig.getReadTimeoutMs(), TimeUnit.MILLISECONDS);
                    log.info("Sign-on completed for channel: {}", channelId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Connection interrupted for channel: {} (will auto-reconnect)", channelId);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                // Don't remove the client - it will auto-reconnect in the background
                log.warn("Initial connection failed for channel {}: {} (auto-reconnect enabled, will retry)",
                        channelId, e.getMessage());
            }
        }

        return client;
    }

    /**
     * Builds DualChannelConfig from ChannelConnection and ConnectionProfile.
     */
    private DualChannelConfig buildDualChannelConfig(String channelId, ChannelConnection config,
                                                     ConnectionProfile profile) {
        // Try to load schema for GenericMessageDecoder
        MessageSchema messageSchema = loadMessageSchema(channelId);

        return DualChannelConfig.builder()
                .channelId(channelId)
                .connectionName(channelId)
                .sendHost(profile.getHost())
                .sendPort(profile.getSendPort())
                .receiveHost(profile.getHost())
                .receivePort(profile.getEffectiveReceivePort())
                .connectTimeoutMs(profile.getConnectTimeout())
                .readTimeoutMs(profile.getResponseTimeout())
                .heartbeatIntervalMs(profile.getHeartbeatInterval())
                .maxRetryAttempts(profile.getMaxRetries())
                .retryDelayMs(profile.getRetryDelay())
                .autoReconnect(profile.isAutoReconnect())
                .institutionId(config.getInstitutionId())
                .enableGenericMessageTransform(channelMessageService != null)
                .dualChannelMode(profile.isDualChannel())
                .messageSchema(messageSchema)
                .build();
    }

    /**
     * Loads the MessageSchema for a channel from ChannelSchemaRegistry.
     * Returns null if schema cannot be loaded (falls back to FiscMessageDecoder).
     */
    private MessageSchema loadMessageSchema(String channelId) {
        try {
            ChannelSchemaRegistry schemaRegistry = ChannelSchemaRegistry.getInstance();
            MessageSchema schema = schemaRegistry.getDefaultRequestSchema(channelId);
            if (schema != null) {
                log.info("Loaded MessageSchema '{}' for channel: {}", schema.getName(), channelId);
            }
            return schema;
        } catch (Exception e) {
            log.debug("Could not load MessageSchema for channel {}: {} (will use default FiscMessageDecoder)",
                    channelId, e.getMessage());
            return null;
        }
    }

    /**
     * Closes a connection gracefully (either client or server).
     * Performs sign-off before closing if the connection is signed on.
     */
    private void closeConnectionGracefully(String channelId) {
        // Try client first
        if (clientConnections.containsKey(channelId)) {
            closeClientConnectionGracefully(channelId);
            return;
        }
        // Try server
        if (serverConnections.containsKey(channelId)) {
            closeServerConnectionGracefully(channelId);
        }
    }

    /**
     * Closes a client connection gracefully.
     * Performs sign-off before closing if the connection is signed on.
     */
    private void closeClientConnectionGracefully(String channelId) {
        FiscDualChannelClient client = clientConnections.remove(channelId);
        if (client == null) {
            return;
        }

        try {
            if (client.isSignedOn()) {
                log.debug("Signing off from channel: {}", channelId);
                try {
                    client.signOff().get(gracefulShutdownTimeoutMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupt status
                    log.warn("Sign-off interrupted for {}", channelId);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.warn("Sign-off timeout for {}, forcing close", channelId);
                } catch (java.util.concurrent.ExecutionException e) {
                    log.warn("Sign-off failed for {}: {}", channelId, e.getMessage());
                }
            }

            client.close();
            log.debug("Client connection closed for channel: {}", channelId);
        } catch (Exception e) {
            log.warn("Error during graceful close of client {}: {}", channelId, e.getMessage());
        }

        notifyConnectionRemoved(channelId);
    }

    /**
     * Closes a server connection gracefully.
     * Disconnects all clients and stops the server.
     */
    private void closeServerConnectionGracefully(String channelId) {
        FiscDualChannelServer server = serverConnections.remove(channelId);
        if (server == null) {
            return;
        }

        try {
            log.debug("Stopping server for channel: {}", channelId);
            server.stop().get(gracefulShutdownTimeoutMs, TimeUnit.MILLISECONDS);
            log.debug("Server stopped for channel: {}", channelId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Server stop interrupted for {}", channelId);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Server stop timeout for {}, forcing close", channelId);
        } catch (java.util.concurrent.ExecutionException e) {
            log.warn("Server stop failed for {}: {}", channelId, e.getMessage());
        } finally {
            try {
                server.close();
            } catch (Exception e) {
                log.warn("Error closing server {}: {}", channelId, e.getMessage());
            }
        }

        notifyConnectionRemoved(channelId);
    }

    /**
     * Checks if a connection needs to be recreated due to configuration change.
     */
    private boolean needsRecreation(FiscDualChannelClient existing, ChannelConnection newConfig) {
        // For now, we don't support in-place reconfiguration
        // Any configuration change requires recreation
        // Future enhancement: compare actual config values
        return true;
    }

    // ==================== Notification Methods ====================

    private void notifyConnectionAdded(String channelId, FiscDualChannelClient client) {
        for (ConnectionLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onConnectionAdded(channelId, client);
            } catch (Exception e) {
                log.error("Lifecycle listener error on add: {}", e.getMessage());
            }
        }
    }

    private void notifyConnectionRemoved(String channelId) {
        for (ConnectionLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onConnectionRemoved(channelId);
            } catch (Exception e) {
                log.error("Lifecycle listener error on remove: {}", e.getMessage());
            }
        }
    }

    private void notifyConnectionFailed(String channelId, Throwable cause) {
        for (ConnectionLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onConnectionFailed(channelId, cause);
            } catch (Exception e) {
                log.error("Lifecycle listener error on failure: {}", e.getMessage());
            }
        }
    }

    private void notifyServerStarted(String channelId, FiscDualChannelServer server) {
        for (ConnectionLifecycleListener listener : lifecycleListeners) {
            try {
                listener.onServerStarted(channelId, server);
            } catch (Exception e) {
                log.error("Lifecycle listener error on server start: {}", e.getMessage());
            }
        }
    }
}
