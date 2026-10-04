package com.ngockhanh.clinic.healthexamination.application.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ParticipantImportColumnMapping;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParticipantRosterRowValidatorTest {
  private final ParticipantRosterRowValidator validator = new ParticipantRosterRowValidator();
  private final ParticipantImportColumnMapping mapping =
      ParticipantImportColumnMapping.of(
          new ParticipantRosterHeaderMapper().suggest(ParticipantRosterHeaderMapper.HEADERS));

  @Test
  void preservesLeadingZeroIdentityAndParsesExcelSerialWithoutOptionalValues() {
    var cells = validCells();
    cells.put(3, "32874");
    var row = validate(cells);
    assertThat(row.isValid()).isTrue();
    assertThat(row.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
    assertThat(row.getIdentificationNumber().value()).isEqualTo("012345678901");
    assertThat(row.getParticipantCode()).isNull();
    assertThat(row.getPhone()).isNull();
    assertThat(row.getEmail()).isNull();
  }

  @Test
  void collectsAllRowErrorsIncludingEmploymentFieldsBeforeStaging() {
    var cells = validCells();
    cells.put(4, "Unknown");
    cells.put(3, "31/02/1990");
    cells.put(5, "12A");
    cells.remove(8);
    cells.remove(9);
    cells.put(7, "invalid-email");
    var row = validate(cells);
    assertThat(row.isValid()).isFalse();
    assertThat(row.getErrorCodes())
        .contains(
            "INVALID_SEX",
            "INVALID_DATE_OF_BIRTH",
            "INVALID_IDENTIFICATION_NUMBER",
            "MISSING_DEPARTMENT_NAME",
            "MISSING_POSITION_NAME",
            "INVALID_EMAIL");
  }

  @Test
  void acceptsDayFirstAndIsoDatesWithoutAgeEligibilityOrCodeGeneration() {
    var cells = validCells();
    cells.put(3, "01/03/2018");
    var child = validate(cells);
    assertThat(child.isValid()).isTrue();
    assertThat(child.getDateOfBirth()).isEqualTo(LocalDate.of(2018, 3, 1));
    cells.put(3, "2018-03-01");
    cells.put(1, "EMP-001");
    cells.put(4, "Nữ");
    var supplied = validate(cells);
    assertThat(supplied.isValid()).isTrue();
    assertThat(supplied.getSex()).isEqualTo("FEMALE");
    assertThat(supplied.getParticipantCode()).isEqualTo("EMP-001");
  }

  @Test
  void numberedIncompleteRowsAndOversizedNamesAreBlocking() {
    assertThat(validate(Map.of(0, "1")).getErrorCodes())
        .contains("MISSING_FULL_NAME", "MISSING_IDENTIFICATION_NUMBER");
    var cells = validCells();
    cells.put(2, "X".repeat(201));
    assertThat(validate(cells).getErrorCodes()).contains("VALUE_TOO_LONG_FULL_NAME");
  }

  private HealthExaminationImportRow validate(Map<Integer, String> cells) {
    return validator.validate(AggregateId.of(new java.util.UUID(0, 1)), 3, cells, mapping);
  }

  private static Map<Integer, String> validCells() {
    return new HashMap<>(
        Map.of(
            2,
            "Test Participant",
            3,
            "01/01/1990",
            4,
            "Nam",
            5,
            "012345678901",
            8,
            "Test Department",
            9,
            "Test Position"));
  }
}
