package com.ngockhanh.clinic.healthexamination.application.contract;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Values of the blank periodic health examination form (contract version 1), already formatted as
 * text. Only the administrative part of the form is filled; nothing clinical is printed. A fact the
 * roster lacks prints as a dotted line so the doctor or the person can write it by hand.
 */
public record PeriodicFormBlankModel(
    String participantCode,
    String participantFullName,
    String participantFullNameUpper,
    String participantSex,
    String participantDateOfBirth,
    String participantYearOfBirth,
    String participantAge,
    String participantIdentificationNumber,
    String participantIdentificationIssueDate,
    String participantIdentificationIssuePlace,
    String participantPhone,
    String participantAddress,
    String participantEthnicity,
    String participantWorkplace,
    String participantDepartmentName,
    String participantPositionName,
    String participantExaminationDate,
    String organizationName,
    String organizationAddress,
    String batchCode,
    String batchName,
    String printedDate) {
  /** Printed where the roster has no value. */
  public static final String MISSING = "…………………………";

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

  /** Formats the source. The workplace defaults to the organization name. */
  public static PeriodicFormBlankModel from(ExaminationFormSource source) {
    LocalDate dateOfBirth = source.dateOfBirth();
    LocalDate reference =
        source.examinationDate() != null ? source.examinationDate() : source.printedDate();
    String fullName = text(source.fullName());
    String workplace = text(source.workplace());
    if (workplace.isEmpty()) workplace = text(source.organizationName());
    return new PeriodicFormBlankModel(
        orMissing(text(source.participantCode())),
        orMissing(fullName),
        orMissing(fullName.toUpperCase(VIETNAMESE)),
        sex(source.sex()),
        date(dateOfBirth),
        dateOfBirth == null ? MISSING : Integer.toString(dateOfBirth.getYear()),
        age(dateOfBirth, reference),
        orMissing(text(source.identificationNumber())),
        date(source.identificationIssueDate()),
        orMissing(text(source.identificationIssuePlace())),
        orMissing(text(source.phone())),
        orMissing(text(source.address())),
        orMissing(text(source.ethnicity())),
        orMissing(workplace),
        orMissing(text(source.departmentName())),
        orMissing(text(source.positionName())),
        date(source.examinationDate()),
        text(source.organizationName()),
        text(source.organizationAddress()),
        text(source.batchCode()),
        text(source.batchName()),
        source.printedDate() == null ? "" : DATE.format(source.printedDate()));
  }

  /** Placeholder key to value, in the order of {@link PeriodicFormBlankContract#placeholders()}. */
  public Map<String, String> toValues() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("participant.code", participantCode);
    // A Participant without a code has no barcode; the picture is left out, never a dotted line.
    values.put("participant.barcode", MISSING.equals(participantCode) ? "" : participantCode);
    values.put("participant.fullName", participantFullName);
    values.put("participant.fullNameUpper", participantFullNameUpper);
    values.put("participant.sex", participantSex);
    values.put("participant.dateOfBirth", participantDateOfBirth);
    values.put("participant.yearOfBirth", participantYearOfBirth);
    values.put("participant.age", participantAge);
    values.put("participant.identificationNumber", participantIdentificationNumber);
    values.put("participant.identificationIssueDate", participantIdentificationIssueDate);
    values.put("participant.identificationIssuePlace", participantIdentificationIssuePlace);
    values.put("participant.phone", participantPhone);
    values.put("participant.address", participantAddress);
    values.put("participant.ethnicity", participantEthnicity);
    values.put("participant.workplace", participantWorkplace);
    values.put("participant.departmentName", participantDepartmentName);
    values.put("participant.positionName", participantPositionName);
    values.put("participant.examinationDate", participantExaminationDate);
    values.put("organization.name", organizationName);
    values.put("organization.address", organizationAddress);
    values.put("batch.code", batchCode);
    values.put("batch.name", batchName);
    values.put("printedDate", printedDate);
    return values;
  }

  private static String text(String value) {
    return value == null ? "" : value.strip();
  }

  private static String orMissing(String value) {
    return value.isEmpty() ? MISSING : value;
  }

  private static String date(LocalDate value) {
    return value == null ? MISSING : DATE.format(value);
  }

  private static String sex(String sex) {
    if (sex == null) return MISSING;
    return switch (sex) {
      case "MALE" -> "Nam";
      case "FEMALE" -> "Nữ";
      case "OTHER" -> "Khác";
      default -> MISSING;
    };
  }

  private static String age(LocalDate dateOfBirth, LocalDate reference) {
    if (dateOfBirth == null || reference == null) return MISSING;
    return Integer.toString(Math.max(0, Period.between(dateOfBirth, reference).getYears()));
  }
}
