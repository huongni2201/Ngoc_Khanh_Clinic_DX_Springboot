package com.ngockhanh.clinic.accesscontrol.api.request;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * The password fits bcrypt: at most 72 UTF-8 bytes after NFKC normalization, as stored by {@code
 * UserPasswordEncoder}. Null is valid; combine with {@code @NotNull}.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BcryptPasswordLength.Validator.class)
public @interface BcryptPasswordLength {
  Integer MAX_BYTES = 72;

  String message() default "must be at most 72 UTF-8 bytes after NFKC normalization";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  /** Checks the encoded length without logging or returning the password. */
  final class Validator implements ConstraintValidator<BcryptPasswordLength, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) return true;
      return Normalizer.normalize(value, Normalizer.Form.NFKC).getBytes(StandardCharsets.UTF_8).length
          <= MAX_BYTES;
    }
  }
}
