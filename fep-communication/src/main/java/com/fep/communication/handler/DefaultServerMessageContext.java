package com.fep.communication.handler;

import com.fep.communication.server.FiscDualChannelServer;
import com.fep.message.iso8583.Iso8583Message;
import lombok.Builder;
import lombok.Getter;

/**
 * Default implementation of ServerMessageContext.
 */
@Getter
@Builder
public class DefaultServerMessageContext implements ServerMessageHandler.ServerMessageContext {

    private final String channelId;
    private final String clientId;
    private final Iso8583Message message;
    private final FiscDualChannelServer server;

    @Override
    public boolean sendResponse(Iso8583Message response) {
        if (server == null) {
            return false;
        }
        return server.sendToClient(clientId, response);
    }
}
