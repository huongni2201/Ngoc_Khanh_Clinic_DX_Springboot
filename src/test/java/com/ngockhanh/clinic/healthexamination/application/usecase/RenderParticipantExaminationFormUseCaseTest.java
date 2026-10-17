package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.IMPORT;
import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.staff;
import static com.ngockhanh.clinic.healthexamination.RosterFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.document.application.rendering.DocumentRenderer;
import com.ngockhanh.clinic.document.application.rendering.RenderedDocument;
import com.ngockhanh.clinic.healthexamination.application.contract.PeriodicFormBlankContract;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormSourceLoader;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RenderParticipantExaminationFormUseCaseTest extends ManualParticipantUseCaseTestBase {
  private final DocumentRenderer renderer = mock(DocumentRenderer.class);
  private final ExaminationFormSourceLoader loader =
      new ExaminationFormSourceLoader(
          support,
          organizations,
          participants,
          sessions,
          assignments,
          Clock.fixed(NOW, ZoneOffset.UTC));
  private final RenderParticipantExaminationFormUseCase useCase =
      new RenderParticipantExaminationFormUseCase(access, loader, renderer, audit);
  private final UUID versionId = UUID.randomUUID();

  RenderParticipantExaminationFormUseCaseTest() {
    super("PARTICIPANT_EXAMINATION_FORM_PRINT");
  }

  private void givenRenderedPdf() {
    when(renderer.render(eq(PeriodicFormBlankContract.DOCUMENT_TYPE), any()))
        .thenReturn(new RenderedDocument(new byte[] {7, 8}, versionId, 3));
  }

  @Test
  void rejectsCallersWithoutThePrintPermissionBeforeReadingAnything() {
    givenOpenAgreement();
    var participant = stored(0);
    givenStored(participant);

    for (var principal : List.of(staff(), staff(IMPORT)))
      assertThatThrownBy(
              () ->
                  useCase.execute(
                      organizationId, agreementId, participant.getId().value(), null, principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));

    verifyNoInteractions(agreements, participants, renderer, audit);
  }

  @Test
  void rendersTheFormFromTheRosterAndAuditsWithoutPersonalData() {
    givenOpenAgreement();
    var participant = stored(0);
    givenStored(participant);
    givenRenderedPdf();

    var form =
        useCase.execute(organizationId, agreementId, participant.getId().value(), null, manager);

    assertThat(form.content()).containsExactly(7, 8);
    assertThat(form.fileName()).isEqualTo("phieu-kham-NV-001.pdf");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> values = ArgumentCaptor.forClass(Map.class);
    verify(renderer).render(eq(PeriodicFormBlankContract.DOCUMENT_TYPE), values.capture());
    assertThat(values.getValue())
        .containsEntry("participant.code", "NV-001")
        .containsEntry("participant.fullName", FULL_NAME)
        .containsEntry("participant.sex", "Nam")
        .containsEntry("participant.dateOfBirth", "12/05/1990")
        .containsEntry("participant.identificationNumber", IDENTIFICATION)
        .containsEntry("participant.phone", PHONE)
        .containsEntry("participant.workplace", "Clinic Partner")
        .containsEntry("participant.departmentName", "Accounting")
        .containsEntry("participant.positionName", "Staff")
        .containsEntry("participant.address", "…………………………")
        .containsEntry("organization.name", "Clinic Partner")
        .containsEntry("participant.examinationDate", "…………………………")
        .containsEntry("printedDate", "04/10/2026");

    ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
    verify(audit)
        .record(
            eq(manager.userId()),
            eq("RENDER_EXAMINATION_FORM"),
            eq("HEALTH_EXAMINATION_PARTICIPANT"),
            eq(participant.getId().value()),
            isNull(),
            after.capture());
    assertThat(after.getValue())
        .isEqualTo(
            Map.of(
                "organizationId", organizationId,
                "agreementId", agreementId,
                "templateVersionId", versionId,
                "templateVersionNo", 3));
    assertThat(after.getValue().toString())
        .doesNotContain(IDENTIFICATION)
        .doesNotContain(FULL_NAME);
  }

  @Test
  void printsForActiveAndEndedAgreementsOnly() {
    givenRenderedPdf();
    for (var status : AgreementStatus.values()) {
      givenAgreement(status, null, true);
      var participant = stored(0);
      givenStored(participant);
      boolean printable = status == AgreementStatus.ACTIVE || status == AgreementStatus.ENDED;
      if (printable)
        assertThat(
                useCase.execute(
                    organizationId, agreementId, participant.getId().value(), null, manager))
            .isNotNull();
      else
        assertThatThrownBy(
                () ->
                    useCase.execute(
                        organizationId, agreementId, participant.getId().value(), null, manager))
            .as(status.name())
            .isInstanceOf(ConflictException.class);
    }
  }

  @Test
  void aCancelledParticipantHasNoForm() {
    givenOpenAgreement();
    var cancelled = stored(1, progress(null, RosterStatus.CANCELLED, null));
    givenStored(cancelled);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, agreementId, cancelled.getId().value(), null, manager))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(renderer, audit);
  }

  @Test
  void reportsAnUnknownAgreementOrParticipantAsNotFound() {
    when(agreements.findDetails(organizationId, agreementId, false)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, UUID.randomUUID(), null, manager))
        .isInstanceOf(ResourceNotFoundException.class);

    givenOpenAgreement();
    when(participants.findInAgreement(any(), any())).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, UUID.randomUUID(), null, manager))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(renderer, audit);
  }

  @Test
  void aConverterOutageIsReportedAndNothingIsAudited() {
    givenOpenAgreement();
    var participant = stored(0);
    givenStored(participant);
    when(renderer.render(any(), any())).thenThrow(new DependencyUnavailableException("down", null));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, agreementId, participant.getId().value(), null, manager))
        .isInstanceOf(DependencyUnavailableException.class);
    verifyNoInteractions(audit);
  }

  @Test
  void theFormShowsTheDateOfTheNamedSessionOrTheNextExpectedOne() {
    givenOpenAgreement();
    var participant = stored(0);
    givenStored(participant);
    givenRenderedPdf();
    var sessionId = UUID.randomUUID();
    var session =
        com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationSession.open(
            AggregateId.of(sessionId),
            AggregateId.of(organizationId),
            AggregateId.of(agreementId),
            java.time.LocalDate.of(2026, 10, 20),
            com.ngockhanh.clinic.healthexamination.domain.enums.SessionActivity.ALL,
            new com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite(
                com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType.CLINIC,
                null));
    when(sessions.findById(organizationId, sessionId, false)).thenReturn(Optional.of(session));

    useCase.execute(organizationId, agreementId, participant.getId().value(), sessionId, manager);
    when(assignments.findExaminationDates(any(), any()))
        .thenReturn(Map.of(participant.getId(), java.time.LocalDate.of(2026, 10, 22)));
    useCase.execute(organizationId, agreementId, participant.getId().value(), null, manager);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> values = ArgumentCaptor.forClass(Map.class);
    verify(renderer, org.mockito.Mockito.times(2))
        .render(eq(PeriodicFormBlankContract.DOCUMENT_TYPE), values.capture());
    assertThat(values.getAllValues().get(0))
        .containsEntry("participant.examinationDate", "20/10/2026");
    assertThat(values.getAllValues().get(1))
        .containsEntry("participant.examinationDate", "22/10/2026");
  }
}
