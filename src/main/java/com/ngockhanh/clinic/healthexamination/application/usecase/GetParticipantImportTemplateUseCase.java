package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.port.ParticipantTemplateWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantTemplateData;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportTemplateResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Produces the Excel template used to import the Participants of one batch.
 *
 * <p>The caller must hold the Participant read permission (the same one that guards the list). The template carries the batch
 * identifier, the configuration version it was issued for and the batch's examination dates, and is
 * only issued for a batch that still accepts imports. The batch is read once and the workbook is
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
  private final HealthExaminationBatchRepository batches;
  private final ParticipantTemplateWriter writer;

  /**
   * Returns the template of the batch.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param principal authenticated staff account
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the template download
   *     permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the organization or the batch is not found
   * @throws ConflictException when the organization or batch does not accept Participant imports
   */
  public ParticipantImportTemplateResponse execute(
      UUID organizationId, UUID batchId, UserPrincipal principal) {
    access.requireTemplateDownload(principal);
    if (organizationId == null || batchId == null)
      throw new IllegalArgumentException("Organization ID and batch ID are required");
    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var details =
        batches
            .findDetails(organizationId, batchId, false)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    var batch = details.batch();
    if (organization.status() != OrganizationStatus.ACTIVE || !batch.acceptsParticipantImport())
      throw new ConflictException("Batch does not accept Participant imports");

    var data =
        new ParticipantTemplateData(
            batchId,
            batch.rowVersion(),
            batch.days().stream()
                .map(HealthExaminationBatchDay::examinationDate)
                .sorted(Comparator.naturalOrder())
                .toList());
    var response = new ParticipantImportTemplateResponse(writer.write(data), FILE_NAME);
    log.debug(
        "Participant import template generated: organizationId={}, batchId={}, days={}",
        organizationId,
        batchId,
        data.examinationDates().size());
    return response;
  }
}
