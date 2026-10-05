package com.ngockhanh.clinic.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler =
      new GlobalExceptionHandler(new ApiResponseWriter(JsonMapper.builder().build()));

  @Test
  void mapsKnownFailuresAndHidesUnexpectedDetails() throws Exception {
    assertError(handler.businessRule(new BusinessRuleException("business rule") {}), 409);
    ResponseEntity<ApiResponse<Void>> invalidInput =
        handler.invalidInput(new IllegalArgumentException("internal validation detail"));
    assertError(invalidInput, 400);
    assertThat(invalidInput.getBody().message()).isEqualTo("Invalid request");
    assertError(
        handler.duplicate(new org.springframework.dao.DuplicateKeyException("unique index")), 409);
    assertError(handler.notFound(new ResourceNotFoundException("patient")), 404);
    assertError(handler.concurrency(new ConcurrentUpdateException()), 409);
    ResponseEntity<ApiResponse<Void>> stalePreview =
        handler.concurrency(
            new ConcurrentUpdateException("IMPORT_PREVIEW_STALE", "Preview is stale"));
    assertError(stalePreview, 409);
    assertThat(stalePreview.getBody().message()).isEqualTo("Preview is stale");
    assertThat(JsonMapper.builder().build().writeValueAsString(stalePreview.getBody()))
        .contains("\"code\":409")
        .doesNotContain("errorCode");
    assertError(
        handler.forbidden(new org.springframework.security.access.AccessDeniedException("denied")),
        403);
    ResponseEntity<ApiResponse<Void>> unexpected =
        handler.unexpected(new RuntimeException("secret database detail"));
    assertError(unexpected, 500);
    assertThat(unexpected.getBody().message()).doesNotContain("secret");
  }

  @Test
  void successResponseUsesOkResultAndCarriesPageData() {
    PageResponse<String> page =
        PageResponse.<String>builder()
            .items(List.of("item"))
            .page(0)
            .size(20)
            .totalElements(1)
            .totalPages(1)
            .build();
    ApiResponse<PageResponse<String>> response = ApiResponse.success(200, "OK", page);

    assertThat(response.result()).isEqualTo("OK");
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.data().items()).containsExactly("item");
    ApiResponse<Void> error = ApiResponse.error(500, "error");
    assertThat(error.result()).isEqualTo("NG");
    assertThat(error.data()).isNull();
  }

  private void assertError(ResponseEntity<ApiResponse<Void>> response, int expectedCode) {
    assertThat(response.getStatusCode().value()).isEqualTo(expectedCode);
    assertThat(response.getBody().result()).isEqualTo("NG");
    assertThat(response.getBody().code()).isEqualTo(expectedCode);
  }
}
