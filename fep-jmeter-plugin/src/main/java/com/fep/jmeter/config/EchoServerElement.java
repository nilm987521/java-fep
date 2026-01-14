package com.fep.jmeter.config;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestStateListener;

import java.util.HexFormat;

/**
 * JMeter Config Element that starts an Echo Server.
 *
 * <p>This server listens on a specified port and echoes back any data it receives.
 * Useful for testing client samplers without needing an external server.
 *
 * <p>The server starts when the test begins and stops when the test ends.
 */
@Slf4j
public class EchoServerElement extends ConfigTestElement implements TestStateListener {

    private static final long serialVersionUID = 1L;

    public static final String PORT = "port";
    public static final String ENABLED = "enabled";

    private transient EventLoopGroup bossGroup;
    private transient EventLoopGroup workerGroup;
    private transient Channel serverChannel;

    private static final HexFormat HEX = HexFormat.of();

    public EchoServerElement() {
        super();
        setProperty(TestElement.GUI_CLASS, "com.fep.jmeter.gui.EchoServerElementGui");
    }

    @Override
    public void testStarted() {
        testStarted("");
    }

    @Override
    public void testStarted(String host) {
        if (!isEnabled()) {
            log.info("Echo Server is disabled");
            return;
        }

        int port = getPort();
        log.info("Starting Echo Server on port {}", port);

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new EchoHandler());
                    }
                });

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            log.info("Echo Server started successfully on port {}", port);

        } catch (Exception e) {
            log.error("Failed to start Echo Server on port {}", port, e);
            shutdown();
        }
    }

    @Override
    public void testEnded() {
        testEnded("");
    }

    @Override
    public void testEnded(String host) {
        log.info("Stopping Echo Server");
        shutdown();
    }

    private void shutdown() {
        if (serverChannel != null) {
            try {
                serverChannel.close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            serverChannel = null;
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }

        log.info("Echo Server stopped");
    }

    // Getters and Setters
    public int getPort() {
        return getPropertyAsInt(PORT, 9999);
    }

    public void setPort(int port) {
        setProperty(PORT, port);
    }

    public boolean isEnabled() {
        return getPropertyAsBoolean(ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        setProperty(ENABLED, enabled);
    }

    /**
     * Handler that echoes back received data.
     */
    @Slf4j
    private static class EchoHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            log.info("Client connected: {}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.info("Client disconnected: {}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            int length = buf.readableBytes();

            // Log received data
            byte[] data = new byte[length];
            buf.getBytes(buf.readerIndex(), data);
            log.info("Received {} bytes from {}: {}",
                    length, ctx.channel().remoteAddress(), HEX.formatHex(data));

            // Echo back - retain the buffer since we're passing it along
            ctx.writeAndFlush(buf.retain());
            log.info("Echoed {} bytes back to {}", length, ctx.channel().remoteAddress());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Echo handler error for {}", ctx.channel().remoteAddress(), cause);
            ctx.close();
        }
    }
}
