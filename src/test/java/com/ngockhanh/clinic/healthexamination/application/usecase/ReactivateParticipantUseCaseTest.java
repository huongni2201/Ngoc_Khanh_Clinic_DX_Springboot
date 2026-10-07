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

import com.ngockhanh.clinic.healthexamination.application.command.ReactivateParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantDetailResponse;
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

class ReactivateParticipantUseCaseTest extends ManualParticipantUseCaseTestBase {
  ReactivateParticipantUseCaseTest() {
    super("PARTICIPANT_REACTIVATE");
  }

  private final ReactivateParticipantUseCase useCase =
      new ReactivateParticipantUseCase(access, support, participants);

  private HealthExaminationBatchParticipant cancelled(long version) {
    return stored(version, progress(null, RosterStatus.CANCELLED, null, null));
  }

  private ParticipantDetailResponse reactivate(
      HealthExaminationBatchParticipant p, Long version, UUID day) {
    return useCase.execute(
        organizationId,
        batchId,
        p.getId().value(),
        new ReactivateParticipantCommand(version, day),
        manager);
  }

  private String auditAfter(HealthExaminationBatchParticipant p) {
    var after = ArgumentCaptor.forClass(Object.class);
    verify(audit)
        .record(
            eq(manager.userId()),
            eq("REACTIVATE_BATCH_PARTICIPANT"),
            eq("HEALTH_EXAMINATION_BATCH_PARTICIPANT"),
            eq(p.getId().value()),
            any(),
            after.capture());
    return after.getValue().toString();
  }

  @Test
  void deniesCallersWithoutManageBeforeTouchingAnyPort() {
    givenOpenBatch();
    var p = cancelled(0);
    for (var principal : List.of(staff(), staff(READ), staff(IMPORT)))
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      organizationId,
                      batchId,
                      p.getId().value(),
                      new ReactivateParticipantCommand(0L, null),
                      principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(batches, organizations, participants, audit);
  }

  @Test
  void rejectsNullIdentifiersCommandAndAMissingOrNegativeVersion() {
    givenOpenBatch();
    var p = cancelled(0);
    givenStored(p);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    null,
                    batchId,
                    p.getId().value(),
                    new ReactivateParticipantCommand(0L, null),
                    manager))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId,
                    batchId,
                    null,
                    new ReactivateParticipantCommand(0L, null),
                    manager))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> useCase.execute(organizationId, batchId, p.getId().value(), null, manager))
        .isInstanceOf(IllegalArgumentException.class);
    for (Long version : new Long[] {null, -1L})
      assertThatThrownBy(() -> reactivate(p, version, null))
          .isInstanceOf(IllegalArgumentException.class);
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void reactivatesTheSameRowKeepingTheDayAndAuditsTheStatusChangeOnly() {
    givenOpenBatch();
    var p = cancelled(4);
    givenStored(p);

    var response = reactivate(p, 4L, null);

    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(p.getBatchDayId().value()).isEqualTo(firstDay());
    assertThat(response.id()).isEqualTo(p.getId().value());
    assertThat(response.rosterStatus()).isEqualTo(RosterStatus.ACTIVE.name());
    verify(participants).save(p, 4L);
    assertThat(auditAfter(p))
        .contains("rosterStatus", "rowVersion=5")
        .doesNotContain("batchDayId", IDENTIFICATION, FULL_NAME, PHONE, EMAIL);
  }

  @Test
  void movesToTheChosenDayAndAuditsTheDayChange() {
    givenOpenBatch();
    var p = cancelled(2);
    givenStored(p);

    reactivate(p, 2L, secondDay());

    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(p.getBatchDayId().value()).isEqualTo(secondDay());
    verify(participants).save(p, 2L);
    assertThat(auditAfter(p))
        .contains("rosterStatus", "batchDayId", "rowVersion=3")
        .doesNotContain(IDENTIFICATION, FULL_NAME, PHONE, EMAIL);
  }

  @Test
  void treatsTheCurrentDayAsNoDayChange() {
    givenOpenBatch();
    var p = cancelled(2);
    givenStored(p);

    reactivate(p, 2L, firstDay());

    assertThat(p.getBatchDayId().value()).isEqualTo(firstDay());
    assertThat(auditAfter(p)).contains("rosterStatus").doesNotContain("batchDayId");
  }

  @Test
  void keepsAttendanceAndRosterAsTheyWereAtCancellation() {
    givenOpenBatch();
    var p = stored(1, progress(null, RosterStatus.CANCELLED, null, AttendanceStatus.ABSENT));
    givenStored(p);

    reactivate(p, 1L, null);

    assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
    assertThat(p.getRoster()).isEqualTo(roster(IDENTIFICATION));
  }

  @Test
  void refusesAnActiveParticipant() {
    givenOpenBatch();
    var p = stored(1);
    givenStored(p);
    assertThatThrownBy(() -> reactivate(p, 1L, null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Participant is not cancelled");
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void refusesInconsistentPreparedAttendedOrReconciledRows() {
    givenOpenBatch();
    var prepared =
        stored(1, progress(new AggregateId(UUID.randomUUID()), RosterStatus.CANCELLED, NOW, null));
    var attended =
        stored(1, progress(null, RosterStatus.CANCELLED, null, AttendanceStatus.ATTENDED));
    for (var p : List.of(prepared, attended)) {
      givenStored(p);
      assertThatThrownBy(() -> reactivate(p, 1L, null))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Participant cannot be reactivated after preparation or attendance");
      assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
    }
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsAStaleVersionBeforeChangingAnything() {
    givenOpenBatch();
    var p = cancelled(4);
    givenStored(p);
    assertThatThrownBy(() -> reactivate(p, 3L, null)).isInstanceOf(ConcurrentUpdateException.class);
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsADayOutsideTheBatchWithoutSaving() {
    givenOpenBatch();
    var p = cancelled(0);
    givenStored(p);
    assertThatThrownBy(() -> reactivate(p, 0L, UUID.randomUUID()))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Examination day is not a day of this batch");
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsBatchesThatDoNotAcceptChangesAndInactiveOrganizations() {
    givenOpenBatch(); // the fixture needs a batch (and its days) before a Participant can be built
    var p = cancelled(0);
    for (var status : List.of(BatchStatus.FINALIZED, BatchStatus.CLOSED)) {
      givenBatch(status, null, true);
      givenStored(p);
      assertThatThrownBy(() -> reactivate(p, 0L, null))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Batch does not accept Participant changes");
    }
    givenBatch(BatchStatus.DRAFT, null, false);
    assertThatThrownBy(() -> reactivate(p, 0L, null)).isInstanceOf(ConflictException.class);
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void reportsAnUnknownBatchOrParticipantAsNotFound() {
    givenOpenBatch();
    var p = cancelled(0);
    when(participants.findInBatch(any(), any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> reactivate(p, 0L, null)).isInstanceOf(ResourceNotFoundException.class);

    when(batches.findDetails(organizationId, batchId, true)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> reactivate(p, 0L, null)).isInstanceOf(ResourceNotFoundException.class);
  }
}
