package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.IMPORT;
import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.READ;
import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.staff;
import static com.ngockhanh.clinic.healthexamination.RosterFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.application.command.CancelParticipantCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CancelParticipantUseCaseTest extends ManualParticipantUseCaseTestBase {
  private final CancelParticipantUseCase useCase =
      new CancelParticipantUseCase(access, support, participants);

  private void cancel(HealthExaminationBatchParticipant p, Long version) {
    useCase.execute(
        organizationId,
        batchId,
        p.getId().value(),
        new CancelParticipantCommand(version),
        manager);
  }

  @Test
  void deniesCallersWithoutManageBeforeTouchingAnyPort() {
    givenOpenBatch();
    var p = stored(0);
    for (var principal : List.of(staff(), staff(READ), staff(IMPORT)))
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      organizationId,
                      batchId,
                      p.getId().value(),
                      new CancelParticipantCommand(0L),
                      principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(batches, organizations, participants, audit);
  }

  @Test
  void cancelsWithoutDeletingAndAuditsTheStatusChangeOnly() {
    givenOpenBatch();
    var p = stored(4);
    givenStored(p);

    cancel(p, 4L);

    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
    verify(participants).save(p, 4L);
    var after = ArgumentCaptor.forClass(Object.class);
    verify(audit)
        .record(
            eq(manager.userId()),
            eq("CANCEL_BATCH_PARTICIPANT"),
            eq("HEALTH_EXAMINATION_BATCH_PARTICIPANT"),
            eq(p.getId().value()),
            any(),
            after.capture());
    assertThat(after.getValue().toString())
        .contains("rosterStatus", "rowVersion=5")
        .doesNotContain(IDENTIFICATION, FULL_NAME, PHONE, EMAIL);
  }

  @Test
  void blocksCancellationAfterPreparationOrAttendanceAndWhenAlreadyCancelled() {
    givenOpenBatch();
    var prepared =
        stored(1, progress(new AggregateId(UUID.randomUUID()), RosterStatus.ACTIVE, NOW, null));
    var attended =
        stored(1, progress(null, RosterStatus.ACTIVE, null, AttendanceStatus.ATTENDED));
    var cancelled = stored(1, progress(null, RosterStatus.CANCELLED, null, null));
    for (var p : List.of(prepared, attended)) {
      givenStored(p);
      assertThatThrownBy(() -> cancel(p, 1L))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Participant cannot be cancelled after preparation or attendance");
    }
    givenStored(cancelled);
    assertThatThrownBy(() -> cancel(cancelled, 1L))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Participant is cancelled");
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsAStaleVersion() {
    givenOpenBatch();
    var p = stored(4);
    givenStored(p);
    assertThatThrownBy(() -> cancel(p, 3L)).isInstanceOf(ConcurrentUpdateException.class);
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void rejectsAMissingOrNegativeVersion() {
    givenOpenBatch();
    var p = stored(0);
    givenStored(p);
    for (Long version : new Long[] {null, -1L})
      assertThatThrownBy(() -> cancel(p, version)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBatchesThatDoNotAcceptChangesAndInactiveOrganizations() {
    givenOpenBatch(); // the fixture needs a batch (and its days) before a Participant can be built
    var p = stored(0);
    for (var status : List.of(BatchStatus.FINALIZED, BatchStatus.CLOSED)) {
      givenBatch(status, null, true);
      givenStored(p);
      assertThatThrownBy(() -> cancel(p, 0L))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Batch does not accept Participant changes");
    }
    givenBatch(BatchStatus.DRAFT, null, false);
    assertThatThrownBy(() -> cancel(p, 0L)).isInstanceOf(ConflictException.class);
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void reportsAnUnknownBatchOrParticipantAsNotFound() {
    givenOpenBatch();
    var p = stored(0);
    when(participants.findInBatch(any(), any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> cancel(p, 0L)).isInstanceOf(ResourceNotFoundException.class);

    when(batches.findDetails(organizationId, batchId, true)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> cancel(p, 0L)).isInstanceOf(ResourceNotFoundException.class);
  }
}
