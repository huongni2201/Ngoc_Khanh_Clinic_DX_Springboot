package com.ngockhanh.clinic.shared.web;

import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiResponse<Void>> malformedRequest(HttpMessageNotReadableException exception) {
    return error(HttpStatus.BAD_REQUEST, "Invalid request");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception) {
    return error(HttpStatus.BAD_REQUEST, "Invalid request");
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
