package com.fep.message.channel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fep.message.interfaces.ConnectionSubscriber;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Unified registry for Channel Connection configurations.
 * Combines logical channel (schema) with physical connection (TCP/IP) settings.
 *
 * <p>This registry manages:
 * <ul>
 *   <li>ConnectionProfiles - Reusable TCP/IP connection settings</li>
 *   <li>ChannelConnections - Channel to ConnectionProfile mappings</li>
 *   <li>Hot-reload of configuration files</li>
 *   <li>Subscriber notifications on configuration changes</li>
 * </ul>
 *
 * <p>Configuration file format (V2):
 * <pre>
 * {
 *   "version": "2.0",
 *   "connectionProfiles": {
 *     "FISC_PRIMARY": { "host": "...", "sendPort": 9001, ... }
 *   },
 *   "channels": {
 *     "FISC_INTERBANK_V1": { "connectionProfile": "FISC_PRIMARY", ... }
 *   }
 * }
 * </pre>
 *
 * <p>The registry also supports V1 format (backward compatible) which only contains
 * channel-schema mappings without connection profiles.
 */
@Slf4j
public class ChannelConnectionRegistry {

    private static final ChannelConnectionRegistry INSTANCE = new ChannelConnectionRegistry();

    // Core storage
    private final ConcurrentMap<String, ConnectionProfile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ChannelConnection> connections = new ConcurrentHashMap<>();

    // Configuration state
    private volatile String configFilePath;
    private volatile String configVersion;
    private volatile boolean v2ConfigLoaded = false;

    // Reference to ChannelSchemaRegistry for V1 fallback
    private volatile ChannelSchemaRegistry schemaRegistry;

    // File watching for hot-reload
    private WatchService watchService;
    private ScheduledExecutorService watchExecutor;
    private volatile boolean watchEnabled = false;

    // Subscribers
    private final List<WeakReference<ConnectionSubscriber>> subscribers = new CopyOnWriteArrayList<>();

    // JSON mapper
    private final ObjectMapper jsonMapper;

    private ChannelConnectionRegistry() {
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Gets the singleton instance.
     *
     * @return the ChannelConnectionRegistry instance
     */
    public static ChannelConnectionRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Sets the reference to ChannelSchemaRegistry for V1 fallback.
     *
     * @param registry the ChannelSchemaRegistry instance
     */
    public void setSchemaRegistry(ChannelSchemaRegistry registry) {
        this.schemaRegistry = registry;
    }

    // ==================== Loading Methods ====================

    /**
     * Loads configuration from a file.
     * Automatically detects V1 or V2 format.
     *
     * @param filePath path to the configuration file
     * @throws ChannelConfigException if loading fails
     */
    public synchronized void loadFromFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                // Try classpath
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(filePath)) {
                    if (is == null) {
                        throw ChannelConfigException.configFileError(filePath, "File not found");
                    }
                    loadFromStream(is, filePath);
                    return;
                }
            }

            String content = Files.readString(file.toPath());
            loadFromJson(content, filePath);

        } catch (IOException e) {
            throw ChannelConfigException.configFileError(filePath, e.getMessage());
        }
    }

    /**
     * Loads configuration from an input stream.
     *
     * @param inputStream the input stream
     * @param sourceName the source name for logging
     * @throws ChannelConfigException if loading fails
     */
    public synchronized void loadFromStream(InputStream inputStream, String sourceName) {
        try {
            String content = new String(inputStream.readAllBytes());
            loadFromJson(content, sourceName);
        } catch (IOException e) {
            throw ChannelConfigException.configFileError(sourceName, e.getMessage());
        }
    }

    /**
     * Loads configuration from a JSON string.
     *
     * @param json the JSON configuration string
     * @param sourceName the source name for logging
     * @throws ChannelConfigException if parsing fails
     */
    public synchronized void loadFromJson(String json, String sourceName) {
        try {
            JsonNode root = jsonMapper.readTree(json);

            // Detect version
            String version = root.has("version") ? root.get("version").asText() : "1.0";
            this.configVersion = version;

            if (version.startsWith("2")) {
                loadV2Config(root);
                this.v2ConfigLoaded = true;
            } else {
                log.info("V1 configuration detected at {}, delegating to ChannelSchemaRegistry", sourceName);
                this.v2ConfigLoaded = false;
                // V1 format - delegate to ChannelSchemaRegistry
                if (schemaRegistry != null) {
                    schemaRegistry.loadFromJson(json);
                }
            }

            this.configFilePath = sourceName;
            log.info("Loaded {} connection profiles and {} channel connections from {}",
                    profiles.size(), connections.size(), sourceName);

            notifySubscribers();

        } catch (IOException e) {
            throw new ChannelConfigException("Failed to parse JSON configuration: " + e.getMessage(), e);
        }
    }

    private void loadV2Config(JsonNode root) {
        // Clear existing
        profiles.clear();
        connections.clear();

        // Load connection profiles
        JsonNode profilesNode = root.get("connectionProfiles");
        if (profilesNode != null && profilesNode.isObject()) {
            profilesNode.fields().forEachRemaining(entry -> {
                String profileId = entry.getKey();
                try {
                    ConnectionProfile profile = jsonMapper.treeToValue(entry.getValue(), ConnectionProfile.class);
                    // Set profileId if not set in JSON
                    if (profile.getProfileId() == null || profile.getProfileId().isBlank()) {
                        profile = ConnectionProfile.builder()
                                .profileId(profileId)
                                .host(profile.getHost())
                                .sendPort(profile.getSendPort())
                                .receivePort(profile.getReceivePort())
                                .connectTimeout(profile.getConnectTimeout())
                                .responseTimeout(profile.getResponseTimeout())
                                .heartbeatInterval(profile.getHeartbeatInterval())
                                .maxRetries(profile.getMaxRetries())
                                .retryDelay(profile.getRetryDelay())
                                .sslEnabled(profile.isSslEnabled())
                                .poolSize(profile.getPoolSize())
                                .autoReconnect(profile.isAutoReconnect())
                                .keepAliveInterval(profile.getKeepAliveInterval())
                                .dualChannel(profile.getDualChannelSetting())
                                .connectionMode(profile.getConnectionMode())
                                .properties(profile.getProperties())
                                .build();
                    }
                    profile.validate();
                    profiles.put(profileId, profile);
                    log.debug("Loaded connection profile: {}", profileId);
                } catch (Exception e) {
                    log.warn("Failed to load connection profile {}: {}", profileId, e.getMessage());
                }
            });
        }

        // Load channel connections
        JsonNode channelsNode = root.get("channels");
        if (channelsNode != null && channelsNode.isObject()) {
            channelsNode.fields().forEachRemaining(entry -> {
                String channelId = entry.getKey();
                try {
                    ChannelConnection connection = jsonMapper.treeToValue(entry.getValue(), ChannelConnection.class);
                    // Set channelId if not set in JSON
                    if (connection.getChannelId() == null || connection.getChannelId().isBlank()) {
                        connection = ChannelConnection.builder()
                                .channelId(channelId)
                                .connectionProfileId(connection.getConnectionProfileId())
                                .schemas(connection.getSchemas())
                                .properties(connection.getProperties())
                                .description(connection.getDescription())
                                .active(connection.isActive())
                                .priority(connection.getPriority())
                                .build();
                    }
                    connection.validate();

                    // Resolve connection profile reference
                    String profileId = connection.getConnectionProfileId();
                    if (profileId != null && profiles.containsKey(profileId)) {
                        connection.resolveConnectionProfile(profiles.get(profileId));
                    } else {
                        log.warn("Channel {} references unknown profile: {}", channelId, profileId);
                    }

                    connections.put(channelId, connection);
                    log.debug("Loaded channel connection: {}", channelId);
                } catch (Exception e) {
                    log.warn("Failed to load channel connection {}: {}", channelId, e.getMessage());
                }
            });
        }
    }

    /**
     * Reloads configuration from the current file path.
     *
     * @throws ChannelConfigException if reload fails
     */
    public synchronized void reload() {
        if (configFilePath != null) {
            log.info("Reloading channel connection configuration from {}", configFilePath);
            loadFromFile(configFilePath);
        } else {
            log.warn("No configuration file path set, cannot reload");
        }
    }

    // ==================== Query Methods ====================

    /**
     * Gets a channel connection by ID.
     *
     * @param channelId the channel ID
     * @return Optional containing the connection, or empty if not found
     */
    public Optional<ChannelConnection> getChannelConnection(String channelId) {
        return Optional.ofNullable(connections.get(channelId));
    }

    /**
     * Gets a channel connection by ID, throwing if not found.
     *
     * @param channelId the channel ID
     * @return the ChannelConnection
     * @throws ChannelConfigException if not found
     */
    public ChannelConnection getChannelConnectionRequired(String channelId) {
        return getChannelConnection(channelId)
                .orElseThrow(() -> ChannelConfigException.channelNotFound(channelId));
    }

    /**
     * Gets a connection profile by ID.
     *
     * @param profileId the profile ID
     * @return Optional containing the profile, or empty if not found
     */
    public Optional<ConnectionProfile> getConnectionProfile(String profileId) {
        return Optional.ofNullable(profiles.get(profileId));
    }

    /**
     * Gets a connection profile by ID, throwing if not found.
     *
     * @param profileId the profile ID
     * @return the ConnectionProfile
     * @throws ChannelConfigException if not found
     */
    public ConnectionProfile getConnectionProfileRequired(String profileId) {
        return getConnectionProfile(profileId)
                .orElseThrow(() -> new ChannelConfigException("Connection profile not found: " + profileId));
    }

    /**
     * Gets all active channel connections.
     *
     * @return list of active connections sorted by priority
     */
    public List<ChannelConnection> getActiveConnections() {
        return connections.values().stream()
                .filter(ChannelConnection::isActive)
                .sorted(Comparator.comparingInt(ChannelConnection::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * Gets all channel connections using a specific profile.
     *
     * @param profileId the profile ID
     * @return list of connections using that profile
     */
    public List<ChannelConnection> getConnectionsByProfile(String profileId) {
        return connections.values().stream()
                .filter(c -> profileId.equals(c.getConnectionProfileId()))
                .collect(Collectors.toList());
    }

    /**
     * Gets all channel IDs.
     *
     * @return unmodifiable set of channel IDs
     */
    public Set<String> getAllChannelIds() {
        return Collections.unmodifiableSet(connections.keySet());
    }

    /**
     * Gets all profile IDs.
     *
     * @return unmodifiable set of profile IDs
     */
    public Set<String> getAllProfileIds() {
        return Collections.unmodifiableSet(profiles.keySet());
    }

    /**
     * Checks if a channel connection exists.
     *
     * @param channelId the channel ID
     * @return true if the connection exists
     */
    public boolean hasConnection(String channelId) {
        return connections.containsKey(channelId);
    }

    /**
     * Checks if a connection profile exists.
     *
     * @param profileId the profile ID
     * @return true if the profile exists
     */
    public boolean hasProfile(String profileId) {
        return profiles.containsKey(profileId);
    }

    /**
     * Gets the connection map.
     *
     * @return unmodifiable map of channelId to ChannelConnection
     */
    public Map<String, ChannelConnection> getConnectionMap() {
        return Collections.unmodifiableMap(new HashMap<>(connections));
    }

    /**
     * Gets the profile map.
     *
     * @return unmodifiable map of profileId to ConnectionProfile
     */
    public Map<String, ConnectionProfile> getProfileMap() {
        return Collections.unmodifiableMap(new HashMap<>(profiles));
    }

    /**
     * Checks if V2 configuration is loaded.
     *
     * @return true if V2 config is loaded
     */
    public boolean isV2ConfigLoaded() {
        return v2ConfigLoaded;
    }

    /**
     * Gets the configuration version.
     *
     * @return the version string
     */
    public String getConfigVersion() {
        return configVersion;
    }

    // ==================== Hot-Reload Support ====================

    /**
     * Enables file watching for automatic hot-reload.
     */
    public synchronized void enableHotReload() {
        if (watchEnabled || configFilePath == null) {
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path configDir = Paths.get(configFilePath).getParent();
            if (configDir == null) {
                configDir = Paths.get(".");
            }
            configDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            watchExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "channel-connection-watcher");
                t.setDaemon(true);
                return t;
            });

            watchExecutor.scheduleWithFixedDelay(this::checkForChanges, 5, 5, TimeUnit.SECONDS);
            watchEnabled = true;
            log.info("Hot-reload enabled for channel connection configuration");

        } catch (IOException e) {
            log.error("Failed to enable hot-reload: {}", e.getMessage());
        }
    }

    /**
     * Disables file watching for hot-reload.
     */
    public synchronized void disableHotReload() {
        watchEnabled = false;
        if (watchExecutor != null) {
            watchExecutor.shutdown();
            watchExecutor = null;
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Error closing watch service: {}", e.getMessage());
            }
            watchService = null;
        }
        log.info("Hot-reload disabled");
    }

    /**
     * Checks if hot-reload is enabled.
     *
     * @return true if hot-reload is enabled
     */
    public boolean isHotReloadEnabled() {
        return watchEnabled;
    }

    private void checkForChanges() {
        if (watchService == null) {
            return;
        }

        WatchKey key = watchService.poll();
        if (key != null) {
            for (WatchEvent<?> event : key.pollEvents()) {
                Path changed = (Path) event.context();
                String configFileName = Paths.get(configFilePath).getFileName().toString();
                if (changed.toString().equals(configFileName)) {
                    log.info("Detected configuration file change, reloading...");
                    try {
                        reload();
                    } catch (Exception e) {
                        log.error("Failed to reload configuration: {}", e.getMessage());
                    }
                }
            }
            key.reset();
        }
    }

    // ==================== Subscriber Management ====================

    /**
     * Registers a subscriber for connection updates.
     * Uses WeakReference to prevent memory leaks.
     *
     * @param subscriber the subscriber to register
     */
    public void subscribe(ConnectionSubscriber subscriber) {
        if (subscriber == null) {
            return;
        }

        boolean exists = subscribers.stream()
                .anyMatch(ref -> ref.get() == subscriber);

        if (!exists) {
            subscribers.add(new WeakReference<>(subscriber));
            log.debug("Registered connection subscriber: {}", subscriber.getClass().getSimpleName());
        }

        // Immediately notify if connections are loaded
        if (!connections.isEmpty()) {
            try {
                subscriber.onConnectionsUpdated(getConnectionMap(), getProfileMap());
            } catch (Exception e) {
                log.error("Failed to notify new subscriber: {}", e.getMessage());
            }
        }
    }

    /**
     * Unregisters a subscriber.
     *
     * @param subscriber the subscriber to unregister
     */
    public void unsubscribe(ConnectionSubscriber subscriber) {
        if (subscriber == null) {
            return;
        }
        subscribers.removeIf(ref -> {
            ConnectionSubscriber s = ref.get();
            return s == null || s == subscriber;
        });
        log.debug("Unregistered connection subscriber: {}", subscriber.getClass().getSimpleName());
    }

    private void notifySubscribers() {
        Map<String, ChannelConnection> connectionMap = getConnectionMap();
        Map<String, ConnectionProfile> profileMap = getProfileMap();
        List<WeakReference<ConnectionSubscriber>> toRemove = new ArrayList<>();

        for (WeakReference<ConnectionSubscriber> ref : subscribers) {
            ConnectionSubscriber subscriber = ref.get();
            if (subscriber == null) {
                toRemove.add(ref);
                continue;
            }
            try {
                subscriber.onConnectionsUpdated(connectionMap, profileMap);
            } catch (Exception e) {
                log.error("Failed to notify subscriber {}: {}",
                        subscriber.getClass().getSimpleName(), e.getMessage());
            }
        }

        if (!toRemove.isEmpty()) {
            subscribers.removeAll(toRemove);
            log.debug("Cleaned up {} GC'd subscriber references", toRemove.size());
        }
    }

    // ==================== Runtime Registration ====================

    /**
     * Registers a connection profile at runtime.
     *
     * @param profile the profile to register
     * @throws ChannelConfigException if profile is invalid
     */
    public void registerProfile(ConnectionProfile profile) {
        profile.validate();
        profiles.put(profile.getProfileId(), profile);
        log.info("Registered connection profile at runtime: {}", profile.getProfileId());

        // Update any connections referencing this profile
        connections.values().stream()
                .filter(c -> profile.getProfileId().equals(c.getConnectionProfileId()))
                .forEach(c -> c.resolveConnectionProfile(profile));

        notifySubscribers();
    }

    /**
     * Registers a channel connection at runtime.
     *
     * @param connection the connection to register
     * @throws ChannelConfigException if connection is invalid
     */
    public void registerConnection(ChannelConnection connection) {
        connection.validate();

        // Resolve profile if exists
        String profileId = connection.getConnectionProfileId();
        if (profileId != null && profiles.containsKey(profileId)) {
            connection.resolveConnectionProfile(profiles.get(profileId));
        }

        connections.put(connection.getChannelId(), connection);
        log.info("Registered channel connection at runtime: {}", connection.getChannelId());
        notifySubscribers();
    }

    /**
     * Unregisters a channel connection.
     *
     * @param channelId the channel ID to unregister
     * @return the removed connection, or null if not found
     */
    public ChannelConnection unregisterConnection(String channelId) {
        ChannelConnection removed = connections.remove(channelId);
        if (removed != null) {
            log.info("Unregistered channel connection: {}", channelId);
            notifySubscribers();
        }
        return removed;
    }

    /**
     * Unregisters a connection profile.
     * Note: This may leave channels without a resolved profile.
     *
     * @param profileId the profile ID to unregister
     * @return the removed profile, or null if not found
     */
    public ConnectionProfile unregisterProfile(String profileId) {
        ConnectionProfile removed = profiles.remove(profileId);
        if (removed != null) {
            log.info("Unregistered connection profile: {}", profileId);
            // Clear resolved profiles from connections
            connections.values().stream()
                    .filter(c -> profileId.equals(c.getConnectionProfileId()))
                    .forEach(c -> c.resolveConnectionProfile(null));
            notifySubscribers();
        }
        return removed;
    }

    // ==================== Cleanup ====================

    /**
     * Clears all profiles and connections.
     */
    public synchronized void clear() {
        disableHotReload();
        profiles.clear();
        connections.clear();
        configFilePath = null;
        configVersion = null;
        v2ConfigLoaded = false;
        log.info("Channel connection registry cleared");
    }

    /**
     * Gets the current configuration file path.
     *
     * @return the config file path, or null if not loaded from file
     */
    public String getConfigFilePath() {
        return configFilePath;
    }
}
