package com.ngockhanh.clinic.healthexamination.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.BatchFixtures;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailImportRow;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailReconciler.Result;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Progress;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
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
  private static final LocalDate PLANNED = BatchFixtures.FIRST_DAY;

  private final ExaminationDetailReconciler reconciler = new ExaminationDetailReconciler();
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
  private final AggregateId actor = AggregateId.of(UUID.randomUUID());
  private HealthExaminationBatch batch;
  private AggregateId serviceA;
  private AggregateId serviceB;
  private final AggregateId participantId = AggregateId.of(UUID.randomUUID());

  @BeforeEach
  void aBatchOfTwoActiveServices() {
    batch = BatchFixtures.draftBatch(organizationId, batchId, 0, UUID.randomUUID(), UUID.randomUUID());
    serviceA = batch.services().get(0).id();
    serviceB = batch.services().get(1).id();
  }

  private static Progress progress(
      AttendanceStatus attendance, LocalDate actualDate, ReconciliationStatus reconciliation) {
    boolean attended = attendance == AttendanceStatus.ATTENDED;
    boolean reconciled = reconciliation == ReconciliationStatus.RECONCILED;
    AggregateId earlier = AggregateId.of(UUID.randomUUID());
    return new Progress(
        null,
        RosterStatus.ACTIVE,
        attendance,
        actualDate,
        attended ? earlier : null,
        attended ? EARLIER : null,
        null,
        reconciliation,
        reconciled ? earlier : null,
        reconciled ? EARLIER : null,
        null);
  }

  private HealthExaminationBatchParticipant participant(
      Progress progress, List<HealthExaminationBatchParticipantService> services) {
    return HealthExaminationBatchParticipant.restore(
        participantId,
        AggregateId.of(batchId),
        batch.days().get(0).id() == null ? null : AggregateId.of(batch.days().get(0).id()),
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

  private HealthExaminationBatchParticipant fresh() {
    return participant(
        progress(AttendanceStatus.UNCONFIRMED, null, ReconciliationStatus.PENDING), List.of());
  }

  private HealthExaminationBatchParticipantService stored(
      AggregateId service, boolean performed) {
    return new HealthExaminationBatchParticipantService(
        AggregateId.of(UUID.randomUUID()),
        AggregateId.of(batchId),
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

  private static ExaminationDetailImportRow row(LocalDate actualDate, AggregateId... ticked) {
    Set<UUID> ids = new java.util.HashSet<>();
    for (AggregateId id : ticked) ids.add(id.value());
    return new ExaminationDetailImportRow(2, UUID.randomUUID(), 3, actualDate, ids);
  }

  private Result apply(HealthExaminationBatchParticipant p, ExaminationDetailImportRow row) {
    return reconciler.apply(p, batch, row, actor, NOW, PLANNED);
  }

  @Test
  void aMarkedServiceAttendsTheParticipantOnThePlannedDateAndSnapshotsTheNegotiatedPrice() {
    var p = fresh();

    assertThat(apply(p, row(null, serviceA))).isEqualTo(Result.UPDATED);

    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
    assertThat(p.getActualExaminationDate()).isEqualTo(PLANNED);
    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.RECONCILED);
    assertThat(p.performedBatchServiceIds()).containsExactly(serviceA);
    var service = p.services().get(0);
    assertThat(service.unitPriceSnapshot().amount()).isEqualByComparingTo("100");
    assertThat(service.recordedBy()).isEqualTo(actor);
    assertThat(service.recordedAt()).isEqualTo(NOW);
    assertThat(service.serviceRequestId()).isNull();
  }

  @Test
  void theActualDateOfTheFileWinsOverThePlannedDate() {
    var p = fresh();

    apply(p, row(BatchFixtures.SECOND_DAY, serviceA));

    assertThat(p.getActualExaminationDate()).isEqualTo(BatchFixtures.SECOND_DAY);
  }

  @Test
  void anAlreadyAttendedParticipantKeepsItsRecordedDateWhenTheFileHasNone() {
    var recorded = BatchFixtures.SECOND_DAY;
    var p =
        participant(
            progress(AttendanceStatus.ATTENDED, recorded, ReconciliationStatus.PENDING), List.of());

    apply(p, row(null, serviceA));

    assertThat(p.getActualExaminationDate()).isEqualTo(recorded);
  }

  @Test
  void anAbsentParticipantWithAMarkedServiceBecomesAttended() {
    var p = participant(progress(AttendanceStatus.ABSENT, null, ReconciliationStatus.PENDING), List.of());

    apply(p, row(null, serviceB));

    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
    assertThat(p.getActualExaminationDate()).isEqualTo(PLANNED);
  }

  @Test
  void aBlankRowForAParticipantWithNoServiceRowsWritesNothingAndKeepsAttendance() {
    var p = fresh();

    assertThat(apply(p, row(null))).isEqualTo(Result.UNCHANGED);
    assertThat(apply(p, row(BatchFixtures.SECOND_DAY))).isEqualTo(Result.UNCHANGED);

    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.UNCONFIRMED);
    assertThat(p.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PENDING);
    assertThat(p.services()).isEmpty();
  }

  @Test
  void anUncheckedServiceIsKeptAsNotPerformedAndAttendanceIsKept() {
    var p =
        participant(
            progress(AttendanceStatus.ATTENDED, PLANNED, ReconciliationStatus.RECONCILED),
            List.of(stored(serviceA, true)));
    var original = p.services().get(0);

    assertThat(apply(p, row(null))).isEqualTo(Result.UPDATED);

    assertThat(p.services()).hasSize(1);
    var kept = p.services().get(0);
    assertThat(kept.id()).isEqualTo(original.id());
    assertThat(kept.performed()).isFalse();
    assertThat(kept.unitPriceSnapshot().amount()).isEqualByComparingTo("70");
    assertThat(p.performedBatchServiceIds()).isEmpty();
    assertThat(p.reconciledBatchServiceIds()).containsExactly(serviceA);
    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
  }

  @Test
  void aServiceTickedAgainKeepsItsOriginalPriceSnapshot() {
    var p =
        participant(
            progress(AttendanceStatus.ATTENDED, PLANNED, ReconciliationStatus.RECONCILED),
            List.of(stored(serviceA, false)));
    var original = p.services().get(0);

    assertThat(apply(p, row(null, serviceA))).isEqualTo(Result.UPDATED);

    assertThat(p.services()).hasSize(1);
    assertThat(p.services().get(0).id()).isEqualTo(original.id());
    assertThat(p.services().get(0).performed()).isTrue();
    assertThat(p.services().get(0).unitPriceSnapshot().amount()).isEqualByComparingTo("70");
  }

  @Test
  void aRowThatMatchesTheStoredStateWritesNothing() {
    var p = fresh();
    assertThat(apply(p, row(null, serviceA, serviceB))).isEqualTo(Result.UPDATED);

    assertThat(apply(p, row(null, serviceA, serviceB))).isEqualTo(Result.UNCHANGED);
    assertThat(apply(p, row(PLANNED, serviceA, serviceB))).isEqualTo(Result.UNCHANGED);
  }

  @Test
  void aNewServiceOnAReconciledParticipantAddsARowAndKeepsTheOthers() {
    var p = fresh();
    apply(p, row(null, serviceA));

    assertThat(apply(p, row(null, serviceA, serviceB))).isEqualTo(Result.UPDATED);

    assertThat(p.performedBatchServiceIds()).containsExactlyInAnyOrder(serviceA, serviceB);
    assertThat(p.services()).hasSize(2);
  }

  @Test
  void aNewlyMarkedInactiveServiceIsAConflictAndLeavesTheParticipantUntouched() {
    var inactive =
        new HealthExaminationBatchService(
            AggregateId.of(UUID.randomUUID()),
            AggregateId.of(UUID.randomUUID()),
            AggregateId.of(batchId),
            Money.vnd("200"),
            Money.vnd("100"),
            1,
            false,
            0);
    var withInactive =
        HealthExaminationBatch.restoreConfiguration(
            AggregateId.of(batchId),
            AggregateId.of(organizationId),
            "B1",
            "Campaign",
            new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", "Address"),
            List.of(new HealthExaminationBatchDay(UUID.randomUUID(), PLANNED)),
            List.of(inactive),
            BatchStatus.DRAFT,
            0,
            null);
    var p = fresh();

    assertThatThrownBy(
            () ->
                reconciler.apply(p, withInactive, row(null, inactive.id()), actor, NOW, PLANNED))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("no longer offered");

    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.UNCONFIRMED);
    assertThat(p.services()).isEmpty();
  }

  @Test
  void aMarkedServiceStaysPerformedEvenAfterItWasDeactivated() {
    var inactive =
        new HealthExaminationBatchService(
            AggregateId.of(UUID.randomUUID()),
            AggregateId.of(UUID.randomUUID()),
            AggregateId.of(batchId),
            Money.vnd("200"),
            Money.vnd("100"),
            1,
            false,
            0);
    var withInactive =
        HealthExaminationBatch.restoreConfiguration(
            AggregateId.of(batchId),
            AggregateId.of(organizationId),
            "B1",
            "Campaign",
            new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", "Address"),
            List.of(new HealthExaminationBatchDay(UUID.randomUUID(), PLANNED)),
            List.of(inactive),
            BatchStatus.DRAFT,
            0,
            null);
    var p =
        participant(
            progress(AttendanceStatus.ATTENDED, PLANNED, ReconciliationStatus.RECONCILED),
            List.of(stored(inactive.id(), true)));

    assertThat(reconciler.apply(p, withInactive, row(null, inactive.id()), actor, NOW, PLANNED))
        .isEqualTo(Result.UNCHANGED);
    assertThat(p.performedBatchServiceIds()).containsExactly(inactive.id());
    assertThat(new ArrayList<>(p.services())).hasSize(1);
  }
}
