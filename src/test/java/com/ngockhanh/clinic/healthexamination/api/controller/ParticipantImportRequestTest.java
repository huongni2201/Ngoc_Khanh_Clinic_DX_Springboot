package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.healthexamination.api.request.*;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.Test;

class ParticipantImportRequestTest {
  @Test
  void uploadRequiresDaysButNoPreviewVersion() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertThat(validator.validate(new ParticipantImportUploadRequest(List.of(UUID.randomUUID()))))
          .isEmpty();
      assertThat(validator.validate(new ParticipantImportUploadRequest(List.of()))).isNotEmpty();
    }
  }

  @Test
  void previewAndConfirmationCannotOmitExpectedVersion() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertThat(
              validator.validate(
                  new ParticipantImportMappingRequest(List.of(UUID.randomUUID()), null, Map.of())))
          .extracting(v -> v.getPropertyPath().toString())
          .contains("expectedRowVersion");
      assertThat(validator.validate(new ParticipantImportConfirmRequest(null))).isNotEmpty();
      assertThat(validator.validate(new ParticipantImportConfirmRequest(-1L))).isNotEmpty();
      assertThat(validator.validate(new ParticipantImportConfirmRequest(0L))).isEmpty();
    }
  }
}
