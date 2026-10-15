package com.ngockhanh.clinic.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tools.jackson.databind.json.JsonMapper;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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
    ResponseEntity<ApiResponse<Void>> unprocessable =
        handler.unprocessable(
            new com.ngockhanh.clinic.shared.exception.UnprocessableEntityException(
                "The role is not available"));
    assertError(unprocessable, 422);
    assertThat(unprocessable.getBody().message()).isEqualTo("The role is not available");
    assertError(handler.concurrency(new ConcurrentUpdateException()), 409);
    ResponseEntity<ApiResponse<Void>> customConflict =
        handler.concurrency(
            new ConcurrentUpdateException("EXPECTED_VERSION_MISMATCH", "Record has changed"));
    assertError(customConflict, 409);
    assertThat(customConflict.getBody().message()).isEqualTo("Record has changed");
    assertThat(JsonMapper.builder().build().writeValueAsString(customConflict.getBody()))
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
            .page(1)
            .size(20)
            .totalElements(1)
            .totalPages(1)
            .build();
    ApiResponse<PageResponse<String>> response = ApiResponse.success(200, "OK", page);

    assertThat(response.result()).isEqualTo("OK");
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.data().items()).containsExactly("item");
    assertThat(response.data().page()).isEqualTo(1);
    ApiResponse<Void> error = ApiResponse.error(500, "error");
    assertThat(error.result()).isEqualTo("NG");
    assertThat(error.data()).isNull();
  }

  @Test
  void namesInvalidBodyFieldsWithoutEchoingRejectedValues() throws Exception {
    BindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
    bindingResult.addError(
        new FieldError(
            "request",
            "batchName",
            "secret-value",
            false,
            new String[] {"NotBlank.request.batchName", "NotBlank"},
            null,
            "must not be blank"));
    bindingResult.addError(
        new FieldError(
            "request", "services", null, false, new String[] {"NotEmpty"}, null, "must not be empty"));

    ResponseEntity<ApiResponse<List<FieldErrorDetail>>> response =
        handler.validation(new MethodArgumentNotValidException(methodParameter(), bindingResult));

    assertError(response, 400);
    assertThat(response.getBody().message()).isEqualTo("Invalid request: batchName, services");
    assertThat(response.getBody().data())
        .containsExactly(
            new FieldErrorDetail("batchName", "NotBlank", "must not be blank"),
            new FieldErrorDetail("services", "NotEmpty", "must not be empty"));
    assertThat(response.getBody().message()).doesNotContain("secret-value");
  }

  @Test
  void mapsSiteAddressCheckToFormFieldName() throws Exception {
    BindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
    bindingResult.addError(
        new FieldError(
            "request",
            "siteAddressValid",
            null,
            false,
            new String[] {"AssertTrue"},
            null,
            "Examination site address is required for an organization site"));

    ResponseEntity<ApiResponse<List<FieldErrorDetail>>> response =
        handler.validation(new MethodArgumentNotValidException(methodParameter(), bindingResult));

    assertError(response, 400);
    assertThat(response.getBody().data())
        .containsExactly(
            new FieldErrorDetail(
                "examinationSiteAddress",
                "AssertTrue",
                "Examination site address is required for an organization site"));
  }

  @Test
  void reportsMissingRequiredFieldFromModelAttributeBinding() throws Exception {
    BindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
    bindingResult.addError(
        new FieldError(
            "request", "rowVersion", null, false, new String[] {"NotNull"}, null, "must not be null"));

    ResponseEntity<ApiResponse<List<FieldErrorDetail>>> response =
        handler.binding(new BindException(bindingResult));

    assertError(response, 400);
    assertThat(response.getBody().message()).isEqualTo("Invalid request: rowVersion");
    assertThat(response.getBody().data())
        .containsExactly(new FieldErrorDetail("rowVersion", "NotNull", "must not be null"));
  }

  @Test
  void reportsBindingFailuresWithoutEchoingInputValue() {
    BindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
    bindingResult.addError(
        new FieldError(
            "request",
            "page",
            "abc",
            true,
            new String[] {"typeMismatch"},
            null,
            "For input string: \"abc\""));

    ResponseEntity<ApiResponse<List<FieldErrorDetail>>> response =
        handler.binding(new BindException(bindingResult));

    assertError(response, 400);
    assertThat(response.getBody().message()).isEqualTo("Invalid request: page");
    assertThat(response.getBody().data())
        .containsExactly(
            new FieldErrorDetail("page", "typeMismatch", "Parameter value has the wrong type"));
    assertThat(response.getBody().data().toString()).doesNotContain("abc");
  }

  @Test
  void namesMissingRequiredParameterAndPart() {
    ResponseEntity<ApiResponse<List<FieldErrorDetail>>> parameter =
        handler.missingParameter(
            new MissingServletRequestParameterException("examinationId", "UUID"));
    assertError(parameter, 400);
    assertThat(parameter.getBody().message()).isEqualTo("Invalid request: examinationId");
    assertThat(parameter.getBody().data())
        .containsExactly(
            new FieldErrorDetail("examinationId", "Missing", "Required parameter is missing"));

    ResponseEntity<ApiResponse<List<FieldErrorDetail>>> part =
        handler.missingPart(new MissingServletRequestPartException("file"));
    assertError(part, 400);
    assertThat(part.getBody().message()).isEqualTo("Invalid request: file");
  }

  private static MethodParameter methodParameter() throws NoSuchMethodException {
    return new MethodParameter(Object.class.getMethod("toString"), -1);
  }

  private void assertError(ResponseEntity<? extends ApiResponse<?>> response, int expectedCode) {
    assertThat(response.getStatusCode().value()).isEqualTo(expectedCode);
    assertThat(response.getBody().result()).isEqualTo("NG");
    assertThat(response.getBody().code()).isEqualTo(expectedCode);
  }
}
