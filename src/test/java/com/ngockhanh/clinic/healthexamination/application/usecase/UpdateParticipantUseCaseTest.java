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
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
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
  UpdateParticipantUseCaseTest() {
    super("PARTICIPANT_UPDATE");
  }

  private final UpdateParticipantUseCase useCase =
      new UpdateParticipantUseCase(access, support, participants);

  private UpdateParticipantCommand command(String identification, String name, Long version) {
    return new UpdateParticipantCommand(
        name,
        LocalDate.of(1990, 5, 12),
        "MALE",
        identification,
        null,
        null,
        null,
        PHONE,
        EMAIL,
        null,
        null,
        "Accounting",
        "Staff",
        null,
        version);
  }

  private void run(
      com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant p,
      UpdateParticipantCommand command) {
    useCase.execute(organizationId, agreementId, p.getId().value(), command, manager);
  }

  @Test
  void deniesCallersWithoutManageBeforeTouchingAnyPort() {
    givenOpenAgreement();
    var p = stored(3);
    for (var principal : List.of(staff(), staff(READ), staff(IMPORT)))
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      organizationId,
                      agreementId,
                      p.getId().value(),
                      command(IDENTIFICATION, FULL_NAME, 3L),
                      principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(agreements, organizations, participants, audit);
  }

  @Test
  void updatesFieldsWithExpectedVersionAndAuditsOnlyFieldNames() {
    givenOpenAgreement();
    var p = stored(3);
    givenStored(p);

    run(p, command(IDENTIFICATION, "Renamed Person", 3L));

    assertThat(p.getRoster().fullName()).isEqualTo("Renamed Person");
    verify(participants).save(p, 3L);
    var before = ArgumentCaptor.forClass(Object.class);
    var after = ArgumentCaptor.forClass(Object.class);
    verify(audit)
        .record(
            eq(manager.userId()),
            eq("UPDATE_PARTICIPANT"),
            eq("HEALTH_EXAMINATION_PARTICIPANT"),
            eq(p.getId().value()),
            before.capture(),
            after.capture());
    assertThat(after.getValue().toString())
        .contains("fullName", "rowVersion=4")
        .doesNotContain("Renamed Person", IDENTIFICATION, PHONE, EMAIL);
    assertThat(before.getValue().toString())
        .contains("rowVersion=3")
        .doesNotContain(FULL_NAME, IDENTIFICATION);
  }

  @Test
  void doesNotCheckDuplicatesWhenTheIdentificationNumberIsUnchanged() {
    givenOpenAgreement();
    var p = stored(0);
    givenStored(p);
    run(p, command(IDENTIFICATION, "Renamed Person", 0L));
    verify(participants, never()).identityTakenByOther(any(), any(), any());
    verify(participants).save(p, 0L);
  }

  @Test
  void checksDuplicatesExcludingItselfWhenTheIdentificationNumberChanges() {
    givenOpenAgreement();
    var p = stored(0);
    givenStored(p);
    when(participants.identityTakenByOther(
            any(), eq(IdentificationNumber.of("999")), eq(p.getId())))
        .thenReturn(true);
    assertThatThrownBy(() -> run(p, command("999", FULL_NAME, 0L)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Participant identity already exists in this agreement");
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void keepsTheIdentificationNumberLockedAfterPreparation() {
    givenOpenAgreement();
    var p = stored(1, progress(new AggregateId(UUID.randomUUID()), RosterStatus.ACTIVE, NOW));
    givenStored(p);
    assertThatThrownBy(() -> run(p, command("999", FULL_NAME, 1L)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Identification number is locked after visit preparation");
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void refusesToEditACancelledParticipant() {
    givenOpenAgreement();
    var p = stored(1, progress(null, RosterStatus.CANCELLED, null));
    givenStored(p);
    assertThatThrownBy(() -> run(p, command(IDENTIFICATION, "Renamed Person", 1L)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Participant is cancelled");
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void rejectsADateOfBirthInTheFutureWithoutSaving() {
    givenOpenAgreement();
    var p = stored(0);
    givenStored(p);
    var future =
        new UpdateParticipantCommand(
            FULL_NAME,
            LocalDate.of(2099, 1, 1),
            "MALE",
            IDENTIFICATION,
            null,
            null,
            null,
            PHONE,
            EMAIL,
            null,
            null,
            "Accounting",
            "Staff",
            null,
            0L);
    assertThatThrownBy(() -> run(p, future))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Date of birth must not be in the future");
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void rejectsAStaleVersionBeforeChangingAnything() {
    givenOpenAgreement();
    var p = stored(3);
    givenStored(p);
    assertThatThrownBy(() -> run(p, command(IDENTIFICATION, "Renamed Person", 2L)))
        .isInstanceOf(ConcurrentUpdateException.class);
    assertThat(p.getRoster().fullName()).isEqualTo(FULL_NAME);
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsAMissingOrNegativeExpectedVersion() {
    givenOpenAgreement();
    var p = stored(0);
    givenStored(p);
    for (Long version : new Long[] {null, -1L})
      assertThatThrownBy(() -> run(p, command(IDENTIFICATION, FULL_NAME, version)))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAgreementsThatDoNotAcceptChangesAndInactiveOrganizations() {
    givenOpenAgreement();
    var p = stored(0);
    for (var status : List.of(AgreementStatus.ENDED)) {
      givenAgreement(status, null, true);
      givenStored(p);
      assertThatThrownBy(() -> run(p, command(IDENTIFICATION, FULL_NAME, 0L)))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Agreement does not accept Participant changes");
    }
    givenAgreement(AgreementStatus.DRAFT, Instant.parse("2026-10-05T00:00:00Z"), true);
    assertThatThrownBy(() -> run(p, command(IDENTIFICATION, FULL_NAME, 0L)))
        .isInstanceOf(ConflictException.class);
    givenAgreement(AgreementStatus.DRAFT, null, false);
    assertThatThrownBy(() -> run(p, command(IDENTIFICATION, FULL_NAME, 0L)))
        .isInstanceOf(ConflictException.class);
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void reportsUnknownParticipantsAsNotFound() {
    givenOpenAgreement();
    var p = stored(0);
    givenStored(p);

    when(participants.findInAgreement(any(), any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> run(p, command(IDENTIFICATION, FULL_NAME, 0L)))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(participants, never()).save(any(), anyLong());
  }
}
