package com.ngockhanh.clinic.healthcheck.application.importparticipant;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;

@Component
public final class ParticipantImportValidator {
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            strictDateFormat("dd/MM/uuuu"), strictDateFormat("d/M/uuuu"), strictDateFormat("uuuu-MM-dd"));

    public List<ValidatedParticipantImportRow> validate(List<RawParticipantImportRow> rawRows,
                                                        LocalDate plannedExaminationDate) {
        if (rawRows == null) throw new IllegalArgumentException("Missing participant import rows");

        List<MutableRow> rows = rawRows.stream().map(row -> validateRow(row, plannedExaminationDate)).toList();
        markDuplicates(rows, MutableRow::participantCode, "participantCode");
        markDuplicates(rows, MutableRow::identificationNumberValue, "identificationNumber");
        return rows.stream().map(MutableRow::toValidatedRow).toList();
    }

    private MutableRow validateRow(RawParticipantImportRow raw, LocalDate plannedDate) {
        if (raw == null || raw.rowNumber() < 1) throw new IllegalArgumentException("Invalid participant import row");
        RawAdministrativeSnapshot source = raw.snapshot();
        List<ImportValidationError> errors = new ArrayList<>();
        String code = normalized(raw.participantCode());
        String department = normalized(raw.departmentName());
        String title = normalized(raw.jobTitle());
        String occupation = normalized(raw.occupation());
        String name = source == null ? null : normalized(source.fullName());
        if (name != null) name = name.toUpperCase(Locale.forLanguageTag("vi"));
        String sex = source == null ? null : normalizeSex(source.sex());
        String idValue = source == null ? null : digitsWithoutWhitespace(source.identificationNumber());
        LocalDate dateOfBirth = source == null ? null : parseDate(source.dateOfBirth());
        LocalDate issueDate = source == null ? null : parseOptionalDate(source.identificationNumberIssueDate(),
                "identificationNumberIssueDate", raw.rowNumber(), errors);

        required(raw.rowNumber(), "participantCode", code, errors);
        required(raw.rowNumber(), "fullName", name, errors);
        if (source == null || blank(source.sex())) {
            addError(errors, raw.rowNumber(), "sex", "REQUIRED_FIELD", "Giới tính là bắt buộc.");
        }
        if (source == null || blank(source.dateOfBirth())) {
            addError(errors, raw.rowNumber(), "dateOfBirth", "REQUIRED_FIELD", "Ngày sinh là bắt buộc.");
        } else if (dateOfBirth == null) {
            addError(errors, raw.rowNumber(), "dateOfBirth", "INVALID_DATE_FORMAT", "Ngày sinh không hợp lệ.");
        }
        if (source == null || blank(source.identificationNumber())) {
            addError(errors, raw.rowNumber(), "identificationNumber", "REQUIRED_FIELD", "Số định danh là bắt buộc.");
        } else {
            try {
                IdentificationNumber.of(idValue);
            } catch (IllegalArgumentException invalidNumber) {
                addError(errors, raw.rowNumber(), "identificationNumber", "INVALID_IDENTIFICATION_NUMBER",
                        "Số định danh chỉ được chứa chữ số.");
            }
        }
        if (sex == null && source != null && !blank(source.sex())) {
            addError(errors, raw.rowNumber(), "sex", "INVALID_SEX", "Giới tính không hợp lệ.");
        }
        if (dateOfBirth != null && plannedDate != null && isUnderEighteen(dateOfBirth, plannedDate)) {
            addError(errors, raw.rowNumber(), "dateOfBirth", "UNDER_18_AT_EXAMINATION",
                    "Người tham gia chưa đủ 18 tuổi vào ngày khám.");
        }

        AdministrativeSnapshot snapshot = null;
        if (source != null && name != null && dateOfBirth != null && sex != null && idValue != null) {
            try {
                snapshot = new AdministrativeSnapshot(name, dateOfBirth, sex, IdentificationNumber.of(idValue),
                        issueDate, normalized(source.identificationNumberIssuePlace()), normalized(source.ethnicity()),
                        normalized(source.subjectType()), normalized(source.payerSource()), normalized(source.bloodGroup()),
                        normalizedPhone(source.phone()), normalized(source.province()), normalized(source.ward()),
                        normalized(source.addressDetail()), normalized(source.occupation()),
                        normalized(source.workplaceOrSchool()), normalized(source.healthExaminationReason()));
            } catch (IllegalArgumentException ignored) {
                // Required-field errors are already reported above.
            }
        }
        return new MutableRow(raw.rowNumber(), code, department, title, occupation, snapshot, idValue, errors);
    }

    private static void markDuplicates(List<MutableRow> rows,
                                      java.util.function.Function<MutableRow, String> key,
                                      String field) {
        Map<String, List<MutableRow>> byValue = new HashMap<>();
        for (MutableRow row : rows) {
            String value = key.apply(row);
            if (value != null && !value.isBlank()) byValue.computeIfAbsent(value, unused -> new ArrayList<>()).add(row);
        }
        byValue.values().stream().filter(duplicates -> duplicates.size() > 1).forEach(duplicates -> {
            for (MutableRow duplicate : duplicates) {
                duplicate.errors().add(new ImportValidationError(duplicate.rowNumber(), field,
                        "DUPLICATED_IN_FILE", "Giá trị bị trùng trong tệp."));
            }
        });
    }

    private static void required(int rowNumber, String field, String value, List<ImportValidationError> errors) {
        if (blank(value)) addError(errors, rowNumber, field, "REQUIRED_FIELD", "Trường bắt buộc.");
    }

    private static void addError(List<ImportValidationError> errors, int rowNumber, String field,
                                 String code, String message) {
        if (errors.stream().noneMatch(error -> error.field().equals(field) && error.code().equals(code))) {
            errors.add(new ImportValidationError(rowNumber, field, code, message));
        }
    }

    private static String normalizeSex(String value) {
        String sex = normalized(value);
        if (sex == null) return null;
        return switch (sex.toUpperCase(Locale.forLanguageTag("vi"))) {
            case "NAM", "MALE", "M" -> "MALE";
            case "NỮ", "NU", "FEMALE", "F" -> "FEMALE";
            case "KHÁC", "KHAC", "OTHER" -> "OTHER";
            default -> null;
        };
    }

    private static String digitsWithoutWhitespace(String value) {
        String trimmed = normalized(value);
        return trimmed == null ? null : trimmed.replaceAll("\\s+", "");
    }

    private static String normalizedPhone(String value) {
        String phone = normalized(value);
        return phone == null ? null : phone.replaceAll("\\s+", "");
    }

    private static String normalized(String value) {
        if (blank(value)) return null;
        return value.strip().replaceAll("(?U)\\s+", " ");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static LocalDate parseOptionalDate(String value, String field, int rowNumber,
                                               List<ImportValidationError> errors) {
        if (blank(value)) return null;
        LocalDate parsed = parseDate(value);
        if (parsed == null) addError(errors, rowNumber, field, "INVALID_DATE_FORMAT", "Ngày không hợp lệ.");
        return parsed;
    }

    private static LocalDate parseDate(String value) {
        if (blank(value)) return null;
        String date = value.strip();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(date, formatter);
            } catch (RuntimeException ignored) {
                // Try the next documented spreadsheet date format.
            }
        }
        return null;
    }

    private static DateTimeFormatter strictDateFormat(String pattern) {
        return new DateTimeFormatterBuilder().appendPattern(pattern).toFormatter(Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    private static boolean isUnderEighteen(LocalDate dateOfBirth, LocalDate examinationDate) {
        LocalDate eighteenthBirthday = dateOfBirth.plusYears(18);
        if (dateOfBirth.getMonthValue() == 2 && dateOfBirth.getDayOfMonth() == 29
                && !eighteenthBirthday.isLeapYear()) {
            eighteenthBirthday = eighteenthBirthday.plusDays(1);
        }
        return eighteenthBirthday.isAfter(examinationDate);
    }

    private static final class MutableRow {
        private final int rowNumber;
        private final String participantCode;
        private final String departmentName;
        private final String jobTitle;
        private final String occupation;
        private final AdministrativeSnapshot snapshot;
        private final String identificationNumberValue;
        private final List<ImportValidationError> errors;

        private MutableRow(int rowNumber, String participantCode, String departmentName, String jobTitle,
                           String occupation, AdministrativeSnapshot snapshot, String identificationNumberValue,
                           List<ImportValidationError> errors) {
            this.rowNumber = rowNumber;
            this.participantCode = participantCode;
            this.departmentName = departmentName;
            this.jobTitle = jobTitle;
            this.occupation = occupation;
            this.snapshot = snapshot;
            this.identificationNumberValue = identificationNumberValue;
            this.errors = errors;
        }

        private int rowNumber() { return rowNumber; }
        private String participantCode() { return participantCode; }
        private String identificationNumberValue() { return identificationNumberValue; }
        private List<ImportValidationError> errors() { return errors; }

        private ValidatedParticipantImportRow toValidatedRow() {
            return new ValidatedParticipantImportRow(rowNumber, participantCode, departmentName, jobTitle,
                    occupation, snapshot, errors);
        }
    }
}
