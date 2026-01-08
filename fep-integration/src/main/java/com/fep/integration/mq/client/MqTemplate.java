package com.fep.integration.mq.client;

import com.fep.integration.mq.config.MqProperties;
import com.fep.integration.mq.exception.MqException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.stereotype.Component;

import jakarta.jms.*;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Template for IBM MQ operations with request-reply pattern.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqTemplate {

    private final JmsTemplate jmsTemplate;
    private final MqProperties mqProperties;

    /**
     * Sends a synchronous request and waits for reply.
     *
     * @param requestMessage the request message payload
     * @return the reply message payload
     * @throws MqException if MQ operation fails
     */
    public String sendAndReceive(String requestMessage) throws MqException {
        return sendAndReceive(requestMessage, mqProperties.getRequestTimeout());
    }

    /**
     * Sends a synchronous request with custom timeout.
     *
     * @param requestMessage the request message payload
     * @param timeoutMs timeout in milliseconds
     * @return the reply message payload
     * @throws MqException if MQ operation fails
     */
    public String sendAndReceive(String requestMessage, int timeoutMs) throws MqException {
        String correlationId = UUID.randomUUID().toString();
        String requestQueue = mqProperties.getRequestQueue();
        String replyQueue = mqProperties.getReplyQueue();
        
        log.debug("Sending MQ request: queue={}, correlationId={}, length={}", 
                requestQueue, correlationId, requestMessage.length());
        
        try {
            // Send request message
            jmsTemplate.send(requestQueue, session -> {
                TextMessage message = session.createTextMessage(requestMessage);
                message.setJMSCorrelationID(correlationId);
                message.setJMSReplyTo(session.createQueue(replyQueue));
                message.setJMSExpiration(System.currentTimeMillis() + timeoutMs);
                return message;
            });
            
            log.debug("MQ request sent, waiting for reply: correlationId={}", correlationId);
            
            // Receive reply message with selector
            String selector = "JMSCorrelationID = '" + correlationId + "'";
            Message replyMessage = jmsTemplate.receiveSelected(replyQueue, selector);
            
            if (replyMessage == null) {
                throw new MqException("No reply received within timeout: " + timeoutMs + "ms", 
                        replyQueue, "TIMEOUT");
            }
            
            if (replyMessage instanceof TextMessage textMessage) {
                String reply = textMessage.getText();
                log.debug("MQ reply received: correlationId={}, length={}", 
                        correlationId, reply.length());
                return reply;
            } else {
                throw new MqException("Unexpected message type: " + replyMessage.getClass().getName(),
                        replyQueue, "INVALID_MESSAGE_TYPE");
            }
            
        } catch (JMSException e) {
            log.error("MQ operation failed: correlationId={}, error={}", 
                    correlationId, e.getMessage(), e);
            throw new MqException("MQ operation failed: " + e.getMessage(), 
                    requestQueue, extractErrorCode(e), e);
        }
    }

    /**
     * Sends an asynchronous request (fire-and-forget).
     *
     * @param requestMessage the request message payload
     * @throws MqException if MQ operation fails
     */
    public void send(String requestMessage) throws MqException {
        send(mqProperties.getRequestQueue(), requestMessage);
    }

    /**
     * Sends an asynchronous request to specified queue.
     *
     * @param queueName target queue name
     * @param requestMessage the request message payload
     * @throws MqException if MQ operation fails
     */
    public void send(String queueName, String requestMessage) throws MqException {
        log.debug("Sending MQ message: queue={}, length={}", queueName, requestMessage.length());

        try {
            jmsTemplate.send(queueName, session -> {
                TextMessage message = session.createTextMessage(requestMessage);
                message.setJMSTimestamp(System.currentTimeMillis());
                return message;
            });

            log.debug("MQ message sent: queue={}", queueName);

        } catch (org.springframework.jms.JmsException e) {
            log.error("MQ send failed: queue={}, error={}", queueName, e.getMessage(), e);
            throw new MqException("MQ send failed: " + e.getMessage(),
                    queueName, "JMS_ERROR", e);
        }
    }

    /**
     * Sends an asynchronous request and returns CompletableFuture.
     *
     * @param requestMessage the request message payload
     * @return CompletableFuture with reply message
     */
    public CompletableFuture<String> sendAsync(String requestMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendAndReceive(requestMessage);
            } catch (MqException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Sends request with byte array payload (for binary data).
     *
     * @param requestBytes the request message as byte array
     * @return the reply message as byte array
     * @throws MqException if MQ operation fails
     */
    public byte[] sendAndReceiveBytes(byte[] requestBytes) throws MqException {
        String correlationId = UUID.randomUUID().toString();
        String requestQueue = mqProperties.getRequestQueue();
        String replyQueue = mqProperties.getReplyQueue();
        
        log.debug("Sending MQ bytes request: queue={}, correlationId={}, length={}", 
                requestQueue, correlationId, requestBytes.length);
        
        try {
            // Send request
            jmsTemplate.send(requestQueue, session -> {
                BytesMessage message = session.createBytesMessage();
                message.writeBytes(requestBytes);
                message.setJMSCorrelationID(correlationId);
                message.setJMSReplyTo(session.createQueue(replyQueue));
                return message;
            });
            
            // Receive reply
            String selector = "JMSCorrelationID = '" + correlationId + "'";
            Message replyMessage = jmsTemplate.receiveSelected(replyQueue, selector);
            
            if (replyMessage == null) {
                throw new MqException("No reply received within timeout", 
                        replyQueue, "TIMEOUT");
            }
            
            if (replyMessage instanceof BytesMessage bytesMessage) {
                byte[] reply = new byte[(int) bytesMessage.getBodyLength()];
                bytesMessage.readBytes(reply);
                log.debug("MQ bytes reply received: correlationId={}, length={}", 
                        correlationId, reply.length);
                return reply;
            } else {
                throw new MqException("Unexpected message type: " + replyMessage.getClass().getName(),
                        replyQueue, "INVALID_MESSAGE_TYPE");
            }
            
        } catch (JMSException e) {
            log.error("MQ bytes operation failed: correlationId={}, error={}", 
                    correlationId, e.getMessage(), e);
            throw new MqException("MQ bytes operation failed: " + e.getMessage(), 
                    requestQueue, extractErrorCode(e), e);
        }
    }

    /**
     * Extracts error code from JMSException.
     */
    private String extractErrorCode(JMSException e) {
        String errorCode = e.getErrorCode();
        if (errorCode != null && !errorCode.isEmpty()) {
            return errorCode;
        }
        
        // Try to extract from linked exception
        Exception linkedException = e.getLinkedException();
        if (linkedException != null) {
            return linkedException.getClass().getSimpleName();
        }
        
        return "UNKNOWN";
    }
}
