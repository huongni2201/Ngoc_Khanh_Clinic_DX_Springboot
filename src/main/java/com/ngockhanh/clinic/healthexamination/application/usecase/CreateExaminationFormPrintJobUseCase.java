package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.document.application.rendering.RenderJobs;
import com.ngockhanh.clinic.healthexamination.application.command.CreateExaminationFormPrintJobCommand;
import com.ngockhanh.clinic.healthexamination.application.contract.PeriodicFormBlankContract;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationFormPrintJobResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormSourceLoader;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormValuesSource;
import com.ngockhanh.clinic.healthexamination.application.service.ExportFileNames;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Queues a background job that prints the blank examination forms of many Participants of a batch
 * and merges them into PDF parts.
 *
 * <p>The caller must hold {@code PARTICIPANT_EXAMINATION_FORM_BULK_PRINT}, checked before anything
 * is read. Only the batch is checked now; each Participant is checked when the job runs, so a
 * Participant that is missing or cancelled is skipped with a reason instead of failing the job. The
 * job holds identifiers only. The audit event {@code CREATE_EXAMINATION_FORM_PRINT_JOB} records the
 * identifiers and the number of Participants, never personal data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateExaminationFormPrintJobUseCase {
  private final ParticipantAccessPolicy access;
  private final ExaminationFormSourceLoader loader;
  private final RenderJobs jobs;
  private final AuditWriter audit;

  /**
   * Queues the job. The same account with the same idempotency key and request gets the job
   * already queued.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param command the Participants and the idempotency key
   * @param principal authenticated staff caller
   * @return the identifier of the queued job
   * @throws ApplicationException {@code ACCESS_DENIED} without the bulk print permission
   * @throws IllegalArgumentException when an identifier or the key is missing, or the list is empty,
   *     too long or has duplicates
   * @throws ResourceNotFoundException when the batch or organization is not found
   * @throws ConflictException when the batch is a draft or closed, the key was used for a different
   *     request, or the caller already has too many jobs waiting or running
   * @throws DependencyUnavailableException when document storage is not configured
   */
  public ExaminationFormPrintJobResponse execute(
      UUID organizationId,
      UUID batchId,
      CreateExaminationFormPrintJobCommand command,
      UserPrincipal principal) {
    access.requireBulkPrint(principal);
    if (organizationId == null || batchId == null || command == null || command.idempotencyKey() == null)
      throw new IllegalArgumentException("Organization, batch and idempotency key are required");
    String batchCode = loader.requirePrintableBatch(organizationId, batchId);
    var queued =
        jobs.enqueue(
            new RenderJobs.NewRenderJob(
                PeriodicFormBlankContract.DOCUMENT_TYPE,
                principal.userId(),
                command.idempotencyKey(),
                Map.of(
                    ExaminationFormValuesSource.ORGANIZATION_ID,
                    organizationId.toString(),
                    ExaminationFormValuesSource.BATCH_ID,
                    batchId.toString(),
                    RenderJobs.FILE_NAME_KEY,
                    ExportFileNames.of("phieu-kham-", batchCode, ".pdf")),
                command.participantIds()));
    if (!queued.replayed()) {
      audit.record(
          principal.userId(),
          "CREATE_EXAMINATION_FORM_PRINT_JOB",
          "DOCUMENT_RENDER_JOB",
          queued.jobId(),
          null,
          Map.of(
              "organizationId",
              organizationId,
              "batchId",
              batchId,
              "participantCount",
              command.participantIds().size()));
      log.info(
          "Examination form print job queued: organizationId={}, batchId={}, jobId={}, participants={}",
          organizationId,
          batchId,
          queued.jobId(),
          command.participantIds().size());
    }
    return new ExaminationFormPrintJobResponse(queued.jobId());
  }
}
