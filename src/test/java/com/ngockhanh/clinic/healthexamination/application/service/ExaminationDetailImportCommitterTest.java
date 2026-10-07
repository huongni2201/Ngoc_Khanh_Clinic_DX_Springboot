package com.ngockhanh.clinic.healthexamination.application.service;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailImportRow;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailImportCommitter.Request;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Progress;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationImportStore;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationReceipt;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationReservation;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExaminationDetailImportCommitterTest {
  private static final Instant EARLIER = Instant.parse("2026-10-04T01:00:00Z");

  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationBatchRepository batches =
      mock(HealthExaminationBatchRepository.class);
  private final HealthExaminationBatchParticipantRepository participants =
      mock(HealthExaminationBatchParticipantRepository.class);
  private final ServiceReconciliationImportStore imports =
      mock(ServiceReconciliationImportStore.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  // 08:00 on 2026-10-06 in Asia/Ho_Chi_Minh.
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-10-06T01:02:03.456789Z"), ZoneOffset.UTC);
  private final ExaminationDetailImportCommitter committer =
      new ExaminationDetailImportCommitter(
          organizations, batches, participants, imports, audit, clock);

  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();
  private final UUID key = UUID.randomUUID();
  private final UUID reservationId = UUID.randomUUID();
  private final UUID jobId = UUID.randomUUID();
  private HealthExaminationBatch batch;
  private UUID serviceA;
  private UUID serviceB;

  @BeforeEach
  void anActiveOrganizationAndADraftBatchWithTwoServices() {
    givenBatch(BatchStatus.DRAFT, true);
    when(imports.reserve(any()))
        .thenReturn(new ServiceReconciliationReservation.New(reservationId));
    when(imports.createValidatedJob(any())).thenReturn(jobId);
  }

  private void givenBatch(BatchStatus status, boolean activeOrganization) {
    batch = batch(organizationId, batchId, status, 2, null, UUID.randomUUID(), UUID.randomUUID());
    serviceA = batch.services().get(0).id().value();
    serviceB = batch.services().get(1).id().value();
    when(batches.findDetails(organizationId, batchId, true))
        .thenReturn(Optional.of(details(batch)));
    var organization =
        activeOrganization ? organization(organizationId) : inactiveOrganization(organizationId);
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization));
  }

  private HealthExaminationBatchParticipant participant(
      RosterStatus roster, AttendanceStatus attendance, long version) {
    boolean attended = attendance == AttendanceStatus.ATTENDED;
    AggregateId earlier = AggregateId.of(UUID.randomUUID());
    return HealthExaminationBatchParticipant.restore(
        AggregateId.of(UUID.randomUUID()),
        AggregateId.of(batchId),
        AggregateId.of(batch.days().get(0).id()),
        new Roster(
            "NV",
            "Synthetic Person",
            LocalDate.of(1990, 1, 1),
            "MALE",
            IdentificationNumber.of(
                String.valueOf(100000000000L + version + (long) (Math.random() * 1e9))),
            null,
            null,
            "Department",
            "Position"),
        new Progress(
            null,
            roster,
            attendance,
            attended ? FIRST_DAY : null,
            attended ? earlier : null,
            attended ? EARLIER : null,
            null,
            ReconciliationStatus.PENDING,
            null,
            null,
            null),
        null,
        null,
        EARLIER,
        EARLIER,
        version,
        List.of());
  }

  private void givenLocked(HealthExaminationBatchParticipant... locked) {
    when(participants.findManyInBatchForUpdate(eq(AggregateId.of(batchId)), anyCollection()))
        .thenReturn(List.of(locked));
  }

  private ExaminationDetailImportRow row(
      int number,
      HealthExaminationBatchParticipant p,
      long version,
      LocalDate actual,
      UUID... ticked) {
    return new ExaminationDetailImportRow(
        number, p.getId().value(), version, actual, Set.of(ticked));
  }

  private Request request(List<ExaminationDetailImportRow> rows) {
    return new Request(
        organizationId, batchId, key, new byte[32], 1, Set.of(serviceA, serviceB), rows, actorId);
  }

  @Test
  void reconcilesChangedRowsSavesOnlyThemAndWritesOneAuditEvent() {
    var changing = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 4);
    var nothing = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 5);
    givenLocked(changing, nothing);

    var outcome =
        committer.commit(
            request(
                List.of(row(3, changing, 4, null, serviceA, serviceB), row(4, nothing, 5, null))));

    assertThat(outcome.replayed()).isFalse();
    var receipt = outcome.receipt();
    assertThat(receipt.importJobId()).isEqualTo(jobId);
    assertThat(receipt.totalRows()).isEqualTo(2);
    assertThat(receipt.updatedParticipants()).isEqualTo(1);
    assertThat(receipt.unchangedParticipants()).isEqualTo(1);
    assertThat(receipt.performedItems()).isEqualTo(2);
    assertThat(receipt.completedAt()).isEqualTo(Instant.parse("2026-10-06T01:02:03.456Z"));

    assertThat(changing.getAttendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
    assertThat(changing.getActualExaminationDate()).isEqualTo(FIRST_DAY);
    assertThat(changing.performedBatchServiceIds()).hasSize(2);
    assertThat(nothing.getAttendanceStatus()).isEqualTo(AttendanceStatus.UNCONFIRMED);

    var order = inOrder(batches, imports, participants, audit);
    order.verify(batches).findDetails(organizationId, batchId, true);
    order.verify(imports).reserve(any());
    order.verify(participants).findManyInBatchForUpdate(any(), anyCollection());
    order.verify(participants).save(changing, 4L);
    order.verify(imports).createValidatedJob(any());
    order.verify(imports).markRowsCommitted(eq(jobId), anyList());
    order.verify(imports).confirmJob(eq(jobId), eq(0L), any(ServiceReconciliationReceipt.class));
    order
        .verify(imports)
        .completeRequest(eq(reservationId), any(ServiceReconciliationReceipt.class));
    order
        .verify(audit)
        .record(
            eq(actorId),
            eq("IMPORT_EXAMINATION_DETAILS"),
            eq("HEALTH_EXAMINATION_BATCH"),
            eq(batchId),
            eq(null),
            any());
    verify(participants, never()).save(eq(nothing), anyLong());
  }

  @Test
  @SuppressWarnings("unchecked")
  void linksOnlyChangedRowsToTheJobAndKeepsPersonalDataOutOfTheAuditPayload() {
    var changing = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 4);
    var nothing = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 5);
    givenLocked(changing, nothing);

    committer.commit(
        request(List.of(row(3, changing, 4, null, serviceA), row(4, nothing, 5, null))));

    ArgumentCaptor<List<com.ngockhanh.clinic.integration.application.imports.CommittedImportRow>>
        committed = ArgumentCaptor.forClass(List.class);
    verify(imports).markRowsCommitted(eq(jobId), committed.capture());
    assertThat(committed.getValue()).hasSize(1);
    assertThat(committed.getValue().get(0).rowNumber()).isEqualTo(3);
    assertThat(committed.getValue().get(0).resourceId()).isEqualTo(changing.getId().value());

    ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
    verify(audit).record(any(), any(), any(), any(), any(), after.capture());
    assertThat(after.getValue().toString()).doesNotContain("Synthetic Person");
  }

  @Test
  void aReplayReturnsTheStoredReceiptWithoutTouchingAnyParticipantOrTheAudit() {
    var stored =
        new ServiceReconciliationReceipt(
            jobId, batchId, 2, 1, 1, 2, Instant.parse("2026-10-05T00:00:00Z"));
    when(imports.reserve(any())).thenReturn(new ServiceReconciliationReservation.Replay(stored));

    var outcome = committer.commit(request(List.of()));

    assertThat(outcome.replayed()).isTrue();
    assertThat(outcome.receipt()).isEqualTo(stored);
    verifyNoInteractions(participants, audit);
    verify(imports, never()).createValidatedJob(any());
  }

  @Test
  void aReplayStillWorksAfterTheOrganizationBecameInactive() {
    givenBatch(BatchStatus.DRAFT, false);
    var stored =
        new ServiceReconciliationReceipt(
            jobId, batchId, 2, 1, 1, 2, Instant.parse("2026-10-05T00:00:00Z"));
    when(imports.reserve(any())).thenReturn(new ServiceReconciliationReservation.Replay(stored));

    assertThat(committer.commit(request(List.of())).replayed()).isTrue();
  }

  @Test
  void rejectsAFileThatDoesNotDeclareExactlyTheServicesOfTheBatch() {
    var p = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 1);
    var other =
        new Request(
            organizationId,
            batchId,
            key,
            new byte[32],
            1,
            Set.of(serviceA),
            List.of(row(3, p, 1, null)),
            actorId);

    assertThatThrownBy(() -> committer.commit(other))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            e -> assertThat(e.type()).isEqualTo(ApplicationException.Type.INVALID_INPUT))
        .hasMessage("The file does not match this batch; export it again");
    verifyNoInteractions(participants, audit);
    verify(imports, never()).createValidatedJob(any());
  }

  @Test
  void rejectsAnActualDateBeforeTheBatchStartOrAfterToday() {
    var p = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 1);
    for (LocalDate bad : List.of(FIRST_DAY.minusDays(1), LocalDate.of(2026, 10, 7)))
      assertThatThrownBy(() -> committer.commit(request(List.of(row(5, p, 1, bad, serviceA)))))
          .isInstanceOf(ApplicationException.class)
          .hasMessageContaining("Row 5")
          .hasMessageContaining("actual_examination_date");
    verifyNoInteractions(participants, audit);
  }

  @Test
  void aBlankActualDateCannotFallBackToAPlannedDateThatIsStillInTheFuture() {
    var early = Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), ZoneOffset.UTC); // planned 10-04
    var committerBeforeTheDay =
        new ExaminationDetailImportCommitter(
            organizations, batches, participants, imports, audit, early);
    var p = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 1);
    givenLocked(p);

    assertThatThrownBy(
            () -> committerBeforeTheDay.commit(request(List.of(row(5, p, 1, null, serviceA)))))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("Row 5")
        .hasMessageContaining("planned examination date");
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.UNCONFIRMED);
  }

  @Test
  void aBlankActualDateStillFallsBackToAPlannedDateThatIsTodayOrPast() {
    var p = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 1);
    givenLocked(p);

    committer.commit(request(List.of(row(5, p, 1, null, serviceA))));

    assertThat(p.getActualExaminationDate()).isEqualTo(FIRST_DAY);
  }

  @Test
  void acceptsTodayInTheBusinessTimeZoneEvenWhenUtcIsStillYesterday() {
    var lateClock =
        Clock.fixed(Instant.parse("2026-10-05T18:30:00Z"), ZoneOffset.UTC); // 01:30 on 10-06
    var late =
        new ExaminationDetailImportCommitter(
            organizations, batches, participants, imports, audit, lateClock);
    var p = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 1);
    givenLocked(p);

    var outcome = late.commit(request(List.of(row(3, p, 1, LocalDate.of(2026, 10, 6), serviceA))));

    assertThat(outcome.receipt().updatedParticipants()).isEqualTo(1);
    assertThat(p.getActualExaminationDate()).isEqualTo(LocalDate.of(2026, 10, 6));
  }

  @Test
  void aStaleRowVersionRejectsTheWholeFileAndSavesNothing() {
    var fine = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 1);
    var stale = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 7);
    givenLocked(fine, stale);

    assertThatThrownBy(
            () ->
                committer.commit(
                    request(
                        List.of(
                            row(3, fine, 1, null, serviceA), row(4, stale, 6, null, serviceA)))))
        .isInstanceOf(ConcurrentUpdateException.class)
        .hasMessageContaining("Row 4");
    verify(participants, never()).save(any(), anyLong());
    verify(imports, never()).createValidatedJob(any());
    verifyNoInteractions(audit);
    assertThat(fine.getAttendanceStatus()).isEqualTo(AttendanceStatus.UNCONFIRMED);
  }

  @Test
  void aParticipantOfAnotherBatchOrACancelledOneIsAConflict() {
    var cancelled = participant(RosterStatus.CANCELLED, AttendanceStatus.UNCONFIRMED, 1);
    var foreign = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 1);
    givenLocked(cancelled);

    assertThatThrownBy(
            () -> committer.commit(request(List.of(row(3, cancelled, 1, null, serviceA)))))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Row 3: participant is cancelled");
    assertThatThrownBy(() -> committer.commit(request(List.of(row(4, foreign, 1, null, serviceA)))))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Row 4: participant is not in this batch");
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void aBatchThatIsNotDraftOrReadyOrAnInactiveOrganizationIsAConflict() {
    var p = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 1);
    for (BatchStatus status : List.of(BatchStatus.FINALIZED, BatchStatus.CLOSED)) {
      givenBatch(status, true);
      assertThatThrownBy(() -> committer.commit(request(List.of(row(3, p, 1, null, serviceA)))))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Batch does not accept examination detail changes");
    }
    givenBatch(BatchStatus.DRAFT, false);
    assertThatThrownBy(() -> committer.commit(request(List.of(row(3, p, 1, null, serviceA)))))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(participants, audit);
  }

  @Test
  void aReadyBatchAcceptsTheImport() {
    givenBatch(BatchStatus.READY, true);
    var p = participant(RosterStatus.ACTIVE, AttendanceStatus.UNCONFIRMED, 1);
    givenLocked(p);

    assertThat(
            committer
                .commit(request(List.of(row(3, p, 1, null, serviceA))))
                .receipt()
                .updatedParticipants())
        .isEqualTo(1);
  }

  @Test
  void anUnknownOrganizationOrBatchIsNotFound() {
    when(organizations.findById(new AggregateId(organizationId))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> committer.commit(request(List.of())))
        .isInstanceOf(ResourceNotFoundException.class);

    when(batches.findDetails(organizationId, batchId, true)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> committer.commit(request(List.of())))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(participants, audit);
  }
}
