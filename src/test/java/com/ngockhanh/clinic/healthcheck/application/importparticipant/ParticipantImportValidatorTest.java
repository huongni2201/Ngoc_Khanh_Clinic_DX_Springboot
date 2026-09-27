package com.ngockhanh.clinic.healthcheck.application.importparticipant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ParticipantImportValidatorTest {
    private final ParticipantImportValidator validator = new ParticipantImportValidator();

    @Test
    void normalizesRosterValuesAndBuildsAdministrativeSnapshot() {
        var row = validator.validate(List.of(row(2, " E-01 ", "  Nguyễn   Văn A ", " Nam ", "01/02/1990",
                "001 234 567 890", " 0912 345 678 ")), LocalDate.of(2026, 9, 26)).getFirst();

        assertThat(row.valid()).isTrue();
        assertThat(row.participantCode()).isEqualTo("E-01");
        assertThat(row.snapshot().fullName()).isEqualTo("NGUYỄN VĂN A");
        assertThat(row.snapshot().sex()).isEqualTo("MALE");
        assertThat(row.snapshot().identificationNumber().value()).isEqualTo("001234567890");
        assertThat(row.snapshot().phone()).isEqualTo("0912345678");
    }

    @Test
    void marksBothRowsWhenEmployeeCodeOrIdentificationNumberIsDuplicated() {
        var rows = validator.validate(List.of(
                row(2, "E-01", "Nguyen A", "Nam", "01/01/1990", "001234567890", ""),
                row(3, "E-02", "Tran B", "Nu", "01/01/1990", "001234567890", "")),
                LocalDate.of(2026, 9, 26));

        assertThat(rows).allSatisfy(row -> assertThat(row.errors())
                .extracting(ImportValidationError::code).contains("DUPLICATED_IN_FILE"));
    }

    @Test
    void marksBothRowsWhenParticipantCodeIsDuplicated() {
        var rows = validator.validate(List.of(
                row(2, "E-01", "Nguyen A", "Nam", "01/01/1990", "001234567890", ""),
                row(3, "E-01", "Tran B", "Nu", "01/01/1990", "001234567891", "")),
                LocalDate.of(2026, 9, 26));

        assertThat(rows).allSatisfy(row -> assertThat(row.errors()).anySatisfy(error -> {
            assertThat(error.field()).isEqualTo("participantCode");
            assertThat(error.code()).isEqualTo("DUPLICATED_IN_FILE");
        }));
    }

    @Test
    void rejectsInvalidDatesAndParticipantsUnderEighteenOnPlannedDate() {
        var rows = validator.validate(List.of(
                row(2, "E-01", "Nguyen A", "Nam", "31/02/2010", "001234567890", ""),
                row(3, "E-02", "Tran B", "Nu", "27/09/2008", "001234567891", "")),
                LocalDate.of(2026, 9, 26));

        assertThat(rows.get(0).errors()).extracting(ImportValidationError::code).contains("INVALID_DATE_FORMAT");
        assertThat(rows.get(1).errors()).extracting(ImportValidationError::code).contains("UNDER_18_AT_EXAMINATION");
    }

    @Test
    void marksEveryMissingRequiredFieldOnTheRow() {
        var row = validator.validate(List.of(row(2, "", "", "", "", "", "")), null).getFirst();

        assertThat(row.valid()).isFalse();
        assertThat(row.errors()).extracting(ImportValidationError::field)
                .contains("participantCode", "fullName", "sex", "dateOfBirth", "identificationNumber");
    }

    @Test
    void reportsUnsupportedSexWithoutAlsoReportingItAsMissing() {
        var row = validator.validate(List.of(row(2, "E-01", "Nguyen A", "unknown", "01/01/1990",
                "001234567890", "")), LocalDate.of(2026, 9, 26)).getFirst();

        assertThat(row.errors()).filteredOn(error -> error.field().equals("sex"))
                .extracting(ImportValidationError::code).containsExactly("INVALID_SEX");
    }

    private static RawParticipantImportRow row(int rowNumber, String participantCode, String fullName, String sex,
                                               String dateOfBirth, String identificationNumber, String phone) {
        return new RawParticipantImportRow(rowNumber, participantCode, "  Phòng A  ", "  Bác sĩ  ", "  Y tế  ",
                new RawAdministrativeSnapshot(fullName, dateOfBirth, sex, identificationNumber, "", "", "", "", "",
                        "", phone, "", "", "", "", "", ""));
    }
}
