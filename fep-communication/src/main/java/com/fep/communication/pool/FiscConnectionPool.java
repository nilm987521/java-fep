package com.fep.communication.pool;

import com.fep.communication.client.ConnectionListener;
import com.fep.communication.client.ConnectionState;
import com.fep.communication.client.FiscClient;
import com.fep.communication.config.FiscConnectionConfig;
import com.fep.communication.exception.CommunicationException;
import com.fep.message.iso8583.Iso8583Message;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connection pool for FISC clients.
 *
 * <p>Manages multiple connections to FISC for high-throughput scenarios.
 * Supports:
 * <ul>
 *   <li>Pool sizing (min/max connections)</li>
 *   <li>Connection acquisition with timeout</li>
 *   <li>Automatic connection validation</li>
 *   <li>Load balancing across connections</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * FiscConnectionConfig config = FiscConnectionConfig.builder()
 *     .primaryHost("fisc.example.com")
 *     .primaryPort(9000)
 *     .poolMinSize(2)
 *     .poolMaxSize(10)
 *     .build();
 *
 * FiscConnectionPool pool = new FiscConnectionPool(config);
 * pool.initialize().get();
 *
 * // Send transaction using pool
 * Iso8583Message response = pool.sendAndReceive(request).get();
 * }</pre>
 */
@Slf4j
public class FiscConnectionPool implements AutoCloseable {

    private final FiscConnectionConfig config;
    private final List<FiscClient> allConnections = new ArrayList<>();
    private final ConcurrentLinkedQueue<FiscClient> availableConnections = new ConcurrentLinkedQueue<>();
    private final Semaphore connectionSemaphore;
    private final AtomicInteger currentSize = new AtomicInteger(0);
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    private volatile boolean initialized = false;
    private volatile boolean closed = false;
    private volatile ConnectionListener poolListener;

    /**
     * Creates a new connection pool.
     *
     * @param config the connection configuration
     */
    public FiscConnectionPool(FiscConnectionConfig config) {
        this.config = config;
        this.connectionSemaphore = new Semaphore(config.getPoolMaxSize(), true);
    }

    /**
     * Sets the pool event listener.
     *
     * @param listener the listener
     */
    public void setPoolListener(ConnectionListener listener) {
        this.poolListener = listener;
    }

    /**
     * Initializes the connection pool with minimum connections.
     *
     * @return CompletableFuture that completes when initialized
     */
    public CompletableFuture<Void> initialize() {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }

        log.info("Initializing FISC connection pool: min={}, max={}",
            config.getPoolMinSize(), config.getPoolMaxSize());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < config.getPoolMinSize(); i++) {
            futures.add(createAndConnectClient(i));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                initialized = true;
                log.info("Connection pool initialized with {} connections", currentSize.get());
            });
    }

    /**
     * Creates and connects a new client.
     */
    private CompletableFuture<Void> createAndConnectClient(int index) {
        FiscConnectionConfig clientConfig = FiscConnectionConfig.builder()
            .primaryHost(config.getPrimaryHost())
            .primaryPort(config.getPrimaryPort())
            .backupHost(config.getBackupHost())
            .backupPort(config.getBackupPort())
            .connectTimeoutMs(config.getConnectTimeoutMs())
            .readTimeoutMs(config.getReadTimeoutMs())
            .writeTimeoutMs(config.getWriteTimeoutMs())
            .idleTimeoutMs(config.getIdleTimeoutMs())
            .heartbeatIntervalMs(config.getHeartbeatIntervalMs())
            .maxRetryAttempts(config.getMaxRetryAttempts())
            .retryDelayMs(config.getRetryDelayMs())
            .autoReconnect(config.isAutoReconnect())
            .useBackupOnFailure(config.isUseBackupOnFailure())
            .tcpKeepAlive(config.isTcpKeepAlive())
            .tcpNoDelay(config.isTcpNoDelay())
            .receiveBufferSize(config.getReceiveBufferSize())
            .sendBufferSize(config.getSendBufferSize())
            .institutionId(config.getInstitutionId())
            .connectionName(config.getConnectionName() + "-" + index)
            .build();

        FiscClient client = new FiscClient(clientConfig);
        client.setListener(new PoolConnectionListener(client));

        synchronized (allConnections) {
            allConnections.add(client);
        }
        currentSize.incrementAndGet();

        return client.connect()
            .thenCompose(v -> client.signOn())
            .thenRun(() -> {
                availableConnections.offer(client);
                log.debug("Client {} added to pool", clientConfig.getConnectionName());
            })
            .exceptionally(ex -> {
                log.error("Failed to initialize client {}: {}",
                    clientConfig.getConnectionName(), ex.getMessage());
                synchronized (allConnections) {
                    allConnections.remove(client);
                }
                currentSize.decrementAndGet();
                return null;
            });
    }

    /**
     * Acquires a connection from the pool.
     *
     * @return a FISC client
     * @throws CommunicationException if pool is exhausted
     */
    public FiscClient acquire() throws CommunicationException {
        return acquire(config.getPoolMaxWaitMs());
    }

    /**
     * Acquires a connection from the pool with timeout.
     *
     * @param timeoutMs timeout in milliseconds
     * @return a FISC client
     * @throws CommunicationException if pool is exhausted or timeout
     */
    public FiscClient acquire(long timeoutMs) throws CommunicationException {
        if (closed) {
            throw CommunicationException.invalidState("Pool is closed");
        }

        try {
            if (!connectionSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw CommunicationException.poolExhausted();
            }

            FiscClient client = availableConnections.poll();
            if (client != null && client.isSignedOn()) {
                return client;
            }

            // No available connection, try to create new one if under max
            if (currentSize.get() < config.getPoolMaxSize()) {
                log.debug("Creating new connection, current size: {}", currentSize.get());
                createAndConnectClient(currentSize.get()).join();
                client = availableConnections.poll();
                if (client != null) {
                    return client;
                }
            }

            // Return any available connection
            client = availableConnections.poll();
            if (client != null) {
                return client;
            }

            connectionSemaphore.release();
            throw CommunicationException.poolExhausted();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw CommunicationException.poolExhausted();
        }
    }

    /**
     * Releases a connection back to the pool.
     *
     * @param client the client to release
     */
    public void release(FiscClient client) {
        if (client == null) {
            return;
        }

        if (client.isSignedOn()) {
            availableConnections.offer(client);
        } else {
            // Connection is not healthy, remove from pool
            synchronized (allConnections) {
                allConnections.remove(client);
            }
            currentSize.decrementAndGet();
            client.close();

            // Try to create replacement
            if (currentSize.get() < config.getPoolMinSize()) {
                createAndConnectClient(currentSize.get());
            }
        }

        connectionSemaphore.release();
    }

    /**
     * Sends a message and receives response using a pooled connection.
     *
     * @param request the request message
     * @return CompletableFuture with the response
     */
    public CompletableFuture<Iso8583Message> sendAndReceive(Iso8583Message request) {
        FiscClient client = null;
        try {
            client = acquire();
            FiscClient finalClient = client;
            return client.sendAndReceive(request)
                .whenComplete((response, ex) -> release(finalClient));
        } catch (CommunicationException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Sends a message using round-robin load balancing (without blocking).
     *
     * @param request the request message
     * @return CompletableFuture with the response
     */
    public CompletableFuture<Iso8583Message> sendRoundRobin(Iso8583Message request) {
        List<FiscClient> clients;
        synchronized (allConnections) {
            clients = new ArrayList<>(allConnections);
        }

        if (clients.isEmpty()) {
            return CompletableFuture.failedFuture(
                CommunicationException.invalidState("No connections available"));
        }

        int index = roundRobinIndex.getAndIncrement() % clients.size();
        FiscClient client = clients.get(index);

        if (!client.isSignedOn()) {
            // Find next signed-on client
            for (int i = 0; i < clients.size(); i++) {
                index = (index + 1) % clients.size();
                client = clients.get(index);
                if (client.isSignedOn()) {
                    break;
                }
            }
        }

        return client.sendAndReceive(request);
    }

    /**
     * Gets the current pool size.
     *
     * @return current number of connections
     */
    public int getCurrentSize() {
        return currentSize.get();
    }

    /**
     * Gets the number of available connections.
     *
     * @return available connection count
     */
    public int getAvailableCount() {
        return availableConnections.size();
    }

    /**
     * Checks if the pool is healthy (has at least one signed-on connection).
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        synchronized (allConnections) {
            return allConnections.stream().anyMatch(FiscClient::isSignedOn);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        log.info("Closing connection pool");

        synchronized (allConnections) {
            for (FiscClient client : allConnections) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.warn("Error closing client: {}", e.getMessage());
                }
            }
            allConnections.clear();
        }
        availableConnections.clear();
        currentSize.set(0);

        log.info("Connection pool closed");
    }

    /**
     * Internal listener for pool connection events.
     */
    private class PoolConnectionListener implements ConnectionListener {
        private final FiscClient client;

        PoolConnectionListener(FiscClient client) {
            this.client = client;
        }

        @Override
        public void onDisconnected(String connectionName, Throwable cause) {
            availableConnections.remove(client);
            if (poolListener != null) {
                poolListener.onDisconnected(connectionName, cause);
            }
        }

        @Override
        public void onSignedOn(String connectionName) {
            if (poolListener != null) {
                poolListener.onSignedOn(connectionName);
            }
        }

        @Override
        public void onStateChanged(String connectionName, ConnectionState oldState, ConnectionState newState) {
            if (poolListener != null) {
                poolListener.onStateChanged(connectionName, oldState, newState);
            }
        }

        @Override
        public void onError(String connectionName, Throwable error) {
            if (poolListener != null) {
                poolListener.onError(connectionName, error);
            }
        }
    }
}
