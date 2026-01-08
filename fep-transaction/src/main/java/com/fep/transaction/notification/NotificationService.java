package com.fep.transaction.notification;

import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service for sending transaction notifications via multiple channels.
 * Supports SMS, Email, App Push, LINE, and In-App notifications.
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Default bank name */
    private static final String DEFAULT_BANK_NAME = "FEP Bank";

    /** Default service phone */
    private static final String DEFAULT_SERVICE_PHONE = "0800-000-000";

    /** Large amount threshold (TWD) */
    private static final BigDecimal LARGE_AMOUNT_THRESHOLD = new BigDecimal("100000");

    /** Date time formatter */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    /** Template registry */
    private final Map<String, NotificationTemplate> templates = new ConcurrentHashMap<>();

    /** Channel handlers */
    private final Map<NotificationChannel, ChannelHandler> channelHandlers = new ConcurrentHashMap<>();

    /** Notification queue for async processing */
    private final BlockingQueue<NotificationRequest> notificationQueue = new LinkedBlockingQueue<>();

    /** Executor for async notifications */
    private final ExecutorService executor;

    /** Notification history (for testing/debugging) */
    private final List<NotificationResult> notificationHistory = Collections.synchronizedList(new ArrayList<>());

    /** Maximum history size */
    private static final int MAX_HISTORY_SIZE = 1000;

    public NotificationService() {
        this(Executors.newFixedThreadPool(4));
    }

    public NotificationService(ExecutorService executor) {
        this.executor = executor;
        initializeDefaultTemplates();
        initializeDefaultHandlers();
    }

    /**
     * Initializes default notification templates.
     */
    private void initializeDefaultTemplates() {
        registerTemplate(NotificationTemplate.smsTransactionSuccessZh());
        registerTemplate(NotificationTemplate.smsTransactionSuccessEn());
        registerTemplate(NotificationTemplate.smsTransactionFailedZh());
        registerTemplate(NotificationTemplate.smsOtpZh());
        registerTemplate(NotificationTemplate.emailTransactionSuccessZh());
        registerTemplate(NotificationTemplate.pushTransactionSuccessZh());
        registerTemplate(NotificationTemplate.smsLargeAmountAlertZh());
        registerTemplate(NotificationTemplate.smsSecurityAlertZh());
    }

    /**
     * Initializes default channel handlers (simulators for now).
     */
    private void initializeDefaultHandlers() {
        channelHandlers.put(NotificationChannel.SMS, new SimulatedSmsHandler());
        channelHandlers.put(NotificationChannel.EMAIL, new SimulatedEmailHandler());
        channelHandlers.put(NotificationChannel.APP_PUSH, new SimulatedPushHandler());
        channelHandlers.put(NotificationChannel.LINE, new SimulatedLineHandler());
        channelHandlers.put(NotificationChannel.IN_APP, new SimulatedInAppHandler());
    }

    /**
     * Registers a notification template.
     */
    public void registerTemplate(NotificationTemplate template) {
        templates.put(template.getTemplateId(), template);
        log.debug("Registered template: {}", template.getTemplateId());
    }

    /**
     * Registers a channel handler.
     */
    public void registerChannelHandler(NotificationChannel channel, ChannelHandler handler) {
        channelHandlers.put(channel, handler);
        log.info("Registered channel handler for: {}", channel);
    }

    /**
     * Sends a notification synchronously.
     */
    public NotificationResult send(NotificationRequest request) {
        log.debug("[{}] Sending notification: type={}, channels={}",
                request.getNotificationId(), request.getType(), request.getChannels());

        long startTime = System.currentTimeMillis();
        NotificationResult result = NotificationResult.builder()
                .notificationId(request.getNotificationId())
                .build();

        boolean anySuccess = false;
        for (NotificationChannel channel : request.getChannels()) {
            NotificationResult.ChannelResult channelResult = sendToChannel(request, channel);
            result.withChannelResult(channelResult);
            if (channelResult.isSuccess()) {
                anySuccess = true;
            }
        }

        result.setSuccess(anySuccess);
        result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

        // Store in history
        addToHistory(result);

        log.info("[{}] Notification sent: success={}, channels={}/{}",
                request.getNotificationId(),
                result.isSuccess(),
                result.getSuccessfulChannelCount(),
                request.getChannels().size());

        return result;
    }

    /**
     * Sends a notification asynchronously.
     */
    public CompletableFuture<NotificationResult> sendAsync(NotificationRequest request) {
        return CompletableFuture.supplyAsync(() -> send(request), executor);
    }

    /**
     * Queues a notification for later processing.
     */
    public void queue(NotificationRequest request) {
        notificationQueue.offer(request);
        log.debug("[{}] Notification queued", request.getNotificationId());
    }

    /**
     * Sends notification to a specific channel.
     */
    private NotificationResult.ChannelResult sendToChannel(NotificationRequest request,
                                                            NotificationChannel channel) {
        ChannelHandler handler = channelHandlers.get(channel);
        if (handler == null) {
            log.warn("No handler registered for channel: {}", channel);
            return NotificationResult.ChannelResult.builder()
                    .channel(channel)
                    .success(false)
                    .errorCode("NO_HANDLER")
                    .errorMessage("No handler registered for channel: " + channel)
                    .build();
        }

        try {
            return handler.send(request, findTemplate(request, channel));
        } catch (Exception e) {
            log.error("Failed to send notification via {}: {}", channel, e.getMessage(), e);
            return NotificationResult.ChannelResult.builder()
                    .channel(channel)
                    .success(false)
                    .errorCode("SEND_ERROR")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Finds the appropriate template for the request and channel.
     */
    private NotificationTemplate findTemplate(NotificationRequest request, NotificationChannel channel) {
        // Try specific template ID first
        if (request.getTemplateId() != null) {
            NotificationTemplate template = templates.get(request.getTemplateId());
            if (template != null) {
                return template;
            }
        }

        // Find by type, channel, and language
        String language = request.isChinese() ? "zh-TW" : "en-US";
        for (NotificationTemplate template : templates.values()) {
            if (template.getType() == request.getType() &&
                template.getChannel() == channel &&
                language.equals(template.getLanguage())) {
                return template;
            }
        }

        // Fallback to any matching template for type and channel
        for (NotificationTemplate template : templates.values()) {
            if (template.getType() == request.getType() && template.getChannel() == channel) {
                return template;
            }
        }

        return null;
    }

    /**
     * Creates a notification request from transaction result.
     */
    public NotificationRequest createFromTransaction(TransactionRequest txnRequest,
                                                      TransactionResponse txnResponse,
                                                      String phoneNumber,
                                                      String email) {
        NotificationType type = txnResponse.isApproved() ?
                NotificationType.TRANSACTION_SUCCESS : NotificationType.TRANSACTION_FAILED;

        Set<NotificationChannel> channels = new HashSet<>();
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            channels.add(NotificationChannel.SMS);
        }
        if (email != null && !email.isBlank()) {
            channels.add(NotificationChannel.EMAIL);
        }

        NotificationRequest.NotificationRequestBuilder builder = NotificationRequest.builder()
                .notificationId(generateNotificationId())
                .type(type)
                .channels(channels)
                .phoneNumber(phoneNumber)
                .emailAddress(email)
                .transactionId(txnRequest.getTransactionId())
                .amount(txnRequest.getAmount())
                .currencyCode(txnRequest.getCurrencyCode())
                .maskedAccount(maskAccount(txnRequest.getSourceAccount()));

        // Add template variables
        Map<String, String> variables = new HashMap<>();
        variables.put("bankName", DEFAULT_BANK_NAME);
        variables.put("servicePhone", DEFAULT_SERVICE_PHONE);
        variables.put("transactionId", txnRequest.getTransactionId());
        variables.put("transactionType", getTransactionTypeDisplay(txnRequest));
        variables.put("maskedAccount", maskAccount(txnRequest.getSourceAccount()));
        variables.put("currency", txnRequest.getCurrencyCode() != null ? txnRequest.getCurrencyCode() : "TWD");
        variables.put("amount", formatAmount(txnRequest.getAmount()));
        variables.put("transactionTime", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        variables.put("balance", formatAmount(txnResponse.getAvailableBalance()));

        if (!txnResponse.isApproved()) {
            variables.put("failureReason", txnResponse.getResponseDescription());
        }

        builder.templateVariables(variables);

        return builder.build();
    }

    /**
     * Sends large amount alert if threshold exceeded.
     */
    public NotificationResult sendLargeAmountAlertIfNeeded(TransactionRequest request,
                                                            TransactionResponse response,
                                                            String phoneNumber) {
        if (request.getAmount() == null ||
            request.getAmount().compareTo(LARGE_AMOUNT_THRESHOLD) < 0) {
            return null;
        }

        if (!response.isApproved()) {
            return null;
        }

        Map<String, String> variables = new HashMap<>();
        variables.put("bankName", DEFAULT_BANK_NAME);
        variables.put("servicePhone", DEFAULT_SERVICE_PHONE);
        variables.put("maskedAccount", maskAccount(request.getSourceAccount()));
        variables.put("transactionType", getTransactionTypeDisplay(request));
        variables.put("currency", request.getCurrencyCode() != null ? request.getCurrencyCode() : "TWD");
        variables.put("amount", formatAmount(request.getAmount()));
        variables.put("transactionTime", LocalDateTime.now().format(DATE_TIME_FORMATTER));

        NotificationRequest notificationRequest = NotificationRequest.builder()
                .notificationId(generateNotificationId())
                .type(NotificationType.LARGE_AMOUNT_ALERT)
                .channels(Set.of(NotificationChannel.SMS))
                .phoneNumber(phoneNumber)
                .transactionId(request.getTransactionId())
                .amount(request.getAmount())
                .templateVariables(variables)
                .build();

        return send(notificationRequest);
    }

    /**
     * Sends OTP verification code.
     */
    public NotificationResult sendOtp(String phoneNumber, String otpCode, int expiryMinutes) {
        Map<String, String> variables = new HashMap<>();
        variables.put("bankName", DEFAULT_BANK_NAME);
        variables.put("otpCode", otpCode);
        variables.put("expiryMinutes", String.valueOf(expiryMinutes));

        NotificationRequest request = NotificationRequest.builder()
                .notificationId(generateNotificationId())
                .type(NotificationType.OTP_CODE)
                .channels(Set.of(NotificationChannel.SMS))
                .phoneNumber(phoneNumber)
                .templateVariables(variables)
                .build();

        return send(request);
    }

    /**
     * Sends security alert.
     */
    public NotificationResult sendSecurityAlert(String phoneNumber, String alertDescription,
                                                 String maskedAccount) {
        Map<String, String> variables = new HashMap<>();
        variables.put("bankName", DEFAULT_BANK_NAME);
        variables.put("servicePhone", DEFAULT_SERVICE_PHONE);
        variables.put("maskedAccount", maskedAccount);
        variables.put("alertDescription", alertDescription);

        NotificationRequest request = NotificationRequest.builder()
                .notificationId(generateNotificationId())
                .type(NotificationType.SECURITY_ALERT)
                .channels(Set.of(NotificationChannel.SMS))
                .phoneNumber(phoneNumber)
                .templateVariables(variables)
                .build();

        return send(request);
    }

    /**
     * Gets notification history.
     */
    public List<NotificationResult> getHistory() {
        return new ArrayList<>(notificationHistory);
    }

    /**
     * Clears notification history.
     */
    public void clearHistory() {
        notificationHistory.clear();
    }

    /**
     * Shuts down the service.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void addToHistory(NotificationResult result) {
        notificationHistory.add(result);
        while (notificationHistory.size() > MAX_HISTORY_SIZE) {
            notificationHistory.remove(0);
        }
    }

    private String generateNotificationId() {
        return "NOTIF" + System.currentTimeMillis() +
               String.format("%04d", (int) (Math.random() * 10000));
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 8) {
            return "****";
        }
        return account.substring(0, 4) + "****" + account.substring(account.length() - 4);
    }

    private String getTransactionTypeDisplay(TransactionRequest request) {
        if (request.getTransactionType() != null) {
            return request.getTransactionType().getChineseDescription();
        }
        return "交易";
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return String.format("%,.2f", amount);
    }

    /**
     * Channel handler interface.
     */
    public interface ChannelHandler {
        NotificationResult.ChannelResult send(NotificationRequest request, NotificationTemplate template);
    }

    // Simulated channel handlers for development/testing

    private static class SimulatedSmsHandler implements ChannelHandler {
        @Override
        public NotificationResult.ChannelResult send(NotificationRequest request, NotificationTemplate template) {
            String message = template != null ?
                    template.renderBody(request.getTemplateVariables()) :
                    request.getCustomMessage();

            log.info("[SMS] To: {}, Message: {}", request.getPhoneNumber(), message);

            return NotificationResult.ChannelResult.builder()
                    .channel(NotificationChannel.SMS)
                    .success(true)
                    .providerRefId("SMS" + System.currentTimeMillis())
                    .deliveredAt(LocalDateTime.now())
                    .build();
        }
    }

    private static class SimulatedEmailHandler implements ChannelHandler {
        @Override
        public NotificationResult.ChannelResult send(NotificationRequest request, NotificationTemplate template) {
            String subject = template != null ?
                    template.renderSubject(request.getTemplateVariables()) : "Transaction Notification";
            String body = template != null ?
                    template.renderBody(request.getTemplateVariables()) :
                    request.getCustomMessage();

            log.info("[EMAIL] To: {}, Subject: {}", request.getEmailAddress(), subject);
            log.debug("[EMAIL] Body: {}", body);

            return NotificationResult.ChannelResult.builder()
                    .channel(NotificationChannel.EMAIL)
                    .success(true)
                    .providerRefId("EMAIL" + System.currentTimeMillis())
                    .deliveredAt(LocalDateTime.now())
                    .build();
        }
    }

    private static class SimulatedPushHandler implements ChannelHandler {
        @Override
        public NotificationResult.ChannelResult send(NotificationRequest request, NotificationTemplate template) {
            String title = template != null ?
                    template.renderSubject(request.getTemplateVariables()) : "通知";
            String body = template != null ?
                    template.renderBody(request.getTemplateVariables()) :
                    request.getCustomMessage();

            log.info("[PUSH] Token: {}, Title: {}, Body: {}",
                    request.getDeviceToken(), title, body);

            return NotificationResult.ChannelResult.builder()
                    .channel(NotificationChannel.APP_PUSH)
                    .success(true)
                    .providerRefId("PUSH" + System.currentTimeMillis())
                    .deliveredAt(LocalDateTime.now())
                    .build();
        }
    }

    private static class SimulatedLineHandler implements ChannelHandler {
        @Override
        public NotificationResult.ChannelResult send(NotificationRequest request, NotificationTemplate template) {
            String message = template != null ?
                    template.renderBody(request.getTemplateVariables()) :
                    request.getCustomMessage();

            log.info("[LINE] UserId: {}, Message: {}", request.getLineUserId(), message);

            return NotificationResult.ChannelResult.builder()
                    .channel(NotificationChannel.LINE)
                    .success(true)
                    .providerRefId("LINE" + System.currentTimeMillis())
                    .deliveredAt(LocalDateTime.now())
                    .build();
        }
    }

    private static class SimulatedInAppHandler implements ChannelHandler {
        @Override
        public NotificationResult.ChannelResult send(NotificationRequest request, NotificationTemplate template) {
            String message = template != null ?
                    template.renderBody(request.getTemplateVariables()) :
                    request.getCustomMessage();

            log.info("[IN_APP] CustomerId: {}, Message: {}", request.getCustomerId(), message);

            return NotificationResult.ChannelResult.builder()
                    .channel(NotificationChannel.IN_APP)
                    .success(true)
                    .providerRefId("INAPP" + System.currentTimeMillis())
                    .deliveredAt(LocalDateTime.now())
                    .build();
        }
    }
}
