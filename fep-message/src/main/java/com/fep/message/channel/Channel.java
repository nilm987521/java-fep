package com.fep.message.channel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.Set;

/**
 * Represents a communication channel in the FEP system.
 * This is NOT an enum to allow dynamic registration of new channels
 * without code changes.
 *
 * <p>Channels are loaded from configuration files and can be:
 * <ul>
 *   <li>ATM channels (NCR, Diebold, Wincor, Hyosung, etc.)</li>
 *   <li>POS channels (Visa, MasterCard, etc.)</li>
 *   <li>Interbank channels (FISC)</li>
 *   <li>Core banking system channels (CBS)</li>
 *   <li>Mobile/Internet banking channels</li>
 * </ul>
 *
 * <p>Example configuration:
 * <pre>
 * {
 *   "id": "ATM_NCR_V1",
 *   "name": "NCR ATM Channel V1",
 *   "type": "ATM",
 *   "vendor": "NCR",
 *   "version": "1.0",
 *   "active": true,
 *   "defaultRequestSchema": "NCR NDC Protocol",
 *   "defaultResponseSchema": "NCR NDC Protocol"
 * }
 * </pre>
 */
@Data
@Builder
@EqualsAndHashCode(of = "id")
public class Channel {

    /**
     * Unique channel identifier.
     * Convention: {TYPE}_{VENDOR}_{VERSION} (e.g., "ATM_NCR_V1", "FISC_INTERBANK_V1")
     */
    @JsonProperty(required = true)
    private String id;

    /**
     * Human-readable channel name for display purposes.
     */
    private String name;

    /**
     * Channel description.
     */
    private String description;

    /**
     * Channel type category (ATM, POS, INTERBANK, CBS, MOBILE, API, BATCH, etc.)
     */
    @JsonProperty(required = true)
    private String type;

    /**
     * Vendor/manufacturer (NCR, Diebold, Wincor, FISC, INTERNAL, etc.)
     */
    private String vendor;

    /**
     * Protocol version.
     */
    @Builder.Default
    private String version = "1.0";

    /**
     * Whether this channel is currently active.
     * Inactive channels are skipped during schema resolution.
     */
    @Builder.Default
    private boolean active = true;

    /**
     * Associated schema names for request messages.
     * Supports multiple schemas for different message types.
     */
    private Set<String> requestSchemas;

    /**
     * Associated schema names for response messages.
     */
    private Set<String> responseSchemas;

    /**
     * Default request schema (primary schema for this channel).
     * Used when no message-type-specific override is defined.
     */
    private String defaultRequestSchema;

    /**
     * Default response schema.
     */
    private String defaultResponseSchema;

    /**
     * Channel-specific configuration properties.
     * (e.g., encoding defaults, timeout settings, MAC requirements)
     */
    private Map<String, String> properties;

    /**
     * Tags for grouping/filtering channels.
     * (e.g., "production", "test", "interbank", "emv")
     */
    private Set<String> tags;

    /**
     * Priority for routing decisions (lower = higher priority).
     */
    @Builder.Default
    private int priority = 100;

    /**
     * JSON creator for deserialization.
     */
    @JsonCreator
    public Channel(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("type") String type,
            @JsonProperty("vendor") String vendor,
            @JsonProperty("version") String version,
            @JsonProperty("active") Boolean active,
            @JsonProperty("requestSchemas") Set<String> requestSchemas,
            @JsonProperty("responseSchemas") Set<String> responseSchemas,
            @JsonProperty("defaultRequestSchema") String defaultRequestSchema,
            @JsonProperty("defaultResponseSchema") String defaultResponseSchema,
            @JsonProperty("properties") Map<String, String> properties,
            @JsonProperty("tags") Set<String> tags,
            @JsonProperty("priority") Integer priority) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.vendor = vendor;
        this.version = version != null ? version : "1.0";
        this.active = active != null ? active : true;
        this.requestSchemas = requestSchemas;
        this.responseSchemas = responseSchemas;
        this.defaultRequestSchema = defaultRequestSchema;
        this.defaultResponseSchema = defaultResponseSchema;
        this.properties = properties;
        this.tags = tags;
        this.priority = priority != null && priority > 0 ? priority : 100;
    }

    /**
     * Gets a property value with a default fallback.
     *
     * @param key the property key
     * @param defaultValue the default value if key not found
     * @return the property value or default
     */
    public String getProperty(String key, String defaultValue) {
        if (properties == null) {
            return defaultValue;
        }
        return properties.getOrDefault(key, defaultValue);
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
     * Checks if this channel has a specific tag.
     *
     * @param tag the tag to check
     * @return true if the channel has the tag
     */
    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }

    /**
     * Checks if this channel matches a given type.
     *
     * @param channelType the type to check (case-insensitive)
     * @return true if the channel type matches
     */
    public boolean isType(String channelType) {
        return type != null && type.equalsIgnoreCase(channelType);
    }

    /**
     * Checks if this channel is from a given vendor.
     *
     * @param vendorName the vendor to check (case-insensitive)
     * @return true if the vendor matches
     */
    public boolean isVendor(String vendorName) {
        return vendor != null && vendor.equalsIgnoreCase(vendorName);
    }
}
