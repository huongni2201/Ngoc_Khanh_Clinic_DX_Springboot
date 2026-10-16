package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.document.application.rendering.DocumentRenderer;
import com.ngockhanh.clinic.healthexamination.application.contract.PeriodicFormBlankContract;
import com.ngockhanh.clinic.healthexamination.application.contract.PeriodicFormBlankModel;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationFormDocumentResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormSourceLoader;
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
 * Prints the blank periodic examination form of one Participant as a PDF.
 *
 * <p>The caller must hold {@code PARTICIPANT_EXAMINATION_FORM_PRINT}, checked before anything is
 * read. The facts are read in a short transaction, the PDF is rendered from the template version in
 * effect now with no transaction open, and the audit event {@code RENDER_EXAMINATION_FORM} records
 * only identifiers and the template version, never personal data. The form is a working document,
 * not an issued official one: it is not stored. Rendering does not prove that anything was printed
 * on paper.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenderParticipantExaminationFormUseCase {
  private final ParticipantAccessPolicy access;
  private final ExaminationFormSourceLoader loader;
  private final DocumentRenderer renderer;
  private final AuditWriter audit;

  /**
   * Renders the form.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param participantId Participant identifier inside the agreement
   * @param sessionId session whose date the form shows, or null for the Participant's own schedule
   * @param principal authenticated staff caller
   * @return the PDF and its download name
   * @throws ApplicationException {@code ACCESS_DENIED} without the print permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the agreement or Participant is not found, or no
   *     template is in effect
   * @throws ConflictException when the agreement is a draft, the session belongs to another
   *     agreement, or the Participant is cancelled
   * @throws DependencyUnavailableException when the PDF converter cannot be reached
   */
  public ExaminationFormDocumentResponse execute(
      UUID organizationId,
      UUID agreementId,
      UUID participantId,
      UUID sessionId,
      UserPrincipal principal) {
    access.requirePrint(principal);
    if (organizationId == null || agreementId == null || participantId == null)
      throw new IllegalArgumentException(
          "Organization, agreement and Participant IDs are required");
    var source = loader.load(organizationId, agreementId, participantId, sessionId);
    var rendered =
        renderer.render(
            PeriodicFormBlankContract.DOCUMENT_TYPE,
            PeriodicFormBlankModel.from(source).toValues());
    audit.record(
        principal.userId(),
        "RENDER_EXAMINATION_FORM",
        "HEALTH_EXAMINATION_PARTICIPANT",
        participantId,
        null,
        Map.of(
            "organizationId",
            organizationId,
            "agreementId",
            agreementId,
            "templateVersionId",
            rendered.templateVersionId(),
            "templateVersionNo",
            rendered.templateVersionNo()));
    log.info(
        "Examination form rendered: organizationId={}, agreementId={}, participantId={}, templateVersionId={}",
        organizationId,
        agreementId,
        participantId,
        rendered.templateVersionId());
    return new ExaminationFormDocumentResponse(
        rendered.pdf(), ExportFileNames.of("phieu-kham-", source.participantCode(), ".pdf"));
  }
}
