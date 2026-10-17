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
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
  private final AtomicReference<HealthExaminationParticipant> inserted = new AtomicReference<>();

  @BeforeEach
  void storeWhatIsInserted() {
    doAnswer(
            call -> {
              inserted.set(call.getArgument(0));
              return null;
            })
        .when(participants)
        .insert(any());
    when(participants.findInAgreement(any(), any()))
        .thenAnswer(call -> Optional.ofNullable(inserted.get()));
  }

  private CreateParticipantCommand command(String identification) {
    return new CreateParticipantCommand(
        "  " + FULL_NAME + " ",
        LocalDate.of(1990, 5, 12),
        "MALE",
        identification,
        LocalDate.of(2021, 3, 15),
        "  Cục Cảnh sát QLHC về TTXH ",
        " Kinh ",
        PHONE,
        " ",
        " 12 Nguyễn Trãi, Hà Nội ",
        "Công ty ABC",
        "Accounting",
        "Staff",
        " ");
  }

  @Test
  void deniesCallersWithoutManageBeforeTouchingAnyPort() {
    givenOpenAgreement();
    for (var principal : List.of(staff(), staff(READ), staff(IMPORT), patient()))
      assertThatThrownBy(
              () ->
                  useCase.execute(organizationId, agreementId, command(IDENTIFICATION), principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, command(IDENTIFICATION), null))
        .isInstanceOf(ApplicationException.class);
    verifyNoInteractions(agreements, organizations, participants, audit);
  }

  @Test
  void rejectsADateOfBirthInTheFutureButAcceptsTodayInTheBusinessTimeZone() {
    givenOpenAgreement();
    when(participants.identityTakenByOther(any(), any(), isNull())).thenReturn(false);
    // the clock is 2026-10-04T00:00Z = 07:00 on 2026-10-04 in Asia/Ho_Chi_Minh
    var tomorrow =
        new CreateParticipantCommand(
            FULL_NAME,
            LocalDate.of(2026, 10, 5),
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
            null);
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, tomorrow, manager))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Date of birth must not be in the future");
    verify(participants, org.mockito.Mockito.never()).insert(any());

    var today =
        new CreateParticipantCommand(
            FULL_NAME,
            LocalDate.of(2026, 10, 4),
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
            null);
    useCase.execute(organizationId, agreementId, today, manager);
    verify(participants).insert(any());
  }

  @Test
  void createsAnActivePendingUnscheduledManualParticipantAndAudits() {
    givenOpenAgreement();
    when(participants.identityTakenByOther(any(), any(), isNull())).thenReturn(false);

    var response = useCase.execute(organizationId, agreementId, command(IDENTIFICATION), manager);

    var stored = inserted.get();
    assertThat(stored.isManual()).isTrue();
    assertThat(stored.getImportJobId()).isNull();
    assertThat(stored.getSourceRowNumber()).isNull();
    assertThat(stored.getRosterStatus().name()).isEqualTo("ACTIVE");
    assertThat(stored.getReconciliationStatus().name()).isEqualTo("PENDING");
    assertThat(stored.getPatientId()).isNull();
    // the code is generated from the agreement code and the count of Participants already in the
    // agreement
    assertThat(stored.getRoster().participantCode()).isEqualTo(agreement.code() + "-0001");
    assertThat(stored.getRoster().fullName()).isEqualTo(FULL_NAME);
    assertThat(stored.getRoster().email()).isNull();
    assertThat(stored.getRoster().note()).isNull();
    assertThat(stored.getRoster().identificationIssueDate()).isEqualTo(LocalDate.of(2021, 3, 15));
    assertThat(stored.getRoster().identificationIssuePlace())
        .isEqualTo("Cục Cảnh sát QLHC về TTXH");
    assertThat(stored.getRoster().ethnicity()).isEqualTo("Kinh");
    assertThat(stored.getRoster().address()).isEqualTo("12 Nguyễn Trãi, Hà Nội");
    assertThat(stored.getRoster().workplace()).isEqualTo("Công ty ABC");
    assertThat(stored.getOrganizationId().value()).isEqualTo(organizationId);
    assertThat(stored.getAgreementId().value()).isEqualTo(agreementId);

    assertThat(response.source()).isEqualTo("MANUAL");
    assertThat(response.patientLinked()).isFalse();
    assertThat(response.identificationNumber()).isEqualTo(IDENTIFICATION);
    assertThat(response.assignments()).isEmpty();
    assertThat(response.attendanceStatus()).isEqualTo("UNCONFIRMED");
    assertThat(response.rowVersion()).isZero();

    var after = ArgumentCaptor.forClass(Object.class);
    verify(audit)
        .record(
            org.mockito.ArgumentMatchers.eq(manager.userId()),
            org.mockito.ArgumentMatchers.eq("CREATE_PARTICIPANT"),
            org.mockito.ArgumentMatchers.eq("HEALTH_EXAMINATION_PARTICIPANT"),
            org.mockito.ArgumentMatchers.eq(stored.getId().value()),
            isNull(),
            after.capture());
    assertThat(after.getValue().toString())
        .contains(organizationId.toString(), agreementId.toString(), "MANUAL")
        .doesNotContain(IDENTIFICATION, FULL_NAME, PHONE, EMAIL);
  }

  @Test
  void reportsAMissingAgreementOrOrganizationAsNotFound() {
    when(agreements.findDetails(organizationId, agreementId, true)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, command(IDENTIFICATION), manager))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(participants, never()).insert(any());
  }

  @Test
  void rejectsAgreementsThatDoNotAcceptChanges() {
    for (var status : List.of(AgreementStatus.ENDED)) {
      givenAgreement(status, null, true);
      assertThatThrownBy(
              () -> useCase.execute(organizationId, agreementId, command(IDENTIFICATION), manager))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Agreement does not accept Participant changes");
    }
    givenAgreement(AgreementStatus.DRAFT, Instant.parse("2026-10-05T00:00:00Z"), true);
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, command(IDENTIFICATION), manager))
        .isInstanceOf(ConflictException.class);
    verify(participants, never()).insert(any());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsAnInactiveOrganization() {
    givenAgreement(AgreementStatus.DRAFT, null, false);
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, command(IDENTIFICATION), manager))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Agreement does not accept Participant changes");
    verify(participants, never()).insert(any());
  }

  @Test
  void rejectsAnIdentificationNumberAlreadyInTheAgreement() {
    givenOpenAgreement();
    when(participants.identityTakenByOther(any(), any(), isNull())).thenReturn(true);
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, command(IDENTIFICATION), manager))
        .isInstanceOf(ConflictException.class)
        .hasMessage("Participant identity already exists in this agreement");
    verify(participants, never()).insert(any());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsInvalidRosterFieldsAsInvalidInput() {
    givenOpenAgreement();
    for (var identification : List.of("12A45", "", "123456789012345678901"))
      assertThatThrownBy(
              () -> useCase.execute(organizationId, agreementId, command(identification), manager))
          .isInstanceOf(IllegalArgumentException.class);
    verify(participants, never()).insert(any());
  }
}
