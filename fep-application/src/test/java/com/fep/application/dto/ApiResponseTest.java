package com.fep.application.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ApiResponse.
 */
@DisplayName("ApiResponse Tests")
class ApiResponseTest {

    @Test
    @DisplayName("Should create success response with data")
    void shouldCreateSuccessResponseWithData() {
        String data = "Test data";

        ApiResponse<String> response = ApiResponse.success(data);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("0000");
        assertThat(response.getMessage()).isEqualTo("Success");
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should create success response with data and custom message")
    void shouldCreateSuccessResponseWithDataAndMessage() {
        Integer data = 42;
        String message = "Operation completed successfully";

        ApiResponse<Integer> response = ApiResponse.success(data, message);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("0000");
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should create error response with code and message")
    void shouldCreateErrorResponseWithCodeAndMessage() {
        String errorCode = "E001";
        String errorMessage = "Invalid request";

        ApiResponse<Object> response = ApiResponse.error(errorCode, errorMessage);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(errorCode);
        assertThat(response.getMessage()).isEqualTo(errorMessage);
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should create error response with message only using default code")
    void shouldCreateErrorResponseWithMessageOnly() {
        String errorMessage = "Something went wrong";

        ApiResponse<Object> response = ApiResponse.error(errorMessage);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("9999");
        assertThat(response.getMessage()).isEqualTo(errorMessage);
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should create response using builder")
    void shouldCreateResponseUsingBuilder() {
        ApiResponse<String> response = ApiResponse.<String>builder()
            .success(true)
            .code("CUSTOM")
            .message("Custom message")
            .data("Custom data")
            .build();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("CUSTOM");
        assertThat(response.getMessage()).isEqualTo("Custom message");
        assertThat(response.getData()).isEqualTo("Custom data");
    }

    @Test
    @DisplayName("Should create response using no-args constructor and setters")
    void shouldCreateResponseUsingNoArgsConstructorAndSetters() {
        ApiResponse<String> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setCode("TEST");
        response.setMessage("Test message");
        response.setData("Test data");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("TEST");
        assertThat(response.getMessage()).isEqualTo("Test message");
        assertThat(response.getData()).isEqualTo("Test data");
    }

    @Test
    @DisplayName("Should support generic types")
    void shouldSupportGenericTypes() {
        // Test with custom object
        TransactionResponse txnResponse = TransactionResponse.builder()
            .responseCode("00")
            .responseMessage("Approved")
            .build();

        ApiResponse<TransactionResponse> response = ApiResponse.success(txnResponse);

        assertThat(response.getData()).isInstanceOf(TransactionResponse.class);
        assertThat(response.getData().getResponseCode()).isEqualTo("00");
    }
}
