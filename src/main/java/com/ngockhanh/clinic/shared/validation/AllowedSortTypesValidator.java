package com.ngockhanh.clinic.shared.validation;

import java.util.List;

import com.ngockhanh.clinic.shared.web.BasePagination;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AllowedSortTypesValidator implements ConstraintValidator<AllowedSortTypes, BasePagination> {
  private List<String> allowedSortTypes;

  @Override
  public void initialize(AllowedSortTypes annotation) {
    allowedSortTypes = List.of(annotation.value());
  }

  @Override
  public boolean isValid(BasePagination request, ConstraintValidatorContext context) {
    if (request == null || request.getSortType() == null || request.getSortType().isBlank()) {
      return true;
    }
    if (allowedSortTypes.contains(request.getSortType())) {
      return true;
    }

    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(
            "sortType must be one of: " + String.join(", ", allowedSortTypes))
        .addPropertyNode("sortType")
        .addConstraintViolation();
    return false;
  }
}
