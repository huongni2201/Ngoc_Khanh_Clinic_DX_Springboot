package com.ngockhanh.clinic.healthexamination.application.validation;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ParticipantImportColumnMapping;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class ParticipantRosterRowValidator {
  private static final DateTimeFormatter DAY_FIRST =
      DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter ISO_DATE =
      DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);
  private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

  public HealthExaminationImportRow validate(
      AggregateId id,
      int rowNumber,
      Map<Integer, String> cells,
      ParticipantImportColumnMapping mapping) {
    if (cells == null || mapping == null)
      throw new IllegalArgumentException("Import row and mapping are required");

    List<String> errors = new ArrayList<>();

    String fullName = value(cells, mapping, ParticipantImportField.FULL_NAME);
    if (fullName == null) errors.add("MISSING_FULL_NAME");
    else if (fullName.length() > 200) errors.add("VALUE_TOO_LONG_FULL_NAME");

    String sex = normalizeSex(value(cells, mapping, ParticipantImportField.SEX));
    if (sex == null) errors.add("INVALID_SEX");

    String rawDateOfBirth = value(cells, mapping, ParticipantImportField.DATE_OF_BIRTH);
    LocalDate dateOfBirth = parseDate(rawDateOfBirth);
    if (dateOfBirth == null)
      errors.add(rawDateOfBirth == null ? "MISSING_DATE_OF_BIRTH" : "INVALID_DATE_OF_BIRTH");

    String rawIdentificationNumber =
        value(cells, mapping, ParticipantImportField.IDENTIFICATION_NUMBER);
    IdentificationNumber identificationNumber = identificationNumber(rawIdentificationNumber);
    if (identificationNumber == null) {
      errors.add(
          rawIdentificationNumber == null
              ? "MISSING_IDENTIFICATION_NUMBER"
              : "INVALID_IDENTIFICATION_NUMBER");
    }

    String department = value(cells, mapping, ParticipantImportField.DEPARTMENT_NAME);
    String position = value(cells, mapping, ParticipantImportField.POSITION_NAME);
    if (department == null) errors.add("MISSING_DEPARTMENT_NAME");
    if (position == null) errors.add("MISSING_POSITION_NAME");
    String email = value(cells, mapping, ParticipantImportField.EMAIL);
    if (email != null && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      errors.add("INVALID_EMAIL");
    return new HealthExaminationImportRow(
        id,
        rowNumber,
        value(cells, mapping, ParticipantImportField.PARTICIPANT_CODE),
        fullName,
        dateOfBirth,
        sex,
        identificationNumber,
        value(cells, mapping, ParticipantImportField.PHONE),
        email,
        department,
        position,
        errors);
  }

  private static LocalDate optionalDate(
      Map<Integer, String> cells,
      ParticipantImportColumnMapping mapping,
      ParticipantImportField field,
      String errorCode,
      List<String> errors) {
    String raw = value(cells, mapping, field);
    if (raw == null) return null;
    LocalDate parsed = parseDate(raw);
    if (parsed == null) errors.add(errorCode);
    return parsed;
  }

  private static String optionalText(
      Map<Integer, String> cells,
      ParticipantImportColumnMapping mapping,
      ParticipantImportField field,
      int maxLength,
      String errorCode,
      List<String> errors) {
    String value = value(cells, mapping, field);
    if (value != null && value.length() > maxLength) errors.add(errorCode);
    return value;
  }

  private static String value(
      Map<Integer, String> cells,
      ParticipantImportColumnMapping mapping,
      ParticipantImportField field) {
    Integer index = mapping.sourceColumn(field);
    if (index == null) return null;
    String value = cells.get(index);
    if (value == null || value.isBlank()) return null;
    return value.strip().replaceAll("\\s+", " ");
  }

  private static String normalizeSex(String raw) {
    if (raw == null) return null;
    return switch (ParticipantRosterHeaderMapper.normalize(raw)) {
      case "NAM", "MALE" -> "MALE";
      case "NU", "FEMALE" -> "FEMALE";
      case "OTHER", "KHAC" -> "OTHER";
      default -> null;
    };
  }

  private static IdentificationNumber identificationNumber(String raw) {
    if (raw == null) return null;
    try {
      return IdentificationNumber.of(raw);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private static LocalDate parseDate(String raw) {
    if (raw == null) return null;
    String value = raw.strip();
    if (value.matches("[0-9]{4,6}(\\.0+)?")) {
      try {
        long serial = new BigDecimal(value).longValueExact();
        if (serial < 1 || serial > 100_000) return null;
        LocalDate date = EXCEL_EPOCH.plusDays(serial);
        return date.getYear() >= 1900 && date.getYear() <= 2100 ? date : null;
      } catch (ArithmeticException | NumberFormatException invalid) {
        return null;
      }
    }
    try {
      return LocalDate.parse(value, DAY_FIRST);
    } catch (DateTimeParseException invalidDayFirst) {
      try {
        return LocalDate.parse(value, ISO_DATE);
      } catch (DateTimeParseException invalidIso) {
        return null;
      }
    }
  }
}
