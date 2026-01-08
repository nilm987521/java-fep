package com.fep.communication.handler;

import com.fep.communication.client.ChannelRole;
import com.fep.communication.client.ConnectionListener;
import com.fep.communication.client.ConnectionState;
import com.fep.communication.manager.PendingRequestManager;
import com.fep.message.iso8583.Iso8583Message;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Netty handler for the Receive Channel in dual-channel FISC architecture.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Receive all incoming messages (responses)</li>
 *   <li>Match responses to pending requests via PendingRequestManager</li>
 *   <li>Handle unsolicited messages from FISC</li>
 *   <li>Track connection state</li>
 * </ul>
 */
@Slf4j
public class ReceiveChannelHandler extends ChannelDuplexHandler {

    private final String connectionName;
    private final PendingRequestManager pendingRequestManager;
    private final ConnectionListener listener;
    private final Consumer<ConnectionState> stateCallback;

    /** Callback for unsolicited messages */
    private final BiConsumer<String, Iso8583Message> unsolicitedMessageHandler;

    @Getter
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    /** Statistics: messages received */
    private final AtomicLong messagesReceived = new AtomicLong(0);

    /** Statistics: messages matched */
    private final AtomicLong messagesMatched = new AtomicLong(0);

    /** Statistics: unsolicited messages */
    private final AtomicLong unsolicitedMessages = new AtomicLong(0);

    /** Channel role identifier */
    @Getter
    private final ChannelRole role = ChannelRole.RECEIVE;

    /**
     * Creates a ReceiveChannelHandler.
     *
     * @param connectionName the connection name for logging
     * @param pendingRequestManager the shared pending request manager for STAN matching
     * @param listener the connection event listener (may be null)
     * @param stateCallback callback for state changes
     */
    public ReceiveChannelHandler(String connectionName,
                                 PendingRequestManager pendingRequestManager,
                                 ConnectionListener listener,
                                 Consumer<ConnectionState> stateCallback) {
        this(connectionName, pendingRequestManager, listener, stateCallback, null);
    }

    /**
     * Creates a ReceiveChannelHandler with unsolicited message handler.
     *
     * @param connectionName the connection name for logging
     * @param pendingRequestManager the shared pending request manager for STAN matching
     * @param listener the connection event listener (may be null)
     * @param stateCallback callback for state changes
     * @param unsolicitedMessageHandler callback for unsolicited messages
     */
    public ReceiveChannelHandler(String connectionName,
                                 PendingRequestManager pendingRequestManager,
                                 ConnectionListener listener,
                                 Consumer<ConnectionState> stateCallback,
                                 BiConsumer<String, Iso8583Message> unsolicitedMessageHandler) {
        this.connectionName = connectionName;
        this.pendingRequestManager = pendingRequestManager;
        this.listener = listener;
        this.stateCallback = stateCallback;
        this.unsolicitedMessageHandler = unsolicitedMessageHandler;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("[{}] Receive channel active: {}", connectionName, ctx.channel().remoteAddress());
        updateState(ConnectionState.CONNECTED);
        if (listener != null) {
            listener.onConnected(connectionName);
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("[{}] Receive channel inactive", connectionName);
        updateState(ConnectionState.DISCONNECTED);

        // Note: We don't cancel pending requests here because that's managed by
        // FiscDualChannelClient based on failure strategy
        if (listener != null) {
            listener.onDisconnected(connectionName, null);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Iso8583Message message) {
            handleMessage(message);
        } else {
            log.warn("[{}] Unexpected message type on Receive channel: {}",
                connectionName, msg.getClass());
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // Receive channel should not send messages in proper dual-channel mode
        // But we log it for debugging purposes
        if (msg instanceof Iso8583Message message) {
            log.warn("[{}] Unexpected write on Receive channel: MTI={}, STAN={}. " +
                    "In dual-channel mode, requests should be sent via Send channel.",
                connectionName, message.getMti(), message.getFieldAsString(11));
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleEvent) {
            handleIdleEvent(ctx, idleEvent);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("[{}] Exception caught on Receive channel: {}", connectionName, cause.getMessage(), cause);
        if (listener != null) {
            listener.onError(connectionName, cause);
        }
        ctx.close();
    }

    /**
     * Handles an incoming message by matching it with pending requests.
     */
    private void handleMessage(Iso8583Message message) {
        String mti = message.getMti();
        String stan = message.getFieldAsString(11);

        log.debug("[{}] Received message: MTI={}, STAN={}", connectionName, mti, stan);
        messagesReceived.incrementAndGet();

        // Notify listener
        if (listener != null) {
            listener.onMessageReceived(connectionName, message);
        }

        // Try to match with pending request
        if (stan != null && !stan.isEmpty()) {
            boolean matched = pendingRequestManager.complete(stan, message);
            if (matched) {
                messagesMatched.incrementAndGet();
                log.debug("[{}] Response matched for STAN={}", connectionName, stan);
                return;
            }
        }

        // Handle as unsolicited message
        handleUnsolicitedMessage(message);
    }

    /**
     * Handles unsolicited messages from FISC.
     *
     * <p>These include:
     * <ul>
     *   <li>0800 - Network management requests from FISC (e.g., heartbeat check)</li>
     *   <li>Responses with no matching pending request</li>
     * </ul>
     */
    private void handleUnsolicitedMessage(Iso8583Message message) {
        String mti = message.getMti();
        String stan = message.getFieldAsString(11);
        unsolicitedMessages.incrementAndGet();

        log.info("[{}] Received unsolicited message: MTI={}, STAN={}", connectionName, mti, stan);

        // Call custom handler if provided
        if (unsolicitedMessageHandler != null) {
            try {
                unsolicitedMessageHandler.accept(connectionName, message);
            } catch (Exception e) {
                log.error("[{}] Error in unsolicited message handler: {}", connectionName, e.getMessage(), e);
            }
        }

        // Handle specific unsolicited messages
        if ("0800".equals(mti)) {
            // Network management request from FISC
            log.debug("[{}] Received network management request from FISC", connectionName);
            // Note: Response should be sent via Send channel
        }
    }

    /**
     * Handles idle state events.
     *
     * <p>In dual-channel mode, read idle on Receive channel may indicate
     * connection issues since we should be receiving responses.
     */
    private void handleIdleEvent(ChannelHandlerContext ctx, IdleStateEvent event) {
        if (event.state() == IdleState.READER_IDLE) {
            log.warn("[{}] Receive channel read idle, no data received. " +
                    "Connection may be stale.", connectionName);
        } else if (event.state() == IdleState.ALL_IDLE) {
            log.debug("[{}] Receive channel all idle", connectionName);
        }
        // WRITER_IDLE is not expected on Receive channel in dual-channel mode
    }

    /**
     * Updates the connection state.
     */
    private void updateState(ConnectionState newState) {
        ConnectionState oldState = this.state;
        this.state = newState;
        log.debug("[{}] Receive channel state changed: {} -> {}", connectionName, oldState, newState);
        if (stateCallback != null) {
            stateCallback.accept(newState);
        }
        if (listener != null) {
            listener.onStateChanged(connectionName, oldState, newState);
        }
    }

    /**
     * Sets the state to signed on.
     */
    public void setSignedOn() {
        updateState(ConnectionState.SIGNED_ON);
        if (listener != null) {
            listener.onSignedOn(connectionName);
        }
    }

    /**
     * Sets the state to reconnecting.
     */
    public void setReconnecting() {
        updateState(ConnectionState.RECONNECTING);
    }

    /**
     * Gets the number of messages received.
     *
     * @return messages received count
     */
    public long getMessagesReceived() {
        return messagesReceived.get();
    }

    /**
     * Gets the number of messages matched.
     *
     * @return messages matched count
     */
    public long getMessagesMatched() {
        return messagesMatched.get();
    }

    /**
     * Gets the number of unsolicited messages.
     *
     * @return unsolicited messages count
     */
    public long getUnsolicitedMessages() {
        return unsolicitedMessages.get();
    }

    /**
     * Resets statistics.
     */
    public void resetStatistics() {
        messagesReceived.set(0);
        messagesMatched.set(0);
        unsolicitedMessages.set(0);
    }
}
