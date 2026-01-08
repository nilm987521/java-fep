package com.fep.message.iso8583.parser;

import com.fep.message.iso8583.Iso8583Message;
import io.netty.buffer.ByteBuf;

/**
 * Interface for assembling ISO 8583 messages.
 */
public interface MessageAssembler {

    /**
     * Assembles an ISO 8583 message into bytes.
     *
     * @param message the message to assemble
     * @param buffer the output buffer
     */
    void assemble(Iso8583Message message, ByteBuf buffer);

    /**
     * Assembles an ISO 8583 message into a byte array.
     *
     * @param message the message to assemble
     * @return the assembled message bytes
     */
    byte[] assemble(Iso8583Message message);
}
