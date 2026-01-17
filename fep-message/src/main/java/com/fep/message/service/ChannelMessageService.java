package com.fep.message.service;

import com.fep.message.channel.Channel;
import com.fep.message.channel.ChannelConfigException;
import com.fep.message.channel.ChannelSchemaRegistry;
import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.parser.GenericMessageAssembler;
import com.fep.message.generic.parser.GenericMessageParser;
import com.fep.message.generic.schema.MessageSchema;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Unified message processing service that integrates ChannelSchemaRegistry
 * with GenericMessageParser and GenericMessageAssembler.
 *
 * <p>This service provides a facade for parsing and assembling messages
 * based on channel configuration, automatically selecting the appropriate
 * schema for each channel and message type (MTI).
 *
 * <p>Usage example:
 * <pre>
 * // Parse incoming message
 * GenericMessage message = channelMessageService.parseMessage("ATM_FISC_V1", "0210", rawBytes);
 *
 * // Assemble outgoing message
 * byte[] data = channelMessageService.assembleMessage("ATM_FISC_V1", "0200", message);
 * </pre>
 */
@Slf4j
@Service
public class ChannelMessageService {

    private final ChannelSchemaRegistry registry;
    private final GenericMessageParser parser;
    private final GenericMessageAssembler assembler;

    /**
     * Creates a new ChannelMessageService.
     * Uses the singleton ChannelSchemaRegistry instance.
     */
    public ChannelMessageService() {
        this.registry = ChannelSchemaRegistry.getInstance();
        this.parser = new GenericMessageParser();
        this.assembler = new GenericMessageAssembler();
    }

    /**
     * Creates a new ChannelMessageService with a custom registry.
     * Useful for testing.
     *
     * @param registry the channel schema registry to use
     */
    public ChannelMessageService(ChannelSchemaRegistry registry) {
        this.registry = registry;
        this.parser = new GenericMessageParser();
        this.assembler = new GenericMessageAssembler();
    }

    // ==================== Parsing Methods ====================

    /**
     * Parses a byte array into a GenericMessage using the schema
     * configured for the specified channel and message type.
     *
     * @param channelId the channel ID (e.g., "ATM_FISC_V1")
     * @param mti       the message type indicator (e.g., "0200", "0210")
     * @param data      the raw message bytes
     * @return the parsed GenericMessage
     * @throws ChannelConfigException if channel or schema not found
     */
    public GenericMessage parseMessage(String channelId, String mti, byte[] data) {
        return parseMessage(channelId, mti, data, false);
    }

    /**
     * Parses a byte array into a GenericMessage with option to skip length field.
     *
     * @param channelId       the channel ID
     * @param mti             the message type indicator
     * @param data            the raw message bytes
     * @param skipLengthField if true, skip parsing the length field
     * @return the parsed GenericMessage
     * @throws ChannelConfigException if channel or schema not found
     */
    public GenericMessage parseMessage(String channelId, String mti, byte[] data, boolean skipLengthField) {
        MessageSchema schema = resolveSchema(channelId, mti, false);
        log.debug("Parsing message for channel [{}] mti [{}] using schema [{}]",
                channelId, mti, schema.getName());
        return parser.parse(data, schema, skipLengthField);
    }

    /**
     * Parses a ByteBuf into a GenericMessage using the schema
     * configured for the specified channel and message type.
     *
     * @param channelId the channel ID
     * @param mti       the message type indicator
     * @param buffer    the input buffer
     * @return the parsed GenericMessage
     * @throws ChannelConfigException if channel or schema not found
     */
    public GenericMessage parseMessage(String channelId, String mti, ByteBuf buffer) {
        return parseMessage(channelId, mti, buffer, false);
    }

    /**
     * Parses a ByteBuf into a GenericMessage with option to skip length field.
     *
     * @param channelId       the channel ID
     * @param mti             the message type indicator
     * @param buffer          the input buffer
     * @param skipLengthField if true, skip parsing the length field
     * @return the parsed GenericMessage
     * @throws ChannelConfigException if channel or schema not found
     */
    public GenericMessage parseMessage(String channelId, String mti, ByteBuf buffer, boolean skipLengthField) {
        MessageSchema schema = resolveSchema(channelId, mti, false);
        log.debug("Parsing message for channel [{}] mti [{}] using schema [{}]",
                channelId, mti, schema.getName());
        return parser.parse(buffer, schema, skipLengthField);
    }

    /**
     * Parses a request message using the request schema.
     *
     * @param channelId the channel ID
     * @param mti       the message type indicator
     * @param data      the raw message bytes
     * @return the parsed GenericMessage
     */
    public GenericMessage parseRequestMessage(String channelId, String mti, byte[] data) {
        return parseRequestMessage(channelId, mti, data, false);
    }

    /**
     * Parses a request message using the request schema with option to skip length field.
     *
     * @param channelId       the channel ID
     * @param mti             the message type indicator
     * @param data            the raw message bytes
     * @param skipLengthField if true, skip parsing the length field
     * @return the parsed GenericMessage
     */
    public GenericMessage parseRequestMessage(String channelId, String mti, byte[] data, boolean skipLengthField) {
        MessageSchema schema = resolveSchema(channelId, mti, true);
        log.debug("Parsing request message for channel [{}] mti [{}] using schema [{}]",
                channelId, mti, schema.getName());
        return parser.parse(data, schema, skipLengthField);
    }

    /**
     * Parses a response message using the response schema.
     *
     * @param channelId the channel ID
     * @param mti       the message type indicator
     * @param data      the raw message bytes
     * @return the parsed GenericMessage
     */
    public GenericMessage parseResponseMessage(String channelId, String mti, byte[] data) {
        return parseResponseMessage(channelId, mti, data, false);
    }

    /**
     * Parses a response message using the response schema with option to skip length field.
     *
     * @param channelId       the channel ID
     * @param mti             the message type indicator
     * @param data            the raw message bytes
     * @param skipLengthField if true, skip parsing the length field
     * @return the parsed GenericMessage
     */
    public GenericMessage parseResponseMessage(String channelId, String mti, byte[] data, boolean skipLengthField) {
        MessageSchema schema = resolveSchema(channelId, mti, false);
        log.debug("Parsing response message for channel [{}] mti [{}] using schema [{}]",
                channelId, mti, schema.getName());
        return parser.parse(data, schema, skipLengthField);
    }

    // ==================== Assembly Methods ====================

    /**
     * Assembles a GenericMessage into a byte array using the schema
     * configured for the specified channel.
     *
     * @param channelId the channel ID
     * @param mti       the message type indicator (used for schema override resolution)
     * @param message   the message to assemble
     * @return the assembled bytes
     */
    public byte[] assembleMessage(String channelId, String mti, GenericMessage message) {
        log.debug("Assembling message for channel [{}] mti [{}] using schema [{}]",
                channelId, mti, message.getSchema().getName());
        return assembler.assemble(message);
    }

    /**
     * Assembles a GenericMessage into a ByteBuf.
     *
     * @param channelId the channel ID
     * @param mti       the message type indicator
     * @param message   the message to assemble
     * @return the assembled ByteBuf (caller is responsible for releasing)
     */
    public ByteBuf assembleToBuffer(String channelId, String mti, GenericMessage message) {
        byte[] data = assembleMessage(channelId, mti, message);
        return Unpooled.wrappedBuffer(data);
    }

    /**
     * Creates a new message with the request schema for the specified channel.
     *
     * @param channelId the channel ID
     * @param mti       the message type indicator
     * @return a new GenericMessage configured with the request schema
     */
    public GenericMessage createRequestMessage(String channelId, String mti) {
        MessageSchema schema = resolveSchema(channelId, mti, true);
        return new GenericMessage(schema);
    }

    /**
     * Creates a new message with the response schema for the specified channel.
     *
     * @param channelId the channel ID
     * @param mti       the message type indicator
     * @return a new GenericMessage configured with the response schema
     */
    public GenericMessage createResponseMessage(String channelId, String mti) {
        MessageSchema schema = resolveSchema(channelId, mti, false);
        return new GenericMessage(schema);
    }

    // ==================== Query Methods ====================

    /**
     * Gets a channel by ID.
     *
     * @param channelId the channel ID
     * @return Optional containing the channel, or empty if not found
     */
    public Optional<Channel> getChannel(String channelId) {
        return registry.getChannel(channelId);
    }

    /**
     * Gets a channel by ID, throwing exception if not found.
     *
     * @param channelId the channel ID
     * @return the channel
     * @throws ChannelConfigException if channel not found
     */
    public Channel getChannelOrThrow(String channelId) {
        return registry.getChannel(channelId)
                .orElseThrow(() -> ChannelConfigException.channelNotFound(channelId));
    }

    /**
     * Checks if a channel exists.
     *
     * @param channelId the channel ID
     * @return true if channel exists
     */
    public boolean hasChannel(String channelId) {
        return registry.hasChannel(channelId);
    }

    /**
     * Gets the request schema for a channel and message type.
     *
     * @param channelId the channel ID
     * @param mti       the message type indicator
     * @return the resolved MessageSchema
     * @throws ChannelConfigException if channel or schema not found
     */
    public MessageSchema getRequestSchema(String channelId, String mti) {
        return registry.getRequestSchema(channelId, mti);
    }

    /**
     * Gets the response schema for a channel and message type.
     *
     * @param channelId the channel ID
     * @param mti       the message type indicator
     * @return the resolved MessageSchema
     * @throws ChannelConfigException if channel or schema not found
     */
    public MessageSchema getResponseSchema(String channelId, String mti) {
        return registry.getResponseSchema(channelId, mti);
    }

    /**
     * Gets the default request schema for a channel.
     *
     * @param channelId the channel ID
     * @return the default request MessageSchema
     * @throws ChannelConfigException if channel not found or no default schema
     */
    public MessageSchema getDefaultRequestSchema(String channelId) {
        return registry.getDefaultRequestSchema(channelId);
    }

    /**
     * Gets the default response schema for a channel.
     *
     * @param channelId the channel ID
     * @return the default response MessageSchema
     * @throws ChannelConfigException if channel not found or no default schema
     */
    public MessageSchema getDefaultResponseSchema(String channelId) {
        return registry.getDefaultResponseSchema(channelId);
    }

    /**
     * Gets a channel property value.
     *
     * @param channelId    the channel ID
     * @param propertyName the property name
     * @return the property value, or null if not found
     */
    public String getChannelProperty(String channelId, String propertyName) {
        return getChannel(channelId)
                .map(c -> c.getProperty(propertyName))
                .orElse(null);
    }

    /**
     * Checks if MAC is required for a channel.
     *
     * @param channelId the channel ID
     * @return true if MAC is required
     */
    public boolean isMacRequired(String channelId) {
        return "true".equalsIgnoreCase(getChannelProperty(channelId, "macRequired"));
    }

    /**
     * Gets the encoding configured for a channel.
     *
     * @param channelId the channel ID
     * @return the encoding (e.g., "ASCII", "EBCDIC"), or "ASCII" as default
     */
    public String getChannelEncoding(String channelId) {
        String encoding = getChannelProperty(channelId, "encoding");
        return encoding != null ? encoding : "ASCII";
    }

    // ==================== Internal Methods ====================

    /**
     * Resolves the schema for a channel and message type.
     *
     * @param channelId the channel ID
     * @param mti       the message type indicator
     * @param isRequest true for request schema, false for response schema
     * @return the resolved MessageSchema
     * @throws ChannelConfigException if channel or schema not found
     */
    private MessageSchema resolveSchema(String channelId, String mti, boolean isRequest) {
        return isRequest
                ? registry.getRequestSchema(channelId, mti)
                : registry.getResponseSchema(channelId, mti);
    }

    /**
     * Gets the underlying ChannelSchemaRegistry.
     *
     * @return the registry instance
     */
    public ChannelSchemaRegistry getRegistry() {
        return registry;
    }
}
