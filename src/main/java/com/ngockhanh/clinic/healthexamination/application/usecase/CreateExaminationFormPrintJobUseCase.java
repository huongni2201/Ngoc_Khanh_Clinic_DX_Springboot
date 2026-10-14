package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.document.application.rendering.RenderJobs;
import com.ngockhanh.clinic.healthexamination.application.command.CreateExaminationFormPrintJobCommand;
import com.ngockhanh.clinic.healthexamination.application.command.ExaminationFormPrintScope;
import com.ngockhanh.clinic.healthexamination.application.contract.PeriodicFormBlankContract;
import com.ngockhanh.clinic.healthexamination.application.port.out.PrintableParticipantReader;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationFormPrintOrder;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationFormPrintJobResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormSourceLoader;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormValuesSource;
import com.ngockhanh.clinic.healthexamination.application.service.ExportFileNames;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
 * job holds identifiers only. With scope {@code BATCH_DAY} the backend chooses the Participants of
 * one examination day when the job is created (active, not yet attended unless a reprint is asked
 * for, never absent) and the list does not change afterwards. The audit event {@code CREATE_EXAMINATION_FORM_PRINT_JOB} records the
 * identifiers and the number of Participants, never personal data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateExaminationFormPrintJobUseCase {
  private final ParticipantAccessPolicy access;
  private final ExaminationFormSourceLoader loader;
  private final PrintableParticipantReader dayReader;
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
   * @throws IllegalArgumentException when an identifier or the key is missing, the list is empty,
   *     too long or has duplicates, or the fields do not fit the scope
   * @throws ResourceNotFoundException when the batch, the organization or the examination day is
   *     not found
   * @throws ConflictException when the batch is a draft or closed, nobody is left to print on the
   *     day, the key was used for a different request, or the caller already has too many jobs
   *     waiting or running
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
    String fileName = ExportFileNames.of("phieu-kham-", batchCode, ".pdf");
    List<UUID> participantIds = command.participantIds();
    if (command.scope() == ExaminationFormPrintScope.BATCH_DAY) {
      if (command.batchDayId() == null)
        throw new IllegalArgumentException("The examination day is required to print by day");
      if (!command.participantIds().isEmpty())
        throw new IllegalArgumentException("Participants cannot be listed when printing by day");
      var day =
          dayReader
              .readDay(
                  organizationId,
                  batchId,
                  command.batchDayId(),
                  command.includeAttended(),
                  command.sort() == null ? ExaminationFormPrintOrder.DEPARTMENT : command.sort())
              .orElseThrow(() -> new ResourceNotFoundException("Examination day"));
      if (day.participantIds().isEmpty())
        throw new ConflictException("No Participant is left to print on this examination day");
      participantIds = day.participantIds();
      fileName =
          ExportFileNames.of(
              "phieu-kham-",
              batchCode + "-" + day.examinationDate().format(DateTimeFormatter.BASIC_ISO_DATE),
              ".pdf");
    } else if (command.batchDayId() != null) {
      throw new IllegalArgumentException("An examination day can only be given when printing by day");
    }
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
                    fileName),
                participantIds));
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
              participantIds.size()));
      log.info(
          "Examination form print job queued: organizationId={}, batchId={}, scope={}, jobId={}, participants={}",
          organizationId,
          batchId,
          command.scope(),
          queued.jobId(),
          participantIds.size());
    }
    return new ExaminationFormPrintJobResponse(queued.jobId());
  }
}
