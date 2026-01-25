package com.fep.message.channel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * Represents a unified channel connection configuration.
 * Combines logical channel (schema mapping) with physical connection (TCP/IP settings).
 *
 * <p>This class bridges the gap between:
 * <ul>
 *   <li>Logical layer: Channel → Schema mapping (from ChannelSchemaRegistry)</li>
 *   <li>Physical layer: TCP/IP connection settings (from ConnectionProfile)</li>
 * </ul>
 *
 * <p>Example configuration:
 * <pre>
 * {
 *   "channelId": "FISC_INTERBANK_V1",
 *   "connectionProfile": "FISC_PRIMARY",
 *   "schemas": {
 *     "0200": "fisc_0200.json",
 *     "0210": "fisc_0210.json",
 *     "0400": "fisc_0400.json"
 *   },
 *   "properties": {
 *     "macRequired": "true",
 *     "encoding": "BIG5"
 *   }
 * }
 * </pre>
 */
@Builder
@EqualsAndHashCode(of = "channelId")
public class ChannelConnection {

    /**
     * Unique channel identifier.
     * Should match the channelId used in ChannelSchemaRegistry.
     */
    private String channelId;

    /**
     * Reference to a ConnectionProfile by its profileId.
     * This links the logical channel to physical connection settings.
     */
    private String connectionProfileId;

    /**
     * The resolved ConnectionProfile instance.
     * This is populated after loading and resolving references.
     */
    private ConnectionProfile resolvedConnectionProfile;

    /**
     * The associated Channel metadata from ChannelSchemaRegistry.
     * This provides access to schema information.
     */
    private Channel channel;

    /**
     * MTI to Schema file mapping.
     * Maps message type indicators to their schema definition files.
     * Example: {"0200": "fisc_0200.json", "0210": "fisc_0210.json"}
     */
    private Map<String, String> schemas;

    /**
     * Channel-specific properties.
     * These override or extend the properties from Channel and ConnectionProfile.
     */
    private Map<String, String> properties;

    /**
     * Human-readable description for this channel connection.
     */
    private String description;

    /**
     * Whether this channel connection is active.
     * Inactive connections are skipped during connection initialization.
     */
    @Builder.Default
    private boolean active = true;

    /**
     * Priority for this channel connection.
     * Lower values indicate higher priority (used for failover ordering).
     */
    @Builder.Default
    private int priority = 100;

    /**
     * JSON creator for deserialization.
     */
    @JsonCreator
    public ChannelConnection(
            @JsonProperty("channelId") String channelId,
            @JsonProperty("connectionProfile") String connectionProfileId,
            @JsonProperty("resolvedConnectionProfile") ConnectionProfile resolvedConnectionProfile,
            @JsonProperty("channel") Channel channel,
            @JsonProperty("schemas") Map<String, String> schemas,
            @JsonProperty("properties") Map<String, String> properties,
            @JsonProperty("description") String description,
            @JsonProperty("active") Boolean active,
            @JsonProperty("priority") Integer priority) {
        this.channelId = channelId;
        this.connectionProfileId = connectionProfileId;
        this.resolvedConnectionProfile = resolvedConnectionProfile;
        this.channel = channel;
        this.schemas = schemas;
        this.properties = properties;
        this.description = description;
        this.active = active == null || active;
        this.priority = priority != null && priority > 0 ? priority : 100;
    }

    // ==================== Getters and Setters ====================

    @JsonProperty("channelId")
    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    /**
     * Gets the connection profile ID reference.
     * This is the JSON property "connectionProfile" that references a profile by its ID.
     *
     * @return the profile ID reference
     */
    @JsonProperty("connectionProfile")
    public String getConnectionProfileId() {
        return connectionProfileId;
    }

    public void setConnectionProfileId(String connectionProfileId) {
        this.connectionProfileId = connectionProfileId;
    }

    @JsonIgnore
    public ConnectionProfile getResolvedConnectionProfile() {
        return resolvedConnectionProfile;
    }

    public void setResolvedConnectionProfile(ConnectionProfile resolvedConnectionProfile) {
        this.resolvedConnectionProfile = resolvedConnectionProfile;
    }

    @JsonIgnore
    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    @JsonProperty("schemas")
    public Map<String, String> getSchemas() {
        return schemas;
    }

    public void setSchemas(Map<String, String> schemas) {
        this.schemas = schemas;
    }

    @JsonProperty("properties")
    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("active")
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @JsonProperty("priority")
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    // ==================== Business Methods ====================

    /**
     * Gets the schema file name for a specific message type.
     *
     * @param mti the message type indicator (e.g., "0200")
     * @return the schema file name, or null if not found
     */
    public String getSchema(String mti) {
        if (schemas != null && schemas.containsKey(mti)) {
            return schemas.get(mti);
        }
        // Fall back to channel's default schema
        if (channel != null) {
            return channel.getDefaultRequestSchema();
        }
        return null;
    }

    /**
     * Checks if MAC is required for this channel.
     *
     * @return true if MAC is required
     */
    public boolean isMacRequired() {
        String value = getProperty("macRequired");
        return "true".equalsIgnoreCase(value);
    }

    /**
     * Gets the encoding for this channel.
     * Defaults to "UTF-8" if not specified.
     *
     * @return the encoding name
     */
    public String getEncoding() {
        return getProperty("encoding", "UTF-8");
    }

    /**
     * Gets the institution ID for this channel.
     *
     * @return the institution ID, or null if not set
     */
    public String getInstitutionId() {
        return getProperty("institutionId");
    }

    /**
     * Gets a property value with cascading lookup.
     * Order: this.properties → channel.properties → connectionProfile.properties
     *
     * @param key the property key
     * @param defaultValue the default value if not found
     * @return the property value or default
     */
    public String getProperty(String key, String defaultValue) {
        // Check local properties first
        if (properties != null && properties.containsKey(key)) {
            return properties.get(key);
        }
        // Check channel properties
        if (channel != null) {
            String value = channel.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        // Check connection profile properties
        if (resolvedConnectionProfile != null) {
            String value = resolvedConnectionProfile.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        return defaultValue;
    }

    /**
     * Gets a property value, returning null if not found.
     *
     * @param key the property key
     * @return the property value or null
     */
    public String getProperty(String key) {
        return getProperty(key, null);
    }

    /**
     * Gets the connection profile for this channel.
     * Returns the resolved instance if available.
     *
     * Note: This method is named differently from the JSON property to avoid Jackson conflicts.
     *
     * @return the ConnectionProfile, or null if not resolved
     */
    @JsonIgnore
    public ConnectionProfile getConnectionProfile() {
        return resolvedConnectionProfile;
    }

    /**
     * Gets the host from the connection profile.
     *
     * @return the host, or null if profile not resolved
     */
    @JsonIgnore
    public String getHost() {
        return resolvedConnectionProfile != null ? resolvedConnectionProfile.getHost() : null;
    }

    /**
     * Gets the send port from the connection profile.
     *
     * @return the send port, or 0 if profile not resolved
     */
    @JsonIgnore
    public int getSendPort() {
        return resolvedConnectionProfile != null ? resolvedConnectionProfile.getSendPort() : 0;
    }

    /**
     * Gets the receive port from the connection profile.
     *
     * @return the receive port, or 0 if profile not resolved
     */
    @JsonIgnore
    public int getReceivePort() {
        return resolvedConnectionProfile != null
                ? resolvedConnectionProfile.getEffectiveReceivePort() : 0;
    }

    /**
     * Checks if this is a dual-channel connection.
     *
     * @return true if send and receive ports are different
     */
    @JsonIgnore
    public boolean isDualChannel() {
        return resolvedConnectionProfile != null && resolvedConnectionProfile.isDualChannel();
    }

    /**
     * Gets the response timeout from the connection profile.
     *
     * @return the response timeout in milliseconds, or 30000 as default
     */
    @JsonIgnore
    public int getResponseTimeout() {
        return resolvedConnectionProfile != null
                ? resolvedConnectionProfile.getResponseTimeout() : 30000;
    }

    /**
     * Gets the heartbeat interval from the connection profile.
     *
     * @return the heartbeat interval in milliseconds, or 60000 as default
     */
    @JsonIgnore
    public int getHeartbeatInterval() {
        return resolvedConnectionProfile != null
                ? resolvedConnectionProfile.getHeartbeatInterval() : 60000;
    }

    /**
     * Resolves the connection profile reference.
     * Called by ChannelConnectionRegistry after loading profiles.
     *
     * @param profile the resolved ConnectionProfile
     */
    public void resolveConnectionProfile(ConnectionProfile profile) {
        this.resolvedConnectionProfile = profile;
    }

    /**
     * Associates the Channel metadata.
     * Called by ChannelConnectionRegistry after loading channels.
     *
     * @param channel the associated Channel
     */
    public void associateChannel(Channel channel) {
        this.channel = channel;
    }

    /**
     * Validates this channel connection.
     *
     * @throws ChannelConfigException if validation fails
     */
    public void validate() {
        if (channelId == null || channelId.isBlank()) {
            throw new ChannelConfigException("ChannelConnection must have a channelId");
        }
        if (connectionProfileId == null || connectionProfileId.isBlank()) {
            throw new ChannelConfigException(
                    "ChannelConnection '" + channelId + "' must have a connectionProfile reference");
        }
    }

    /**
     * Checks if this channel connection is fully resolved.
     * A fully resolved connection has both the profile and channel associated.
     *
     * @return true if fully resolved
     */
    @JsonIgnore
    public boolean isFullyResolved() {
        return resolvedConnectionProfile != null;
    }

    @Override
    public String toString() {
        return "ChannelConnection{" +
                "channelId='" + channelId + '\'' +
                ", connectionProfileId='" + connectionProfileId + '\'' +
                ", active=" + active +
                ", priority=" + priority +
                ", resolved=" + isFullyResolved() +
                '}';
    }
}
