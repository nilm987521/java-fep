package com.fep.communication.handler;

import com.fep.communication.server.FiscDualChannelServer;
import com.fep.message.iso8583.Iso8583Message;

/**
 * Handler interface for processing messages received by FEP Server.
 *
 * <p>When FEP operates in SERVER mode (e.g., ATM_SERVER), it receives
 * incoming requests from clients (ATMs, POS terminals). This handler
 * processes those requests and returns responses.
 *
 * <p>Message flow:
 * <pre>
 *     ATM ──request──► FEP Server
 *                          │
 *                   ServerMessageHandler.handleMessage()
 *                          │
 *                          ├── Process locally, or
 *                          ├── Forward to FISC, or
 *                          └── Forward to CBS (Core Banking)
 *                          │
 *     ATM ◄──response── FEP Server
 * </pre>
 *
 * <p>Implementations can:
 * <ul>
 *   <li>Process messages locally (e.g., echo test, status inquiry)</li>
 *   <li>Forward to FISC and wait for response (e.g., interbank transfer)</li>
 *   <li>Forward to core banking system (e.g., on-us transactions)</li>
 * </ul>
 */
public interface ServerMessageHandler {

    /**
     * Handles an incoming message from a client.
     *
     * <p>This method is called when the server receives a message.
     * The implementation should process the message and optionally
     * send a response back using {@code server.sendToClient()}.
     *
     * @param context the message context containing all relevant information
     */
    void handleMessage(ServerMessageContext context);

    /**
     * Context object containing all information needed to process a message.
     */
    interface ServerMessageContext {
        /**
         * Gets the channel ID (e.g., "ATM_FISC_V1").
         */
        String getChannelId();

        /**
         * Gets the client ID (e.g., "127.0.0.1:12345").
         */
        String getClientId();

        /**
         * Gets the received message.
         */
        Iso8583Message getMessage();

        /**
         * Gets the server instance for sending responses.
         */
        FiscDualChannelServer getServer();

        /**
         * Sends a response back to the client.
         *
         * @param response the response message
         * @return true if sent successfully
         */
        boolean sendResponse(Iso8583Message response);
    }
}
