package com.fep.jmeter.sampler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fep.jmeter.config.TransactionTemplate;
import com.fep.jmeter.config.TransactionTemplateConfig;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.message.iso8583.Iso8583MessageFactory;
import com.fep.message.iso8583.MessageType;
import com.fep.message.iso8583.parser.FiscMessageAssembler;
import com.fep.message.iso8583.parser.FiscMessageParser;
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
import lombok.extern.slf4j.Slf4j;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMeter Sampler for simulating ATM transactions.
 *
 * <p>This sampler simulates an ATM terminal sending transactions to FEP.
 * It supports various ATM transaction types:
 * <ul>
 *   <li>Cash Withdrawal (提款)</li>
 *   <li>Balance Inquiry (餘額查詢)</li>
 *   <li>Cash Transfer (轉帳)</li>
 *   <li>Cash Deposit (存款)</li>
 *   <li>PIN Change (密碼變更)</li>
 *   <li>Mini Statement (交易明細查詢)</li>
 *   <li>Cardless Withdrawal (無卡提款)</li>
 * </ul>
 *
 * <p>The ATM connects directly to FEP, and FEP routes the transaction
 * to the appropriate destination (FISC, Core Banking, etc.).
 */
@Slf4j
public class AtmSimulatorSampler extends AbstractSampler implements TestBean, TestStateListener {

    private static final long serialVersionUID = 1L;

    // Property names - Connection
    public static final String FEP_HOST = "fepHost";
    public static final String FEP_PORT = "fepPort";
    public static final String CONNECTION_TIMEOUT = "connectionTimeout";
    public static final String READ_TIMEOUT = "readTimeout";

    // Property names - Transaction
    public static final String TRANSACTION_TYPE = "transactionType";
    public static final String MTI_OVERRIDE = "mtiOverride";
    public static final String PROCESSING_CODE_OVERRIDE = "processingCodeOverride";

    // Property names - ATM Info
    public static final String ATM_ID = "atmId";
    public static final String ATM_LOCATION = "atmLocation";
    public static final String BANK_CODE = "bankCode";

    // Property names - Card & Account
    public static final String CARD_NUMBER = "cardNumber";
    public static final String AMOUNT = "amount";
    public static final String DESTINATION_ACCOUNT = "destinationAccount";

    // Property names - Advanced Customization
    public static final String CUSTOM_FIELDS = "customFields";
    public static final String MESSAGE_TEMPLATE = "messageTemplate";
    public static final String ENABLE_PIN_BLOCK = "enablePinBlock";
    public static final String PIN_BLOCK = "pinBlock";

    // Property names - Protocol Selection (RAW mode support)
    public static final String PROTOCOL_TYPE = "protocolType";
    public static final String RAW_MESSAGE_FORMAT = "rawMessageFormat";
    public static final String RAW_MESSAGE_DATA = "rawMessageData";
    public static final String LENGTH_HEADER_TYPE = "lengthHeaderType";
    public static final String EXPECT_RESPONSE = "expectResponse";
    public static final String RESPONSE_MATCH_PATTERN = "responseMatchPattern";

    // Property names - Template Config Integration
    public static final String USE_TEMPLATE_CONFIG = "useTemplateConfig";
    public static final String TEMPLATE_NAME = "templateName";

    // Static resources
    private static final Map<String, ChannelHolder> channelPool = new ConcurrentHashMap<>();
    private static final Map<String, RawChannelHolder> rawChannelPool = new ConcurrentHashMap<>();
    private static final Object channelLock = new Object();
    private static EventLoopGroup workerGroup;
    private static final AtomicInteger stanCounter = new AtomicInteger(0);
    private static final AtomicLong rawMessageCounter = new AtomicLong(0);
    private static final Iso8583MessageFactory messageFactory = new Iso8583MessageFactory();
    private static final FiscMessageAssembler messageAssembler = new FiscMessageAssembler();
    private static final FiscMessageParser messageParser = new FiscMessageParser();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HexFormat hexFormat = HexFormat.of();

    public AtmSimulatorSampler() {
        super();
        setName("ATM Simulator");
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);

        // Dispatch based on protocol type
        AtmProtocolType protocolType = AtmProtocolType.fromString(getProtocolType());
        return switch (protocolType) {
            case ISO_8583 -> sampleIso8583Mode(result);
            case RAW -> sampleRawMode(result);
        };
    }

    /**
     * Execute ISO 8583 mode sampling.
     */
    private SampleResult sampleIso8583Mode(SampleResult result) {
        String fepHost = getFepHost();
        int fepPort = getFepPort();
        String transactionType = getTransactionType();

        try {
            // Get or create connection
            ChannelHolder holder = getOrCreateChannel(fepHost, fepPort);

            // Build the transaction message
            Iso8583Message request = buildAtmTransaction(transactionType);
            String requestStr = formatMessageForDisplay(request);
            result.setSamplerData(requestStr);

            // Create response future
            CompletableFuture<Iso8583Message> responseFuture = new CompletableFuture<>();
            String stan = request.getFieldAsString(11);
            holder.pendingRequests.put(stan, responseFuture);

            // Start timing
            result.sampleStart();

            // Send message
            byte[] messageBytes = messageAssembler.assemble(request);
            ByteBuf buf = Unpooled.buffer(messageBytes.length);
            buf.writeBytes(messageBytes);
            holder.channel.writeAndFlush(buf).sync();

            // Wait for response
            Iso8583Message response = responseFuture.get(getReadTimeout(), TimeUnit.MILLISECONDS);

            // Stop timing
            result.sampleEnd();

            // Process response
            String responseCode = response.getFieldAsString(39);
            String responseStr = formatMessageForDisplay(response);
            result.setResponseData(responseStr, StandardCharsets.UTF_8.name());
            result.setResponseCode(responseCode != null ? responseCode : "N/A");
            result.setResponseMessage(getResponseMessage(responseCode));

            // Check success
            boolean success = "00".equals(responseCode);
            result.setSuccessful(success);

            // Store response variables
            storeResponseVariables(response);

            if (success) {
                log.debug("ATM transaction successful: {} -> {}", transactionType, responseCode);
            } else {
                log.debug("ATM transaction declined: {} -> {}", transactionType, responseCode);
            }

        } catch (TimeoutException e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("TIMEOUT");
            result.setResponseMessage("Response timeout");
            result.setResponseData("Timeout waiting for FEP response", StandardCharsets.UTF_8.name());
            log.warn("ATM transaction timeout: {}", transactionType);
        } catch (Exception e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("ERROR");
            result.setResponseMessage(e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            log.error("ATM sampler error", e);
        }

        return result;
    }

    /**
     * Execute RAW mode sampling - sends arbitrary bytes.
     */
    private SampleResult sampleRawMode(SampleResult result) {
        String fepHost = getFepHost();
        int fepPort = getFepPort();

        try {
            // Get or create connection for RAW mode
            RawChannelHolder holder = getOrCreateRawChannel(fepHost, fepPort);

            // Build raw message
            byte[] rawMessage = buildRawMessage();
            String requestId = String.valueOf(rawMessageCounter.incrementAndGet());

            // Format request for display
            String requestDisplay = formatRawMessageForDisplay(rawMessage);
            result.setSamplerData(requestDisplay);

            boolean expectResponse = isExpectResponse();

            if (expectResponse) {
                // Create response future
                CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();
                holder.pendingRequests.put(requestId, responseFuture);
                holder.lastRequestId = requestId;
            }

            // Start timing
            result.sampleStart();

            // Send message (with or without length header based on configuration)
            ByteBuf buf = prepareRawMessageBuffer(rawMessage);
            holder.channel.writeAndFlush(buf).sync();

            if (expectResponse) {
                // Wait for response
                CompletableFuture<byte[]> responseFuture = holder.pendingRequests.get(requestId);
                byte[] response = responseFuture.get(getReadTimeout(), TimeUnit.MILLISECONDS);
                holder.pendingRequests.remove(requestId);

                // Stop timing
                result.sampleEnd();

                // Process response
                String responseDisplay = formatRawMessageForDisplay(response);
                result.setResponseData(responseDisplay, StandardCharsets.UTF_8.name());

                // Check success based on pattern if configured
                boolean success = validateRawResponse(response);
                result.setSuccessful(success);
                result.setResponseCode(success ? "OK" : "MISMATCH");
                result.setResponseMessage(success ? "Response received" : "Response pattern mismatch");

                // Store response variables
                storeRawResponseVariables(response);

                log.debug("RAW message sent and response received: {} bytes -> {} bytes",
                    rawMessage.length, response.length);
            } else {
                // Fire and forget mode
                result.sampleEnd();
                result.setSuccessful(true);
                result.setResponseCode("SENT");
                result.setResponseMessage("Message sent (no response expected)");
                result.setResponseData("Message sent successfully, no response expected",
                    StandardCharsets.UTF_8.name());

                log.debug("RAW message sent (fire-and-forget): {} bytes", rawMessage.length);
            }

        } catch (TimeoutException e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("TIMEOUT");
            result.setResponseMessage("Response timeout");
            result.setResponseData("Timeout waiting for response", StandardCharsets.UTF_8.name());
            log.warn("RAW message timeout");
        } catch (Exception e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("ERROR");
            result.setResponseMessage(e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            log.error("RAW sampler error", e);
        }

        return result;
    }

    /**
     * Build raw message bytes from configured data.
     */
    private byte[] buildRawMessage() {
        String rawData = substituteVariables(getRawMessageData());
        RawMessageFormat format = RawMessageFormat.fromString(getRawMessageFormat());

        return switch (format) {
            case HEX -> hexFormat.parseHex(rawData.replaceAll("\\s+", ""));
            case BASE64 -> Base64.getDecoder().decode(rawData);
            case TEXT -> rawData.getBytes(StandardCharsets.UTF_8);
        };
    }

    /**
     * Prepare ByteBuf with appropriate length header.
     */
    private ByteBuf prepareRawMessageBuffer(byte[] message) {
        LengthHeaderType headerType = LengthHeaderType.fromString(getLengthHeaderType());
        int headerSize = headerType.getHeaderSize();

        ByteBuf buf = Unpooled.buffer(headerSize + message.length);

        switch (headerType) {
            case NONE -> {
                // No header, just the message
            }
            case TWO_BYTES -> {
                buf.writeShort(message.length);
            }
            case FOUR_BYTES -> {
                buf.writeInt(message.length);
            }
            case TWO_BYTES_BCD -> {
                // BCD encoding: 2 bytes can represent 0-9999
                int len = message.length;
                int bcd1 = ((len / 1000) << 4) | ((len / 100) % 10);
                int bcd2 = (((len / 10) % 10) << 4) | (len % 10);
                buf.writeByte(bcd1);
                buf.writeByte(bcd2);
            }
            case ASCII_4 -> {
                // 4-character ASCII decimal
                String lenStr = String.format("%04d", message.length);
                buf.writeBytes(lenStr.getBytes(StandardCharsets.US_ASCII));
            }
        }

        buf.writeBytes(message);
        return buf;
    }

    /**
     * Format raw message for display.
     */
    private String formatRawMessageForDisplay(byte[] data) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== RAW Message ===\n");
        sb.append("Length: ").append(data.length).append(" bytes\n\n");

        // Hex dump
        sb.append("Hex:\n");
        sb.append(hexFormat.formatHex(data)).append("\n\n");

        // Try to show as text (printable characters only)
        sb.append("Text (printable):\n");
        StringBuilder text = new StringBuilder();
        for (byte b : data) {
            char c = (char) (b & 0xFF);
            if (c >= 32 && c < 127) {
                text.append(c);
            } else {
                text.append('.');
            }
        }
        sb.append(text).append("\n");

        return sb.toString();
    }

    /**
     * Validate raw response against configured pattern.
     */
    private boolean validateRawResponse(byte[] response) {
        String pattern = getResponseMatchPattern();
        if (pattern == null || pattern.isEmpty()) {
            return true; // No pattern = always success
        }

        // Pattern format: "HEX:0200..." or "CONTAINS:OK" or "REGEX:..."
        if (pattern.startsWith("HEX:")) {
            String expectedHex = pattern.substring(4).replaceAll("\\s+", "");
            String actualHex = hexFormat.formatHex(response);
            return actualHex.toUpperCase().startsWith(expectedHex.toUpperCase());
        } else if (pattern.startsWith("CONTAINS:")) {
            String searchText = pattern.substring(9);
            String responseText = new String(response, StandardCharsets.UTF_8);
            return responseText.contains(searchText);
        } else if (pattern.startsWith("REGEX:")) {
            String regex = pattern.substring(6);
            String responseText = new String(response, StandardCharsets.UTF_8);
            return responseText.matches(regex);
        } else if (pattern.startsWith("LENGTH:")) {
            try {
                int expectedLen = Integer.parseInt(pattern.substring(7).trim());
                return response.length == expectedLen;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // Default: treat as hex prefix match
        String actualHex = hexFormat.formatHex(response);
        return actualHex.toUpperCase().startsWith(pattern.toUpperCase());
    }

    /**
     * Store raw response in JMeter variables.
     */
    private void storeRawResponseVariables(byte[] response) {
        JMeterContext context = JMeterContextService.getContext();
        if (context != null) {
            JMeterVariables vars = context.getVariables();
            if (vars != null) {
                vars.put("RAW_RESPONSE_LENGTH", String.valueOf(response.length));
                vars.put("RAW_RESPONSE_HEX", hexFormat.formatHex(response));
                vars.put("RAW_RESPONSE_BASE64", Base64.getEncoder().encodeToString(response));
                vars.put("RAW_RESPONSE_TEXT", new String(response, StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Get or create RAW mode channel with configurable frame decoder.
     */
    private RawChannelHolder getOrCreateRawChannel(String host, int port) throws Exception {
        LengthHeaderType headerType = LengthHeaderType.fromString(getLengthHeaderType());
        String key = Thread.currentThread().getName() + "_RAW_" + host + "_" + port + "_" + headerType.name();

        synchronized (channelLock) {
            RawChannelHolder holder = rawChannelPool.get(key);

            if (holder == null || !holder.channel.isActive()) {
                // Initialize worker group if needed
                if (workerGroup == null || workerGroup.isShutdown()) {
                    workerGroup = new NioEventLoopGroup(2);
                }

                // Create new connection
                Bootstrap bootstrap = new Bootstrap()
                    .group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getConnectionTimeout())
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true);

                holder = new RawChannelHolder();
                RawChannelHolder finalHolder = holder;

                bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // Configure frame decoder based on length header type
                        switch (headerType) {
                            case NONE -> {
                                // No length framing - read whatever comes
                                // This mode requires expectResponse=false or fixed-length responses
                            }
                            case TWO_BYTES -> {
                                pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                                pipeline.addLast(new LengthFieldPrepender(2));
                            }
                            case FOUR_BYTES -> {
                                pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                                pipeline.addLast(new LengthFieldPrepender(4));
                            }
                            case TWO_BYTES_BCD, ASCII_4 -> {
                                // Custom decoder needed for BCD/ASCII length headers
                                // For now, use 2-byte as fallback
                                pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                            }
                        }

                        pipeline.addLast(new ReadTimeoutHandler(getReadTimeout(), TimeUnit.MILLISECONDS));
                        pipeline.addLast(new RawResponseHandler(finalHolder));
                    }
                });

                ChannelFuture connectFuture = bootstrap.connect(host, port).sync();
                holder.channel = connectFuture.channel();
                rawChannelPool.put(key, holder);

                log.info("RAW mode connected to {}:{} with {} header", host, port, headerType);
            }

            return holder;
        }
    }

    private ChannelHolder getOrCreateChannel(String host, int port) throws Exception {
        String key = Thread.currentThread().getName() + "_" + host + "_" + port;

        synchronized (channelLock) {
            ChannelHolder holder = channelPool.get(key);

            if (holder == null || !holder.channel.isActive()) {
                // Initialize worker group if needed
                if (workerGroup == null || workerGroup.isShutdown()) {
                    workerGroup = new NioEventLoopGroup(2);
                }

                // Create new connection
                Bootstrap bootstrap = new Bootstrap()
                    .group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getConnectionTimeout())
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true);

                holder = new ChannelHolder();
                ChannelHolder finalHolder = holder;

                bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 2-byte length header
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
                        pipeline.addLast(new LengthFieldPrepender(2));
                        pipeline.addLast(new ReadTimeoutHandler(getReadTimeout(), TimeUnit.MILLISECONDS));
                        pipeline.addLast(new AtmResponseHandler(finalHolder));
                    }
                });

                ChannelFuture connectFuture = bootstrap.connect(host, port).sync();
                holder.channel = connectFuture.channel();
                channelPool.put(key, holder);

                log.info("ATM connected to FEP at {}:{}", host, port);
            }

            return holder;
        }
    }

    private Iso8583Message buildAtmTransaction(String transactionTypeStr) {
        // Check if Template Config mode is enabled
        if (isUseTemplateConfig()) {
            return buildFromTemplateConfig();
        }

        AtmTransactionType transactionType = AtmTransactionType.fromString(transactionTypeStr);

        // Determine MTI - use override if provided, otherwise use transaction type default
        String mti = getMtiOverride();
        if (mti == null || mti.isEmpty()) {
            mti = transactionType.getDefaultMti();
        }

        // Create message with appropriate MTI
        MessageType messageType = switch (mti) {
            case "0100" -> MessageType.AUTH_REQUEST;
            case "0200" -> MessageType.FINANCIAL_REQUEST;
            case "0400" -> MessageType.REVERSAL_REQUEST;
            case "0800" -> MessageType.NETWORK_MANAGEMENT_REQUEST;
            default -> MessageType.FINANCIAL_REQUEST;
        };
        Iso8583Message message = messageFactory.createMessage(messageType);

        // If CUSTOM type with JSON template, apply template first
        if (transactionType.isCustom()) {
            applyMessageTemplate(message);
        }

        // Set common ATM fields (may be overridden by template)
        setCommonAtmFields(message);

        // Determine processing code
        String processingCode = getProcessingCodeOverride();
        if (processingCode == null || processingCode.isEmpty()) {
            processingCode = transactionType.getDefaultProcessingCode();
        }

        // Set transaction-specific fields based on type
        switch (transactionType) {
            case WITHDRAWAL -> {
                message.setField(3, processingCode);
                setAmountField(message);
            }
            case BALANCE_INQUIRY -> {
                message.setField(3, processingCode);
            }
            case TRANSFER -> {
                message.setField(3, processingCode);
                setAmountField(message);
                setTransferFields(message);
            }
            case DEPOSIT -> {
                message.setField(3, processingCode);
                setAmountField(message);
            }
            case PIN_CHANGE -> {
                message.setField(3, processingCode);
            }
            case MINI_STATEMENT -> {
                message.setField(3, processingCode);
            }
            case CARDLESS_WITHDRAWAL -> {
                message.setField(3, processingCode);
                setAmountField(message);
            }
            case BILL_PAYMENT -> {
                message.setField(3, processingCode);
                setAmountField(message);
            }
            case AUTHORIZATION -> {
                message.setField(3, processingCode);
                setAmountField(message);
            }
            case REVERSAL -> {
                message.setField(3, processingCode);
                setAmountField(message);
            }
            case SIGN_ON -> {
                message.setField(70, "001"); // Sign-on network code
            }
            case SIGN_OFF -> {
                message.setField(70, "002"); // Sign-off network code
            }
            case ECHO_TEST -> {
                message.setField(70, "301"); // Echo test network code
            }
            case KEY_EXCHANGE -> {
                message.setField(70, "101"); // Key exchange network code
            }
            case CUSTOM -> {
                // Processing code already set by template or override
                if (processingCode != null && !processingCode.isEmpty()) {
                    message.setField(3, processingCode);
                }
                setAmountField(message);
            }
        }

        // Apply PIN block if enabled
        if (isEnablePinBlock()) {
            String pinBlock = getPinBlock();
            if (pinBlock != null && !pinBlock.isEmpty()) {
                message.setField(52, pinBlock);
            }
        }

        // Apply custom fields (highest priority, can override anything)
        applyCustomFields(message);

        return message;
    }

    /**
     * Applies JSON message template to the message.
     * Template format: {"mti": "0200", "fields": {"2": "4111...", "3": "010000", ...}}
     */
    private void applyMessageTemplate(Iso8583Message message) {
        String template = getMessageTemplate();
        if (template == null || template.trim().isEmpty()) {
            return;
        }

        try {
            Map<String, Object> templateMap = objectMapper.readValue(
                template, new TypeReference<Map<String, Object>>() {});

            // Apply fields from template
            Object fieldsObj = templateMap.get("fields");
            if (fieldsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fields = (Map<String, Object>) fieldsObj;
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    try {
                        int fieldNum = Integer.parseInt(entry.getKey());
                        String value = substituteVariables(String.valueOf(entry.getValue()));
                        message.setField(fieldNum, value);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid field number in template: {}", entry.getKey());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing message template JSON: {}", e.getMessage());
        }
    }

    private void setCommonAtmFields(Iso8583Message message) {
        // STAN (Field 11)
        String stan = String.format("%06d", stanCounter.incrementAndGet() % 1000000);
        message.setField(11, stan);

        // Transmission date/time (Field 7)
        LocalDateTime now = LocalDateTime.now();
        message.setField(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));

        // Local transaction time (Field 12)
        message.setField(12, now.format(DateTimeFormatter.ofPattern("HHmmss")));

        // Local transaction date (Field 13)
        message.setField(13, now.format(DateTimeFormatter.ofPattern("MMdd")));

        // Card number (Field 2)
        String cardNumber = getCardNumber();
        if (cardNumber != null && !cardNumber.isEmpty()) {
            message.setField(2, cardNumber);
        }

        // ATM Terminal ID (Field 41)
        String atmId = getAtmId();
        if (atmId != null && !atmId.isEmpty()) {
            message.setField(41, atmId);
        } else {
            message.setField(41, "ATM00001");
        }

        // ATM Location (Field 43)
        String atmLocation = getAtmLocation();
        if (atmLocation != null && !atmLocation.isEmpty()) {
            message.setField(43, String.format("%-40s", atmLocation));
        }

        // Acquiring Institution (Field 32) - Bank code
        String bankCode = getBankCode();
        if (bankCode != null && !bankCode.isEmpty()) {
            message.setField(32, bankCode);
        }

        // POS Entry Mode (Field 22) - 051 for chip card
        message.setField(22, "051");

        // POS Condition Code (Field 25) - 00 for normal
        message.setField(25, "00");

        // Generate RRN (Field 37)
        String rrn = String.format("%012d", System.currentTimeMillis() % 1000000000000L);
        message.setField(37, rrn);
    }

    private void setAmountField(Iso8583Message message) {
        String amount = getAmount();
        if (amount != null && !amount.isEmpty()) {
            // Convert to 12-digit format (cents)
            long amountValue = Long.parseLong(amount.replaceAll("[^0-9]", ""));
            message.setField(4, String.format("%012d", amountValue));
            // Currency code (Field 49) - TWD = 901
            message.setField(49, "901");
        }
    }

    private void setTransferFields(Iso8583Message message) {
        String destAccount = getDestinationAccount();
        if (destAccount != null && !destAccount.isEmpty()) {
            // Destination account (Field 103)
            message.setField(103, destAccount);
        }

        String cardNumber = getCardNumber();
        if (cardNumber != null && !cardNumber.isEmpty()) {
            // Source account (Field 102) - usually derived from card
            message.setField(102, cardNumber);
        }
    }

    private void applyCustomFields(Iso8583Message message) {
        String customFieldsStr = getCustomFields();
        if (customFieldsStr == null || customFieldsStr.isEmpty()) {
            return;
        }

        for (String pair : customFieldsStr.split(";")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                try {
                    int fieldNum = Integer.parseInt(kv[0].trim());
                    String value = substituteVariables(kv[1].trim());
                    message.setField(fieldNum, value);
                } catch (NumberFormatException e) {
                    log.warn("Invalid field number: {}", kv[0]);
                }
            }
        }
    }

    /**
     * Finds TransactionTemplateConfig in the current test plan.
     * If not found, returns a default config using COMMON templates.
     */
    private TransactionTemplateConfig findTemplateConfig() {
        JMeterContext context = JMeterContextService.getContext();
        if (context != null) {
            // Search for TransactionTemplateConfig in the sampler's scope
            org.apache.jmeter.testelement.TestElement current = context.getCurrentSampler();
            if (current != null) {
                // Try to find config from thread group's config elements
                // This is a simplified approach - in real JMeter, configs are
                // automatically merged into the sampler's scope
                org.apache.jmeter.threads.JMeterThread thread =
                    org.apache.jmeter.threads.JMeterContextService.getContext().getThread();
                if (thread != null) {
                    // Check if there's a TransactionTemplateConfig in scope
                    // For now, we'll create a default one using COMMON templates
                }
            }
        }

        // Default: use COMMON templates
        TransactionTemplateConfig defaultConfig = new TransactionTemplateConfig();
        defaultConfig.setTemplateSource("COMMON");
        defaultConfig.setAutoGenerateStan(true);
        defaultConfig.setAutoGenerateTimestamp(true);
        return defaultConfig;
    }

    /**
     * Builds an ISO 8583 message from TransactionTemplateConfig.
     */
    private Iso8583Message buildFromTemplateConfig() {
        TransactionTemplateConfig config = findTemplateConfig();
        String templateName = getTemplateName();

        // Get template
        Optional<TransactionTemplate> templateOpt = config.getTemplate(templateName);
        if (templateOpt.isEmpty()) {
            log.warn("Template not found: {}. Using Balance Inquiry as fallback.", templateName);
            templateOpt = Optional.of(TransactionTemplate.CommonTemplates.balanceInquiry());
        }

        TransactionTemplate template = templateOpt.get();

        // Build variables
        Map<String, String> variables = buildTemplateVariables();

        // Create message from template
        Iso8583Message message = template.createMessage(variables);

        // Apply MTI override if provided
        String mtiOverride = getMtiOverride();
        if (mtiOverride != null && !mtiOverride.isEmpty()) {
            message.setMti(mtiOverride);
        }

        // Apply processing code override if provided
        String processingCodeOverride = getProcessingCodeOverride();
        if (processingCodeOverride != null && !processingCodeOverride.isEmpty()) {
            message.setField(3, processingCodeOverride);
        }

        // Apply custom fields (highest priority, can override anything)
        applyCustomFields(message);

        return message;
    }

    /**
     * Builds variable map for template substitution.
     */
    private Map<String, String> buildTemplateVariables() {
        Map<String, String> variables = new HashMap<>();

        // Auto-generate STAN
        String stan = String.format("%06d", stanCounter.incrementAndGet() % 1000000);
        variables.put("stan", stan);

        // Auto-generate timestamps
        LocalDateTime now = LocalDateTime.now();
        variables.put("time", now.format(DateTimeFormatter.ofPattern("HHmmss")));
        variables.put("date", now.format(DateTimeFormatter.ofPattern("MMdd")));
        variables.put("datetime", now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));

        // From sampler properties
        addIfNotEmpty(variables, "amount", formatAmount(getAmount()));
        addIfNotEmpty(variables, "cardNumber", getCardNumber());
        addIfNotEmpty(variables, "pan", getCardNumber());
        addIfNotEmpty(variables, "terminalId", getAtmId());
        addIfNotEmpty(variables, "atmId", getAtmId());
        addIfNotEmpty(variables, "atmLocation", getAtmLocation());
        addIfNotEmpty(variables, "bankCode", getBankCode());
        addIfNotEmpty(variables, "destAccount", getDestinationAccount());
        addIfNotEmpty(variables, "sourceAccount", getCardNumber());

        // Generate RRN
        String rrn = String.format("%012d", System.currentTimeMillis() % 1000000000000L);
        variables.put("rrn", rrn);

        // From JMeter variables
        JMeterContext context = JMeterContextService.getContext();
        if (context != null && context.getVariables() != null) {
            JMeterVariables vars = context.getVariables();
            // Add any JMeter variables that might be useful for templates
            for (String varName : new String[]{"amount", "cardNumber", "pan", "terminalId",
                    "atmId", "bankCode", "destAccount", "sourceAccount", "billerCode", "billReference"}) {
                String value = vars.get(varName);
                if (value != null && !variables.containsKey(varName)) {
                    variables.put(varName, value);
                }
            }
        }

        return variables;
    }

    /**
     * Formats amount to 12-digit format.
     */
    private String formatAmount(String amount) {
        if (amount == null || amount.isEmpty()) {
            return "";
        }
        try {
            long amountValue = Long.parseLong(amount.replaceAll("[^0-9]", ""));
            return String.format("%012d", amountValue);
        } catch (NumberFormatException e) {
            return amount;
        }
    }

    /**
     * Adds value to map if not empty.
     */
    private void addIfNotEmpty(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    private String substituteVariables(String value) {
        JMeterContext context = JMeterContextService.getContext();
        if (context != null) {
            JMeterVariables vars = context.getVariables();
            if (vars != null) {
                while (value.contains("${")) {
                    int start = value.indexOf("${");
                    int end = value.indexOf("}", start);
                    if (end > start) {
                        String varName = value.substring(start + 2, end);
                        String varValue = vars.get(varName);
                        if (varValue != null) {
                            value = value.substring(0, start) + varValue + value.substring(end + 1);
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }
        return value;
    }

    private String formatMessageForDisplay(Iso8583Message message) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ATM Transaction ===\n");
        sb.append("MTI: ").append(message.getMti()).append("\n");
        sb.append("Fields:\n");
        for (Integer fieldNum : message.getFieldNumbers()) {
            Object value = message.getField(fieldNum);
            String displayValue = maskSensitiveField(fieldNum, value);
            sb.append(String.format("  F%03d: %s\n", fieldNum, displayValue));
        }
        return sb.toString();
    }

    private String maskSensitiveField(int fieldNum, Object value) {
        if (value == null) return "null";
        String strValue = value.toString();
        return switch (fieldNum) {
            case 2 -> maskPan(strValue);
            case 14 -> "****";
            case 35, 36 -> "****";
            case 52 -> "****";
            default -> strValue;
        };
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "****";
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }

    private void storeResponseVariables(Iso8583Message response) {
        JMeterContext context = JMeterContextService.getContext();
        if (context != null) {
            JMeterVariables vars = context.getVariables();
            if (vars != null) {
                vars.put("ATM_MTI", response.getMti());
                vars.put("ATM_RESPONSE_CODE", response.getFieldAsString(39));
                vars.put("ATM_STAN", response.getFieldAsString(11));
                vars.put("ATM_RRN", response.getFieldAsString(37));
                vars.put("ATM_AUTH_CODE", response.getFieldAsString(38));

                String balance = response.getFieldAsString(54);
                if (balance != null) {
                    vars.put("ATM_BALANCE", balance);
                }
            }
        }
    }

    private String getResponseMessage(String responseCode) {
        if (responseCode == null) return "Unknown";
        return switch (responseCode) {
            case "00" -> "Approved";
            case "01" -> "Refer to card issuer";
            case "03" -> "Invalid merchant";
            case "04" -> "Capture card";
            case "05" -> "Do not honor";
            case "12" -> "Invalid transaction";
            case "13" -> "Invalid amount";
            case "14" -> "Invalid card number";
            case "30" -> "Format error";
            case "41" -> "Lost card - capture";
            case "43" -> "Stolen card - capture";
            case "51" -> "Insufficient funds";
            case "54" -> "Expired card";
            case "55" -> "Incorrect PIN";
            case "57" -> "Transaction not permitted to cardholder";
            case "58" -> "Transaction not permitted to terminal";
            case "61" -> "Exceeds withdrawal amount limit";
            case "65" -> "Exceeds withdrawal frequency limit";
            case "75" -> "Allowable number of PIN tries exceeded";
            case "91" -> "Issuer or switch is inoperative";
            case "96" -> "System malfunction";
            default -> "Response: " + responseCode;
        };
    }

    // TestStateListener implementation
    @Override
    public void testStarted() {
        testStarted("");
    }

    @Override
    public void testStarted(String host) {
        log.info("ATM simulator test started on {}", host);
    }

    @Override
    public void testEnded() {
        testEnded("");
    }

    @Override
    public void testEnded(String host) {
        log.info("ATM simulator test ended on {}, closing connections", host);
        synchronized (channelLock) {
            // Close ISO 8583 channels
            channelPool.values().forEach(holder -> {
                try {
                    if (holder.channel != null && holder.channel.isOpen()) {
                        holder.channel.close().sync();
                    }
                } catch (Exception e) {
                    log.warn("Error closing ATM channel", e);
                }
            });
            channelPool.clear();

            // Close RAW mode channels
            rawChannelPool.values().forEach(holder -> {
                try {
                    if (holder.channel != null && holder.channel.isOpen()) {
                        holder.channel.close().sync();
                    }
                } catch (Exception e) {
                    log.warn("Error closing RAW channel", e);
                }
            });
            rawChannelPool.clear();
        }

        if (workerGroup != null && !workerGroup.isShutdown()) {
            workerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            workerGroup = null;
        }
    }

    // Getters and Setters
    public String getFepHost() {
        return getPropertyAsString(FEP_HOST, "localhost");
    }

    public void setFepHost(String host) {
        setProperty(FEP_HOST, host);
    }

    public int getFepPort() {
        return getPropertyAsInt(FEP_PORT, 8080);
    }

    public void setFepPort(int port) {
        setProperty(FEP_PORT, port);
    }

    public int getConnectionTimeout() {
        return getPropertyAsInt(CONNECTION_TIMEOUT, 10000);
    }

    public void setConnectionTimeout(int timeout) {
        setProperty(CONNECTION_TIMEOUT, timeout);
    }

    public int getReadTimeout() {
        return getPropertyAsInt(READ_TIMEOUT, 30000);
    }

    public void setReadTimeout(int timeout) {
        setProperty(READ_TIMEOUT, timeout);
    }

    public String getTransactionType() {
        return getPropertyAsString(TRANSACTION_TYPE, AtmTransactionType.BALANCE_INQUIRY.name());
    }

    public void setTransactionType(String type) {
        setProperty(TRANSACTION_TYPE, type);
    }

    public String getAtmId() {
        return getPropertyAsString(ATM_ID, "");
    }

    public void setAtmId(String atmId) {
        setProperty(ATM_ID, atmId);
    }

    public String getAtmLocation() {
        return getPropertyAsString(ATM_LOCATION, "");
    }

    public void setAtmLocation(String location) {
        setProperty(ATM_LOCATION, location);
    }

    public String getCardNumber() {
        return getPropertyAsString(CARD_NUMBER, "");
    }

    public void setCardNumber(String cardNumber) {
        setProperty(CARD_NUMBER, cardNumber);
    }

    public String getAmount() {
        return getPropertyAsString(AMOUNT, "");
    }

    public void setAmount(String amount) {
        setProperty(AMOUNT, amount);
    }

    public String getDestinationAccount() {
        return getPropertyAsString(DESTINATION_ACCOUNT, "");
    }

    public void setDestinationAccount(String account) {
        setProperty(DESTINATION_ACCOUNT, account);
    }

    public String getBankCode() {
        return getPropertyAsString(BANK_CODE, "");
    }

    public void setBankCode(String bankCode) {
        setProperty(BANK_CODE, bankCode);
    }

    public String getCustomFields() {
        return getPropertyAsString(CUSTOM_FIELDS, "");
    }

    public void setCustomFields(String fields) {
        setProperty(CUSTOM_FIELDS, fields);
    }

    public String getMtiOverride() {
        return getPropertyAsString(MTI_OVERRIDE, "");
    }

    public void setMtiOverride(String mti) {
        setProperty(MTI_OVERRIDE, mti);
    }

    public String getProcessingCodeOverride() {
        return getPropertyAsString(PROCESSING_CODE_OVERRIDE, "");
    }

    public void setProcessingCodeOverride(String code) {
        setProperty(PROCESSING_CODE_OVERRIDE, code);
    }

    public String getMessageTemplate() {
        return getPropertyAsString(MESSAGE_TEMPLATE, "");
    }

    public void setMessageTemplate(String template) {
        setProperty(MESSAGE_TEMPLATE, template);
    }

    public boolean isEnablePinBlock() {
        return getPropertyAsBoolean(ENABLE_PIN_BLOCK, false);
    }

    public void setEnablePinBlock(boolean enable) {
        setProperty(ENABLE_PIN_BLOCK, enable);
    }

    public String getPinBlock() {
        return getPropertyAsString(PIN_BLOCK, "");
    }

    public void setPinBlock(String pinBlock) {
        setProperty(PIN_BLOCK, pinBlock);
    }

    // ===== RAW Mode Getters and Setters =====

    public String getProtocolType() {
        return getPropertyAsString(PROTOCOL_TYPE, AtmProtocolType.ISO_8583.name());
    }

    public void setProtocolType(String type) {
        setProperty(PROTOCOL_TYPE, type);
    }

    public String getRawMessageFormat() {
        return getPropertyAsString(RAW_MESSAGE_FORMAT, RawMessageFormat.HEX.name());
    }

    public void setRawMessageFormat(String format) {
        setProperty(RAW_MESSAGE_FORMAT, format);
    }

    public String getRawMessageData() {
        return getPropertyAsString(RAW_MESSAGE_DATA, "");
    }

    public void setRawMessageData(String data) {
        setProperty(RAW_MESSAGE_DATA, data);
    }

    public String getLengthHeaderType() {
        return getPropertyAsString(LENGTH_HEADER_TYPE, LengthHeaderType.TWO_BYTES.name());
    }

    public void setLengthHeaderType(String type) {
        setProperty(LENGTH_HEADER_TYPE, type);
    }

    public boolean isExpectResponse() {
        return getPropertyAsBoolean(EXPECT_RESPONSE, true);
    }

    public void setExpectResponse(boolean expect) {
        setProperty(EXPECT_RESPONSE, expect);
    }

    public String getResponseMatchPattern() {
        return getPropertyAsString(RESPONSE_MATCH_PATTERN, "");
    }

    public void setResponseMatchPattern(String pattern) {
        setProperty(RESPONSE_MATCH_PATTERN, pattern);
    }

    // ===== Template Config Getters and Setters =====

    public boolean isUseTemplateConfig() {
        return getPropertyAsBoolean(USE_TEMPLATE_CONFIG, false);
    }

    public void setUseTemplateConfig(boolean use) {
        setProperty(USE_TEMPLATE_CONFIG, use);
    }

    public String getTemplateName() {
        return getPropertyAsString(TEMPLATE_NAME, "Withdrawal");
    }

    public void setTemplateName(String name) {
        setProperty(TEMPLATE_NAME, name);
    }

    /**
     * Holds channel and pending requests for response matching.
     */
    private static class ChannelHolder {
        Channel channel;
        final Map<String, CompletableFuture<Iso8583Message>> pendingRequests = new ConcurrentHashMap<>();
    }

    /**
     * Handler for ATM responses.
     */
    private static class AtmResponseHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final ChannelHolder holder;

        AtmResponseHandler(ChannelHolder holder) {
            this.holder = holder;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            byte[] data = new byte[msg.readableBytes()];
            msg.readBytes(data);

            try {
                Iso8583Message response = messageParser.parse(data);

                String stan = response.getFieldAsString(11);
                CompletableFuture<Iso8583Message> future = holder.pendingRequests.remove(stan);

                if (future != null) {
                    future.complete(response);
                } else {
                    log.warn("Received response with unknown STAN: {}", stan);
                }
            } catch (Exception e) {
                log.error("Error parsing ATM response", e);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("ATM channel error", cause);
            // Complete all pending futures with exception
            holder.pendingRequests.forEach((stan, future) ->
                future.completeExceptionally(cause));
            holder.pendingRequests.clear();
            ctx.close();
        }
    }

    /**
     * Holds channel and pending requests for RAW mode response matching.
     */
    private static class RawChannelHolder {
        Channel channel;
        String lastRequestId;
        final Map<String, CompletableFuture<byte[]>> pendingRequests = new ConcurrentHashMap<>();
    }

    /**
     * Handler for RAW mode responses.
     */
    private static class RawResponseHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final RawChannelHolder holder;

        RawResponseHandler(RawChannelHolder holder) {
            this.holder = holder;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            byte[] data = new byte[msg.readableBytes()];
            msg.readBytes(data);

            // For RAW mode, we complete the last pending request since there's no STAN matching
            String requestId = holder.lastRequestId;
            if (requestId != null) {
                CompletableFuture<byte[]> future = holder.pendingRequests.get(requestId);
                if (future != null) {
                    future.complete(data);
                    log.debug("RAW response received: {} bytes", data.length);
                }
            } else {
                log.warn("Received RAW response with no pending request");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("RAW channel error", cause);
            // Complete all pending futures with exception
            holder.pendingRequests.forEach((id, future) ->
                future.completeExceptionally(cause));
            holder.pendingRequests.clear();
            ctx.close();
        }
    }
}
