package com.fep.communication.codec;

import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.parser.GenericMessageAssembler;
import com.fep.message.generic.schema.MessageSchema;
import com.fep.message.iso8583.Iso8583Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty encoder for GenericMessage using schema-based assembly.
 *
 * <p>This encoder uses GenericMessageAssembler to assemble messages according to
 * the provided MessageSchema, supporting flexible message formats including
 * configurable length field encoding (ASCII, BCD, BINARY).
 *
 * <p>This encoder can handle both GenericMessage and Iso8583Message objects.
 * Iso8583Message objects are converted to GenericMessage before encoding.
 */
@Slf4j
public class GenericMessageEncoder extends MessageToByteEncoder<Object> {

    private final MessageSchema schema;
    private final GenericMessageAssembler assembler;

    /**
     * Creates an encoder with the specified schema.
     *
     * @param schema the message schema defining the format
     */
    public GenericMessageEncoder(MessageSchema schema) {
        this.schema = schema;
        this.assembler = new GenericMessageAssembler();

        log.info("GenericMessageEncoder initialized: schema={}", schema.getName());
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof GenericMessage || msg instanceof Iso8583Message;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        GenericMessage genericMessage;

        if (msg instanceof GenericMessage) {
            genericMessage = (GenericMessage) msg;
        } else if (msg instanceof Iso8583Message) {
            genericMessage = convertToGenericMessage((Iso8583Message) msg);
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + msg.getClass().getName());
        }

        String mti = genericMessage.getFieldAsString("mti");
        log.debug("Encoding message: schema={}, MTI={}", schema.getName(), mti);

        // Assemble message (includes length prefix based on schema config)
        byte[] data = assembler.assemble(genericMessage);

        // Write to output buffer
        out.writeBytes(data);

        log.debug("Encoded message: {} bytes, MTI={}", data.length, mti);
    }

    /**
     * Converts an Iso8583Message to GenericMessage.
     */
    private GenericMessage convertToGenericMessage(Iso8583Message iso8583) {
        GenericMessage generic = new GenericMessage(schema);

        // Set MTI as a field
        generic.setField("mti", iso8583.getMti());

        // Copy all fields
        for (int fieldNum : iso8583.getFieldNumbers()) {
            Object value = iso8583.getField(fieldNum);
            if (value != null) {
                // Map ISO 8583 field numbers to schema field names
                String fieldName = mapFieldNumberToName(fieldNum);
                if (fieldName != null) {
                    generic.setField(fieldName, value.toString());
                }
            }
        }

        return generic;
    }

    /**
     * Maps ISO 8583 field number to schema field name.
     * This is a simplified mapping - in production, this should be configurable.
     */
    private String mapFieldNumberToName(int fieldNum) {
        return switch (fieldNum) {
            case 2 -> "pan";
            case 3 -> "processingCode";
            case 4 -> "amount";
            case 11 -> "stan";
            case 12 -> "localTime";
            case 13 -> "localDate";
            case 14 -> "expirationDate";
            case 22 -> "posEntryMode";
            case 23 -> "cardSequenceNumber";
            case 32 -> "acquirerInstitutionId";
            case 35 -> "track2Data";
            case 37 -> "retrievalReferenceNumber";
            case 38 -> "authorizationCode";
            case 39 -> "responseCode";
            case 41 -> "terminalId";
            case 42 -> "merchantId";
            case 43 -> "merchantName";
            case 48 -> "additionalData";
            case 49 -> "currencyCode";
            case 52 -> "pinBlock";
            case 54 -> "additionalAmounts";
            case 55 -> "emvData";
            case 60 -> "reservedPrivate60";
            case 61 -> "reservedPrivate61";
            case 62 -> "reservedPrivate62";
            case 63 -> "reservedPrivate63";
            case 100 -> "receivingInstitutionId";
            case 102 -> "accountIdFrom";
            case 103 -> "accountIdTo";
            case 120 -> "destAccount";
            default -> null;
        };
    }
}
