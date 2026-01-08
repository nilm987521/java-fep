package com.fep.communication.handler;

import com.fep.communication.client.ChannelRole;
import com.fep.communication.client.ConnectionListener;
import com.fep.communication.client.ConnectionState;
import com.fep.message.iso8583.Iso8583Message;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Netty handler for the Send Channel in dual-channel FISC architecture.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Send all outgoing messages (requests)</li>
 *   <li>Track connection state</li>
 *   <li>Handle idle events for heartbeat triggering</li>
 *   <li>Does NOT handle response matching (that's ReceiveChannelHandler's job)</li>
 * </ul>
 */
@Slf4j
public class SendChannelHandler extends ChannelDuplexHandler {

    private final String connectionName;
    private final ConnectionListener listener;
    private final Consumer<ConnectionState> stateCallback;

    @Getter
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    /** Statistics: messages sent */
    private final AtomicLong messagesSent = new AtomicLong(0);

    /** Statistics: bytes sent */
    private final AtomicLong bytesSent = new AtomicLong(0);

    /** Channel role identifier */
    @Getter
    private final ChannelRole role = ChannelRole.SEND;

    /**
     * Creates a SendChannelHandler.
     *
     * @param connectionName the connection name for logging
     * @param listener the connection event listener (may be null)
     * @param stateCallback callback for state changes
     */
    public SendChannelHandler(String connectionName, ConnectionListener listener,
                              Consumer<ConnectionState> stateCallback) {
        this.connectionName = connectionName;
        this.listener = listener;
        this.stateCallback = stateCallback;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("[{}] Send channel active: {}", connectionName, ctx.channel().remoteAddress());
        updateState(ConnectionState.CONNECTED);
        if (listener != null) {
            listener.onConnected(connectionName);
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("[{}] Send channel inactive", connectionName);
        updateState(ConnectionState.DISCONNECTED);
        if (listener != null) {
            listener.onDisconnected(connectionName, null);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Send channel should not receive responses in proper dual-channel mode
        // But we log it for debugging purposes
        if (msg instanceof Iso8583Message message) {
            log.warn("[{}] Unexpected message received on Send channel: MTI={}, STAN={}. " +
                    "In dual-channel mode, responses should come via Receive channel.",
                connectionName, message.getMti(), message.getFieldAsString(11));
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Iso8583Message message) {
            String mti = message.getMti();
            String stan = message.getFieldAsString(11);
            log.debug("[{}] Sending message: MTI={}, STAN={}", connectionName, mti, stan);

            // Add write completion listener for statistics
            promise.addListener(future -> {
                if (future.isSuccess()) {
                    messagesSent.incrementAndGet();
                    log.trace("[{}] Message sent successfully: STAN={}", connectionName, stan);
                } else {
                    log.error("[{}] Failed to send message: STAN={}, error={}",
                        connectionName, stan, future.cause().getMessage());
                }
            });
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
        log.error("[{}] Exception caught on Send channel: {}", connectionName, cause.getMessage(), cause);
        if (listener != null) {
            listener.onError(connectionName, cause);
        }
        ctx.close();
    }

    /**
     * Handles idle state events.
     *
     * <p>In dual-channel mode, write idle on Send channel may indicate
     * that we need to send a heartbeat to keep the connection alive.
     */
    private void handleIdleEvent(ChannelHandlerContext ctx, IdleStateEvent event) {
        if (event.state() == IdleState.WRITER_IDLE) {
            log.debug("[{}] Send channel write idle, heartbeat may be needed", connectionName);
            // The FiscDualChannelClient should handle sending heartbeat
        } else if (event.state() == IdleState.ALL_IDLE) {
            log.debug("[{}] Send channel all idle", connectionName);
        }
        // READER_IDLE is not expected on Send channel in dual-channel mode
    }

    /**
     * Updates the connection state.
     */
    private void updateState(ConnectionState newState) {
        ConnectionState oldState = this.state;
        this.state = newState;
        log.debug("[{}] Send channel state changed: {} -> {}", connectionName, oldState, newState);
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
     * Gets the number of messages sent.
     *
     * @return messages sent count
     */
    public long getMessagesSent() {
        return messagesSent.get();
    }

    /**
     * Resets statistics.
     */
    public void resetStatistics() {
        messagesSent.set(0);
        bytesSent.set(0);
    }
}
