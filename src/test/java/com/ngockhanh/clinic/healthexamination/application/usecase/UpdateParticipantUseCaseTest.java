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

import com.ngockhanh.clinic.healthexamination.application.command.UpdateParticipantCommand;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateParticipantUseCaseTest extends ManualParticipantUseCaseTestBase {
  private final UpdateParticipantUseCase useCase =
      new UpdateParticipantUseCase(access, support, participants);

  private UpdateParticipantCommand command(
      String identification, String name, UUID day, Long version) {
    return new UpdateParticipantCommand(
        "NV-001",
        name,
        LocalDate.of(1990, 5, 12),
        "MALE",
        identification,
        PHONE,
        EMAIL,
        "Accounting",
        "Staff",
        day,
        version);
  }

  private void run(
      com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant p,
      UpdateParticipantCommand command) {
    useCase.execute(organizationId, batchId, p.getId().value(), command, manager);
  }

  @Test
  void deniesCallersWithoutManageBeforeTouchingAnyPort() {
    givenOpenBatch();
    var p = stored(3);
    for (var principal : List.of(staff(), staff(READ), staff(IMPORT)))
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      organizationId,
                      batchId,
                      p.getId().value(),
                      command(IDENTIFICATION, FULL_NAME, firstDay(), 3L),
                      principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(batches, organizations, participants, audit);
  }

  @Test
  void updatesFieldsAndDayWithExpectedVersionAndAuditsOnlyFieldNames() {
    givenOpenBatch();
    var p = stored(3);
    givenStored(p);

    run(p, command(IDENTIFICATION, "Renamed Person", secondDay(), 3L));

    assertThat(p.getRoster().fullName()).isEqualTo("Renamed Person");
    assertThat(p.getBatchDayId().value()).isEqualTo(secondDay());
    verify(participants).save(p, 3L);
    var before = ArgumentCaptor.forClass(Object.class);
    var after = ArgumentCaptor.forClass(Object.class);
    verify(audit)
        .record(
            eq(manager.userId()),
            eq("UPDATE_BATCH_PARTICIPANT"),
            eq("HEALTH_EXAMINATION_BATCH_PARTICIPANT"),
            eq(p.getId().value()),
            before.capture(),
            after.capture());
    assertThat(after.getValue().toString())
        .contains("fullName", "examinationDay", "rowVersion=4")
        .doesNotContain("Renamed Person", IDENTIFICATION, PHONE, EMAIL);
    assertThat(before.getValue().toString())
        .contains("rowVersion=3")
        .doesNotContain(FULL_NAME, IDENTIFICATION);
  }

  @Test
  void doesNotCheckDuplicatesWhenTheIdentificationNumberIsUnchanged() {
    givenOpenBatch();
    var p = stored(0);
    givenStored(p);
    run(p, command(IDENTIFICATION, "Renamed Person", firstDay(), 0L));
    verify(participants, never()).identityTakenByOther(any(), any(), any());
    verify(participants).save(p, 0L);
  }

  @Test
  void checksDuplicatesExcludingItselfWhenTheIdentificationNumberChanges() {
    givenOpenBatch();
    var p = stored(0);
    givenStored(p);
    when(participants.identityTakenByOther(any(), eq(IdentificationNumber.of("999")), eq(p.getId())))
        .thenReturn(true);
    assertThatThrownBy(() -> run(p, command("999", FULL_NAME, firstDay(), 0L)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Participant identity already exists in this batch");
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void keepsTheIdentificationNumberLockedAfterPreparation() {
    givenOpenBatch();
    var p = stored(1, progress(new AggregateId(UUID.randomUUID()), RosterStatus.ACTIVE, NOW, null));
    givenStored(p);
    assertThatThrownBy(() -> run(p, command("999", FULL_NAME, firstDay(), 1L)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Identification number is locked after visit preparation");
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void refusesToEditACancelledParticipant() {
    givenOpenBatch();
    var p = stored(1, progress(null, RosterStatus.CANCELLED, null, null));
    givenStored(p);
    assertThatThrownBy(() -> run(p, command(IDENTIFICATION, "Renamed Person", firstDay(), 1L)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Participant is cancelled");
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void rejectsAStaleVersionBeforeChangingAnything() {
    givenOpenBatch();
    var p = stored(3);
    givenStored(p);
    assertThatThrownBy(() -> run(p, command(IDENTIFICATION, "Renamed Person", firstDay(), 2L)))
        .isInstanceOf(ConcurrentUpdateException.class);
    assertThat(p.getRoster().fullName()).isEqualTo(FULL_NAME);
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsAMissingOrNegativeExpectedVersion() {
    givenOpenBatch();
    var p = stored(0);
    givenStored(p);
    for (Long version : new Long[] {null, -1L})
      assertThatThrownBy(() -> run(p, command(IDENTIFICATION, FULL_NAME, firstDay(), version)))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBatchesThatDoNotAcceptChangesAndInactiveOrganizations() {
    givenOpenBatch();
    var p = stored(0);
    for (var status : List.of(BatchStatus.FINALIZED, BatchStatus.CLOSED)) {
      givenBatch(status, null, true);
      givenStored(p);
      assertThatThrownBy(() -> run(p, command(IDENTIFICATION, FULL_NAME, firstDay(), 0L)))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Batch does not accept Participant changes");
    }
    givenBatch(BatchStatus.DRAFT, Instant.parse("2026-10-05T00:00:00Z"), true);
    assertThatThrownBy(() -> run(p, command(IDENTIFICATION, FULL_NAME, firstDay(), 0L)))
        .isInstanceOf(ConflictException.class);
    givenBatch(BatchStatus.DRAFT, null, false);
    assertThatThrownBy(() -> run(p, command(IDENTIFICATION, FULL_NAME, firstDay(), 0L)))
        .isInstanceOf(ConflictException.class);
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void rejectsADayOutsideTheBatchAndReportsUnknownParticipantsAsNotFound() {
    givenOpenBatch();
    var p = stored(0);
    givenStored(p);
    assertThatThrownBy(() -> run(p, command(IDENTIFICATION, FULL_NAME, UUID.randomUUID(), 0L)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Examination day is not a day of this batch");

    when(participants.findInBatch(any(), any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> run(p, command(IDENTIFICATION, FULL_NAME, firstDay(), 0L)))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(participants, never()).save(any(), anyLong());
  }
}
