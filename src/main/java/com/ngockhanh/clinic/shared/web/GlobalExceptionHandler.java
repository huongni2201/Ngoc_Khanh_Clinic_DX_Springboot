package com.ngockhanh.clinic.shared.web;

import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import com.ngockhanh.clinic.shared.exception.UnsupportedFileTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(ApplicationException.class)
  ResponseEntity<ApiResponse<Void>> application(ApplicationException exception) {
    HttpStatus status = statusFor(exception);
    var builder = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
    if (exception.retryAfterSeconds() > 0) {
      builder.header("Retry-After", Long.toString(exception.retryAfterSeconds()));
    }
    return builder.body(ApiResponse.error(status.value(), exception.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiResponse<List<FieldErrorDetail>>> malformedRequest(
      HttpMessageNotReadableException exception) {
    return validationError(List.of(), "Invalid request body");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiResponse<List<FieldErrorDetail>>> typeMismatch(
      MethodArgumentTypeMismatchException exception) {
    return validationError(
        List.of(
            new FieldErrorDetail(
                exception.getName(), "TypeMismatch", "Parameter value has the wrong type")),
        "Invalid request");
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ApiResponse<List<FieldErrorDetail>>> missingParameter(
      MissingServletRequestParameterException exception) {
    return validationError(
        List.of(
            new FieldErrorDetail(
                exception.getParameterName(), "Missing", "Required parameter is missing")),
        "Invalid request");
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  ResponseEntity<ApiResponse<List<FieldErrorDetail>>> missingPart(
      MissingServletRequestPartException exception) {
    return validationError(
        List.of(
            new FieldErrorDetail(
                exception.getRequestPartName(), "Missing", "Required part is missing")),
        "Invalid request");
  }

  @ExceptionHandler(ServletRequestBindingException.class)
  ResponseEntity<ApiResponse<Void>> missingRequestInput(Exception exception) {
    return error(HttpStatus.BAD_REQUEST, "Invalid request");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiResponse<Void>> uploadTooLarge(MaxUploadSizeExceededException exception) {
    return error(HttpStatus.CONTENT_TOO_LARGE, "Upload exceeds the permitted size");
  }

  @ExceptionHandler(MultipartException.class)
  ResponseEntity<ApiResponse<Void>> malformedMultipart(MultipartException exception) {
    return error(HttpStatus.BAD_REQUEST, "Invalid request");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiResponse<Void>> unsupportedMediaType(
      HttpMediaTypeNotSupportedException exception) {
    return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported request content type");
  }

  @ExceptionHandler(UnsupportedFileTypeException.class)
  ResponseEntity<ApiResponse<Void>> unsupportedFile(UnsupportedFileTypeException exception) {
    return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ApiResponse<Void>> conflict(ConflictException exception) {
    return error(HttpStatus.CONFLICT, exception.getMessage());
  }

  @ExceptionHandler(UnprocessableEntityException.class)
  ResponseEntity<ApiResponse<Void>> unprocessable(UnprocessableEntityException exception) {
    return error(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<List<FieldErrorDetail>>> validation(
      MethodArgumentNotValidException exception) {
    return validationError(bindingDetails(exception.getBindingResult()), "Invalid request");
  }

  @ExceptionHandler(BindException.class)
  ResponseEntity<ApiResponse<List<FieldErrorDetail>>> binding(BindException exception) {
    return validationError(bindingDetails(exception.getBindingResult()), "Invalid request");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiResponse<Void>> methodNotAllowed(
      HttpRequestMethodNotSupportedException exception) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .headers(exception.getHeaders())
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.error(
                HttpStatus.METHOD_NOT_ALLOWED.value(), "HTTP method is not supported"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiResponse<Void>> forbidden(AccessDeniedException exception) {
    return error(HttpStatus.FORBIDDEN, "You are not authorized to perform this action");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiResponse<Void>> invalidInput(IllegalArgumentException exception) {
    return error(HttpStatus.BAD_REQUEST, "Invalid request");
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<ApiResponse<Void>> duplicate(DuplicateKeyException exception) {
    return error(HttpStatus.CONFLICT, "A record with the same identity already exists");
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ApiResponse<Void>> notFound(ResourceNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler(ConcurrentUpdateException.class)
  ResponseEntity<ApiResponse<Void>> concurrency(ConcurrentUpdateException exception) {
    return error(HttpStatus.CONFLICT, exception.getMessage());
  }

  @ExceptionHandler(DependencyUnavailableException.class)
  ResponseEntity<ApiResponse<Void>> dependencyUnavailable(
      DependencyUnavailableException exception) {
    log.info("Dependency unavailable: {}", exception.getMessage());
    return error(HttpStatus.SERVICE_UNAVAILABLE, "Service is temporarily unavailable");
  }

  @ExceptionHandler(BusinessRuleException.class)
  ResponseEntity<ApiResponse<Void>> businessRule(BusinessRuleException exception) {
    return error(HttpStatus.CONFLICT, "Business rule could not be completed");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
    log.error("Unhandled request failure type={}", exception.getClass().getSimpleName());
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }

  private HttpStatus statusFor(ApplicationException exception) {
    return switch (exception.type()) {
      case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
      case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
      case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
      case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
      case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
    };
  }

  /** Names the rejected fields in the message and in {@code data}, so the cause is visible. */
  private ResponseEntity<ApiResponse<List<FieldErrorDetail>>> validationError(
      List<FieldErrorDetail> details, String summary) {
    String fields =
        details.stream()
            .map(FieldErrorDetail::field)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining(", "));
    String message = fields.isEmpty() ? summary : summary + ": " + fields;
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message, details));
  }

  /** Maps Bean Validation and binding failures to field details; input values never appear. */
  private static List<FieldErrorDetail> bindingDetails(BindingResult result) {
    List<FieldErrorDetail> details = new ArrayList<>();
    for (FieldError fieldError : result.getFieldErrors()) {
      String message =
          fieldError.isBindingFailure()
              ? "Parameter value has the wrong type"
              : fieldError.getDefaultMessage();
      details.add(
          new FieldErrorDetail(
              publicFieldName(fieldError.getField()), fieldError.getCode(), message));
    }
    for (ObjectError globalError : result.getGlobalErrors()) {
      details.add(
          new FieldErrorDetail(
              globalError.getObjectName(), globalError.getCode(), globalError.getDefaultMessage()));
    }
    return details;
  }

  /**
   * Bean Validation names the getter {@code isSiteAddressValid()} as a property. Clients know the
   * form field, so the report uses it.
   */
  private static String publicFieldName(String field) {
    return "siteAddressValid".equals(field) ? "examinationSiteAddress" : field;
  }

  private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.error(status.value(), message));
  }
}
