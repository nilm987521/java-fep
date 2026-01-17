package com.fep.message.channel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import com.fep.message.interfaces.ChannelSubscriber;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Central registry for Channel to Schema mappings.
 * Supports dynamic loading, hot-reload, and subscriber notifications.
 *
 * <p>This is a singleton class that manages:
 * <ul>
 *   <li>Loading channel configurations from JSON/YAML files</li>
 *   <li>Resolving schemas for specific channels and message types</li>
 *   <li>Dynamic channel registration at runtime</li>
 *   <li>Hot-reload of configuration files</li>
 *   <li>Subscriber notifications on channel changes</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>
 * // Load configuration
 * ChannelSchemaRegistry.getInstance().loadFromFile("config/channel-schema-mapping.json");
 *
 * // Get schema for a channel
 * MessageSchema schema = ChannelSchemaRegistry.getInstance()
 *     .getRequestSchema("ATM_NCR_V1", "0200");
 *
 * // Dynamic registration
 * ChannelSchemaRegistry.getInstance().registerChannel(newChannel);
 * </pre>
 */
@Slf4j
public class ChannelSchemaRegistry {

    private static final ChannelSchemaRegistry INSTANCE = new ChannelSchemaRegistry();

    // Core storage
    private final ConcurrentMap<String, Channel> channels = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, SchemaOverride>> schemaOverrides = new ConcurrentHashMap<>();

    // Configuration
    private volatile ChannelSchemaConfig config;
    private volatile String configFilePath;
    private volatile Path configBasePath;

    // File watching for hot-reload
    private WatchService watchService;
    private ScheduledExecutorService watchExecutor;
    private volatile boolean watchEnabled = false;

    // Subscribers
    private final List<WeakReference<ChannelSubscriber>> subscribers = new CopyOnWriteArrayList<>();

    // JSON/YAML mappers
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    private ChannelSchemaRegistry() {
        this.jsonMapper = createObjectMapper(null);
        this.yamlMapper = createObjectMapper(new YAMLFactory());
    }

    private ObjectMapper createObjectMapper(com.fasterxml.jackson.core.JsonFactory factory) {
        ObjectMapper mapper = factory != null ? new ObjectMapper(factory) : new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Gets the singleton instance.
     *
     * @return the ChannelSchemaRegistry instance
     */
    public static ChannelSchemaRegistry getInstance() {
        return INSTANCE;
    }

    // ==================== Loading Methods ====================

    /**
     * Loads channel configuration from a JSON or YAML file.
     * Automatically detects format based on file extension.
     *
     * @param filePath path to the configuration file
     * @throws ChannelConfigException if loading fails
     */
    public synchronized void loadFromFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw ChannelConfigException.configFileError(filePath, "File not found");
            }

            String content = Files.readString(file.toPath());
            ObjectMapper mapper = isYamlFile(filePath) ? yamlMapper : jsonMapper;

            ChannelSchemaConfig newConfig = mapper.readValue(content, ChannelSchemaConfig.class);

            // Store base path for relative schema file resolution
            this.configBasePath = file.toPath().getParent();
            if (this.configBasePath == null) {
                this.configBasePath = Paths.get(".");
            }

            // Load associated schema files
            loadSchemaFiles(newConfig.getSchemaFiles());

            // Process channels
            processChannels(newConfig);

            // Process overrides
            processOverrides(newConfig);

            this.config = newConfig;
            this.configFilePath = filePath;

            log.info("Loaded {} channels from {}", channels.size(), filePath);

            // Notify subscribers
            notifySubscribers();

        } catch (IOException e) {
            throw ChannelConfigException.configFileError(filePath, e.getMessage());
        }
    }

    /**
     * Loads configuration from a JSON string.
     *
     * @param json the JSON configuration string
     * @throws ChannelConfigException if parsing fails
     */
    public synchronized void loadFromJson(String json) {
        try {
            ChannelSchemaConfig newConfig = jsonMapper.readValue(json, ChannelSchemaConfig.class);

            processChannels(newConfig);
            processOverrides(newConfig);

            this.config = newConfig;

            log.info("Loaded {} channels from JSON string", channels.size());
            notifySubscribers();

        } catch (IOException e) {
            throw new ChannelConfigException("Failed to parse JSON configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Reloads configuration from the current file path.
     *
     * @throws ChannelConfigException if reload fails
     */
    public synchronized void reload() {
        if (configFilePath != null) {
            log.info("Reloading channel configuration from {}", configFilePath);
            loadFromFile(configFilePath);
        } else {
            log.warn("No configuration file path set, cannot reload");
        }
    }

    private boolean isYamlFile(String filePath) {
        String lower = filePath.toLowerCase();
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private void loadSchemaFiles(List<SchemaFileReference> schemaFiles) {
        if (schemaFiles == null || schemaFiles.isEmpty()) {
            return;
        }

        for (SchemaFileReference ref : schemaFiles) {
            Path schemaPath = configBasePath.resolve(ref.getPath());
            if (Files.exists(schemaPath)) {
                try {
                    JsonSchemaLoader.reloadFromFilePath(schemaPath.toString());
                    log.debug("Loaded schema file: {}", schemaPath);
                } catch (Exception e) {
                    log.warn("Failed to load schema file {}: {}", schemaPath, e.getMessage());
                }
            } else {
                log.warn("Schema file not found: {}", schemaPath);
            }
        }
    }

    private void processChannels(ChannelSchemaConfig config) {
        channels.clear();
        if (config.getChannels() != null) {
            for (Channel channel : config.getChannels()) {
                validateChannel(channel);
                channels.put(channel.getId(), channel);
            }
        }
    }

    private void processOverrides(ChannelSchemaConfig config) {
        schemaOverrides.clear();
        if (config.getSchemaOverrides() != null) {
            schemaOverrides.putAll(config.getSchemaOverrides());
        }
    }

    private void validateChannel(Channel channel) {
        if (channel.getId() == null || channel.getId().isBlank()) {
            throw new ChannelConfigException("Channel must have an id");
        }
        if (channel.getType() == null || channel.getType().isBlank()) {
            throw ChannelConfigException.invalidChannel(channel.getId(), "type is required");
        }
    }

    // ==================== Query Methods ====================

    /**
     * Gets a channel by ID.
     *
     * @param channelId the channel ID
     * @return Optional containing the channel, or empty if not found
     */
    public Optional<Channel> getChannel(String channelId) {
        return Optional.ofNullable(channels.get(channelId));
    }

    /**
     * Gets all active channels.
     *
     * @return list of active channels sorted by priority
     */
    public List<Channel> getActiveChannels() {
        return channels.values().stream()
                .filter(Channel::isActive)
                .sorted(Comparator.comparingInt(Channel::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * Gets channels by type (ATM, POS, INTERBANK, etc.).
     *
     * @param type the channel type
     * @return list of matching active channels
     */
    public List<Channel> getChannelsByType(String type) {
        return channels.values().stream()
                .filter(c -> c.isType(type))
                .filter(Channel::isActive)
                .sorted(Comparator.comparingInt(Channel::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * Gets channels by vendor.
     *
     * @param vendor the vendor name
     * @return list of matching active channels
     */
    public List<Channel> getChannelsByVendor(String vendor) {
        return channels.values().stream()
                .filter(c -> c.isVendor(vendor))
                .filter(Channel::isActive)
                .collect(Collectors.toList());
    }

    /**
     * Gets channels by tag.
     *
     * @param tag the tag to filter by
     * @return list of matching active channels
     */
    public List<Channel> getChannelsByTag(String tag) {
        return channels.values().stream()
                .filter(c -> c.hasTag(tag))
                .filter(Channel::isActive)
                .collect(Collectors.toList());
    }

    /**
     * Gets all channel IDs.
     *
     * @return unmodifiable set of channel IDs
     */
    public Set<String> getAllChannelIds() {
        return Collections.unmodifiableSet(channels.keySet());
    }

    /**
     * Checks if a channel exists.
     *
     * @param channelId the channel ID
     * @return true if the channel exists
     */
    public boolean hasChannel(String channelId) {
        return channels.containsKey(channelId);
    }

    // ==================== Schema Resolution ====================

    /**
     * Gets the request schema for a channel and message type.
     *
     * @param channelId the channel ID
     * @param messageType the message type (e.g., MTI like "0200")
     * @return the resolved MessageSchema
     * @throws ChannelConfigException if channel or schema not found
     */
    public MessageSchema getRequestSchema(String channelId, String messageType) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            return handleUnknownChannel(channelId, messageType, true);
        }

        String schemaName = resolveSchemaName(channelId, messageType, true);
        return getSchemaByName(schemaName, channelId);
    }

    /**
     * Gets the response schema for a channel and message type.
     *
     * @param channelId the channel ID
     * @param messageType the message type (e.g., MTI like "0210")
     * @return the resolved MessageSchema
     * @throws ChannelConfigException if channel or schema not found
     */
    public MessageSchema getResponseSchema(String channelId, String messageType) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            return handleUnknownChannel(channelId, messageType, false);
        }

        String schemaName = resolveSchemaName(channelId, messageType, false);
        return getSchemaByName(schemaName, channelId);
    }

    /**
     * Gets the default request schema for a channel.
     *
     * @param channelId the channel ID
     * @return the default request MessageSchema
     * @throws ChannelConfigException if channel not found or no default schema
     */
    public MessageSchema getDefaultRequestSchema(String channelId) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            throw ChannelConfigException.channelNotFound(channelId);
        }

        String schemaName = channel.getDefaultRequestSchema();
        if (schemaName == null || schemaName.isBlank()) {
            throw ChannelConfigException.invalidChannel(channelId, "no default request schema configured");
        }

        return getSchemaByName(schemaName, channelId);
    }

    /**
     * Gets the default response schema for a channel.
     *
     * @param channelId the channel ID
     * @return the default response MessageSchema
     * @throws ChannelConfigException if channel not found or no default schema
     */
    public MessageSchema getDefaultResponseSchema(String channelId) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            throw ChannelConfigException.channelNotFound(channelId);
        }

        String schemaName = channel.getDefaultResponseSchema();
        if (schemaName == null || schemaName.isBlank()) {
            throw ChannelConfigException.invalidChannel(channelId, "no default response schema configured");
        }

        return getSchemaByName(schemaName, channelId);
    }

    private String resolveSchemaName(String channelId, String messageType, boolean isRequest) {
        // Check for message-type-specific override
        Map<String, SchemaOverride> channelOverrides = schemaOverrides.get(channelId);
        if (channelOverrides != null && messageType != null) {
            SchemaOverride override = channelOverrides.get(messageType);
            if (override != null) {
                String overrideSchema = isRequest ? override.getRequest() : override.getResponse();
                if (overrideSchema != null && !overrideSchema.isBlank()) {
                    return overrideSchema;
                }
            }
        }

        // Fall back to default schema
        Channel channel = channels.get(channelId);
        return isRequest ? channel.getDefaultRequestSchema() : channel.getDefaultResponseSchema();
    }

    private MessageSchema getSchemaByName(String schemaName, String channelId) {
        if (schemaName == null || schemaName.isBlank()) {
            throw ChannelConfigException.invalidChannel(channelId, "schema name is null or empty");
        }

        Map<String, MessageSchema> schemaMap = JsonSchemaLoader.getSchemaMap();
        MessageSchema schema = schemaMap.get(schemaName);

        if (schema == null) {
            DefaultsConfig defaults = config != null ? config.getDefaults() : null;
            if (defaults != null && defaults.throwErrorForSchemaNotFound()) {
                throw ChannelConfigException.schemaNotFound(schemaName);
            }
            log.warn("Schema not found: {} for channel: {}", schemaName, channelId);
        }

        return schema;
    }

    private MessageSchema handleUnknownChannel(String channelId, String messageType, boolean isRequest) {
        DefaultsConfig defaults = config != null ? config.getDefaults() : null;

        if (defaults == null || defaults.throwErrorForUnknownChannel()) {
            throw ChannelConfigException.channelNotFound(channelId);
        }

        if (defaults.useFallbackForUnknownChannel()) {
            String fallback = defaults.getFallbackChannel();
            if (fallback != null && channels.containsKey(fallback)) {
                log.warn("Using fallback channel '{}' for unknown channel '{}'", fallback, channelId);
                return isRequest
                        ? getRequestSchema(fallback, messageType)
                        : getResponseSchema(fallback, messageType);
            }
        }

        throw ChannelConfigException.channelNotFound(channelId);
    }

    // ==================== Hot-Reload Support ====================

    /**
     * Enables file watching for automatic hot-reload.
     * When enabled, the configuration will be automatically reloaded
     * when the file is modified.
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
                Thread t = new Thread(r, "channel-config-watcher");
                t.setDaemon(true);
                return t;
            });

            watchExecutor.scheduleWithFixedDelay(this::checkForChanges, 5, 5, TimeUnit.SECONDS);
            watchEnabled = true;
            log.info("Hot-reload enabled for channel configuration");

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
     * Registers a subscriber for channel updates.
     * Uses WeakReference to prevent memory leaks.
     * If channels are already loaded, immediately notifies the new subscriber.
     *
     * @param subscriber the subscriber to register
     */
    public void registerSubscriber(ChannelSubscriber subscriber) {
        if (subscriber == null) {
            return;
        }

        boolean exists = subscribers.stream()
                .anyMatch(ref -> ref.get() == subscriber);

        if (!exists) {
            subscribers.add(new WeakReference<>(subscriber));
            log.debug("Registered channel subscriber: {}", subscriber.getClass().getSimpleName());
        }

        // Immediately notify if channels are loaded
        if (!channels.isEmpty()) {
            try {
                subscriber.onChannelsUpdated(getChannelMap());
            } catch (Exception e) {
                log.error("Failed to notify new subscriber: {}", e.getMessage());
            }
        }
    }

    /**
     * Unregisters a subscriber from receiving channel updates.
     *
     * @param subscriber the subscriber to unregister
     */
    public void unregisterSubscriber(ChannelSubscriber subscriber) {
        if (subscriber == null) {
            return;
        }
        subscribers.removeIf(ref -> {
            ChannelSubscriber s = ref.get();
            return s == null || s == subscriber;
        });
        log.debug("Unregistered channel subscriber: {}", subscriber.getClass().getSimpleName());
    }

    private void notifySubscribers() {
        Map<String, Channel> channelMap = getChannelMap();
        List<WeakReference<ChannelSubscriber>> toRemove = new ArrayList<>();

        for (WeakReference<ChannelSubscriber> ref : subscribers) {
            ChannelSubscriber subscriber = ref.get();
            if (subscriber == null) {
                toRemove.add(ref);
                continue;
            }
            try {
                subscriber.onChannelsUpdated(channelMap);
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

    /**
     * Gets an unmodifiable view of all channels.
     *
     * @return unmodifiable map of channelId to Channel
     */
    public Map<String, Channel> getChannelMap() {
        return Collections.unmodifiableMap(new HashMap<>(channels));
    }

    /**
     * Gets all channels as a collection.
     *
     * @return unmodifiable collection of all channels
     */
    public Collection<Channel> getAllChannels() {
        return Collections.unmodifiableCollection(channels.values());
    }

    // ==================== Runtime Registration ====================

    /**
     * Registers a new channel at runtime.
     * Useful for dynamically adding channels without configuration file changes.
     *
     * @param channel the channel to register
     * @throws ChannelConfigException if channel is invalid
     */
    public void registerChannel(Channel channel) {
        validateChannel(channel);
        channels.put(channel.getId(), channel);
        log.info("Registered channel at runtime: {}", channel.getId());
        notifySubscribers();
    }

    /**
     * Unregisters a channel at runtime.
     *
     * @param channelId the channel ID to unregister
     * @return the removed channel, or null if not found
     */
    public Channel unregisterChannel(String channelId) {
        Channel removed = channels.remove(channelId);
        if (removed != null) {
            schemaOverrides.remove(channelId);
            log.info("Unregistered channel: {}", channelId);
            notifySubscribers();
        }
        return removed;
    }

    /**
     * Updates an existing channel.
     *
     * @param channel the updated channel
     * @throws ChannelConfigException if channel doesn't exist or is invalid
     */
    public void updateChannel(Channel channel) {
        validateChannel(channel);
        if (!channels.containsKey(channel.getId())) {
            throw ChannelConfigException.channelNotFound(channel.getId());
        }
        channels.put(channel.getId(), channel);
        log.info("Updated channel: {}", channel.getId());
        notifySubscribers();
    }

    /**
     * Adds or updates a schema override for a channel.
     *
     * @param channelId the channel ID
     * @param messageType the message type (e.g., "0200")
     * @param override the schema override
     */
    public void addSchemaOverride(String channelId, String messageType, SchemaOverride override) {
        schemaOverrides
                .computeIfAbsent(channelId, k -> new ConcurrentHashMap<>())
                .put(messageType, override);
        log.debug("Added schema override for channel {} messageType {}", channelId, messageType);
    }

    // ==================== Cleanup ====================

    /**
     * Clears all channels and stops watching.
     * Useful for testing or reinitializing the registry.
     */
    public synchronized void clear() {
        disableHotReload();
        channels.clear();
        schemaOverrides.clear();
        config = null;
        configFilePath = null;
        configBasePath = null;
        log.info("Channel registry cleared");
    }

    /**
     * Gets the current configuration.
     *
     * @return the current ChannelSchemaConfig, or null if not loaded
     */
    public ChannelSchemaConfig getConfig() {
        return config;
    }

    /**
     * Gets the current configuration file path.
     *
     * @return the config file path, or null if loaded from JSON string
     */
    public String getConfigFilePath() {
        return configFilePath;
    }
}
