package com.fep.communication.codec;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.parser.FiscMessageAssembler;
import com.fep.message.iso8583.parser.MessageAssembler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty encoder for ISO 8583 messages.
 * Converts Iso8583Message objects to bytes for transmission.
 */
@Slf4j
public class FiscMessageEncoder extends MessageToByteEncoder<Iso8583Message> {

    private final MessageAssembler assembler;

    public FiscMessageEncoder() {
        this.assembler = new FiscMessageAssembler();
    }

    public FiscMessageEncoder(MessageAssembler assembler) {
        this.assembler = assembler;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Iso8583Message msg, ByteBuf out) throws Exception {
        log.debug("Encoding message: MTI={}, traceId={}", msg.getMti(), msg.getTraceId());

        byte[] data = assembler.assemble(msg);
        out.writeBytes(data);

        log.trace("Encoded {} bytes for MTI={}", data.length, msg.getMti());
    }
}
