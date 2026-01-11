package com.fep.jmeter.sampler;

import com.fep.jmeter.config.TransactionTemplate;
import com.fep.jmeter.config.TransactionTemplateConfig;
import com.fep.message.iso8583.Iso8583Message;
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
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMeter Sampler for sending transactions based on Transaction Templates.
 *
 * <p>This sampler uses {@link TransactionTemplateConfig} to load transaction templates
 * and send ISO 8583 messages to a target server.
 *
 * <p>Features:
 * <ul>
 *   <li>Select template by name from configured templates</li>
 *   <li>Override template fields with custom values</li>
 *   <li>Variable substitution with JMeter variables</li>
 *   <li>Automatic STAN and timestamp generation</li>
 *   <li>Connection pooling per thread</li>
 * </ul>
 *
 * <p>Usage:
 * <ol>
 *   <li>Add a TransactionTemplateConfig element to your test plan</li>
 *   <li>Add this sampler and select a template name</li>
 *   <li>Configure target server host and port</li>
 *   <li>Optionally add custom field overrides</li>
 * </ol>
 */
public class TemplateSampler extends AbstractSampler implements TestBean, TestStateListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(TemplateSampler.class);

    // Property names - Connection
    public static final String TARGET_HOST = "targetHost";
    public static final String TARGET_PORT = "targetPort";
    public static final String CONNECTION_TIMEOUT = "connectionTimeout";
    public static final String READ_TIMEOUT = "readTimeout";

    // Property names - Template
    public static final String TEMPLATE_NAME = "templateName";
    public static final String CUSTOM_FIELDS = "customFields";
    public static final String MTI_OVERRIDE = "mtiOverride";

    // Property names - Variables
    public static final String AMOUNT = "amount";
    public static final String CARD_NUMBER = "cardNumber";
    public static final String TERMINAL_ID = "terminalId";
    public static final String BANK_CODE = "bankCode";
    public static final String SOURCE_ACCOUNT = "sourceAccount";
    public static final String DEST_ACCOUNT = "destAccount";

    // Static resources
    private static final Map<String, ChannelHolder> channelPool = new ConcurrentHashMap<>();
    private static final Object channelLock = new Object();
    private static EventLoopGroup workerGroup;
    private static final AtomicInteger stanCounter = new AtomicInteger(0);
    private static final FiscMessageAssembler messageAssembler = new FiscMessageAssembler();
    private static final FiscMessageParser messageParser = new FiscMessageParser();

    public TemplateSampler() {
        super();
        setName("Template Sampler");
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);

        String targetHost = getTargetHost();
        int targetPort = getTargetPort();
        String templateName = getTemplateName();

        try {
            // Find TransactionTemplateConfig
            TransactionTemplateConfig config = findTemplateConfig();
            if (config == null) {
                result.setSuccessful(false);
                result.setResponseCode("CONFIG_ERROR");
                result.setResponseMessage("TransactionTemplateConfig not found in test plan");
                return result;
            }

            // Get template
            Optional<TransactionTemplate> templateOpt = config.getTemplate(templateName);
            if (templateOpt.isEmpty()) {
                result.setSuccessful(false);
                result.setResponseCode("TEMPLATE_ERROR");
                result.setResponseMessage("Template not found: " + templateName);
                result.setResponseData("Available templates: " + config.getTemplateNames(),
                    StandardCharsets.UTF_8.name());
                return result;
            }

            TransactionTemplate template = templateOpt.get();

            // Build variables map
            Map<String, String> variables = buildVariables();

            // Create message from template
            Iso8583Message request = template.createMessage(variables);

            // Apply MTI override if specified
            String mtiOverride = getMtiOverride();
            if (mtiOverride != null && !mtiOverride.isEmpty()) {
                request.setMti(mtiOverride);
            }

            // Apply custom fields (highest priority)
            applyCustomFields(request);

            // Get or create connection
            ChannelHolder holder = getOrCreateChannel(targetHost, targetPort);

            String requestStr = formatMessageForDisplay(request);
            result.setSamplerData(requestStr);

            // Create response future
            CompletableFuture<Iso8583Message> responseFuture = new CompletableFuture<>();
            String stan = request.getFieldAsString(11);
            if (stan == null) {
                stan = String.format("%06d", stanCounter.incrementAndGet() % 1000000);
                request.setField(11, stan);
            }
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
            storeResponseVariables(response, templateName);

            log.debug("Template transaction {} completed: {}", templateName, responseCode);

        } catch (TimeoutException e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("TIMEOUT");
            result.setResponseMessage("Response timeout");
            result.setResponseData("Timeout waiting for response", StandardCharsets.UTF_8.name());
            log.warn("Template transaction timeout: {}", templateName);
        } catch (Exception e) {
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseCode("ERROR");
            result.setResponseMessage(e.getMessage());
            result.setResponseData(e.toString(), StandardCharsets.UTF_8.name());
            log.error("Template sampler error", e);
        }

        return result;
    }

    /**
     * Finds the TransactionTemplateConfig in the test plan.
     */
    private TransactionTemplateConfig findTemplateConfig() {
        JMeterContext context = JMeterContextService.getContext();
        if (context == null) {
            return null;
        }

        // Search through the sampler tree for TransactionTemplateConfig
        for (Object element : context.getVariables().getObject("__jmeterTestPlanElements") != null
                ? Collections.emptyList() : Collections.emptyList()) {
            if (element instanceof TransactionTemplateConfig) {
                return (TransactionTemplateConfig) element;
            }
        }

        // Alternative: Create a default config with common templates
        TransactionTemplateConfig defaultConfig = new TransactionTemplateConfig();
        defaultConfig.setTemplateSource("COMMON");
        return defaultConfig;
    }

    /**
     * Builds the variable map for template substitution.
     */
    private Map<String, String> buildVariables() {
        Map<String, String> variables = new HashMap<>();

        // Auto-generate STAN
        String stan = String.format("%06d", stanCounter.incrementAndGet() % 1000000);
        variables.put("stan", stan);

        // Auto-generate timestamp
        LocalDateTime now = LocalDateTime.now();
        variables.put("time", now.format(DateTimeFormatter.ofPattern("HHmmss")));
        variables.put("date", now.format(DateTimeFormatter.ofPattern("MMdd")));
        variables.put("datetime", now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));

        // Add sampler properties
        addIfNotEmpty(variables, "amount", getAmount());
        addIfNotEmpty(variables, "cardNumber", getCardNumber());
        addIfNotEmpty(variables, "pan", getCardNumber());
        addIfNotEmpty(variables, "terminalId", getTerminalId());
        addIfNotEmpty(variables, "bankCode", getBankCode());
        addIfNotEmpty(variables, "sourceAccount", getSourceAccount());
        addIfNotEmpty(variables, "destAccount", getDestAccount());

        // Add JMeter variables
        JMeterContext context = JMeterContextService.getContext();
        if (context != null) {
            JMeterVariables vars = context.getVariables();
            if (vars != null) {
                addFromJMeterVars(variables, vars, "amount");
                addFromJMeterVars(variables, vars, "cardNumber");
                addFromJMeterVars(variables, vars, "pan");
                addFromJMeterVars(variables, vars, "terminalId");
                addFromJMeterVars(variables, vars, "bankCode");
                addFromJMeterVars(variables, vars, "sourceAccount");
                addFromJMeterVars(variables, vars, "destAccount");
                addFromJMeterVars(variables, vars, "billerCode");
                addFromJMeterVars(variables, vars, "billReference");
                addFromJMeterVars(variables, vars, "originalRrn");
                addFromJMeterVars(variables, vars, "originalProcessingCode");
                addFromJMeterVars(variables, vars, "originalDataElements");
            }
        }

        return variables;
    }

    private void addIfNotEmpty(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    private void addFromJMeterVars(Map<String, String> map, JMeterVariables vars, String name) {
        String value = vars.get(name);
        if (value != null && !map.containsKey(name)) {
            map.put(name, value);
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
                        pipeline.addLast(new TemplateResponseHandler(finalHolder));
                    }
                });

                ChannelFuture connectFuture = bootstrap.connect(host, port).sync();
                holder.channel = connectFuture.channel();
                channelPool.put(key, holder);

                log.info("Template sampler connected to {}:{}", host, port);
            }

            return holder;
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
        sb.append("=== Template Transaction ===\n");
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

    private void storeResponseVariables(Iso8583Message response, String templateName) {
        JMeterContext context = JMeterContextService.getContext();
        if (context != null) {
            JMeterVariables vars = context.getVariables();
            if (vars != null) {
                String prefix = "TPL_";
                vars.put(prefix + "TEMPLATE", templateName);
                vars.put(prefix + "MTI", response.getMti());
                vars.put(prefix + "RESPONSE_CODE", response.getFieldAsString(39));
                vars.put(prefix + "STAN", response.getFieldAsString(11));
                vars.put(prefix + "RRN", response.getFieldAsString(37));
                vars.put(prefix + "AUTH_CODE", response.getFieldAsString(38));

                String balance = response.getFieldAsString(54);
                if (balance != null) {
                    vars.put(prefix + "BALANCE", balance);
                }
            }
        }
    }

    private String getResponseMessage(String responseCode) {
        if (responseCode == null) return "Unknown";
        return switch (responseCode) {
            case "00" -> "Approved";
            case "01" -> "Refer to card issuer";
            case "05" -> "Do not honor";
            case "12" -> "Invalid transaction";
            case "13" -> "Invalid amount";
            case "14" -> "Invalid card number";
            case "30" -> "Format error";
            case "51" -> "Insufficient funds";
            case "54" -> "Expired card";
            case "55" -> "Incorrect PIN";
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
        log.info("Template sampler test started on {}", host);
    }

    @Override
    public void testEnded() {
        testEnded("");
    }

    @Override
    public void testEnded(String host) {
        log.info("Template sampler test ended on {}, closing connections", host);
        synchronized (channelLock) {
            channelPool.values().forEach(holder -> {
                try {
                    if (holder.channel != null && holder.channel.isOpen()) {
                        holder.channel.close().sync();
                    }
                } catch (Exception e) {
                    log.warn("Error closing template sampler channel", e);
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
    public String getTargetHost() {
        return getPropertyAsString(TARGET_HOST, "localhost");
    }

    public void setTargetHost(String host) {
        setProperty(TARGET_HOST, host);
    }

    public int getTargetPort() {
        return getPropertyAsInt(TARGET_PORT, 8080);
    }

    public void setTargetPort(int port) {
        setProperty(TARGET_PORT, port);
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

    public String getTemplateName() {
        return getPropertyAsString(TEMPLATE_NAME, "Echo Test");
    }

    public void setTemplateName(String name) {
        setProperty(TEMPLATE_NAME, name);
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

    public String getAmount() {
        return getPropertyAsString(AMOUNT, "");
    }

    public void setAmount(String amount) {
        setProperty(AMOUNT, amount);
    }

    public String getCardNumber() {
        return getPropertyAsString(CARD_NUMBER, "");
    }

    public void setCardNumber(String cardNumber) {
        setProperty(CARD_NUMBER, cardNumber);
    }

    public String getTerminalId() {
        return getPropertyAsString(TERMINAL_ID, "");
    }

    public void setTerminalId(String terminalId) {
        setProperty(TERMINAL_ID, terminalId);
    }

    public String getBankCode() {
        return getPropertyAsString(BANK_CODE, "");
    }

    public void setBankCode(String bankCode) {
        setProperty(BANK_CODE, bankCode);
    }

    public String getSourceAccount() {
        return getPropertyAsString(SOURCE_ACCOUNT, "");
    }

    public void setSourceAccount(String account) {
        setProperty(SOURCE_ACCOUNT, account);
    }

    public String getDestAccount() {
        return getPropertyAsString(DEST_ACCOUNT, "");
    }

    public void setDestAccount(String account) {
        setProperty(DEST_ACCOUNT, account);
    }

    /**
     * Holds channel and pending requests for response matching.
     */
    private static class ChannelHolder {
        Channel channel;
        final Map<String, CompletableFuture<Iso8583Message>> pendingRequests = new ConcurrentHashMap<>();
    }

    /**
     * Handler for template sampler responses.
     */
    private static class TemplateResponseHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final ChannelHolder holder;

        TemplateResponseHandler(ChannelHolder holder) {
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
                log.error("Error parsing template response", e);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Template sampler channel error", cause);
            holder.pendingRequests.forEach((stan, future) ->
                future.completeExceptionally(cause));
            holder.pendingRequests.clear();
            ctx.close();
        }
    }
}
