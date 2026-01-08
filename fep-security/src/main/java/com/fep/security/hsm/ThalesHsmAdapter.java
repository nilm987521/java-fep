package com.fep.security.hsm;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Thales payShield HSM adapter implementation.
 *
 * This adapter implements the Thales Host Command Interface protocol.
 * Message format: 2-byte length + header + command code + data
 */
public class ThalesHsmAdapter implements HsmAdapter {

    private static final Logger log = LoggerFactory.getLogger(ThalesHsmAdapter.class);

    // Thales command codes (partial list)
    private static final String CMD_GENERATE_TMK = "A0";    // Generate TMK
    private static final String CMD_IMPORT_KEY = "A6";      // Import key
    private static final String CMD_EXPORT_KEY = "A8";      // Export key
    private static final String CMD_TRANSLATE_PIN = "CA";   // Translate PIN block
    private static final String CMD_GENERATE_MAC = "M0";    // Generate MAC
    private static final String CMD_VERIFY_MAC = "M2";      // Verify MAC
    private static final String CMD_ENCRYPT = "M4";         // Encrypt
    private static final String CMD_DECRYPT = "M6";         // Decrypt
    private static final String CMD_DIAGNOSTICS = "NC";     // Diagnostics
    private static final String CMD_HSM_STATUS = "NO";      // HSM status

    private final HsmConnectionConfig config;
    private EventLoopGroup eventLoopGroup;
    private Channel channel;
    private volatile boolean connected = false;

    // Pending requests waiting for response
    private final Map<String, CompletableFuture<HsmResponse>> pendingRequests = new ConcurrentHashMap<>();

    // Message header (configurable per installation)
    private String messageHeader = "";

    public ThalesHsmAdapter(HsmConnectionConfig config) {
        this.config = config;
    }

    @Override
    public HsmVendor getVendor() {
        return HsmVendor.THALES;
    }

    @Override
    public void connect() throws HsmException {
        if (connected) {
            return;
        }

        try {
            eventLoopGroup = new NioEventLoopGroup();

            Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectionTimeout())
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // Length-based frame decoder (2-byte length prefix)
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                            pipeline.addLast(new LengthFieldPrepender(2));

                            // Read timeout
                            pipeline.addLast(new ReadTimeoutHandler(config.getReadTimeout(), TimeUnit.MILLISECONDS));

                            // Response handler
                            pipeline.addLast(new ThalesResponseHandler());
                        }
                    });

            ChannelFuture future = bootstrap.connect(config.getPrimaryHost(), config.getPrimaryPort()).sync();
            channel = future.channel();
            connected = true;

            log.info("Connected to Thales HSM at {}:{}", config.getPrimaryHost(), config.getPrimaryPort());

        } catch (Exception e) {
            disconnect();
            throw new HsmException("Failed to connect to Thales HSM", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (channel != null) {
            channel.close();
            channel = null;
        }
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
            eventLoopGroup = null;
        }
        log.info("Disconnected from Thales HSM");
    }

    @Override
    public boolean isConnected() {
        return connected && channel != null && channel.isActive();
    }

    @Override
    public HsmResponse execute(HsmRequest request) throws HsmException {
        ensureConnected();

        String requestId = request.getRequestId() != null ? request.getRequestId() : generateRequestId();
        CompletableFuture<HsmResponse> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);

        try {
            byte[] message = buildMessage(request, requestId);
            ByteBuf buffer = Unpooled.wrappedBuffer(message);
            channel.writeAndFlush(buffer);

            return responseFuture.get(request.getTimeoutMs(), TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            throw new HsmException("HSM request timeout", "99");
        } catch (Exception e) {
            throw new HsmException("HSM request failed", e);
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    @Override
    public HsmResponse generateKey(String keyType, String keyId) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.GENERATE_KEY)
                .requestId(generateRequestId())
                .build()
                .addParameter("keyType", keyType)
                .addParameter("keyId", keyId);

        return execute(request);
    }

    @Override
    public HsmResponse importKey(String keyType, byte[] encryptedKey, String keyId) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.IMPORT_KEY)
                .requestId(generateRequestId())
                .build()
                .addParameter("keyType", keyType)
                .addParameter("encryptedKey", encryptedKey)
                .addParameter("keyId", keyId);

        return execute(request);
    }

    @Override
    public HsmResponse exportKey(String keyId, String kekId) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.EXPORT_KEY)
                .requestId(generateRequestId())
                .build()
                .addParameter("keyId", keyId)
                .addParameter("kekId", kekId);

        return execute(request);
    }

    @Override
    public HsmResponse translatePinBlock(byte[] pinBlock, String sourceKeyId, String destKeyId,
                                          String sourceFormat, String destFormat, String pan) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.TRANSLATE_PIN_BLOCK)
                .requestId(generateRequestId())
                .build()
                .addParameter("pinBlock", pinBlock)
                .addParameter("sourceKeyId", sourceKeyId)
                .addParameter("destKeyId", destKeyId)
                .addParameter("sourceFormat", sourceFormat)
                .addParameter("destFormat", destFormat)
                .addParameter("pan", pan);

        return execute(request);
    }

    @Override
    public HsmResponse generateMac(byte[] data, String keyId, String algorithm) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.GENERATE_MAC)
                .requestId(generateRequestId())
                .build()
                .addParameter("data", data)
                .addParameter("keyId", keyId)
                .addParameter("algorithm", algorithm);

        return execute(request);
    }

    @Override
    public HsmResponse verifyMac(byte[] data, byte[] mac, String keyId, String algorithm) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.VERIFY_MAC)
                .requestId(generateRequestId())
                .build()
                .addParameter("data", data)
                .addParameter("mac", mac)
                .addParameter("keyId", keyId)
                .addParameter("algorithm", algorithm);

        return execute(request);
    }

    @Override
    public HsmResponse encryptData(byte[] data, String keyId, String algorithm) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.ENCRYPT_DATA)
                .requestId(generateRequestId())
                .build()
                .addParameter("data", data)
                .addParameter("keyId", keyId)
                .addParameter("algorithm", algorithm);

        return execute(request);
    }

    @Override
    public HsmResponse decryptData(byte[] encryptedData, String keyId, String algorithm) throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.DECRYPT_DATA)
                .requestId(generateRequestId())
                .build()
                .addParameter("encryptedData", encryptedData)
                .addParameter("keyId", keyId)
                .addParameter("algorithm", algorithm);

        return execute(request);
    }

    @Override
    public HsmResponse getDiagnostics() throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.GET_DIAGNOSTICS)
                .requestId(generateRequestId())
                .build();

        return execute(request);
    }

    @Override
    public HsmResponse getStatus() throws HsmException {
        HsmRequest request = HsmRequest.builder()
                .command(HsmCommand.GET_STATUS)
                .requestId(generateRequestId())
                .build();

        return execute(request);
    }

    /**
     * Sets the message header for Thales HSM.
     */
    public void setMessageHeader(String header) {
        this.messageHeader = header;
    }

    private void ensureConnected() throws HsmException {
        if (!isConnected()) {
            if (config.isAutoReconnect()) {
                connect();
            } else {
                throw new HsmException("Not connected to HSM");
            }
        }
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private byte[] buildMessage(HsmRequest request, String requestId) {
        StringBuilder sb = new StringBuilder();

        // Add message header
        sb.append(messageHeader);

        // Add command code based on command type
        String commandCode = getThalesCommandCode(request.getCommand());
        sb.append(commandCode);

        // Add command-specific data
        appendCommandData(sb, request);

        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private String getThalesCommandCode(HsmCommand command) {
        return switch (command) {
            case GENERATE_KEY -> CMD_GENERATE_TMK;
            case IMPORT_KEY -> CMD_IMPORT_KEY;
            case EXPORT_KEY -> CMD_EXPORT_KEY;
            case TRANSLATE_PIN_BLOCK -> CMD_TRANSLATE_PIN;
            case GENERATE_MAC -> CMD_GENERATE_MAC;
            case VERIFY_MAC -> CMD_VERIFY_MAC;
            case ENCRYPT_DATA -> CMD_ENCRYPT;
            case DECRYPT_DATA -> CMD_DECRYPT;
            case GET_DIAGNOSTICS -> CMD_DIAGNOSTICS;
            case GET_STATUS -> CMD_HSM_STATUS;
            default -> throw new IllegalArgumentException("Unsupported command: " + command);
        };
    }

    private void appendCommandData(StringBuilder sb, HsmRequest request) {
        // Add command-specific parameters
        // This is a simplified implementation - actual Thales format is more complex
        for (Map.Entry<String, Object> entry : request.getParameters().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof byte[]) {
                sb.append(bytesToHex((byte[]) value));
            } else if (value != null) {
                sb.append(value.toString());
            }
        }
    }

    private HsmResponse parseResponse(byte[] responseData) {
        String response = new String(responseData, StandardCharsets.US_ASCII);

        // Skip header, get response code (2 bytes after command code)
        int headerLength = messageHeader.length();
        String responseCode = response.substring(headerLength + 2, headerLength + 4);

        boolean success = "00".equals(responseCode);

        HsmResponse hsmResponse = HsmResponse.builder()
                .success(success)
                .responseCode(responseCode)
                .errorMessage(success ? null : getErrorMessage(responseCode))
                .build();

        // Parse response data based on command
        if (success && response.length() > headerLength + 4) {
            String data = response.substring(headerLength + 4);
            hsmResponse.addData("responseData", data);
        }

        return hsmResponse;
    }

    private String getErrorMessage(String errorCode) {
        return switch (errorCode) {
            case "01" -> "Invalid key type";
            case "02" -> "Invalid key length";
            case "03" -> "Invalid key parity";
            case "04" -> "Invalid key usage";
            case "05" -> "Invalid PIN block format";
            case "10" -> "Key not found";
            case "11" -> "Key expired";
            case "20" -> "Invalid MAC";
            case "21" -> "Invalid data length";
            case "30" -> "HSM busy";
            case "31" -> "HSM error";
            case "99" -> "General error";
            default -> "Unknown error: " + errorCode;
        };
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Netty handler for processing HSM responses.
     */
    private class ThalesResponseHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buffer = (ByteBuf) msg;
            try {
                byte[] data = new byte[buffer.readableBytes()];
                buffer.readBytes(data);

                HsmResponse response = parseResponse(data);

                // Complete the pending request
                String requestId = extractRequestId(data);
                CompletableFuture<HsmResponse> future = pendingRequests.get(requestId);
                if (future != null) {
                    response.setRequestId(requestId);
                    future.complete(response);
                }

            } finally {
                buffer.release();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("HSM channel error", cause);
            ctx.close();
            connected = false;

            // Fail all pending requests
            for (CompletableFuture<HsmResponse> future : pendingRequests.values()) {
                future.completeExceptionally(new HsmException("Channel error", cause));
            }
            pendingRequests.clear();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.warn("HSM connection lost");
            connected = false;
        }

        private String extractRequestId(byte[] data) {
            // In real implementation, extract from response header
            return pendingRequests.keySet().stream().findFirst().orElse(null);
        }
    }
}
