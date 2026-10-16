package com.ngockhanh.clinic.healthexamination.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.AgreementFixtures;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailImportRow;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailReconciler.Result;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Progress;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** One row of the file against one Participant: the E4–E7 rules of the reconciliation import. */
class ExaminationDetailReconcilerTest {
  private static final Instant EARLIER = Instant.parse("2026-10-04T01:00:00Z");
  private static final Instant NOW = Instant.parse("2026-10-06T01:00:00Z");

  private final ExaminationDetailReconciler reconciler = new ExaminationDetailReconciler();
  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();
  private final AggregateId actor = AggregateId.of(UUID.randomUUID());
  private HealthExaminationAgreement agreement;
  private AggregateId serviceA;
  private AggregateId serviceB;
  private final AggregateId participantId = AggregateId.of(UUID.randomUUID());

  @BeforeEach
  void anAgreementOfTwoActiveServices() {
    agreement =
        AgreementFixtures.draftAgreement(
            organizationId, agreementId, 0, UUID.randomUUID(), UUID.randomUUID());
    serviceA = agreement.services().get(0).id();
    serviceB = agreement.services().get(1).id();
  }

  private static Progress progress(ReconciliationStatus reconciliation) {
    boolean reconciled = reconciliation == ReconciliationStatus.RECONCILED;
    AggregateId earlier = AggregateId.of(UUID.randomUUID());
    return new Progress(
        null,
        RosterStatus.ACTIVE,
        reconciliation,
        reconciled ? earlier : null,
        reconciled ? EARLIER : null,
        null);
  }

  private HealthExaminationParticipant participant(
      Progress progress, List<HealthExaminationParticipantService> services) {
    return HealthExaminationParticipant.restore(
        participantId,
        AggregateId.of(organizationId),
        AggregateId.of(agreementId),
        new Roster(
            "NV-1",
            "Synthetic Person",
            LocalDate.of(1990, 1, 1),
            "MALE",
            IdentificationNumber.of("000000000001"),
            null,
            null,
            "Department",
            "Position"),
        progress,
        null,
        null,
        EARLIER,
        EARLIER,
        3,
        services);
  }

  private HealthExaminationParticipant fresh() {
    return participant(progress(ReconciliationStatus.PENDING), List.of());
  }

  private HealthExaminationParticipantService stored(AggregateId service, boolean performed) {
    return new HealthExaminationParticipantService(
        AggregateId.of(UUID.randomUUID()),
        AggregateId.of(agreementId),
        participantId,
        service,
        performed,
        null,
        Money.vnd("70"),
        AggregateId.of(UUID.randomUUID()),
        EARLIER,
        EARLIER,
        EARLIER,
        0);
  }

  private static ExaminationDetailEntry row(AggregateId... ticked) {
    Set<UUID> ids = new java.util.HashSet<>();
    for (AggregateId id : ticked) ids.add(id.value());
    return new ExaminationDetailImportRow(2, UUID.randomUUID(), 3, ids).toEntry();
  }

  private Result apply(HealthExaminationParticipant p, ExaminationDetailEntry row) {
    return reconciler.apply(p, agreement, row, actor, NOW);
  }

  private HealthExaminationAgreement agreementWith(HealthExaminationAgreementService line) {
    return HealthExaminationAgreement.restoreConfiguration(
        AggregateId.of(agreementId),
        AggregateId.of(organizationId),
        "B1",
        "Campaign",
        AgreementFixtures.FIRST_DAY,
        AgreementFixtures.SECOND_DAY,
        List.of(line),
        AgreementStatus.DRAFT,
        0,
        null);
  }

  private HealthExaminationAgreementService retiredLine() {
    return new HealthExaminationAgreementService(
        AggregateId.of(UUID.randomUUID()),
        AggregateId.of(UUID.randomUUID()),
        AggregateId.of(agreementId),
        Money.vnd("200"),
        Money.vnd("100"),
        1,
        false,
        0);
  }

  @Test
  void aMarkedServiceReconcilesTheParticipantAndSnapshotsTheNegotiatedPrice() {
    var p = fresh();

    assertThat(apply(p, row(serviceA))).isEqualTo(Result.UPDATED);

    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    assertThat(p.performedAgreementServiceIds()).containsExactly(serviceA);
    var service = p.services().get(0);
    assertThat(service.unitPriceSnapshot().amount()).isEqualByComparingTo("100");
    assertThat(service.recordedBy()).isEqualTo(actor);
    assertThat(service.recordedAt()).isEqualTo(NOW);
    assertThat(service.serviceRequestId()).isNull();
  }

  @Test
  void aBlankRowForAParticipantWithNoServiceRowsWritesNothing() {
    var p = fresh();

    assertThat(apply(p, row())).isEqualTo(Result.UNCHANGED);

    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
    assertThat(p.services()).isEmpty();
  }

  @Test
  void anUncheckedServiceIsKeptAsNotPerformed() {
    var p = participant(progress(ReconciliationStatus.RECONCILED), List.of(stored(serviceA, true)));
    var original = p.services().get(0);

    assertThat(apply(p, row())).isEqualTo(Result.UPDATED);

    assertThat(p.services()).hasSize(1);
    var kept = p.services().get(0);
    assertThat(kept.id()).isEqualTo(original.id());
    assertThat(kept.performed()).isFalse();
    assertThat(kept.unitPriceSnapshot().amount()).isEqualByComparingTo("70");
    assertThat(p.performedAgreementServiceIds()).isEmpty();
    assertThat(p.reconciledAgreementServiceIds()).containsExactly(serviceA);
  }

  @Test
  void aServiceTickedAgainKeepsItsOriginalPriceSnapshot() {
    var p =
        participant(progress(ReconciliationStatus.RECONCILED), List.of(stored(serviceA, false)));
    var original = p.services().get(0);

    assertThat(apply(p, row(serviceA))).isEqualTo(Result.UPDATED);

    assertThat(p.services()).hasSize(1);
    assertThat(p.services().get(0).id()).isEqualTo(original.id());
    assertThat(p.services().get(0).performed()).isTrue();
    assertThat(p.services().get(0).unitPriceSnapshot().amount()).isEqualByComparingTo("70");
  }

  @Test
  void aRowThatMatchesTheStoredStateWritesNothing() {
    var p = fresh();
    assertThat(apply(p, row(serviceA, serviceB))).isEqualTo(Result.UPDATED);

    assertThat(apply(p, row(serviceA, serviceB))).isEqualTo(Result.UNCHANGED);
  }

  @Test
  void aNewServiceOnAReconciledParticipantAddsARowAndKeepsTheOthers() {
    var p = fresh();
    apply(p, row(serviceA));

    assertThat(apply(p, row(serviceA, serviceB))).isEqualTo(Result.UPDATED);

    assertThat(p.performedAgreementServiceIds()).containsExactlyInAnyOrder(serviceA, serviceB);
    assertThat(p.services()).hasSize(2);
  }

  @Test
  void aNewlyMarkedRetiredServiceIsAConflictAndLeavesTheParticipantUntouched() {
    var retired = retiredLine();
    var withRetired = agreementWith(retired);
    var p = fresh();

    assertThatThrownBy(() -> reconciler.apply(p, withRetired, row(retired.id()), actor, NOW))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("no longer offered");

    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
    assertThat(p.services()).isEmpty();
  }

  @Test
  void aMarkedServiceStaysPerformedEvenAfterItWasRetired() {
    var retired = retiredLine();
    var withRetired = agreementWith(retired);
    var p =
        participant(progress(ReconciliationStatus.RECONCILED), List.of(stored(retired.id(), true)));

    assertThat(reconciler.apply(p, withRetired, row(retired.id()), actor, NOW))
        .isEqualTo(Result.UNCHANGED);
    assertThat(p.performedAgreementServiceIds()).containsExactly(retired.id());
    assertThat(new ArrayList<>(p.services())).hasSize(1);
  }
}
