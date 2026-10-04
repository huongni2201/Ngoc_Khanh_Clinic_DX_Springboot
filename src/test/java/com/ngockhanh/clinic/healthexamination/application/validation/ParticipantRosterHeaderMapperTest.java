package com.ngockhanh.clinic.healthexamination.application.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParticipantRosterHeaderMapperTest {
  private final ParticipantRosterHeaderMapper mapper = new ParticipantRosterHeaderMapper();
  private final List<String> standard =
      List.of(
          "STT",
          "Mã nhân viên",
          "Họ và tên",
          "Ngày sinh",
          "Giới tính",
          "CCCD",
          "Điện thoại",
          "Email",
          "Phòng ban",
          "Chức danh");

  @Test
  void mapsTheStandardBatchRosterWithoutInventingAnEmployeeCode() {
    assertThat(mapper.suggest(standard))
        .containsEntry(ParticipantImportField.PARTICIPANT_CODE, 1)
        .containsEntry(ParticipantImportField.FULL_NAME, 2)
        .containsEntry(ParticipantImportField.DATE_OF_BIRTH, 3)
        .containsEntry(ParticipantImportField.IDENTIFICATION_NUMBER, 5)
        .containsEntry(ParticipantImportField.DEPARTMENT_NAME, 8)
        .containsEntry(ParticipantImportField.POSITION_NAME, 9);
  }

  @Test
  void matchesAccentsCaseAndWhitespaceButRejectsMissingOrReorderedColumns() {
    var normalized =
        standard.stream()
            .map(
                value ->
                    ParticipantRosterHeaderMapper.normalize(value)
                        .toLowerCase(java.util.Locale.ROOT))
            .toList();
    assertThat(mapper.suggest(normalized)).isEqualTo(mapper.suggest(standard));
    assertThatThrownBy(() -> mapper.suggest(standard.subList(0, 9)))
        .isInstanceOf(IllegalArgumentException.class);
    var reordered = new java.util.ArrayList<>(standard);
    java.util.Collections.swap(reordered, 2, 3);
    assertThatThrownBy(() -> mapper.suggest(reordered))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
