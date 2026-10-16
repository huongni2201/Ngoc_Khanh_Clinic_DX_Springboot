package com.ngockhanh.clinic.healthexamination.application.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PeriodicFormBlankModelTest {
  private static final String DOTS = PeriodicFormBlankModel.MISSING;

  @Test
  void formatsTheRosterForPrinting() {
    var values =
        PeriodicFormBlankModel.from(PeriodicFormBlankContractTest.sampleSource()).toValues();

    assertThat(values)
        .containsEntry("participant.code", "KSK-2026-001-0001")
        .containsEntry("participant.barcode", "KSK-2026-001-0001")
        .containsEntry("participant.fullName", "Nguyễn Văn An")
        .containsEntry("participant.fullNameUpper", "NGUYỄN VĂN AN")
        .containsEntry("participant.sex", "Nam")
        .containsEntry("participant.dateOfBirth", "01/02/1990")
        .containsEntry("participant.yearOfBirth", "1990")
        .containsEntry("participant.identificationNumber", "001090000123")
        .containsEntry("participant.identificationIssueDate", "15/06/2021")
        .containsEntry("participant.examinationDate", "20/10/2026")
        .containsEntry("printedDate", "12/10/2026")
        .containsEntry("organization.name", "Công ty TNHH ABC")
        .containsEntry("batch.code", "KSK-2026-001");
  }

  @Test
  void ageIsCountedAtTheExaminationDate() {
    assertThat(age("1990-10-20", "2026-10-20", "2026-01-01")).isEqualTo("36");
    assertThat(age("1990-10-21", "2026-10-20", "2026-01-01")).isEqualTo("35");
    // Without an examination day the printing date is the reference.
    assertThat(age("1990-10-20", null, "2026-10-19")).isEqualTo("35");
    // A birth date after the reference never prints a negative age.
    assertThat(age("2030-01-01", "2026-10-20", "2026-10-12")).isEqualTo("0");
  }

  @Test
  void theWorkplaceDefaultsToTheOrganizationName() {
    var source = PeriodicFormBlankContractTest.sampleSource();

    assertThat(PeriodicFormBlankModel.from(source).participantWorkplace())
        .isEqualTo("Công ty TNHH ABC");
    var own =
        new ExaminationFormSource(
            source.participantCode(),
            source.fullName(),
            source.dateOfBirth(),
            source.sex(),
            source.identificationNumber(),
            source.identificationIssueDate(),
            source.identificationIssuePlace(),
            source.ethnicity(),
            source.phone(),
            source.address(),
            "  Chi nhánh 2  ",
            source.departmentName(),
            source.positionName(),
            source.organizationName(),
            source.organizationAddress(),
            source.agreementCode(),
            source.agreementName(),
            source.examinationDate(),
            source.printedDate());
    assertThat(PeriodicFormBlankModel.from(own).participantWorkplace()).isEqualTo("Chi nhánh 2");
  }

  @Test
  void factsTheRosterLacksPrintAsDottedLines() {
    var values =
        PeriodicFormBlankModel.from(
                new ExaminationFormSource(
                    "P1",
                    "Trần Thị B",
                    LocalDate.of(1985, 5, 5),
                    "UNKNOWN",
                    "079185000456",
                    null,
                    "  ",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
            .toValues();

    assertThat(values)
        .containsEntry("participant.sex", DOTS)
        .containsEntry("participant.identificationIssueDate", DOTS)
        .containsEntry("participant.identificationIssuePlace", DOTS)
        .containsEntry("participant.ethnicity", DOTS)
        .containsEntry("participant.phone", DOTS)
        .containsEntry("participant.address", DOTS)
        .containsEntry("participant.workplace", DOTS)
        .containsEntry("participant.departmentName", DOTS)
        .containsEntry("participant.positionName", DOTS)
        .containsEntry("participant.age", DOTS)
        .containsEntry("participant.fullName", "Trần Thị B");
    // Facts about the organization and agreement are not form fields: they stay empty.
    assertThat(values)
        .containsEntry("organization.name", "")
        .containsEntry("batch.code", "")
        .containsEntry("printedDate", "");
  }

  @Test
  void mapsEverySexValue() {
    assertThat(sex("MALE")).isEqualTo("Nam");
    assertThat(sex("FEMALE")).isEqualTo("Nữ");
    assertThat(sex("OTHER")).isEqualTo("Khác");
    assertThat(sex("UNKNOWN")).isEqualTo(DOTS);
    assertThat(sex(null)).isEqualTo(DOTS);
  }

  private static String age(String birth, String examination, String printed) {
    var source = PeriodicFormBlankContractTest.sampleSource();
    return PeriodicFormBlankModel.from(
            new ExaminationFormSource(
                source.participantCode(),
                source.fullName(),
                LocalDate.parse(birth),
                source.sex(),
                source.identificationNumber(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                examination == null ? null : LocalDate.parse(examination),
                LocalDate.parse(printed)))
        .participantAge();
  }

  private static String sex(String sex) {
    var source = PeriodicFormBlankContractTest.sampleSource();
    return PeriodicFormBlankModel.from(
            new ExaminationFormSource(
                source.participantCode(),
                source.fullName(),
                source.dateOfBirth(),
                sex,
                source.identificationNumber(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                source.examinationDate(),
                source.printedDate()))
        .participantSex();
  }

  @Test
  void aParticipantWithoutACodeHasNoBarcodeValueNotADottedLine() {
    var values =
        PeriodicFormBlankModel.from(
                new ExaminationFormSource(
                    null,
                    "Trần Thị B",
                    LocalDate.of(1985, 5, 5),
                    "FEMALE",
                    "079185000456",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
            .toValues();

    assertThat(values)
        .containsEntry("participant.code", DOTS)
        .containsEntry("participant.barcode", "");
  }
}
