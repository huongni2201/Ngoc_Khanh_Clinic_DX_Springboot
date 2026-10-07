package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.IMPORT;
import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.READ;
import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.patient;
import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.staff;
import static com.ngockhanh.clinic.healthexamination.RosterFixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.application.command.CreateParticipantCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateParticipantUseCaseTest extends ManualParticipantUseCaseTestBase {
  CreateParticipantUseCaseTest() {
    super("PARTICIPANT_CREATE");
  }

  private final CreateParticipantUseCase useCase =
      new CreateParticipantUseCase(access, support, participants, CLOCK);
  private final AtomicReference<HealthExaminationBatchParticipant> inserted =
      new AtomicReference<>();

  @BeforeEach
  void storeWhatIsInserted() {
    doAnswer(
            call -> {
              inserted.set(call.getArgument(0));
              return null;
            })
        .when(participants)
        .insert(any());
    when(participants.findInBatch(any(), any()))
        .thenAnswer(call -> Optional.ofNullable(inserted.get()));
  }

  private CreateParticipantCommand command(UUID day, String identification) {
    return new CreateParticipantCommand(
        "  NV-001 ",
        "  " + FULL_NAME + " ",
        LocalDate.of(1990, 5, 12),
        "MALE",
        identification,
        PHONE,
        " ",
        "Accounting",
        "Staff",
        day);
  }

  @Test
  void deniesCallersWithoutManageBeforeTouchingAnyPort() {
    givenOpenBatch();
    for (var principal : List.of(staff(), staff(READ), staff(IMPORT), patient()))
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      organizationId, batchId, command(firstDay(), IDENTIFICATION), principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    assertThatThrownBy(
            () ->
                useCase.execute(organizationId, batchId, command(firstDay(), IDENTIFICATION), null))
        .isInstanceOf(ApplicationException.class);
    verifyNoInteractions(batches, organizations, participants, audit);
  }

  @Test
  void rejectsADateOfBirthInTheFutureButAcceptsTodayInTheBusinessTimeZone() {
    givenOpenBatch();
    when(participants.identityTakenByOther(any(), any(), isNull())).thenReturn(false);
    // the clock is 2026-10-04T00:00Z = 07:00 on 2026-10-04 in Asia/Ho_Chi_Minh
    var tomorrow =
        new CreateParticipantCommand(
            "NV-001",
            FULL_NAME,
            LocalDate.of(2026, 10, 5),
            "MALE",
            IDENTIFICATION,
            PHONE,
            EMAIL,
            "Accounting",
            "Staff",
            firstDay());
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, tomorrow, manager))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Date of birth must not be in the future");
    verify(participants, org.mockito.Mockito.never()).insert(any());

    var today =
        new CreateParticipantCommand(
            "NV-001",
            FULL_NAME,
            LocalDate.of(2026, 10, 4),
            "MALE",
            IDENTIFICATION,
            PHONE,
            EMAIL,
            "Accounting",
            "Staff",
            firstDay());
    useCase.execute(organizationId, batchId, today, manager);
    verify(participants).insert(any());
  }

  @Test
  void createsAnActiveUnconfirmedPendingManualParticipantAndAudits() {
    givenOpenBatch();
    when(participants.identityTakenByOther(any(), any(), isNull())).thenReturn(false);

    var response =
        useCase.execute(organizationId, batchId, command(secondDay(), IDENTIFICATION), manager);

    var stored = inserted.get();
    assertThat(stored.isManual()).isTrue();
    assertThat(stored.getImportJobId()).isNull();
    assertThat(stored.getSourceRowNumber()).isNull();
    assertThat(stored.getRosterStatus().name()).isEqualTo("ACTIVE");
    assertThat(stored.getAttendanceStatus().name()).isEqualTo("UNCONFIRMED");
    assertThat(stored.getReconciliationStatus().name()).isEqualTo("PENDING");
    assertThat(stored.getPatientId()).isNull();
    assertThat(stored.getRoster().participantCode()).isEqualTo("NV-001");
    assertThat(stored.getRoster().fullName()).isEqualTo(FULL_NAME);
    assertThat(stored.getRoster().email()).isNull();
    assertThat(stored.getBatchDayId().value()).isEqualTo(secondDay());

    assertThat(response.source()).isEqualTo("MANUAL");
    assertThat(response.patientLinked()).isFalse();
    assertThat(response.identificationNumber()).isEqualTo(IDENTIFICATION);
    assertThat(response.examinationDate()).isEqualTo(batch.days().get(1).examinationDate());
    assertThat(response.rowVersion()).isZero();

    var after = ArgumentCaptor.forClass(Object.class);
    verify(audit)
        .record(
            org.mockito.ArgumentMatchers.eq(manager.userId()),
            org.mockito.ArgumentMatchers.eq("CREATE_BATCH_PARTICIPANT"),
            org.mockito.ArgumentMatchers.eq("HEALTH_EXAMINATION_BATCH_PARTICIPANT"),
            org.mockito.ArgumentMatchers.eq(stored.getId().value()),
            isNull(),
            after.capture());
    assertThat(after.getValue().toString())
        .contains(organizationId.toString(), batchId.toString(), "MANUAL")
        .doesNotContain(IDENTIFICATION, FULL_NAME, PHONE, EMAIL);
  }

  @Test
  void reportsAMissingBatchOrOrganizationAsNotFound() {
    when(batches.findDetails(organizationId, batchId, true)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, batchId, command(UUID.randomUUID(), IDENTIFICATION), manager))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(participants, never()).insert(any());
  }

  @Test
  void rejectsBatchesThatDoNotAcceptChanges() {
    for (var status : List.of(BatchStatus.FINALIZED, BatchStatus.CLOSED)) {
      givenBatch(status, null, true);
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      organizationId, batchId, command(firstDay(), IDENTIFICATION), manager))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Batch does not accept Participant changes");
    }
    givenBatch(BatchStatus.DRAFT, Instant.parse("2026-10-05T00:00:00Z"), true);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, batchId, command(firstDay(), IDENTIFICATION), manager))
        .isInstanceOf(ConflictException.class);
    verify(participants, never()).insert(any());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsAnInactiveOrganization() {
    givenBatch(BatchStatus.DRAFT, null, false);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, batchId, command(firstDay(), IDENTIFICATION), manager))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Batch does not accept Participant changes");
    verify(participants, never()).insert(any());
  }

  @Test
  void rejectsADayThatIsNotOfTheBatch() {
    givenOpenBatch();
    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, batchId, command(UUID.randomUUID(), IDENTIFICATION), manager))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Examination day is not a day of this batch");
    verify(participants, never()).insert(any());
  }

  @Test
  void rejectsAnIdentificationNumberAlreadyInTheBatch() {
    givenOpenBatch();
    when(participants.identityTakenByOther(any(), any(), isNull())).thenReturn(true);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, batchId, command(firstDay(), IDENTIFICATION), manager))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Participant identity already exists in this batch");
    verify(participants, never()).insert(any());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsInvalidRosterFieldsAsInvalidInput() {
    givenOpenBatch();
    for (var identification : List.of("12A45", "", "123456789012345678901"))
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      organizationId, batchId, command(firstDay(), identification), manager))
          .isInstanceOf(IllegalArgumentException.class);
    verify(participants, never()).insert(any());
  }
}
