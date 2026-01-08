package com.fep.message.iso8583.parser;

import com.fep.message.iso8583.Iso8583Message;
import io.netty.buffer.ByteBuf;

/**
 * Interface for parsing ISO 8583 messages.
 */
public interface MessageParser {

    /**
     * Parses an ISO 8583 message from a byte buffer.
     *
     * @param buffer the input buffer containing the message
     * @return the parsed message
     */
    Iso8583Message parse(ByteBuf buffer);

    /**
     * Parses an ISO 8583 message from a byte array.
     *
     * @param data the message bytes
     * @return the parsed message
     */
    Iso8583Message parse(byte[] data);
}
