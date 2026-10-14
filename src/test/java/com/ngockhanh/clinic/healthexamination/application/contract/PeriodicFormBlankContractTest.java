package com.ngockhanh.clinic.healthexamination.application.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.document.application.contract.PlaceholderSpec;
import com.ngockhanh.clinic.document.infrastructure.rendering.DomDocxTemplateEngine;
import java.io.IOException;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Pins the contract of the blank periodic form. A change to this list is a change to what clinic
 * managers may write in their templates: renaming, removing or retyping a placeholder, or making
 * one required, needs a new contract version and a decision about the templates already uploaded.
 */
class PeriodicFormBlankContractTest {
  private static final List<String> V1_PLACEHOLDERS =
      List.of(
          "participant.code",
          "participant.barcode",
          "participant.fullName",
          "participant.fullNameUpper",
          "participant.sex",
          "participant.dateOfBirth",
          "participant.yearOfBirth",
          "participant.age",
          "participant.identificationNumber",
          "participant.identificationIssueDate",
          "participant.identificationIssuePlace",
          "participant.phone",
          "participant.address",
          "participant.ethnicity",
          "participant.workplace",
          "participant.departmentName",
          "participant.positionName",
          "participant.examinationDate",
          "organization.name",
          "organization.address",
          "batch.code",
          "batch.name",
          "printedDate");

  private final PeriodicFormBlankContract contract = new PeriodicFormBlankContract();

  @Test
  void version1HasExactlyTheCommittedPlaceholders() {
    assertThat(contract.contractVersion()).isEqualTo(1);
    assertThat(contract.placeholders())
        .extracting(PlaceholderSpec::key)
        .containsExactlyElementsOf(V1_PLACEHOLDERS);
  }

  @Test
  void onlyThePlaceholdersEveryTemplateNeedsAreRequired() {
    assertThat(contract.placeholders())
        .filteredOn(PlaceholderSpec::required)
        .extracting(PlaceholderSpec::key)
        .containsExactlyInAnyOrder(
            "participant.code", "participant.dateOfBirth", "participant.identificationNumber");
  }

  @Test
  void theModelSuppliesAValueForEveryPlaceholderAndNothingElse() {
    var values = PeriodicFormBlankModel.from(sampleSource()).toValues();

    assertThat(values.keySet()).containsExactlyElementsOf(V1_PLACEHOLDERS);
    assertThat(values.values()).doesNotContainNull();
  }

  @Test
  void theStarterTemplateUsesOnlyContractPlaceholdersAndHoldsTheRequiredOnes() throws IOException {
    byte[] starter;
    try (var in =
        getClass().getClassLoader().getResourceAsStream(contract.starterResource())) {
      assertThat(in).as("starter template on the classpath").isNotNull();
      starter = in.readAllBytes();
    }

    var found = new TreeSet<>(new DomDocxTemplateEngine().placeholders(starter));

    assertThat(contract.placeholderKeys()).containsAll(found);
    assertThat(found)
        .containsAll(
            contract.placeholders().stream()
                .filter(PlaceholderSpec::required)
                .map(PlaceholderSpec::key)
                .toList());
  }

  static ExaminationFormSource sampleSource() {
    return new ExaminationFormSource(
        "KSK-2026-001-0001",
        "Nguyễn Văn An",
        java.time.LocalDate.of(1990, 2, 1),
        "MALE",
        "001090000123",
        java.time.LocalDate.of(2021, 6, 15),
        "Cục Cảnh sát QLHC về TTXH",
        "Kinh",
        "0912345678",
        "12 Lê Lợi, Quận 1, TP.HCM",
        null,
        "Phòng Kế toán",
        "Kế toán",
        "Công ty TNHH ABC",
        "1 Hàng Bài",
        "KSK-2026-001",
        "Khám định kỳ",
        java.time.LocalDate.of(2026, 10, 20),
        java.time.LocalDate.of(2026, 10, 12));
  }
}
