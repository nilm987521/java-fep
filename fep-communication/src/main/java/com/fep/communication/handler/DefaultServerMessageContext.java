package com.fep.communication.handler;

import com.fep.communication.server.FiscDualChannelServer;
import com.fep.message.generic.message.GenericMessage;
import com.fep.message.iso8583.Iso8583Message;
import lombok.Builder;
import lombok.Getter;

/**
 * Default implementation of ServerMessageContext.
 *
 * <p>支援兩種訊息格式：
 * <ul>
 *   <li>{@link Iso8583Message} - 傳統 ISO 8583 格式（向後相容）</li>
 *   <li>{@link GenericMessage} - 通用訊息格式（新架構建議使用）</li>
 * </ul>
 */
@Getter
@Builder
public class DefaultServerMessageContext implements ServerMessageHandler.ServerMessageContext {

    private final String channelId;
    private final String clientId;
    private final Iso8583Message message;
    private final GenericMessage genericMessage;
    private final FiscDualChannelServer server;

    @Override
    @Deprecated
    public boolean sendResponse(Iso8583Message response) {
        if (server == null) {
            return false;
        }
        return server.sendToClient(clientId, response);
    }

    @Override
    public boolean sendRawResponse(byte[] data) {
        if (server == null) {
            return false;
        }
        return server.sendRawToClient(clientId, data);
    }
}
