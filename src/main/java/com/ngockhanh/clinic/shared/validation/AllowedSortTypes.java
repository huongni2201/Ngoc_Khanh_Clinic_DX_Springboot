package com.ngockhanh.clinic.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AllowedSortTypesValidator.class)
public @interface AllowedSortTypes {
  String message() default "sortType is not allowed";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  String[] value();
}
