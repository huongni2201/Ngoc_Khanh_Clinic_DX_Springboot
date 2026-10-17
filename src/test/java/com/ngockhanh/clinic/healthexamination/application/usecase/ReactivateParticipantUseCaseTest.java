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
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
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

  private HealthExaminationParticipant cancelled(long version) {
    return stored(version, progress(null, RosterStatus.CANCELLED, null));
  }

  private ParticipantDetailResponse reactivate(HealthExaminationParticipant p, Long version) {
    return useCase.execute(
        organizationId,
        agreementId,
        p.getId().value(),
        new ReactivateParticipantCommand(version),
        manager);
  }

  private String auditAfter(HealthExaminationParticipant p) {
    var after = ArgumentCaptor.forClass(Object.class);
    verify(audit)
        .record(
            eq(manager.userId()),
            eq("REACTIVATE_PARTICIPANT"),
            eq("HEALTH_EXAMINATION_PARTICIPANT"),
            eq(p.getId().value()),
            any(),
            after.capture());
    return after.getValue().toString();
  }

  @Test
  void deniesCallersWithoutManageBeforeTouchingAnyPort() {
    givenOpenAgreement();
    var p = cancelled(0);
    for (var principal : List.of(staff(), staff(READ), staff(IMPORT)))
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      organizationId,
                      agreementId,
                      p.getId().value(),
                      new ReactivateParticipantCommand(0L),
                      principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(agreements, organizations, participants, audit);
  }

  @Test
  void rejectsNullIdentifiersCommandAndAMissingOrNegativeVersion() {
    givenOpenAgreement();
    var p = cancelled(0);
    givenStored(p);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    null,
                    agreementId,
                    p.getId().value(),
                    new ReactivateParticipantCommand(0L),
                    manager))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId,
                    agreementId,
                    null,
                    new ReactivateParticipantCommand(0L),
                    manager))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, p.getId().value(), null, manager))
        .isInstanceOf(IllegalArgumentException.class);
    for (Long version : new Long[] {null, -1L})
      assertThatThrownBy(() -> reactivate(p, version)).isInstanceOf(IllegalArgumentException.class);
    verify(participants, never()).save(any(), anyLong());
  }

  @Test
  void reactivatesTheSameRowAndAuditsTheStatusChangeOnly() {
    givenOpenAgreement();
    var p = cancelled(4);
    givenStored(p);

    var response = reactivate(p, 4L);

    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(response.id()).isEqualTo(p.getId().value());
    assertThat(response.rosterStatus()).isEqualTo(RosterStatus.ACTIVE.name());
    verify(participants).save(p, 4L);
    assertThat(auditAfter(p))
        .contains("rosterStatus", "rowVersion=5")
        .doesNotContain(IDENTIFICATION, FULL_NAME, PHONE, EMAIL);
  }

  @Test
  void keepsTheRosterAsItWasAtCancellationAndDoesNotRestoreTheSchedule() {
    givenOpenAgreement();
    var p = cancelled(1);
    givenStored(p);

    reactivate(p, 1L);

    org.mockito.Mockito.verify(assignments, never()).insertMany(any());
    org.mockito.Mockito.verify(assignments, never())
        .cancelExpectedOfParticipant(any(), any(), any());
    assertThat(p.getRoster()).isEqualTo(roster(IDENTIFICATION));
  }

  @Test
  void refusesAnActiveParticipant() {
    givenOpenAgreement();
    var p = stored(1);
    givenStored(p);
    assertThatThrownBy(() -> reactivate(p, 1L))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Participant is not cancelled");
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void refusesInconsistentPreparedRows() {
    givenOpenAgreement();
    var prepared =
        stored(1, progress(new AggregateId(UUID.randomUUID()), RosterStatus.CANCELLED, NOW));
    for (var p : List.of(prepared)) {
      givenStored(p);
      assertThatThrownBy(() -> reactivate(p, 1L))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Participant cannot be reactivated after preparation or attendance");
      assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
    }
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsAStaleVersionBeforeChangingAnything() {
    givenOpenAgreement();
    var p = cancelled(4);
    givenStored(p);
    assertThatThrownBy(() -> reactivate(p, 3L)).isInstanceOf(ConcurrentUpdateException.class);
    assertThat(p.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsAgreementsThatDoNotAcceptChangesAndInactiveOrganizations() {
    givenOpenAgreement(); // the fixture needs an agreement (and its days) before a Participant can
    // be built
    var p = cancelled(0);
    for (var status : List.of(AgreementStatus.ENDED)) {
      givenAgreement(status, null, true);
      givenStored(p);
      assertThatThrownBy(() -> reactivate(p, 0L))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Agreement does not accept Participant changes");
    }
    givenAgreement(AgreementStatus.DRAFT, null, false);
    assertThatThrownBy(() -> reactivate(p, 0L)).isInstanceOf(ConflictException.class);
    verify(participants, never()).save(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void reportsAnUnknownAgreementOrParticipantAsNotFound() {
    givenOpenAgreement();
    var p = cancelled(0);
    when(participants.findInAgreement(any(), any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> reactivate(p, 0L)).isInstanceOf(ResourceNotFoundException.class);

    when(agreements.findDetails(organizationId, agreementId, true)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> reactivate(p, 0L)).isInstanceOf(ResourceNotFoundException.class);
  }
}
