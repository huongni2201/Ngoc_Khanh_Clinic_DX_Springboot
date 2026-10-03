package com.ngockhanh.clinic.healthexamination.application.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ParticipantImportColumnMapping;

class ParticipantRosterRowValidatorTest {
    private final ParticipantRosterRowValidator validator = new ParticipantRosterRowValidator();

    @Test
    void parsesExcelDateSerialAndKeepsCccdLeadingZero() {
        HealthExaminationImportRow row = validator.validate(id(1), 3,
                Map.of(1, "Test Person", 2, "Nam", 3, "32874", 5, "012345678901", 15, "Roster note"),
                mapping());

        assertThat(row.isValid()).isTrue();
        assertThat(row.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(row.getIdentificationNumber().value()).isEqualTo("012345678901");
        assertThat(row.getRosterNote()).isEqualTo("Roster note");
        assertThat(row.getWarningCodes()).contains("OPTIONAL_FIELDS_MISSING");
    }

    @Test
    void reportsMultipleRequiredAndFormatErrorsOnOneRow() {
        HealthExaminationImportRow row = validator.validate(id(1), 4,
                Map.of(1, "Test Person", 2, "Unknown", 3, "31/02/1990", 5, "12A"),
                mapping());

        assertThat(row.isValid()).isFalse();
        assertThat(row.getErrorCodes()).contains("INVALID_SEX", "INVALID_DATE_OF_BIRTH", "INVALID_IDENTIFICATION_NUMBER");
    }

    @Test
    void doesNotRejectRowsByAge() {
        HealthExaminationImportRow row = validator.validate(id(1), 5,
                Map.of(1, "Test Person", 2, "Nam", 3, "01/03/2018", 5, "012345678901"), mapping());

        assertThat(row.isValid()).isTrue();
    }

    private static ParticipantImportColumnMapping mapping() {
        EnumMap<ParticipantImportField, Integer> columns = new EnumMap<>(ParticipantImportField.class);
        columns.put(ParticipantImportField.FULL_NAME, 1);
        columns.put(ParticipantImportField.SEX, 2);
        columns.put(ParticipantImportField.DATE_OF_BIRTH, 3);
        columns.put(ParticipantImportField.IDENTIFICATION_NUMBER, 5);
        columns.put(ParticipantImportField.ROSTER_NOTE, 15);
        return ParticipantImportColumnMapping.of(columns);
    }

    private static AggregateId id(long value) {
        return AggregateId.of(new java.util.UUID(0L, value));
    }
}
