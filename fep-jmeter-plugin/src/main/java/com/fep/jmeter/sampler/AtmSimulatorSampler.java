package com.fep.jmeter.sampler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.parser.GenericMessageAssembler;
import com.fep.message.generic.parser.GenericMessageParser;
import com.fep.message.generic.schema.MessageSchema;
import com.fep.message.generic.schema.JsonSchemaLoader;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import com.fep.jmeter.codec.GenericLengthFieldDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
// TestBean removed to use custom GUI (AtmSimulatorSamplerGui)
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMeter Sampler for simulating ATM transactions using Generic Schema.
 *
 * <p>This sampler uses JSON Schema to define message formats, supporting
 * various ATM protocols including FISC, NCR NDC, and custom formats.
 *
 * <p>Message structure is defined by the schema, which specifies:
 * <ul>
 *   <li>Field definitions (id, type, length, encoding)</li>
 *   <li>Header configuration (length field handling)</li>
 *   <li>Validation rules</li>
 * </ul>
 */
@Slf4j
public class AtmSimulatorSampler extends AbstractSampler implements TestStateListener {

    private static final long serialVersionUID = 1L;

    // Property names - Connection
    public static final String FEP_HOST = "fepHost";
    public static final String FEP_PORT = "fepPort";
    public static final String CONNECTION_TIMEOUT = "connectionTimeout";
    public static final String READ_TIMEOUT = "readTimeout";
    public static final String EXPECT_RESPONSE = "expectResponse";

    // Property names - Schema Settings
    public static final String SCHEMA_FILE = "schemaFile";
    public static final String SELECTED_SCHEMA = "selectedSchema";
    public static final String FIELD_VALUES = "fieldValues";

    // Property names - Response Schema
    public static final String USE_DIFFERENT_RESPONSE_SCHEMA = "useDifferentResponseSchema";
    public static final String RESPONSE_SCHEMA_SOURCE = "responseSchemaSource";
    public static final String RESPONSE_SCHEMA_FILE = "responseSchemaFile";
    public static final String RESPONSE_SELECTED_SCHEMA = "responseSelectedSchema";

    // Static resources
    private static final Map<String, ChannelHolder> channelPool = new ConcurrentHashMap<>();
    private static final Object channelLock = new Object();
    private static EventLoopGroup workerGroup;
    private static final AtomicLong messageCounter = new AtomicLong(0);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HexFormat hexFormat = HexFormat.of();

    public AtmSimulatorSampler() {
        super();
        setName("ATM Simulator");
        // Register a custom GUI class
        setProperty(TestElement.GUI_CLASS, "com.fep.jmeter.gui.AtmSimulatorSamplerGui");
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);

        String fepHost = getFepHost();
        int fepPort = getFepPort();

        try {
            // Load schema
            MessageSchema schema = loadMessageSchema();

            // Create a message
            GenericMessage message = new GenericMessage(schema);

            // Apply field values from JSON configuration
            try {
                applyFieldValuesToGenericMessage(message);
            } catch (FieldValuesParseException e) {
                result.sampleEnd();
                result.setSuccessful(false);
                result.setResponseCode("JSON_PARSE_ERROR");
                result.setResponseMessage(e.getMessage());
                return result;
            }

            // Populate schema default values (before variable substitution)
            message.populateDefaults();

            // Apply JMeter variables to all fields (including defaults with ${var} placeholders)
            Map<String, String> variables = buildVariables();
            message.applyVariables(variables);

            // Validate a message
            var validationResult = message.validate();
            if (!validationResult.isValid()) {
                result.sampleEnd();
                result.setSuccessful(false);
                result.setResponseCode("VALIDATION_ERROR");
                result.setResponseMessage("Message validation failed: " + validationResult.errors());
                return result;
            }

            // Assemble a message (includes length header based on schema)
            GenericMessageAssembler assembler = new GenericMessageAssembler();
            byte[] messageBytes = assembler.assemble(message);

            // Format request for display (request bytes include length field)
            String requestStr = formatMessageForDisplay(message, messageBytes, true);
            result.setSamplerData(requestStr);

            // Get or create a channel
            ChannelHolder holder = getOrCreateChannel(fepHost, fepPort, schema);

            // Create a response future
            CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();
            String messageId = String.valueOf(messageCounter.incrementAndGet());
            holder.pendingRequests.put(messageId, responseFuture);
            holder.lastRequestId = messageId;

            // Start timing
            result.sampleStart();

            // Send message directly (length header already included by assembler)
            log.info("Sending request: {} bytes to {}:{}", messageBytes.length, fepHost, fepPort);
            log.info("Request hex: {}", hexFormat.formatHex(messageBytes));

            ByteBuf buf = Unpooled.wrappedBuffer(messageBytes);
            holder.channel.writeAndFlush(buf).sync();
            log.info("Request sent successfully, waiting for response (timeout={}ms)", getReadTimeout());

            // Wait for response
            if (isExpectResponse()) {
                byte[] responseBytes = responseFuture.get(getReadTimeout(), TimeUnit.MILLISECONDS);
                log.info("Response received: {} bytes", responseBytes.length);

                // Stop timing
                result.sampleEnd();

                // Load response schema (may be different from request schema)
                MessageSchema responseSchema = loadResponseSchema(schema);
                log.debug("Using response schema: {}", responseSchema.getName());

                // Parse response
                // Note: skipLengthField=true because GenericLengthFieldDecoder already stripped the length field
                GenericMessageParser parser = new GenericMessageParser();
                GenericMessage response = parser.parse(responseBytes, responseSchema, true);

                // Format response (response bytes don't include length field - stripped by decoder)
                String responseStr = formatMessageForDisplay(response, responseBytes, false);
                result.setResponseData(responseStr, StandardCharsets.UTF_8.name());

                // Try to get response code from common field names
                String responseCode = getResponseCodeFromMessage(response);
                result.setResponseCode(responseCode != null ? responseCode : "OK");
                result.setResponseMessage(getResponseMessage(responseCode));

                // Check success
                boolean success = responseCode == null || "00".equals(responseCode) || "000".equals(responseCode);
                result.setSuccessful(success);

                // Store response variables
                storeResponseVariables(response);

                log.debug("ATM transaction completed: {}", responseCode);
            } else {
                result.sampleEnd();
                result.setSuccessful(true);
                result.setResponseCode("SENT");
                result.setResponseMessage("Message sent, no response expected");
            }

        } catch (TimeoutException e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("TIMEOUT");
            result.setResponseMessage("Response timeout");
            result.setResponseData("Timeout waiting for response", StandardCharsets.UTF_8.name());
            log.warn("ATM transaction timeout");
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
     * Load message schema from schema collection file.
     * Schema file path defaults to ${user.dir}/schemas/atm-schemas.json
     */
    private MessageSchema loadMessageSchema() {
        String schemaFile = getSchemaFile();
        if (schemaFile == null || schemaFile.isBlank()) {
            schemaFile = SchemaSource.getDefaultSchemaPath();
        } else {
            schemaFile = substituteVariables(schemaFile);
        }

        String selectedSchema = getSelectedSchema();
        if (selectedSchema == null || selectedSchema.isBlank()) {
            selectedSchema = "FISC ATM Format"; // default schema
        }

        return JsonSchemaLoader.fromCollectionFile(Path.of(schemaFile), selectedSchema);
    }

    /**
     * Load response schema based on configuration.
     * If "use different response schema" is disabled, returns the request schema.
     *
     * @param requestSchema the request schema to use as fallback
     * @return the response schema
     */
    private MessageSchema loadResponseSchema(MessageSchema requestSchema) {
        if (!isUseDifferentResponseSchema()) {
            return requestSchema;
        }

        ResponseSchemaSource source = ResponseSchemaSource.fromString(getResponseSchemaSource());

        return switch (source) {
            case SAME_AS_REQUEST -> requestSchema;
            case FILE -> {
                String schemaFile = getResponseSchemaFile();
                if (schemaFile == null || schemaFile.isBlank()) {
                    schemaFile = SchemaSource.getDefaultSchemaPath();
                } else {
                    schemaFile = substituteVariables(schemaFile);
                }

                String selectedSchema = getResponseSelectedSchema();
                if (selectedSchema == null || selectedSchema.isBlank()) {
                    selectedSchema = "FISC ATM Format"; // default schema
                }

                yield JsonSchemaLoader.fromCollectionFile(Path.of(schemaFile), selectedSchema);
            }
        };
    }

    /**
     * Apply field values from JSON configuration to generic message.
     *
     * @throws FieldValuesParseException if JSON parsing fails
     */
    @SuppressWarnings("unchecked")
    private void applyFieldValuesToGenericMessage(GenericMessage message) throws FieldValuesParseException {
        String fieldValuesJson = getFieldValues();
        if (fieldValuesJson == null || fieldValuesJson.isBlank()) {
            return;
        }

        try {
            Map<String, Object> values = objectMapper.readValue(
                    substituteVariables(fieldValuesJson),
                    new TypeReference<Map<String, Object>>() {});

            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String fieldId = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof Map) {
                    // Composite field
                    Map<String, Object> nested = (Map<String, Object>) value;
                    for (Map.Entry<String, Object> nestedEntry : nested.entrySet()) {
                        message.setNestedField(fieldId, nestedEntry.getKey(), nestedEntry.getValue());
                    }
                    message.setField(fieldId, nested);
                } else {
                    message.setField(fieldId, value);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse field values JSON: {}", e.getMessage());
            throw new FieldValuesParseException(
                    "Invalid JSON format in Field Values: " + e.getMessage(), e);
        }
    }

    /**
     * Build variables map for template substitution.
     */
    private Map<String, String> buildVariables() {
        Map<String, String> variables = new HashMap<>();

        // Auto-generated values
        LocalDateTime now = LocalDateTime.now();
        variables.put("stan", String.format("%06d", messageCounter.incrementAndGet() % 1000000));
        variables.put("time", now.format(DateTimeFormatter.ofPattern("HHmmss")));
        variables.put("date", now.format(DateTimeFormatter.ofPattern("MMdd")));
        variables.put("datetime", now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        variables.put("rrn", String.format("%012d", System.currentTimeMillis() % 1000000000000L));

        // Default ATM-related variables (can be overridden by JMeter variables)
        variables.put("atmLocation", "ATM LOCATION                            ");
        variables.put("merchantId", "MERCHANT123456 ");
        variables.put("currencyCode", "901");

        // Add JMeter variables (these will override defaults above)
        JMeterVariables jmeterVars = JMeterContextService.getContext().getVariables();
        if (jmeterVars != null) {
            for (java.util.Iterator<Map.Entry<String, Object>> it = jmeterVars.getIterator(); it.hasNext(); ) {
                Map.Entry<String, Object> entry = it.next();
                if (entry.getValue() != null) {
                    variables.put(entry.getKey(), entry.getValue().toString());
                }
            }
        }

        return variables;
    }

    /**
     * Format message for display (includes default values from schema).
     *
     * @param message the message to display
     * @param bytes the raw bytes
     * @param bytesIncludeLengthField true if bytes include the length field (request),
     *                                 false if length field was stripped (response)
     */
    private String formatMessageForDisplay(GenericMessage message, byte[] bytes, boolean bytesIncludeLengthField) {
        StringBuilder sb = new StringBuilder();
        MessageSchema schema = message.getSchema();

        sb.append("=== ATM Message (Generic Schema) ===\n");
        sb.append("Schema: ").append(schema.getName()).append("\n");
        sb.append("Length: ").append(bytes.length).append(" bytes\n\n");

        // Display header configuration
        MessageSchema.HeaderSection header = schema.getHeader();
        if (header != null) {
            sb.append("Header Configuration:\n");
            sb.append("  Include Length: ").append(header.isIncludeLength()).append("\n");
            if (header.isIncludeLength()) {
                sb.append("  Length Bytes: ").append(header.getLengthBytes()).append("\n");
                sb.append("  Length Encoding: ").append(header.getLengthEncoding()).append("\n");
                sb.append("  Length Includes Header: ").append(header.isLengthIncludesHeader()).append("\n");

                // Display actual length value from bytes (only if bytes include the length field)
                if (bytesIncludeLengthField && bytes.length >= header.getLengthBytes()) {
                    sb.append("  Length Value: ");
                    sb.append(formatLengthValue(bytes, header.getLengthBytes(), header.getLengthEncoding()));
                    sb.append("\n");
                } else if (!bytesIncludeLengthField) {
                    sb.append("  Length Value: (stripped by decoder)\n");
                }
            }

            // Display header fields if any
            if (header.getFields() != null && !header.getFields().isEmpty()) {
                sb.append("  Header Fields:\n");
                for (var fieldSchema : header.getFields()) {
                    String fieldId = fieldSchema.getId();
                    Object value = message.getField(fieldId);
                    if (value == null) {
                        value = fieldSchema.getDefaultValue();
                    }
                    sb.append("    ").append(fieldId).append("=");
                    if (fieldSchema.isSensitive()) {
                        sb.append("****");
                    } else {
                        sb.append(value != null ? value : "(not set)");
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("Fields:\n");
        sb.append(message.toString(true)).append("\n\n");  // Include default values

        sb.append("Hex:\n");
        sb.append(hexFormat.formatHex(bytes)).append("\n");

        return sb.toString();
    }

    /**
     * Format the length value from the message bytes based on encoding.
     */
    private String formatLengthValue(byte[] bytes, int lengthBytes, String encoding) {
        if (bytes.length < lengthBytes) {
            return "(insufficient bytes)";
        }

        StringBuilder sb = new StringBuilder();

        if ("BCD".equalsIgnoreCase(encoding)) {
            // BCD: each byte represents 2 decimal digits
            int value = 0;
            for (int i = 0; i < lengthBytes; i++) {
                int high = (bytes[i] & 0xF0) >> 4;
                int low = bytes[i] & 0x0F;
                value = value * 100 + high * 10 + low;
            }
            sb.append(value);
            sb.append(" (BCD: ");
            for (int i = 0; i < lengthBytes; i++) {
                sb.append(String.format("%02X", bytes[i] & 0xFF));
            }
            sb.append(")");
        } else if ("BINARY".equalsIgnoreCase(encoding)) {
            // Binary: big-endian
            int value = 0;
            for (int i = 0; i < lengthBytes; i++) {
                value = (value << 8) | (bytes[i] & 0xFF);
            }
            sb.append(value);
            sb.append(" (BINARY: ");
            for (int i = 0; i < lengthBytes; i++) {
                sb.append(String.format("%02X", bytes[i] & 0xFF));
            }
            sb.append(")");
        } else {
            // ASCII: each byte is a digit character
            sb.append(new String(bytes, 0, lengthBytes, java.nio.charset.StandardCharsets.US_ASCII));
            sb.append(" (ASCII)");
        }

        return sb.toString();
    }

    /**
     * Try to get response code from message.
     */
    private String getResponseCodeFromMessage(GenericMessage message) {
        // Try common response code field names
        String[] responseCodeFields = {"responseCode", "response_code", "respCode", "rc", "39"};
        for (String fieldId : responseCodeFields) {
            String value = message.getFieldAsString(fieldId);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Store message fields as JMeter variables.
     */
    private void storeResponseVariables(GenericMessage response) {
        JMeterContext context = JMeterContextService.getContext();
        JMeterVariables vars = context.getVariables();
        if (vars == null) return;

        for (Map.Entry<String, Object> entry : response.getAllFields().entrySet()) {
            String key = "RESPONSE_" + entry.getKey();
            vars.put(key, entry.getValue() != null ? entry.getValue().toString() : "");
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

    private String getResponseMessage(String responseCode) {
        if (responseCode == null) return "OK";
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

    /**
     * Get or create channel based on schema header configuration.
     */
    private ChannelHolder getOrCreateChannel(String host, int port, MessageSchema schema) throws Exception {
        String key = Thread.currentThread().getName() + "_" + host + "_" + port;

        synchronized (channelLock) {
            ChannelHolder holder = channelPool.get(key);

            if (holder == null || !holder.channel.isActive()) {
                // Initialize worker group if needed
                if (workerGroup == null || workerGroup.isShutdown()) {
                    workerGroup = new NioEventLoopGroup(2);
                }

                // Determine length field configuration from schema
                int lengthFieldLength = 2; // default
                String lengthEncoding = "BCD"; // default
                boolean lengthIncludesHeader = false;

                if (schema.getHeader() != null && schema.getHeader().isIncludeLength()) {
                    lengthFieldLength = schema.getHeader().getLengthBytes();
                    lengthEncoding = schema.getHeader().getLengthEncoding();
                    lengthIncludesHeader = schema.getHeader().isLengthIncludesHeader();
                }

                final int finalLengthFieldBytes = lengthFieldLength;
                final String finalLengthEncoding = lengthEncoding;
                final boolean finalLengthIncludesHeader = lengthIncludesHeader;

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
                        // Frame decoder based on schema header configuration
                        // Uses GenericLengthFieldDecoder to support ASCII/BCD/BINARY length encodings
                        pipeline.addLast(new GenericLengthFieldDecoder(
                            finalLengthFieldBytes, finalLengthEncoding,
                            finalLengthIncludesHeader, 65535));
                        pipeline.addLast(new ReadTimeoutHandler(getReadTimeout(), TimeUnit.MILLISECONDS));
                        pipeline.addLast(new ResponseHandler(finalHolder));
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

    public boolean isExpectResponse() {
        return getPropertyAsBoolean(EXPECT_RESPONSE, true);
    }

    public void setExpectResponse(boolean expect) {
        setProperty(EXPECT_RESPONSE, expect);
    }

    public String getSchemaFile() {
        return getPropertyAsString(SCHEMA_FILE, SchemaSource.getDefaultSchemaPath());
    }

    public void setSchemaFile(String file) {
        setProperty(SCHEMA_FILE, file);
    }

    public String getSelectedSchema() {
        return getPropertyAsString(SELECTED_SCHEMA, "FISC ATM Format");
    }

    public void setSelectedSchema(String schema) {
        setProperty(SELECTED_SCHEMA, schema);
    }

    public String getFieldValues() {
        return getPropertyAsString(FIELD_VALUES, "");
    }

    public void setFieldValues(String values) {
        setProperty(FIELD_VALUES, values);
    }

    // Response Schema Getters/Setters

    public boolean isUseDifferentResponseSchema() {
        return getPropertyAsBoolean(USE_DIFFERENT_RESPONSE_SCHEMA, false);
    }

    public void setUseDifferentResponseSchema(boolean useDifferent) {
        setProperty(USE_DIFFERENT_RESPONSE_SCHEMA, useDifferent);
    }

    public String getResponseSchemaSource() {
        return getPropertyAsString(RESPONSE_SCHEMA_SOURCE, ResponseSchemaSource.SAME_AS_REQUEST.name());
    }

    public void setResponseSchemaSource(String source) {
        setProperty(RESPONSE_SCHEMA_SOURCE, source);
    }

    public String getResponseSchemaFile() {
        return getPropertyAsString(RESPONSE_SCHEMA_FILE, SchemaSource.getDefaultSchemaPath());
    }

    public void setResponseSchemaFile(String file) {
        setProperty(RESPONSE_SCHEMA_FILE, file);
    }

    public String getResponseSelectedSchema() {
        return getPropertyAsString(RESPONSE_SELECTED_SCHEMA, "FISC ATM Format");
    }

    public void setResponseSelectedSchema(String schema) {
        setProperty(RESPONSE_SELECTED_SCHEMA, schema);
    }

    /**
     * Holds channel and pending requests for response matching.
     */
    private static class ChannelHolder {
        Channel channel;
        String lastRequestId;
        final Map<String, CompletableFuture<byte[]>> pendingRequests = new ConcurrentHashMap<>();
    }

    /**
     * Handler for responses.
     */
    private static class ResponseHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final ChannelHolder holder;

        ResponseHandler(ChannelHolder holder) {
            this.holder = holder;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            byte[] data = new byte[msg.readableBytes()];
            msg.readBytes(data);

            log.info("Response received: {} bytes, hex: {}", data.length, hexFormat.formatHex(data));

            // Complete the last pending request
            String requestId = holder.lastRequestId;
            if (requestId != null) {
                CompletableFuture<byte[]> future = holder.pendingRequests.remove(requestId);
                if (future != null) {
                    future.complete(data);
                    log.debug("Response completed for requestId={}", requestId);
                } else {
                    log.warn("No future found for requestId={}", requestId);
                }
            } else {
                log.warn("Received response with no pending request");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Channel error", cause);
            // Complete all pending futures with exception
            holder.pendingRequests.forEach((id, future) ->
                future.completeExceptionally(cause));
            holder.pendingRequests.clear();
            ctx.close();
        }
    }
}
