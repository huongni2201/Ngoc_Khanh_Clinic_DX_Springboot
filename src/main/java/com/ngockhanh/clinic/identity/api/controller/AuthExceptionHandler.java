package com.ngockhanh.clinic.identity.api.controller;

import com.ngockhanh.clinic.identity.application.port.AuthenticationFailure;
import com.ngockhanh.clinic.shared.web.ApiError;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> malformedRequest() {
    return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
        .body(new ApiError(400, "Invalid request"));
  }

  @ExceptionHandler(AuthenticationFailure.class)
  ResponseEntity<ApiError> authentication(AuthenticationFailure error) {
    var builder = ResponseEntity.status(error.status()).cacheControl(CacheControl.noStore());
    if (error.retryAfter() > 0) builder.header("Retry-After", Long.toString(error.retryAfter()));
    return builder.body(new ApiError(error.status(), error.getMessage()));
  }
}
