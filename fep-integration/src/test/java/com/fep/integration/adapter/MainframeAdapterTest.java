package com.fep.integration.adapter;

import com.fep.integration.converter.Iso8583ToMainframeConverter;
import com.fep.integration.converter.MainframeToIso8583Converter;
import com.fep.integration.model.MainframeRequest;
import com.fep.integration.model.MainframeResponse;
import com.fep.integration.mq.client.MqTemplate;
import com.fep.integration.mq.exception.MqException;
import com.fep.message.iso8583.Iso8583Message;
import com.fep.transaction.domain.TransactionRequest;
import com.fep.transaction.domain.TransactionResponse;
import com.fep.transaction.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MainframeAdapterTest {

    @Mock
    private MqTemplate mqTemplate;

    @Mock
    private Iso8583ToMainframeConverter toMainframeConverter;

    @Mock
    private MainframeToIso8583Converter toIso8583Converter;

    @InjectMocks
    private MainframeAdapter adapter;

    private TransactionRequest transactionRequest;

    @BeforeEach
    void setUp() {
        transactionRequest = TransactionRequest.builder()
                .transactionId("TXN123456")
                .transactionType(TransactionType.WITHDRAWAL)
                .pan("4111111111111111")
                .amount(BigDecimal.valueOf(100.00))
                .stan("123456")
                .rrn("123456789012")
                .terminalId("ATM00001")
                .build();
    }

    @Test
    void testProcess_Success() throws MqException {
        // Given
        MainframeRequest mainframeRequest = MainframeRequest.builder()
                .transactionCode("3001")
                .rawPayload("MOCK_REQUEST_PAYLOAD")
                .build();

        String mqResponse = "MOCK_RESPONSE_PAYLOAD";

        MainframeResponse mainframeResponse = MainframeResponse.builder()
                .transactionId("TXN123456")
                .responseCode("00")
                .responseMessage("Approved")
                .build();

        Iso8583Message iso8583Response = new Iso8583Message();
        iso8583Response.setMti("0210");
        iso8583Response.setField(39, "00");

        when(toMainframeConverter.convert(any(Iso8583Message.class))).thenReturn(mainframeRequest);
        when(mqTemplate.sendAndReceive(anyString())).thenReturn(mqResponse);
        when(toIso8583Converter.parseCobolFormat(anyString())).thenReturn(mainframeResponse);
        when(toIso8583Converter.convert(any(MainframeResponse.class))).thenReturn(iso8583Response);

        // When
        TransactionResponse response = adapter.process(transactionRequest);

        // Then
        assertNotNull(response);
        assertTrue(response.isApproved());
        assertEquals("00", response.getResponseCode());

        verify(toMainframeConverter).convert(any(Iso8583Message.class));
        verify(mqTemplate).sendAndReceive("MOCK_REQUEST_PAYLOAD");
        verify(toIso8583Converter).parseCobolFormat(mqResponse);
        verify(toIso8583Converter).convert(mainframeResponse);
    }

    @Test
    void testProcess_MqException() throws MqException {
        // Given
        MainframeRequest mainframeRequest = MainframeRequest.builder()
                .rawPayload("MOCK_REQUEST_PAYLOAD")
                .build();

        when(toMainframeConverter.convert(any(Iso8583Message.class))).thenReturn(mainframeRequest);
        when(mqTemplate.sendAndReceive(anyString()))
                .thenThrow(new MqException("Connection timeout", "CBS.REQUEST.QUEUE", "TIMEOUT"));

        // When
        TransactionResponse response = adapter.process(transactionRequest);

        // Then
        assertNotNull(response);
        assertFalse(response.isApproved());
        assertEquals("91", response.getResponseCode()); // MQ error
        assertTrue(response.getResponseDescription().contains("MQ communication error"));
    }

    @Test
    void testProcess_UnexpectedException() throws MqException {
        // Given
        MainframeRequest mainframeRequest = MainframeRequest.builder()
                .rawPayload("MOCK_REQUEST_PAYLOAD")
                .build();

        when(toMainframeConverter.convert(any(Iso8583Message.class))).thenReturn(mainframeRequest);
        when(mqTemplate.sendAndReceive(anyString()))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When
        TransactionResponse response = adapter.process(transactionRequest);

        // Then
        assertNotNull(response);
        assertFalse(response.isApproved());
        assertEquals("96", response.getResponseCode()); // System error
        assertTrue(response.getResponseDescription().contains("System error"));
    }

    @Test
    void testHealthCheck_Success() throws MqException {
        // Given
        when(mqTemplate.sendAndReceive(anyString(), anyInt())).thenReturn("PONG");

        // When
        boolean healthy = adapter.healthCheck();

        // Then
        assertTrue(healthy);
        verify(mqTemplate).sendAndReceive(anyString(), eq(5000));
    }

    @Test
    void testHealthCheck_Failed() throws MqException {
        // Given
        when(mqTemplate.sendAndReceive(anyString(), anyInt()))
                .thenThrow(new MqException("Connection failed"));

        // When
        boolean healthy = adapter.healthCheck();

        // Then
        assertFalse(healthy);
    }

    @Test
    void testGetAdapterName() {
        assertEquals("MAINFRAME-MQ-ADAPTER", adapter.getAdapterName());
    }
}
