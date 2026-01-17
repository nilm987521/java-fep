package com.fep.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fep.application.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class
})
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testProcessTransaction() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .transactionType("WITHDRAWAL")
                .channelType("ATM")
                .sourceAccount("1234567890123456")
                .amount(new BigDecimal("1000.00"))
                .terminalId("ATM001")
                .build();

        mockMvc.perform(post("/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.responseCode").value("00"));
    }

    @Test
    void testBalanceInquiry() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .transactionType("BALANCE_INQUIRY")
                .channelType("ATM")
                .sourceAccount("1234567890123456")
                .terminalId("ATM001")
                .build();

        mockMvc.perform(post("/v1/transactions/balance-inquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.availableBalance").exists());
    }

    @Test
    void testGetTransaction() throws Exception {
        mockMvc.perform(get("/v1/transactions/123456789012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.referenceNumber").value("123456789012"));
    }
}
