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
import com.ngockhanh.clinic.healthexamination.application.contract.PeriodicFormBlankContract;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormSourceLoader;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormValuesSource;
import com.ngockhanh.clinic.healthexamination.application.service.ExportFileNames;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
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

class CreateExaminationFormPrintJobUseCaseTest extends ManualParticipantUseCaseTestBase {
  private final RenderJobs jobs = mock(RenderJobs.class);
  private final ExaminationFormSourceLoader loader =
      new ExaminationFormSourceLoader(
          support,
          organizations,
          participants,
          Clock.fixed(com.ngockhanh.clinic.healthexamination.RosterFixtures.NOW, ZoneOffset.UTC));
  private final CreateExaminationFormPrintJobUseCase useCase =
      new CreateExaminationFormPrintJobUseCase(access, loader, jobs, audit);
  private final UUID key = UUID.randomUUID();
  private final List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
  private final UUID jobId = UUID.randomUUID();

  CreateExaminationFormPrintJobUseCaseTest() {
    super("PARTICIPANT_EXAMINATION_FORM_BULK_PRINT");
  }

  private CreateExaminationFormPrintJobCommand command() {
    return new CreateExaminationFormPrintJobCommand(ids, key);
  }

  @Test
  void rejectsCallersWithoutTheBulkPrintPermissionBeforeReadingAnything() {
    givenOpenBatch();

    for (var principal :
        List.of(staff(), staff("PARTICIPANT_EXAMINATION_FORM_PRINT"), staff("DOCUMENT_RENDER_JOB_READ")))
      assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(), principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));

    verifyNoInteractions(batches, organizations, jobs, audit);
  }

  @Test
  void queuesTheJobWithIdentifiersOnlyAndAuditsWithoutPersonalData() {
    givenOpenBatch();
    when(jobs.enqueue(any())).thenReturn(new RenderJobs.Queued(jobId, false));

    var response = useCase.execute(organizationId, batchId, command(), manager);

    assertThat(response.jobId()).isEqualTo(jobId);
    var request = ArgumentCaptor.forClass(RenderJobs.NewRenderJob.class);
    verify(jobs).enqueue(request.capture());
    assertThat(request.getValue().documentType()).isEqualTo(PeriodicFormBlankContract.DOCUMENT_TYPE);
    assertThat(request.getValue().requestedBy()).isEqualTo(manager.userId());
    assertThat(request.getValue().idempotencyKey()).isEqualTo(key);
    assertThat(request.getValue().recordIds()).containsExactlyElementsOf(ids);
    assertThat(request.getValue().context())
        .containsEntry(ExaminationFormValuesSource.ORGANIZATION_ID, organizationId.toString())
        .containsEntry(ExaminationFormValuesSource.BATCH_ID, batchId.toString())
        .containsEntry(
            RenderJobs.FILE_NAME_KEY, ExportFileNames.of("phieu-kham-", batch.code(), ".pdf"))
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
        .isEqualTo(Map.of("organizationId", organizationId, "batchId", batchId, "participantCount", 2));
  }

  @Test
  void aReplayedRequestReturnsTheSameJobAndIsNotAuditedAgain() {
    givenOpenBatch();
    when(jobs.enqueue(any())).thenReturn(new RenderJobs.Queued(jobId, true));

    assertThat(useCase.execute(organizationId, batchId, command(), manager).jobId()).isEqualTo(jobId);

    verifyNoInteractions(audit);
  }

  @Test
  void printsForReadyAndFinalizedBatchesOnly() {
    when(jobs.enqueue(any())).thenReturn(new RenderJobs.Queued(jobId, false));
    for (var status : BatchStatus.values()) {
      givenBatch(status, null, true);
      boolean printable = status == BatchStatus.READY || status == BatchStatus.FINALIZED;
      if (printable)
        assertThat(useCase.execute(organizationId, batchId, command(), manager)).isNotNull();
      else
        assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(), manager))
            .as(status.name())
            .isInstanceOf(ConflictException.class);
    }
  }

  @Test
  void reportsAnUnknownBatchOrOrganizationAsNotFoundAndQueuesNothing() {
    when(batches.findDetails(organizationId, batchId, false)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(), manager))
        .isInstanceOf(ResourceNotFoundException.class);

    givenOpenBatch();
    when(organizations.findById(any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(), manager))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(jobs, audit);
  }

  @Test
  void queueingProblemsAreReportedAndNothingIsAudited() {
    givenOpenBatch();
    when(jobs.enqueue(any()))
        .thenThrow(new ConflictException("Too many print jobs"))
        .thenThrow(new DependencyUnavailableException("Document storage is not configured", null))
        .thenThrow(new IllegalArgumentException("Between 1 and 1000 records are required"));

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(), manager))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(), manager))
        .isInstanceOf(DependencyUnavailableException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, command(), manager))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(audit);
  }

  @Test
  void requiresTheIdentifiersAndTheIdempotencyKey() {
    assertThatThrownBy(() -> useCase.execute(null, batchId, command(), manager))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, null, command(), manager))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, null, manager))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId,
                    batchId,
                    new CreateExaminationFormPrintJobCommand(ids, null),
                    manager))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(jobs, audit);
  }
}
