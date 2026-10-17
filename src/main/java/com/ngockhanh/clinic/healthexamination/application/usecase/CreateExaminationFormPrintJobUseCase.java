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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Queues a background job that prints the blank examination forms of many Participants of an
 * agreement and merges them into PDF parts.
 *
 * <p>The caller must hold {@code PARTICIPANT_EXAMINATION_FORM_BULK_PRINT}, checked before anything
 * is read. Only the agreement is checked now; each Participant is checked when the job runs, so a
 * Participant that is missing or cancelled is skipped with a reason instead of failing the job. The
 * job holds identifiers only. With scope {@code SESSION} the backend chooses the Participants
 * expected in one session when the job is created (active, not yet attended unless a reprint is
 * asked for, never a no-show) and the list does not change afterwards; the forms show the date of
 * that session. The audit event {@code CREATE_EXAMINATION_FORM_PRINT_JOB} records the identifiers
 * and the number of Participants, never personal data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateExaminationFormPrintJobUseCase {
  private final ParticipantAccessPolicy access;
  private final ExaminationFormSourceLoader loader;
  private final PrintableParticipantReader sessionReader;
  private final RenderJobs jobs;
  private final AuditWriter audit;

  /**
   * Queues the job. The same account with the same idempotency key and request gets the job already
   * queued.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param command the Participants and the idempotency key
   * @param principal authenticated staff caller
   * @return the identifier of the queued job
   * @throws ApplicationException {@code ACCESS_DENIED} without the bulk print permission
   * @throws IllegalArgumentException when an identifier or the key is missing, the list is empty,
   *     too long or has duplicates, or the fields do not fit the scope
   * @throws ResourceNotFoundException when the agreement, the organization or the session is not
   *     found
   * @throws ConflictException when the agreement is a draft, nobody is left to print in the
   *     session, the key was used for a different request, or the caller already has too many jobs
   *     waiting or running
   * @throws DependencyUnavailableException when document storage is not configured
   */
  public ExaminationFormPrintJobResponse execute(
      UUID organizationId,
      UUID agreementId,
      CreateExaminationFormPrintJobCommand command,
      UserPrincipal principal) {
    access.requireBulkPrint(principal);
    if (organizationId == null
        || agreementId == null
        || command == null
        || command.idempotencyKey() == null)
      throw new IllegalArgumentException(
          "Organization, agreement and idempotency key are required");
    String agreementCode = loader.requirePrintableAgreement(organizationId, agreementId);
    String fileName = ExportFileNames.of("phieu-kham-", agreementCode, ".pdf");
    List<UUID> participantIds = command.participantIds();
    if (command.scope() == ExaminationFormPrintScope.SESSION) {
      if (command.sessionId() == null)
        throw new IllegalArgumentException("The session is required to print by session");
      if (!command.participantIds().isEmpty())
        throw new IllegalArgumentException(
            "Participants cannot be listed when printing by session");
      var session =
          sessionReader
              .readSession(
                  organizationId,
                  agreementId,
                  command.sessionId(),
                  command.includeAttended(),
                  command.sort() == null ? ExaminationFormPrintOrder.DEPARTMENT : command.sort())
              .orElseThrow(() -> new ResourceNotFoundException("Session"));
      if (session.participantIds().isEmpty())
        throw new ConflictException("No Participant is left to print in this session");
      participantIds = session.participantIds();
      fileName =
          ExportFileNames.of(
              "phieu-kham-",
              agreementCode + "-" + session.sessionDate().format(DateTimeFormatter.BASIC_ISO_DATE),
              ".pdf");
    }
    Map<String, String> context = new HashMap<>();
    context.put(ExaminationFormValuesSource.ORGANIZATION_ID, organizationId.toString());
    context.put(ExaminationFormValuesSource.AGREEMENT_ID, agreementId.toString());
    if (command.sessionId() != null)
      context.put(ExaminationFormValuesSource.SESSION_ID, command.sessionId().toString());
    context.put(RenderJobs.FILE_NAME_KEY, fileName);
    var queued =
        jobs.enqueue(
            new RenderJobs.NewRenderJob(
                PeriodicFormBlankContract.DOCUMENT_TYPE,
                principal.userId(),
                command.idempotencyKey(),
                Map.copyOf(context),
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
              "agreementId",
              agreementId,
              "participantCount",
              participantIds.size()));
      log.info(
          "Examination form print job queued: organizationId={}, agreementId={}, scope={}, jobId={},"
              + " participants={}",
          organizationId,
          agreementId,
          command.scope(),
          queued.jobId(),
          participantIds.size());
    }
    return new ExaminationFormPrintJobResponse(queued.jobId());
  }
}
