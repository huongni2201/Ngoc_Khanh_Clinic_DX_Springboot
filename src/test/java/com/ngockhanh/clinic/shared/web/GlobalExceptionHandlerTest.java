package com.ngockhanh.clinic.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsKnownFailuresAndHidesUnexpectedDetails() {
        assertError(handler.businessRule(new BusinessRuleException("business rule") { }), 409);
        ResponseEntity<ApiError> invalidInput = handler.invalidInput(new IllegalArgumentException("internal validation detail"));
        assertError(invalidInput, 400);
        assertThat(invalidInput.getBody().message()).isEqualTo("Invalid request");
        assertError(handler.duplicate(new org.springframework.dao.DuplicateKeyException("unique index")), 409);
        assertError(handler.notFound(new ResourceNotFoundException("patient")), 404);
        assertError(handler.concurrency(new ConcurrentUpdateException()), 409);
        ResponseEntity<ApiError> unexpected = handler.unexpected(new RuntimeException("secret database detail"));
        assertError(unexpected, 500);
        assertThat(unexpected.getBody().message()).doesNotContain("secret");
    }

    @Test
    void successResponseUsesOkResultAndCarriesPageData() {
        PageResponse<String> page = new PageResponse<>(List.of("item"), 0, 20, 1, 1);
        ApiResponse<PageResponse<String>> response = new ApiResponse<>(200, "OK", page);

        assertThat(response.result()).isEqualTo("OK");
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.data().items()).containsExactly("item");
        assertThat(new ApiResponse<>("NG", 500, "error", page).result()).isEqualTo("OK");
        assertThat(new ApiError("OK", 400, "message").result()).isEqualTo("NG");
    }

    private void assertError(ResponseEntity<ApiError> response, int expectedCode) {
        assertThat(response.getStatusCode().value()).isEqualTo(expectedCode);
        assertThat(response.getBody().result()).isEqualTo("NG");
        assertThat(response.getBody().code()).isEqualTo(expectedCode);
    }
}
