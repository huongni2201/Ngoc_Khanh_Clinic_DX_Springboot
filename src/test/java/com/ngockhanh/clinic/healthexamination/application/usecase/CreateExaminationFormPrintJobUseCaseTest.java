package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.staff;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.document.application.rendering.RenderJobs;
import com.ngockhanh.clinic.healthexamination.application.command.CreateExaminationFormPrintJobCommand;
import com.ngockhanh.clinic.healthexamination.application.command.ExaminationFormPrintScope;
import com.ngockhanh.clinic.healthexamination.application.contract.PeriodicFormBlankContract;
import com.ngockhanh.clinic.healthexamination.application.port.out.PrintableParticipantReader;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationFormPrintOrder;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormSourceLoader;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormValuesSource;
import com.ngockhanh.clinic.healthexamination.application.service.ExportFileNames;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateExaminationFormPrintJobUseCaseTest extends ManualParticipantUseCaseTestBase {
  private final RenderJobs jobs = mock(RenderJobs.class);
  private final PrintableParticipantReader sessionReader = mock(PrintableParticipantReader.class);
  private final ExaminationFormSourceLoader loader =
      new ExaminationFormSourceLoader(
          support,
          organizations,
          participants,
          sessions,
          assignments,
          Clock.fixed(com.ngockhanh.clinic.healthexamination.RosterFixtures.NOW, ZoneOffset.UTC));
  private final CreateExaminationFormPrintJobUseCase useCase =
      new CreateExaminationFormPrintJobUseCase(access, loader, sessionReader, jobs, audit);
  private final UUID key = UUID.randomUUID();
  private final List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
  private final UUID jobId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();

  CreateExaminationFormPrintJobUseCaseTest() {
    super("PARTICIPANT_EXAMINATION_FORM_BULK_PRINT");
  }

  private CreateExaminationFormPrintJobCommand command() {
    return CreateExaminationFormPrintJobCommand.ofIds(ids, key);
  }

  @Test
  void rejectsCallersWithoutTheBulkPrintPermissionBeforeReadingAnything() {
    givenOpenAgreement();

    for (var principal :
        List.of(
            staff(),
            staff("PARTICIPANT_EXAMINATION_FORM_PRINT"),
            staff("DOCUMENT_RENDER_JOB_READ")))
      assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, command(), principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));

    verifyNoInteractions(agreements, organizations, sessionReader, jobs, audit);
  }

  @Test
  void queuesTheJobWithIdentifiersOnlyAndAuditsWithoutPersonalData() {
    givenOpenAgreement();
    when(jobs.enqueue(any())).thenReturn(new RenderJobs.Queued(jobId, false));

    var response = useCase.execute(organizationId, agreementId, command(), manager);

    assertThat(response.jobId()).isEqualTo(jobId);
    var request = ArgumentCaptor.forClass(RenderJobs.NewRenderJob.class);
    verify(jobs).enqueue(request.capture());
    assertThat(request.getValue().documentType())
        .isEqualTo(PeriodicFormBlankContract.DOCUMENT_TYPE);
    assertThat(request.getValue().requestedBy()).isEqualTo(manager.userId());
    assertThat(request.getValue().idempotencyKey()).isEqualTo(key);
    assertThat(request.getValue().recordIds()).containsExactlyElementsOf(ids);
    assertThat(request.getValue().context())
        .containsEntry(ExaminationFormValuesSource.ORGANIZATION_ID, organizationId.toString())
        .containsEntry(ExaminationFormValuesSource.AGREEMENT_ID, agreementId.toString())
        .containsEntry(
            RenderJobs.FILE_NAME_KEY, ExportFileNames.of("phieu-kham-", agreement.code(), ".pdf"))
        .hasSize(3);

    var after = ArgumentCaptor.forClass(Object.class);
    verify(audit)
        .record(
            eq(manager.userId()),
            eq("CREATE_EXAMINATION_FORM_PRINT_JOB"),
            eq("DOCUMENT_RENDER_JOB"),
            eq(jobId),
            isNull(),
            after.capture());
    assertThat(after.getValue())
        .isEqualTo(
            Map.of(
                "organizationId",
                organizationId,
                "agreementId",
                agreementId,
                "participantCount",
                2));
  }

  @Test
  void aReplayedRequestReturnsTheSameJobAndIsNotAuditedAgain() {
    givenOpenAgreement();
    when(jobs.enqueue(any())).thenReturn(new RenderJobs.Queued(jobId, true));

    assertThat(useCase.execute(organizationId, agreementId, command(), manager).jobId())
        .isEqualTo(jobId);

    verifyNoInteractions(audit);
  }

  @Test
  void printsForActiveAndEndedAgreementsOnly() {
    when(jobs.enqueue(any())).thenReturn(new RenderJobs.Queued(jobId, false));
    for (var status : AgreementStatus.values()) {
      givenAgreement(status, null, true);
      boolean printable = status == AgreementStatus.ACTIVE || status == AgreementStatus.ENDED;
      if (printable)
        assertThat(useCase.execute(organizationId, agreementId, command(), manager)).isNotNull();
      else
        assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, command(), manager))
            .as(status.name())
            .isInstanceOf(ConflictException.class);
    }
  }

  @Test
  void reportsAnUnknownAgreementOrOrganizationAsNotFoundAndQueuesNothing() {
    when(agreements.findDetails(organizationId, agreementId, false)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, command(), manager))
        .isInstanceOf(ResourceNotFoundException.class);

    givenOpenAgreement();
    when(organizations.findById(any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, command(), manager))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(sessionReader, jobs, audit);
  }

  private CreateExaminationFormPrintJobCommand sessionCommand(boolean includeAttended) {
    return new CreateExaminationFormPrintJobCommand(
        ExaminationFormPrintScope.SESSION,
        List.of(),
        sessionId,
        includeAttended,
        ExaminationFormPrintOrder.CODE,
        key);
  }

  @Test
  void printingBySessionQueuesTheParticipantsChosenByTheBackendInTheirOrder() {
    givenOpenAgreement();
    when(sessionReader.readSession(
            organizationId, agreementId, sessionId, true, ExaminationFormPrintOrder.CODE))
        .thenReturn(
            Optional.of(
                new PrintableParticipantReader.PrintableSession(LocalDate.of(2026, 10, 15), ids)));
    when(jobs.enqueue(any())).thenReturn(new RenderJobs.Queued(jobId, false));

    var response = useCase.execute(organizationId, agreementId, sessionCommand(true), manager);

    assertThat(response.jobId()).isEqualTo(jobId);
    var request = ArgumentCaptor.forClass(RenderJobs.NewRenderJob.class);
    verify(jobs).enqueue(request.capture());
    assertThat(request.getValue().recordIds()).containsExactlyElementsOf(ids);
    assertThat(request.getValue().context())
        .containsEntry(
            RenderJobs.FILE_NAME_KEY,
            ExportFileNames.of("phieu-kham-", agreement.code() + "-20261015", ".pdf"));
    verify(audit)
        .record(
            eq(manager.userId()),
            eq("CREATE_EXAMINATION_FORM_PRINT_JOB"),
            eq("DOCUMENT_RENDER_JOB"),
            eq(jobId),
            isNull(),
            eq(
                Map.of(
                    "organizationId",
                    organizationId,
                    "agreementId",
                    agreementId,
                    "participantCount",
                    2)));
  }

  @Test
  void aListedPrintMayNameTheSessionWhoseDateIsPrintedOnTheForms() {
    givenOpenAgreement();
    when(jobs.enqueue(any())).thenReturn(new RenderJobs.Queued(jobId, false));
    var command =
        new CreateExaminationFormPrintJobCommand(
            ExaminationFormPrintScope.IDS,
            ids,
            sessionId,
            false,
            ExaminationFormPrintOrder.DEPARTMENT,
            key);

    useCase.execute(organizationId, agreementId, command, manager);

    var request = ArgumentCaptor.forClass(RenderJobs.NewRenderJob.class);
    verify(jobs).enqueue(request.capture());
    assertThat(request.getValue().recordIds()).containsExactlyElementsOf(ids);
    assertThat(request.getValue().context())
        .containsEntry(ExaminationFormValuesSource.SESSION_ID, sessionId.toString());
    verifyNoInteractions(sessionReader);
  }

  @Test
  void printingBySessionReportsAnUnknownSessionAsNotFoundAndAnEmptySessionAsAConflict() {
    givenOpenAgreement();
    when(sessionReader.readSession(any(), any(), any(), eq(false), any()))
        .thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, sessionCommand(false), manager))
        .isInstanceOf(ResourceNotFoundException.class);

    when(sessionReader.readSession(any(), any(), any(), eq(false), any()))
        .thenReturn(
            Optional.of(
                new PrintableParticipantReader.PrintableSession(
                    LocalDate.of(2026, 10, 15), List.of())));
    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, sessionCommand(false), manager))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(jobs, audit);
  }

  @Test
  void aSessionPrintNeedsTheSessionAndRefusesAList() {
    givenOpenAgreement();
    var noDay =
        new CreateExaminationFormPrintJobCommand(
            ExaminationFormPrintScope.SESSION,
            List.of(),
            null,
            false,
            ExaminationFormPrintOrder.DEPARTMENT,
            key);
    var dayAndList =
        new CreateExaminationFormPrintJobCommand(
            ExaminationFormPrintScope.SESSION,
            ids,
            sessionId,
            false,
            ExaminationFormPrintOrder.DEPARTMENT,
            key);
    for (var command : List.of(noDay, dayAndList))
      assertThatThrownBy(() -> useCase.execute(organizationId, agreementId, command, manager))
          .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(sessionReader, jobs, audit);
  }

  @Test
  void printingBySessionStillRequiresAPrintableAgreement() {
    givenAgreement(AgreementStatus.DRAFT, null, true);

    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, sessionCommand(false), manager))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(sessionReader, jobs, audit);
  }
}
