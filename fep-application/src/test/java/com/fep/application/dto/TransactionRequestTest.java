package com.fep.application.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TransactionRequest.
 */
@DisplayName("TransactionRequest Tests")
class TransactionRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should create request using builder")
    void shouldCreateRequestUsingBuilder() {
        TransactionRequest request = TransactionRequest.builder()
            .transactionType("WITHDRAWAL")
            .channelType("ATM")
            .cardNumber("4111111111111111")
            .sourceAccount("1234567890")
            .amount(new BigDecimal("1000.00"))
            .currency("TWD")
            .terminalId("ATM001")
            .build();

        assertThat(request.getTransactionType()).isEqualTo("WITHDRAWAL");
        assertThat(request.getChannelType()).isEqualTo("ATM");
        assertThat(request.getCardNumber()).isEqualTo("4111111111111111");
        assertThat(request.getSourceAccount()).isEqualTo("1234567890");
        assertThat(request.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(request.getCurrency()).isEqualTo("TWD");
        assertThat(request.getTerminalId()).isEqualTo("ATM001");
    }

    @Test
    @DisplayName("Should create request using no-args constructor and setters")
    void shouldCreateRequestUsingNoArgsConstructorAndSetters() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionType("TRANSFER");
        request.setChannelType("INTERNET_BANKING");
        request.setSourceAccount("1234567890");
        request.setDestinationAccount("0987654321");
        request.setDestinationBankCode("812");
        request.setAmount(new BigDecimal("5000.00"));
        request.setTerminalId("WEB001");
        request.setMemo("Test transfer");

        assertThat(request.getTransactionType()).isEqualTo("TRANSFER");
        assertThat(request.getChannelType()).isEqualTo("INTERNET_BANKING");
        assertThat(request.getSourceAccount()).isEqualTo("1234567890");
        assertThat(request.getDestinationAccount()).isEqualTo("0987654321");
        assertThat(request.getDestinationBankCode()).isEqualTo("812");
        assertThat(request.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(request.getTerminalId()).isEqualTo("WEB001");
        assertThat(request.getMemo()).isEqualTo("Test transfer");
    }

    @Test
    @DisplayName("Should create request with all-args constructor")
    void shouldCreateRequestWithAllArgsConstructor() {
        TransactionRequest request = new TransactionRequest(
            "BILL_PAYMENT",
            "MOBILE_BANKING",
            "4111111111111111",
            "1234567890",
            null,
            null,
            new BigDecimal("2500.00"),
            "TWD",
            "MOBILE001",
            null,
            "ELEC001",
            "PAY123456",
            "電費繳納"
        );

        assertThat(request.getTransactionType()).isEqualTo("BILL_PAYMENT");
        assertThat(request.getChannelType()).isEqualTo("MOBILE_BANKING");
        assertThat(request.getPayeeCode()).isEqualTo("ELEC001");
        assertThat(request.getPaymentNumber()).isEqualTo("PAY123456");
        assertThat(request.getMemo()).isEqualTo("電費繳納");
    }

    @Test
    @DisplayName("Should pass validation for valid request")
    void shouldPassValidationForValidRequest() {
        TransactionRequest request = TransactionRequest.builder()
            .transactionType("WITHDRAWAL")
            .channelType("ATM")
            .cardNumber("4111111111111111")
            .sourceAccount("1234567890")
            .amount(new BigDecimal("1000.00"))
            .currency("TWD")
            .terminalId("ATM001")
            .build();

        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when transactionType is blank")
    void shouldFailValidationWhenTransactionTypeIsBlank() {
        TransactionRequest request = TransactionRequest.builder()
            .transactionType("")
            .channelType("ATM")
            .sourceAccount("1234567890")
            .terminalId("ATM001")
            .build();

        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("交易類型不得為空"));
    }

    @Test
    @DisplayName("Should fail validation when channelType is blank")
    void shouldFailValidationWhenChannelTypeIsBlank() {
        TransactionRequest request = TransactionRequest.builder()
            .transactionType("WITHDRAWAL")
            .channelType("")
            .sourceAccount("1234567890")
            .terminalId("ATM001")
            .build();

        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("通路類型不得為空"));
    }

    @Test
    @DisplayName("Should fail validation when cardNumber format is invalid")
    void shouldFailValidationWhenCardNumberFormatIsInvalid() {
        TransactionRequest request = TransactionRequest.builder()
            .transactionType("WITHDRAWAL")
            .channelType("ATM")
            .cardNumber("123")  // Invalid: should be 16 digits
            .sourceAccount("1234567890")
            .terminalId("ATM001")
            .build();

        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("卡號格式錯誤"));
    }

    @Test
    @DisplayName("Should fail validation when sourceAccount is too short")
    void shouldFailValidationWhenSourceAccountIsTooShort() {
        TransactionRequest request = TransactionRequest.builder()
            .transactionType("WITHDRAWAL")
            .channelType("ATM")
            .sourceAccount("12345")  // Invalid: should be 10-16 characters
            .terminalId("ATM001")
            .build();

        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("帳號長度須為10-16碼"));
    }

    @Test
    @DisplayName("Should fail validation when destinationBankCode format is invalid")
    void shouldFailValidationWhenDestinationBankCodeFormatIsInvalid() {
        TransactionRequest request = TransactionRequest.builder()
            .transactionType("TRANSFER")
            .channelType("ATM")
            .sourceAccount("1234567890")
            .destinationAccount("0987654321")
            .destinationBankCode("12")  // Invalid: should be 3 digits
            .terminalId("ATM001")
            .build();

        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("銀行代碼須為3碼數字"));
    }

    @Test
    @DisplayName("Should fail validation when currency format is invalid")
    void shouldFailValidationWhenCurrencyFormatIsInvalid() {
        TransactionRequest request = TransactionRequest.builder()
            .transactionType("WITHDRAWAL")
            .channelType("ATM")
            .sourceAccount("1234567890")
            .currency("tw")  // Invalid: should be 3 uppercase letters
            .terminalId("ATM001")
            .build();

        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("幣別代碼須為3碼英文"));
    }

    @Test
    @DisplayName("Should fail validation when terminalId is blank")
    void shouldFailValidationWhenTerminalIdIsBlank() {
        TransactionRequest request = TransactionRequest.builder()
            .transactionType("WITHDRAWAL")
            .channelType("ATM")
            .sourceAccount("1234567890")
            .terminalId("")
            .build();

        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("終端機代號不得為空"));
    }

    @Test
    @DisplayName("Should set pinBlock correctly")
    void shouldSetPinBlockCorrectly() {
        TransactionRequest request = TransactionRequest.builder()
            .transactionType("WITHDRAWAL")
            .channelType("ATM")
            .sourceAccount("1234567890")
            .terminalId("ATM001")
            .pinBlock("0123456789ABCDEF")
            .build();

        assertThat(request.getPinBlock()).isEqualTo("0123456789ABCDEF");
    }
}
