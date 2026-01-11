package com.fep.jmeter.sampler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
public class AtmSimulatorSampler extends AbstractSampler implements TestBean, TestStateListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(AtmSimulatorSampler.class);

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

    // Transaction types (deprecated, use AtmTransactionType enum instead)
    /** @deprecated Use {@link AtmTransactionType#WITHDRAWAL} instead */
    @Deprecated
    public static final String TXN_WITHDRAWAL = AtmTransactionType.WITHDRAWAL.name();
    /** @deprecated Use {@link AtmTransactionType#BALANCE_INQUIRY} instead */
    @Deprecated
    public static final String TXN_BALANCE_INQUIRY = AtmTransactionType.BALANCE_INQUIRY.name();
    /** @deprecated Use {@link AtmTransactionType#TRANSFER} instead */
    @Deprecated
    public static final String TXN_TRANSFER = AtmTransactionType.TRANSFER.name();
    /** @deprecated Use {@link AtmTransactionType#DEPOSIT} instead */
    @Deprecated
    public static final String TXN_DEPOSIT = AtmTransactionType.DEPOSIT.name();
    /** @deprecated Use {@link AtmTransactionType#PIN_CHANGE} instead */
    @Deprecated
    public static final String TXN_PIN_CHANGE = AtmTransactionType.PIN_CHANGE.name();
    /** @deprecated Use {@link AtmTransactionType#MINI_STATEMENT} instead */
    @Deprecated
    public static final String TXN_MINI_STATEMENT = AtmTransactionType.MINI_STATEMENT.name();
    /** @deprecated Use {@link AtmTransactionType#CARDLESS_WITHDRAWAL} instead */
    @Deprecated
    public static final String TXN_CARDLESS = AtmTransactionType.CARDLESS_WITHDRAWAL.name();

    // Static resources
    private static final Map<String, ChannelHolder> channelPool = new ConcurrentHashMap<>();
    private static final Object channelLock = new Object();
    private static EventLoopGroup workerGroup;
    private static final AtomicInteger stanCounter = new AtomicInteger(0);
    private static final Iso8583MessageFactory messageFactory = new Iso8583MessageFactory();
    private static final FiscMessageAssembler messageAssembler = new FiscMessageAssembler();
    private static final FiscMessageParser messageParser = new FiscMessageParser();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AtmSimulatorSampler() {
        super();
        setName("ATM Simulator");
    }

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setDataType(SampleResult.TEXT);

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
}
