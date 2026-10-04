package com.ngockhanh.clinic.healthexamination.application.validation;

import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class ParticipantRosterHeaderMapper {
  public static final List<String> HEADERS =
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

  public Map<ParticipantImportField, Integer> suggest(List<String> headers) {
    if (headers == null
        || !headers.stream()
            .map(ParticipantRosterHeaderMapper::normalize)
            .toList()
            .equals(HEADERS.stream().map(ParticipantRosterHeaderMapper::normalize).toList()))
      throw new IllegalArgumentException(
          "Workbook does not match the standard batch participant template");
    var fields =
        List.of(
            ParticipantImportField.PARTICIPANT_CODE,
            ParticipantImportField.FULL_NAME,
            ParticipantImportField.DATE_OF_BIRTH,
            ParticipantImportField.SEX,
            ParticipantImportField.IDENTIFICATION_NUMBER,
            ParticipantImportField.PHONE,
            ParticipantImportField.EMAIL,
            ParticipantImportField.DEPARTMENT_NAME,
            ParticipantImportField.POSITION_NAME);
    var mapping = new EnumMap<ParticipantImportField, Integer>(ParticipantImportField.class);
    for (int i = 0; i < fields.size(); i++) mapping.put(fields.get(i), i + 1);
    return Map.copyOf(mapping);
  }

  public static String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9]+", " ")
        .trim();
  }
}
