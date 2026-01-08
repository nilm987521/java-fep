package com.fep.jmeter.config;

import com.fep.communication.client.ConnectionListener;
import com.fep.communication.client.ConnectionState;
import com.fep.communication.client.FiscClient;
import com.fep.communication.config.FiscConnectionConfig;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.engine.event.LoopIterationListener;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * JMeter Config Element for FISC connection management.
 *
 * <p>This config element allows users to define FISC connection settings
 * at the Test Plan or Thread Group level. It manages connection pooling
 * and provides shared connections to all samplers within its scope.
 *
 * <p>Features:
 * <ul>
 *   <li>Connection pooling per thread</li>
 *   <li>Auto-reconnect on failure</li>
 *   <li>Primary/Backup host failover</li>
 *   <li>Sign-on management</li>
 * </ul>
 *
 * <p>Usage:
 * <ol>
 *   <li>Add this config element to your Test Plan or Thread Group</li>
 *   <li>Configure the FISC connection settings</li>
 *   <li>The connection will be established automatically when the test starts</li>
 *   <li>Samplers can access the shared connection via {@link #getClient(String)}</li>
 * </ol>
 */
public class FiscConfigElement extends ConfigTestElement
    implements TestBean, TestStateListener, LoopIterationListener, ConnectionListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(FiscConfigElement.class);

    // Property names
    public static final String CONFIG_NAME = "configName";
    public static final String PRIMARY_HOST = "primaryHost";
    public static final String PRIMARY_PORT = "primaryPort";
    public static final String BACKUP_HOST = "backupHost";
    public static final String BACKUP_PORT = "backupPort";
    public static final String CONNECTION_TIMEOUT = "connectionTimeout";
    public static final String READ_TIMEOUT = "readTimeout";
    public static final String IDLE_TIMEOUT = "idleTimeout";
    public static final String INSTITUTION_ID = "institutionId";
    public static final String AUTO_SIGN_ON = "autoSignOn";
    public static final String AUTO_RECONNECT = "autoReconnect";

    // Connection pool - thread-safe storage for clients per config name and thread
    private static final Map<String, Map<String, FiscClient>> configClientPools = new ConcurrentHashMap<>();

    public FiscConfigElement() {
        super();
        setName("FISC Connection Config");
    }

    /**
     * Gets the FiscClient for the current thread.
     *
     * @param configName the configuration name
     * @return the FiscClient for this thread
     */
    public static FiscClient getClient(String configName) {
        Map<String, FiscClient> pool = configClientPools.get(configName);
        if (pool == null) {
            throw new IllegalStateException("FISC config not found: " + configName);
        }

        String threadKey = Thread.currentThread().getName();
        return pool.get(threadKey);
    }

    /**
     * Gets or creates a FiscClient for the current thread.
     */
    private FiscClient getOrCreateClient() {
        String configName = getConfigName();
        String threadKey = Thread.currentThread().getName();

        Map<String, FiscClient> pool = configClientPools.computeIfAbsent(
            configName, k -> new ConcurrentHashMap<>());

        return pool.computeIfAbsent(threadKey, k -> createClient(threadKey));
    }

    /**
     * Creates a new FISC client.
     */
    private FiscClient createClient(String threadName) {
        FiscConnectionConfig.FiscConnectionConfigBuilder builder = FiscConnectionConfig.builder()
            .primaryHost(getPrimaryHost())
            .primaryPort(getPrimaryPort())
            .connectTimeoutMs(getConnectionTimeout())
            .readTimeoutMs(getReadTimeout())
            .idleTimeoutMs(getIdleTimeout())
            .connectionName(getConfigName() + "-" + threadName)
            .institutionId(getInstitutionId())
            .autoReconnect(isAutoReconnect());

        // Configure backup host if provided
        String backupHost = getBackupHost();
        int backupPort = getBackupPort();
        if (backupHost != null && !backupHost.isEmpty() && backupPort > 0) {
            builder.backupHost(backupHost)
                   .backupPort(backupPort)
                   .useBackupOnFailure(true);
        }

        FiscConnectionConfig config = builder.build();
        FiscClient client = new FiscClient(config);
        client.setListener(this);

        return client;
    }

    /**
     * Establishes connection and optionally signs on.
     */
    private void establishConnection(FiscClient client) {
        try {
            client.connect().get(getConnectionTimeout(), TimeUnit.MILLISECONDS);
            log.info("[{}] Connected to FISC", client.getConnectionName());

            if (isAutoSignOn()) {
                client.signOn().get(getReadTimeout(), TimeUnit.MILLISECONDS);
                log.info("[{}] Signed on to FISC", client.getConnectionName());
            }

            // Store connection state in JMeter variables
            JMeterVariables vars = JMeterContextService.getContext().getVariables();
            if (vars != null) {
                vars.put("FISC_CONNECTED", "true");
                vars.put("FISC_CONFIG", getConfigName());
            }

        } catch (Exception e) {
            log.error("[{}] Failed to establish FISC connection: {}",
                getConfigName(), e.getMessage());
            throw new RuntimeException("Failed to connect to FISC: " + e.getMessage(), e);
        }
    }

    // TestStateListener implementation
    @Override
    public void testStarted() {
        testStarted("");
    }

    @Override
    public void testStarted(String host) {
        log.info("FISC config '{}' test started on {}", getConfigName(), host);
        configClientPools.computeIfAbsent(getConfigName(), k -> new ConcurrentHashMap<>());
    }

    @Override
    public void testEnded() {
        testEnded("");
    }

    @Override
    public void testEnded(String host) {
        log.info("FISC config '{}' test ended, closing connections", getConfigName());

        Map<String, FiscClient> pool = configClientPools.remove(getConfigName());
        if (pool != null) {
            pool.values().forEach(client -> {
                try {
                    if (client.isSignedOn()) {
                        client.signOff().get(5, TimeUnit.SECONDS);
                    }
                    client.close();
                } catch (Exception e) {
                    log.warn("Error closing FISC client: {}", e.getMessage());
                }
            });
        }
    }

    // LoopIterationListener implementation - called at start of each thread iteration
    @Override
    public void iterationStart(LoopIterationEvent event) {
        FiscClient client = getOrCreateClient();

        // Ensure connection is established
        if (!client.isConnected()) {
            establishConnection(client);
        }
    }

    // ConnectionListener implementation
    @Override
    public void onConnected(String connectionName) {
        log.info("[{}] FISC connection established", connectionName);
    }

    @Override
    public void onDisconnected(String connectionName, Throwable cause) {
        log.warn("[{}] FISC connection lost: {}", connectionName,
            cause != null ? cause.getMessage() : "clean disconnect");
    }

    @Override
    public void onSignedOn(String connectionName) {
        log.info("[{}] FISC signed on", connectionName);
    }

    @Override
    public void onSignedOff(String connectionName) {
        log.info("[{}] FISC signed off", connectionName);
    }

    @Override
    public void onError(String connectionName, Throwable error) {
        log.error("[{}] FISC error: {}", connectionName, error.getMessage());
    }

    @Override
    public void onStateChanged(String connectionName, ConnectionState oldState, ConnectionState newState) {
        log.debug("[{}] State changed: {} -> {}", connectionName, oldState, newState);
    }

    @Override
    public void onReconnecting(String connectionName, int attempt) {
        log.info("[{}] Reconnecting, attempt {}", connectionName, attempt);
    }

    // Getters and Setters
    public String getConfigName() {
        return getPropertyAsString(CONFIG_NAME, "default");
    }

    public void setConfigName(String name) {
        setProperty(CONFIG_NAME, name);
    }

    public String getPrimaryHost() {
        return getPropertyAsString(PRIMARY_HOST, "localhost");
    }

    public void setPrimaryHost(String host) {
        setProperty(PRIMARY_HOST, host);
    }

    public int getPrimaryPort() {
        return getPropertyAsInt(PRIMARY_PORT, 9000);
    }

    public void setPrimaryPort(int port) {
        setProperty(PRIMARY_PORT, port);
    }

    public String getBackupHost() {
        return getPropertyAsString(BACKUP_HOST, "");
    }

    public void setBackupHost(String host) {
        setProperty(BACKUP_HOST, host);
    }

    public int getBackupPort() {
        return getPropertyAsInt(BACKUP_PORT, 0);
    }

    public void setBackupPort(int port) {
        setProperty(BACKUP_PORT, port);
    }

    public int getConnectionTimeout() {
        return getPropertyAsInt(CONNECTION_TIMEOUT, 10000);
    }

    public void setConnectionTimeout(int timeout) {
        setProperty(CONNECTION_TIMEOUT, timeout);
    }

    public int getReadTimeout() {
        return getPropertyAsInt(READ_TIMEOUT, 30000);
    }

    public void setReadTimeout(int timeout) {
        setProperty(READ_TIMEOUT, timeout);
    }

    public int getIdleTimeout() {
        return getPropertyAsInt(IDLE_TIMEOUT, 30000);
    }

    public void setIdleTimeout(int timeout) {
        setProperty(IDLE_TIMEOUT, timeout);
    }

    public String getInstitutionId() {
        return getPropertyAsString(INSTITUTION_ID, "");
    }

    public void setInstitutionId(String id) {
        setProperty(INSTITUTION_ID, id);
    }

    public boolean isAutoSignOn() {
        return getPropertyAsBoolean(AUTO_SIGN_ON, true);
    }

    public void setAutoSignOn(boolean autoSignOn) {
        setProperty(AUTO_SIGN_ON, autoSignOn);
    }

    public boolean isAutoReconnect() {
        return getPropertyAsBoolean(AUTO_RECONNECT, true);
    }

    public void setAutoReconnect(boolean autoReconnect) {
        setProperty(AUTO_RECONNECT, autoReconnect);
    }
}
