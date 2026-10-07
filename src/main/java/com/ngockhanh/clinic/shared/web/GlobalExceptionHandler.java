package com.ngockhanh.clinic.shared.web;

import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.exception.UnsupportedFileTypeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {
  private final ApiResponseWriter responseWriter;

  @ExceptionHandler(ApplicationException.class)
  ResponseEntity<ApiResponse<Void>> application(ApplicationException exception) {
    HttpStatus status = HttpStatus.valueOf(responseWriter.statusFor(exception));
    var builder = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
    if (exception.retryAfterSeconds() > 0) {
      builder.header("Retry-After", Long.toString(exception.retryAfterSeconds()));
    }
    return builder.body(ApiResponse.error(status.value(), exception.getMessage()));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiResponse<Void>> malformedRequest(RuntimeException exception) {
    return error(HttpStatus.BAD_REQUEST, "Invalid request");
  }

  @ExceptionHandler({ServletRequestBindingException.class, MissingServletRequestPartException.class})
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

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception) {
    return error(HttpStatus.BAD_REQUEST, "Invalid request");
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
    log.warn("Dependency unavailable: {}", exception.getMessage());
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

  private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.error(status.value(), message));
  }
}
