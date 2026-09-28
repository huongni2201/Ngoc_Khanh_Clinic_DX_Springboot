package com.ngockhanh.clinic.shared.validation;

import java.util.List;

import com.ngockhanh.clinic.shared.web.BasePagination;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AllowedSortKeysValidator implements ConstraintValidator<AllowedSortKeys, BasePagination> {
  private List<String> allowedSortKeys;

  @Override
  public void initialize(AllowedSortKeys annotation) {
    allowedSortKeys = List.of(annotation.value());
  }

  @Override
  public boolean isValid(BasePagination request, ConstraintValidatorContext context) {
    if (request == null || request.getSortKey() == null || request.getSortKey().isBlank()) {
      return true;
    }
    if (allowedSortKeys.contains(request.getSortKey())) {
      return true;
    }

    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(
            "sortKey must be one of: " + String.join(", ", allowedSortKeys))
        .addPropertyNode("sortKey")
        .addConstraintViolation();
    return false;
  }
}
