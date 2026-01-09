package com.fep.jmeter.sampler;

import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.parser.FiscMessageAssembler;
import com.fep.message.iso8583.parser.FiscMessageParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMeter Sampler for simulating a FISC Financial Server.
 *
 * <p>This sampler starts a TCP server that acts as a FISC host simulator,
 * accepting ISO 8583 requests and returning configurable responses.
 *
 * <p>Features:
 * <ul>
 *   <li>Simulates FISC server for testing FEP clients</li>
 *   <li>Configurable response codes for different transaction types</li>
 *   <li>Simulated processing delay for realistic testing</li>
 *   <li>Statistics tracking (messages received/sent)</li>
 *   <li>Support for Network Management (0800), Financial (0200), Reversal (0400)</li>
 * </ul>
 *
 * <p>Usage:
 * <ul>
 *   <li>Add this sampler to a Thread Group</li>
 *   <li>Configure the port and response settings</li>
 *   <li>The server runs while the sampler is active</li>
 *   <li>Use Loop Controller with infinite loop for continuous operation</li>
 * </ul>
 */
public class FiscServerSampler extends AbstractSampler implements TestBean, TestStateListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(FiscServerSampler.class);

    // Property names for TestBean
    public static final String PORT = "port";
    public static final String RESPONSE_CODE = "responseCode";
    public static final String RESPONSE_DELAY = "responseDelay";
    public static final String BALANCE_AMOUNT = "balanceAmount";
    public static final String CUSTOM_RESPONSE_FIELDS = "customResponseFields";
    public static final String RESPONSE_RULES = "responseRules";
    public static final String SAMPLE_INTERVAL = "sampleInterval";

    // Default values
    private static final int DEFAULT_PORT = 9001;
    private static final String DEFAULT_RESPONSE_CODE = "00";
    private static final int DEFAULT_RESPONSE_DELAY = 0;
    private static final int DEFAULT_SAMPLE_INTERVAL = 1000;

    // Server instance management (one server per port)
    private static final Map<Integer, FiscServerInstance> serverInstances = new ConcurrentHashMap<>();
    private static final Object serverLock = new Object();

    public FiscServerSampler() {
        super();
        setName("FISC Server Simulator");
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);

        int port = getPort();

        try {
            result.sampleStart();

            // Get or create server instance for this port
            FiscServerInstance server = getOrCreateServer(port);

            // Wait for sample interval (allows server to process requests)
            int sampleInterval = getSampleInterval();
            if (sampleInterval > 0) {
                Thread.sleep(sampleInterval);
            }

            // Collect statistics
            int received = server.getMessagesReceived();
            int sent = server.getMessagesSent();
            int clients = server.getConnectedClients();

            result.sampleEnd();

            // Build response data
            StringBuilder sb = new StringBuilder();
            sb.append("=== FISC Server Simulator Status ===\n");
            sb.append("Port: ").append(server.getActualPort()).append("\n");
            sb.append("Status: Running\n");
            sb.append("Connected Clients: ").append(clients).append("\n");
            sb.append("Messages Received: ").append(received).append("\n");
            sb.append("Messages Sent: ").append(sent).append("\n");
            sb.append("\n=== Configuration ===\n");
            sb.append("Default Response Code: ").append(getResponseCode()).append("\n");
            sb.append("Response Delay: ").append(getResponseDelay()).append(" ms\n");

            String responseRules = getResponseRules();
            if (responseRules != null && !responseRules.isEmpty()) {
                sb.append("\n=== Response Rules ===\n");
                sb.append(responseRules).append("\n");
            }

            result.setResponseData(sb.toString(), StandardCharsets.UTF_8.name());
            result.setResponseCode("200");
            result.setResponseMessage("Server Running");
            result.setSuccessful(true);

            // Store statistics in JMeter variables
            storeVariables(server);

        } catch (Exception e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("ERROR");
            result.setResponseMessage(e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            log.error("FISC server sampler error", e);
        }

        return result;
    }

    /**
     * Gets or creates a server instance for the given port.
     */
    private FiscServerInstance getOrCreateServer(int port) throws Exception {
        synchronized (serverLock) {
            FiscServerInstance server = serverInstances.get(port);

            if (server == null || !server.isRunning()) {
                // Create new server with current configuration
                server = new FiscServerInstance(
                    port,
                    getResponseCode(),
                    getResponseDelay(),
                    getBalanceAmount(),
                    getCustomResponseFields(),
                    getResponseRules()
                );
                server.start();
                serverInstances.put(port, server);
                log.info("Started FISC server simulator on port {}", server.getActualPort());
            } else {
                // Update configuration on existing server
                server.updateConfiguration(
                    getResponseCode(),
                    getResponseDelay(),
                    getBalanceAmount(),
                    getCustomResponseFields(),
                    getResponseRules()
                );
            }

            return server;
        }
    }

    /**
     * Stores server statistics in JMeter variables.
     */
    private void storeVariables(FiscServerInstance server) {
        JMeterContext context = JMeterContextService.getContext();
        if (context != null) {
            JMeterVariables vars = context.getVariables();
            if (vars != null) {
                vars.put("FISC_SERVER_PORT", String.valueOf(server.getActualPort()));
                vars.put("FISC_SERVER_RECEIVED", String.valueOf(server.getMessagesReceived()));
                vars.put("FISC_SERVER_SENT", String.valueOf(server.getMessagesSent()));
                vars.put("FISC_SERVER_CLIENTS", String.valueOf(server.getConnectedClients()));
            }
        }
    }

    // TestStateListener implementation
    @Override
    public void testStarted() {
        testStarted("");
    }

    @Override
    public void testStarted(String host) {
        log.info("FISC server test started on {}", host);
    }

    @Override
    public void testEnded() {
        testEnded("");
    }

    @Override
    public void testEnded(String host) {
        log.info("FISC server test ended on {}, stopping all server instances", host);
        synchronized (serverLock) {
            serverInstances.values().forEach(server -> {
                try {
                    server.stop();
                } catch (Exception e) {
                    log.warn("Error stopping FISC server", e);
                }
            });
            serverInstances.clear();
        }
    }

    // Getters and Setters for TestBean properties
    public int getPort() {
        return getPropertyAsInt(PORT, DEFAULT_PORT);
    }

    public void setPort(int port) {
        setProperty(PORT, port);
    }

    public String getResponseCode() {
        return getPropertyAsString(RESPONSE_CODE, DEFAULT_RESPONSE_CODE);
    }

    public void setResponseCode(String responseCode) {
        setProperty(RESPONSE_CODE, responseCode);
    }

    public int getResponseDelay() {
        return getPropertyAsInt(RESPONSE_DELAY, DEFAULT_RESPONSE_DELAY);
    }

    public void setResponseDelay(int delay) {
        setProperty(RESPONSE_DELAY, delay);
    }

    public String getBalanceAmount() {
        return getPropertyAsString(BALANCE_AMOUNT, "");
    }

    public void setBalanceAmount(String amount) {
        setProperty(BALANCE_AMOUNT, amount);
    }

    public String getCustomResponseFields() {
        return getPropertyAsString(CUSTOM_RESPONSE_FIELDS, "");
    }

    public void setCustomResponseFields(String fields) {
        setProperty(CUSTOM_RESPONSE_FIELDS, fields);
    }

    public String getResponseRules() {
        return getPropertyAsString(RESPONSE_RULES, "");
    }

    public void setResponseRules(String rules) {
        setProperty(RESPONSE_RULES, rules);
    }

    public int getSampleInterval() {
        return getPropertyAsInt(SAMPLE_INTERVAL, DEFAULT_SAMPLE_INTERVAL);
    }

    public void setSampleInterval(int interval) {
        setProperty(SAMPLE_INTERVAL, interval);
    }

    /**
     * Inner class representing a running FISC server instance.
     */
    private static class FiscServerInstance {
        private final int port;
        private volatile String responseCode;
        private volatile int responseDelay;
        private volatile String balanceAmount;
        private volatile String customResponseFields;
        private volatile String responseRules;
        private volatile Map<String, String> parsedRules;

        private final FiscMessageParser messageParser;
        private final FiscMessageAssembler messageAssembler;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicInteger messagesReceived = new AtomicInteger(0);
        private final AtomicInteger messagesSent = new AtomicInteger(0);
        private final AtomicInteger connectedClients = new AtomicInteger(0);

        private EventLoopGroup bossGroup;
        private EventLoopGroup workerGroup;
        private Channel serverChannel;

        public FiscServerInstance(int port, String responseCode, int responseDelay,
                                  String balanceAmount, String customResponseFields,
                                  String responseRules) {
            this.port = port;
            this.responseCode = responseCode;
            this.responseDelay = responseDelay;
            this.balanceAmount = balanceAmount;
            this.customResponseFields = customResponseFields;
            this.responseRules = responseRules;
            this.parsedRules = parseRules(responseRules);
            this.messageParser = new FiscMessageParser(false);
            this.messageAssembler = new FiscMessageAssembler(true);
        }

        public void updateConfiguration(String responseCode, int responseDelay,
                                        String balanceAmount, String customResponseFields,
                                        String responseRules) {
            this.responseCode = responseCode;
            this.responseDelay = responseDelay;
            this.balanceAmount = balanceAmount;
            this.customResponseFields = customResponseFields;
            this.responseRules = responseRules;
            this.parsedRules = parseRules(responseRules);
        }

        private Map<String, String> parseRules(String rules) {
            Map<String, String> result = new ConcurrentHashMap<>();
            if (rules == null || rules.isEmpty()) {
                return result;
            }
            // Format: "processingCode:responseCode;processingCode:responseCode"
            // Example: "010000:00;400000:51;310000:00"
            String[] pairs = rules.split(";");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    result.put(kv[0].trim(), kv[1].trim());
                }
            }
            return result;
        }

        public void start() throws Exception {
            if (!running.compareAndSet(false, true)) {
                return;
            }

            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("decoder", new FiscServerDecoder());
                        pipeline.addLast("encoder", new FiscServerEncoder());
                        pipeline.addLast("handler", new FiscServerHandler());
                    }
                });

            ChannelFuture bindFuture = bootstrap.bind(port).sync();
            serverChannel = bindFuture.channel();
            log.info("FISC server simulator started on port {}", getActualPort());
        }

        public void stop() {
            if (!running.compareAndSet(true, false)) {
                return;
            }

            log.info("Stopping FISC server simulator on port {}", getActualPort());

            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().syncUninterruptibly();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().syncUninterruptibly();
            }
        }

        public boolean isRunning() {
            return running.get() && serverChannel != null && serverChannel.isActive();
        }

        public int getActualPort() {
            if (serverChannel == null) {
                return port;
            }
            return ((InetSocketAddress) serverChannel.localAddress()).getPort();
        }

        public int getMessagesReceived() {
            return messagesReceived.get();
        }

        public int getMessagesSent() {
            return messagesSent.get();
        }

        public int getConnectedClients() {
            return connectedClients.get();
        }

        /**
         * Determines the response code for a request based on rules.
         */
        private String determineResponseCode(Iso8583Message request) {
            String processingCode = request.getFieldAsString(3);

            // Check if there's a specific rule for this processing code
            if (processingCode != null && parsedRules.containsKey(processingCode)) {
                return parsedRules.get(processingCode);
            }

            // Return default response code
            return responseCode;
        }

        /**
         * Applies custom response fields to the response.
         */
        private void applyCustomFields(Iso8583Message response) {
            if (customResponseFields == null || customResponseFields.isEmpty()) {
                return;
            }

            // Parse format: "field:value;field:value"
            String[] pairs = customResponseFields.split(";");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    try {
                        int fieldNum = Integer.parseInt(kv[0].trim());
                        String value = kv[1].trim();
                        response.setField(fieldNum, value);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid field number in custom response fields: {}", kv[0]);
                    }
                }
            }
        }

        /**
         * ISO 8583 Message Decoder for server.
         */
        private class FiscServerDecoder extends ByteToMessageDecoder {
            private static final int LENGTH_PREFIX_SIZE = 2;

            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                if (in.readableBytes() < LENGTH_PREFIX_SIZE) {
                    return;
                }

                in.markReaderIndex();
                int messageLength = readBcdLength(in);

                if (messageLength <= 0 || messageLength > 65535) {
                    in.resetReaderIndex();
                    log.error("Invalid message length: {}", messageLength);
                    return;
                }

                if (in.readableBytes() < messageLength) {
                    in.resetReaderIndex();
                    return;
                }

                byte[] messageData = new byte[messageLength];
                in.readBytes(messageData);

                try {
                    Iso8583Message message = messageParser.parse(messageData);
                    out.add(message);
                    messagesReceived.incrementAndGet();
                    log.debug("Server received: MTI={}, STAN={}",
                        message.getMti(), message.getFieldAsString(11));
                } catch (Exception e) {
                    log.error("Failed to decode message", e);
                }
            }

            private int readBcdLength(ByteBuf in) {
                byte b1 = in.readByte();
                byte b2 = in.readByte();
                int high1 = (b1 >> 4) & 0x0F;
                int low1 = b1 & 0x0F;
                int high2 = (b2 >> 4) & 0x0F;
                int low2 = b2 & 0x0F;
                return high1 * 1000 + low1 * 100 + high2 * 10 + low2;
            }
        }

        /**
         * ISO 8583 Message Encoder for server.
         */
        private class FiscServerEncoder extends MessageToByteEncoder<Iso8583Message> {
            @Override
            protected void encode(ChannelHandlerContext ctx, Iso8583Message msg, ByteBuf out) {
                byte[] data = messageAssembler.assemble(msg);
                out.writeBytes(data);
                messagesSent.incrementAndGet();
                log.debug("Server sent: MTI={}, size={} bytes",
                    msg.getMti(), data.length);
            }
        }

        /**
         * Business logic handler for server.
         */
        private class FiscServerHandler extends SimpleChannelInboundHandler<Iso8583Message> {

            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                int count = connectedClients.incrementAndGet();
                log.info("Client connected to server: {} (total: {})",
                    ctx.channel().remoteAddress(), count);
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                int count = connectedClients.decrementAndGet();
                log.info("Client disconnected from server: {} (total: {})",
                    ctx.channel().remoteAddress(), count);
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Iso8583Message request) {
                String mti = request.getMti();
                String stan = request.getFieldAsString(11);
                String processingCode = request.getFieldAsString(3);

                log.info("Server received request: MTI={}, STAN={}, ProcessingCode={}",
                    mti, stan, processingCode);

                // Apply response delay if configured
                if (responseDelay > 0) {
                    try {
                        Thread.sleep(responseDelay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // Create response message
                Iso8583Message response = request.createResponse();

                // Set response code based on rules or default
                String respCode = determineResponseCode(request);
                response.setField(39, respCode);

                // Set balance if configured (for balance inquiry)
                if (balanceAmount != null && !balanceAmount.isEmpty()) {
                    String paddedBalance = String.format("%012d",
                        Long.parseLong(balanceAmount.replaceAll("[^0-9]", "")));
                    response.setField(54, paddedBalance);
                }

                // Apply custom response fields
                applyCustomFields(response);

                // Send response
                ctx.writeAndFlush(response).addListener((ChannelFutureListener) f -> {
                    if (f.isSuccess()) {
                        log.info("Server sent response: MTI={}, STAN={}, ResponseCode={}",
                            response.getMti(), response.getFieldAsString(11), respCode);
                    } else {
                        log.error("Failed to send response", f.cause());
                    }
                });
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                log.error("Exception in server handler", cause);
                ctx.close();
            }
        }
    }
}
