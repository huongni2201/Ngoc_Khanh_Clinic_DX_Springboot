package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantTemplateWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantTemplateData;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportTemplateResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Produces the Excel template used to import the Participants of one agreement.
 *
 * <p>The caller must hold the Participant read permission (the same one that guards the list). The
 * template carries the agreement identifier and the version it was issued for, and is only issued
 * for an agreement that still accepts imports. The agreement is read once and the workbook is
 * rendered afterwards, so no database transaction is held while rendering. Nothing is written or
 * audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetParticipantImportTemplateUseCase {
  static final String FILE_NAME = "participant-import-template.xlsx";

  private final ParticipantAccessPolicy access;
  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final ParticipantTemplateWriter writer;

  /**
   * Returns the template of the agreement.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param principal authenticated staff account
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the template download
   *     permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the organization or the agreement is not found
   * @throws ConflictException when the organization or agreement does not accept Participant
   *     imports
   */
  public ParticipantImportTemplateResponse execute(
      UUID organizationId, UUID agreementId, UserPrincipal principal) {
    access.requireTemplateDownload(principal);
    if (organizationId == null || agreementId == null)
      throw new IllegalArgumentException("Organization ID and agreement ID are required");
    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var details =
        agreements
            .findDetails(organizationId, agreementId, false)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"));
    var agreement = details.agreement();
    if (organization.status() != OrganizationStatus.ACTIVE || !agreement.acceptsParticipantImport())
      throw new ConflictException("Agreement does not accept Participant imports");

    var data = new ParticipantTemplateData(agreementId, agreement.rowVersion());
    var response = new ParticipantImportTemplateResponse(writer.write(data), FILE_NAME);
    log.info(
        "Participant import template generated: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    return response;
  }
}
