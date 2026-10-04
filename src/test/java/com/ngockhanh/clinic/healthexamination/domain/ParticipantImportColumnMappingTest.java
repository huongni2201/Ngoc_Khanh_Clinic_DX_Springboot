package com.ngockhanh.clinic.healthexamination.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ParticipantImportColumnMapping;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParticipantImportColumnMappingTest {
  @Test
  void acceptsEveryRequiredFieldAndKeepsOptionalColumnsUnmapped() {
    Map<ParticipantImportField, Integer> columns = new EnumMap<>(ParticipantImportField.class);
    columns.put(ParticipantImportField.FULL_NAME, 1);
    columns.put(ParticipantImportField.SEX, 2);
    columns.put(ParticipantImportField.DATE_OF_BIRTH, 3);
    columns.put(ParticipantImportField.IDENTIFICATION_NUMBER, 5);
    columns.put(ParticipantImportField.DEPARTMENT_NAME, 8);
    columns.put(ParticipantImportField.POSITION_NAME, 9);

    ParticipantImportColumnMapping mapping = ParticipantImportColumnMapping.of(columns);

    assertThat(mapping.sourceColumn(ParticipantImportField.IDENTIFICATION_NUMBER)).isEqualTo(5);
    assertThat(mapping.sourceColumn(ParticipantImportField.PHONE)).isNull();
    assertThatThrownBy(() -> mapping.columns().put(ParticipantImportField.PHONE, 4))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsMissingRequiredColumn() {
    Map<ParticipantImportField, Integer> columns = new EnumMap<>(ParticipantImportField.class);
    columns.put(ParticipantImportField.FULL_NAME, 1);

    assertThatThrownBy(() -> ParticipantImportColumnMapping.of(columns))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsTwoFieldsMappedToOneSourceColumn() {
    Map<ParticipantImportField, Integer> columns = new EnumMap<>(ParticipantImportField.class);
    columns.put(ParticipantImportField.FULL_NAME, 1);
    columns.put(ParticipantImportField.SEX, 1);
    columns.put(ParticipantImportField.DATE_OF_BIRTH, 3);
    columns.put(ParticipantImportField.IDENTIFICATION_NUMBER, 5);
    columns.put(ParticipantImportField.DEPARTMENT_NAME, 8);
    columns.put(ParticipantImportField.POSITION_NAME, 9);

    assertThatThrownBy(() -> ParticipantImportColumnMapping.of(columns))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
