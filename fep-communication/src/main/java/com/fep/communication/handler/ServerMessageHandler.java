package com.fep.communication.handler;

import com.fep.communication.server.FiscDualChannelServer;
import com.fep.message.generic.message.GenericMessage;
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
 *
 * <p>訊息格式說明：
 * <ul>
 *   <li>{@code getMessage()} - 取得 Iso8583Message（向後相容）</li>
 *   <li>{@code getGenericMessage()} - 取得 GenericMessage（新架構建議使用）</li>
 *   <li>{@code sendRawResponse()} - 發送已編碼的 byte[] 回應</li>
 * </ul>
 */
public interface ServerMessageHandler {

    /**
     * Handles an incoming message from a client.
     *
     * <p>This method is called when the server receives a message.
     * The implementation should process the message and optionally
     * send a response back using {@code context.sendResponse()} or
     * {@code context.sendRawResponse()}.
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
         * Gets the received message as Iso8583Message.
         *
         * @deprecated 建議使用 {@link #getGenericMessage()} 取得通用訊息格式
         */
        @Deprecated
        Iso8583Message getMessage();

        /**
         * Gets the received message as GenericMessage.
         *
         * <p>GenericMessage 是通用訊息格式，包含 schema 定義的所有欄位。
         * 建議新的 Handler 使用此方法取得訊息。
         *
         * @return GenericMessage，如果來源不是 GenericMessage 則返回 null
         */
        GenericMessage getGenericMessage();

        /**
         * Gets the server instance for sending responses.
         */
        FiscDualChannelServer getServer();

        /**
         * Sends a response back to the client.
         *
         * @param response the response message
         * @return true if sent successfully
         * @deprecated 建議使用 {@link #sendRawResponse(byte[])} 發送已編碼的回應
         */
        @Deprecated
        boolean sendResponse(Iso8583Message response);

        /**
         * Sends a raw byte array response back to the client.
         *
         * <p>此方法用於發送已經編碼好的電文 byte[]，
         * 不經過 Netty pipeline 的 encoder。
         *
         * <p>適用於新架構：
         * <pre>
         * InternalMessage → MessageTransformer.toExternal() → GenericMessage
         *                → GenericMessageAssembler.assemble() → byte[]
         *                → sendRawResponse()
         * </pre>
         *
         * @param data the encoded message bytes
         * @return true if sent successfully
         */
        boolean sendRawResponse(byte[] data);
    }
}
