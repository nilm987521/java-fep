package com.fep.message.channel;

/**
 * Exception thrown when there is an error in channel configuration
 * or channel-schema resolution.
 */
public class ChannelConfigException extends RuntimeException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param message the error message
     */
    public ChannelConfigException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public ChannelConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception for a missing channel.
     *
     * @param channelId the channel ID that was not found
     * @return a new ChannelConfigException
     */
    public static ChannelConfigException channelNotFound(String channelId) {
        return new ChannelConfigException("Channel not found: " + channelId);
    }

    /**
     * Creates an exception for a missing schema.
     *
     * @param schemaName the schema name that was not found
     * @return a new ChannelConfigException
     */
    public static ChannelConfigException schemaNotFound(String schemaName) {
        return new ChannelConfigException("Schema not found: " + schemaName);
    }

    /**
     * Creates an exception for an invalid channel configuration.
     *
     * @param channelId the channel ID with invalid configuration
     * @param reason the reason for invalidity
     * @return a new ChannelConfigException
     */
    public static ChannelConfigException invalidChannel(String channelId, String reason) {
        return new ChannelConfigException("Invalid channel '" + channelId + "': " + reason);
    }

    /**
     * Creates an exception for a configuration file error.
     *
     * @param filePath the configuration file path
     * @param reason the error reason
     * @return a new ChannelConfigException
     */
    public static ChannelConfigException configFileError(String filePath, String reason) {
        return new ChannelConfigException("Error loading config file '" + filePath + "': " + reason);
    }
}
