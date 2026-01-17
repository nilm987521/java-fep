package com.fep.message.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Default configuration for channel-schema resolution.
 * Defines behavior when unknown channels or missing schemas are encountered.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefaultsConfig {

    /**
     * Fallback channel ID to use when the requested channel is unknown.
     */
    private String fallbackChannel;

    /**
     * Behavior when an unknown channel is requested.
     * <ul>
     *   <li>USE_FALLBACK - Use the fallback channel</li>
     *   <li>THROW_ERROR - Throw an exception</li>
     * </ul>
     */
    private String unknownChannelBehavior = "THROW_ERROR";

    /**
     * Behavior when a schema is not found.
     * <ul>
     *   <li>THROW_ERROR - Throw an exception</li>
     *   <li>RETURN_NULL - Return null</li>
     * </ul>
     */
    private String schemaNotFoundBehavior = "THROW_ERROR";

    /**
     * Checks if fallback should be used for unknown channels.
     *
     * @return true if USE_FALLBACK behavior is configured
     */
    public boolean useFallbackForUnknownChannel() {
        return "USE_FALLBACK".equalsIgnoreCase(unknownChannelBehavior);
    }

    /**
     * Checks if errors should be thrown for unknown channels.
     *
     * @return true if THROW_ERROR behavior is configured
     */
    public boolean throwErrorForUnknownChannel() {
        return "THROW_ERROR".equalsIgnoreCase(unknownChannelBehavior);
    }

    /**
     * Checks if errors should be thrown when schema is not found.
     *
     * @return true if THROW_ERROR behavior is configured
     */
    public boolean throwErrorForSchemaNotFound() {
        return "THROW_ERROR".equalsIgnoreCase(schemaNotFoundBehavior);
    }
}
