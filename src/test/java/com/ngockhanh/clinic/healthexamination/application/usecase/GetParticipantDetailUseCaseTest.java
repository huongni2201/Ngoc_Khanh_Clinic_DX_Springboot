package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.DETAIL_READ;
import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.IMPORT;
import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.staff;
import static com.ngockhanh.clinic.healthexamination.RosterFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetParticipantDetailUseCaseTest extends ManualParticipantUseCaseTestBase {
  GetParticipantDetailUseCaseTest() {
    super("PARTICIPANT_DETAIL_VIEW");
  }

  private final GetParticipantDetailUseCase useCase =
      new GetParticipantDetailUseCase(access, support, participants);

  @Test
  void rejectsCallersWithoutTheViewPermissionBeforeReadingData() {
    givenOpenAgreement();
    var participant = stored(2);
    givenStored(participant);
    for (var principal : List.of(staff(), staff(IMPORT)))
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      organizationId, agreementId, participant.getId().value(), principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(agreements, participants);
  }

  @Test
  void returnsTheCompleteDetailForAManager() {
    givenOpenAgreement();
    var participant = stored(2);
    givenStored(participant);

    var detail =
        useCase.execute(
            organizationId, agreementId, participant.getId().value(), staff(DETAIL_READ));

    assertThat(detail.identificationNumber()).isEqualTo(IDENTIFICATION);
    assertThat(detail.identificationNumberMasked()).isEqualTo("********8901");
    assertThat(detail.phone()).isEqualTo(PHONE);
    assertThat(detail.email()).isEqualTo(EMAIL);
    assertThat(detail.rowVersion()).isEqualTo(2);
    assertThat(detail.patientLinked()).isFalse();
    assertThat(detail.source()).isEqualTo("MANUAL");
    assertThat(detail.assignments()).isEmpty();
    assertThat(detail.attendanceStatus()).isEqualTo("UNCONFIRMED");
  }

  @Test
  void flagsAPatientLinkedParticipantAndStillReturnsACancelledOne() {
    givenOpenAgreement();
    var linked = stored(1, progress(new AggregateId(UUID.randomUUID()), RosterStatus.ACTIVE, NOW));
    givenStored(linked);
    assertThat(
            useCase
                .execute(organizationId, agreementId, linked.getId().value(), manager)
                .patientLinked())
        .isTrue();

    var cancelled = stored(1, progress(null, RosterStatus.CANCELLED, null));
    givenStored(cancelled);
    assertThat(
            useCase
                .execute(organizationId, agreementId, cancelled.getId().value(), manager)
                .rosterStatus())
        .isEqualTo("CANCELLED");
  }

  @Test
  void readsEvenWhenTheAgreementNoLongerAcceptsChanges() {
    givenAgreement(AgreementStatus.ENDED, null, true);
    var participant = stored(0);
    givenStored(participant);
    assertThat(useCase.execute(organizationId, agreementId, participant.getId().value(), manager))
        .isNotNull();
  }

  @Test
  void reportsAnUnknownAgreementOrParticipantAsNotFound() {
    when(agreements.findDetails(organizationId, agreementId, false)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, UUID.randomUUID(), manager))
        .isInstanceOf(ResourceNotFoundException.class);

    givenOpenAgreement();
    when(participants.findInAgreement(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, UUID.randomUUID(), manager))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
