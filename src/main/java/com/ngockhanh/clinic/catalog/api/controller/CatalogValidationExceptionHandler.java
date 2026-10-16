package com.ngockhanh.clinic.catalog.api.controller;

import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.FieldErrorDetail;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps catalog method parameter validation to the existing field-error contract. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ngockhanh.clinic.catalog.api")
public class CatalogValidationExceptionHandler {
  /**
   * @param exception violations of catalog request parameters, without logging rejected values
   * @return HTTP 400 with safe field names and constraint codes
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<List<FieldErrorDetail>>> invalidParameter(
      ConstraintViolationException exception) {
    var fields =
        exception.getConstraintViolations().stream()
            .map(
                v -> {
                  String path = v.getPropertyPath().toString();
                  return new FieldErrorDetail(
                      path.substring(path.lastIndexOf('.') + 1),
                      v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                      v.getMessage());
                })
            .sorted(Comparator.comparing(FieldErrorDetail::field))
            .toList();
    String names =
        fields.stream()
            .map(FieldErrorDetail::field)
            .distinct()
            .collect(java.util.stream.Collectors.joining(", "));
    return ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.error(400, "Invalid request: " + names, fields));
  }
}
