package com.ngockhanh.clinic.healthexamination.domain;

import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.enums.*;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class HealthExaminationAgreementCrudTest {
  private static final LocalDate FROM = LocalDate.of(2026, 10, 4);
  private static final LocalDate TO = LocalDate.of(2026, 10, 8);

  private AggregateId id() {
    return new AggregateId(UUID.randomUUID());
  }

  private HealthExaminationAgreementService line(
      AggregateId agreementId, AggregateId serviceId, int order, boolean active) {
    return new HealthExaminationAgreementService(
        id(), serviceId, agreementId, Money.vnd("200"), Money.vnd("100"), order, active, 0);
  }

  private HealthExaminationAgreement draft() {
    var id = id();
    return HealthExaminationAgreement.createDraft(
        id, id(), "B", "Agreement", FROM, TO, List.of(line(id, id(), 1, true)));
  }

  @Test
  void requiresAServiceAndAValidPeriod() {
    var id = id();
    var org = id();
    var service = line(id, id(), 1, true);
    assertThatThrownBy(
            () ->
                HealthExaminationAgreement.createDraft(
                    id, org, "B", "Agreement", FROM, TO, List.of()))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(
            () ->
                HealthExaminationAgreement.createDraft(
                    id, org, "B", "Agreement", TO, FROM, List.of(service)))
        .isInstanceOf(IllegalArgumentException.class);
    var open =
        HealthExaminationAgreement.createDraft(
            id, org, "B", "Agreement", null, null, List.of(service));
    assertThat(open.validFrom()).isNull();
    assertThat(open.validTo()).isNull();
    var bounded =
        HealthExaminationAgreement.createDraft(
            id, org, "B", "Agreement", FROM, TO, List.of(service));
    assertThat(bounded.validFrom()).isEqualTo(FROM);
    assertThat(bounded.validTo()).isEqualTo(TO);
  }

  @Test
  void rejectsAServiceListedTwiceAmongTheActiveLinesButKeepsRetiredHistory() {
    var id = id();
    var serviceId = id();
    assertThatThrownBy(
            () ->
                HealthExaminationAgreement.createDraft(
                    id,
                    id(),
                    "B",
                    "Agreement",
                    FROM,
                    TO,
                    List.of(line(id, serviceId, 1, true), line(id, serviceId, 2, true))))
        .isInstanceOf(DomainRuleViolation.class);
    var agreement =
        HealthExaminationAgreement.createDraft(
            id,
            id(),
            "B",
            "Agreement",
            FROM,
            TO,
            List.of(line(id, serviceId, 1, false), line(id, serviceId, 2, true)));
    assertThat(agreement.services()).hasSize(2);
    assertThat(agreement.activeServices()).hasSize(1);
  }

  @Test
  void agreementMovesFromDraftToActiveToEndedAndNowhereElse() {
    var b = draft();
    assertThatThrownBy(b::end).isInstanceOf(DomainRuleViolation.class);
    b.activate();
    assertThat(b.status()).isEqualTo(AgreementStatus.ACTIVE);
    assertThatThrownBy(b::activate).isInstanceOf(DomainRuleViolation.class);
    b.end();
    assertThat(b.status()).isEqualTo(AgreementStatus.ENDED);
    assertThatThrownBy(b::activate).isInstanceOf(DomainRuleViolation.class);
    assertThat(AgreementStatus.values())
        .containsExactly(AgreementStatus.DRAFT, AgreementStatus.ACTIVE, AgreementStatus.ENDED);
  }

  @Test
  void onlyDraftAndActiveAgreementsAcceptRosterChanges() {
    var b = draft();
    assertThat(b.acceptsParticipantChanges()).isTrue();
    b.activate();
    assertThat(b.acceptsParticipantChanges()).isTrue();
    b.end();
    assertThat(b.acceptsParticipantChanges()).isFalse();
    assertThat(b.acceptsParticipantImport()).isFalse();
  }

  @Test
  void agreementPricesUseNumericFourteenTwo() {
    var id = id();
    assertThatThrownBy(
            () ->
                new HealthExaminationAgreementService(
                    id(), id(), id, Money.vnd("1000000000000"), Money.vnd("1"), 1, true, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateDraftReplacesTheWholeConfiguration() {
    var b = draft();
    var agreementId = b.id();
    var service =
        new HealthExaminationAgreementService(
            id(), id(), agreementId, Money.vnd("300"), Money.vnd("250"), 1, true, 0);

    b.updateDraft("Renamed", null, TO, List.of(service));

    assertThat(b.code()).as("the code never changes").isEqualTo("B");
    assertThat(b.name()).isEqualTo("Renamed");
    assertThat(b.validFrom()).isNull();
    assertThat(b.validTo()).isEqualTo(TO);
    assertThat(b.services()).containsExactly(service);
    assertThat(b.id()).isEqualTo(agreementId);
  }

  @Test
  void updateDraftKeepsTheOldConfigurationWhenTheNewOneIsInvalid() {
    var b = draft();
    var before = b.services();

    assertThatThrownBy(() -> b.updateDraft("Renamed", TO, FROM, b.services()))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(b.name()).isEqualTo("Agreement");
    assertThat(b.services()).isEqualTo(before);
  }

  @Test
  void onlyAnUndeletedDraftCanBeEditedOrDeleted() {
    var b = draft();
    b.requireDraft();
    b.activate();
    assertThatThrownBy(b::requireDraft).isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> b.updateDraft("N", FROM, TO, b.services()))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> b.softDelete(Instant.now())).isInstanceOf(DomainRuleViolation.class);
  }

  @Test
  void softDeleteMarksTheDraftDeletedAndCannotBeRepeatedOrFollowedByAnEdit() {
    var b = draft();
    var at = Instant.parse("2026-10-06T03:00:00Z");
    assertThat(b.isDeleted()).isFalse();
    assertThat(b.deletedAt()).isNull();

    b.softDelete(at);

    assertThat(b.isDeleted()).isTrue();
    assertThat(b.deletedAt()).isEqualTo(at);
    assertThat(b.status()).isEqualTo(AgreementStatus.DRAFT);
    assertThatThrownBy(() -> b.softDelete(at)).isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> b.updateDraft("N", FROM, TO, b.services()))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> draft().softDelete(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theServicesAreFixedOnceTheAgreementIsActive() {
    var b = draft();
    var services = b.services();
    b.activate();

    assertThatThrownBy(() -> b.updateDraft("N", FROM, TO, services))
        .isInstanceOf(DomainRuleViolation.class);
    assertThat(b.services()).isEqualTo(services);
  }
}
