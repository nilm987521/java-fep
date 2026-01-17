package com.fep.message.channel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Root configuration object for channel-schema mapping.
 * Represents the structure of the channel-schema-mapping.json file.
 *
 * <p>Example configuration:
 * <pre>
 * {
 *   "$schema": "fep-channel-schema-mapping-v1",
 *   "version": "1.0.0",
 *   "schemaFiles": [
 *     { "path": "schemas/fisc-schemas.json" }
 *   ],
 *   "channels": [
 *     { "id": "ATM_NCR_V1", ... }
 *   ],
 *   "schemaOverrides": {
 *     "FISC_INTERBANK_V1": {
 *       "0400": { "request": "FISC Reversal", "response": "FISC Reversal" }
 *     }
 *   },
 *   "defaults": {
 *     "fallbackChannel": "FISC_INTERBANK_V1",
 *     "unknownChannelBehavior": "USE_FALLBACK"
 *   }
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSchemaConfig {

    /**
     * Schema identifier for the configuration file format.
     */
    @JsonProperty("$schema")
    private String schema;

    /**
     * Configuration version.
     */
    private String version;

    /**
     * Last modification timestamp.
     */
    private String lastModified;

    /**
     * List of schema files to load.
     */
    private List<SchemaFileReference> schemaFiles;

    /**
     * List of channel definitions.
     */
    private List<Channel> channels;

    /**
     * Message-type-specific schema overrides.
     * Map structure: channelId -> (messageType -> SchemaOverride)
     */
    private Map<String, Map<String, SchemaOverride>> schemaOverrides;

    /**
     * Default configuration for unknown channels and missing schemas.
     */
    private DefaultsConfig defaults;
}
