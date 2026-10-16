package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailExcelWriter;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailExportResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailWorkbookAssembler;
import com.ngockhanh.clinic.healthexamination.application.service.ExportFileNames;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports the examination detail matrix of one health examination agreement to an Excel workbook
 * that is also the template of the import.
 *
 * <p>The caller must hold the service read permission, checked before the agreement is looked up.
 * The export always holds every active Participant of the agreement, whatever filter the screen
 * shows, so the file can be imported again as it is. The identification number is masked before it
 * reaches the writer, and no phone, email or attendance note is exported. The agreement, its
 * services, the Participants and the audit event {@code EXPORT_EXAMINATION_DETAILS} (the file holds
 * Participant names) are handled in one repeatable-read transaction, so the workbook is consistent
 * and the export is not served without its audit trail.
 */
@Slf4j
@Service
public class ExportExaminationDetailsUseCase {
  private final ExaminationDetailAccessPolicy access;
  private final ExaminationDetailWorkbookAssembler assembler;
  private final AuditWriter audit;

  public ExportExaminationDetailsUseCase(
      ExaminationDetailAccessPolicy access,
      OrganizationRepository organizations,
      HealthExaminationAgreementRepository agreements,
      ExaminationDetailReader reader,
      ServiceCatalogQuery catalog,
      ExaminationDetailExcelWriter writer,
      AuditWriter audit,
      Clock clock) {
    this.access = access;
    this.assembler =
        new ExaminationDetailWorkbookAssembler(
            organizations, agreements, reader, catalog, writer, clock);
    this.audit = audit;
  }

  /**
   * Returns the workbook of the agreement.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param principal authenticated staff account
   * @return the XLSX bytes and the download name
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the service read permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the organization or the agreement is not found
   */
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public ExaminationDetailExportResponse execute(
      UUID organizationId, UUID agreementId, UserPrincipal principal) {
    access.requireServiceExport(principal);
    var workbook = assembler.assemble(organizationId, agreementId);
    audit.record(
        principal.userId(),
        "EXPORT_EXAMINATION_DETAILS",
        "HEALTH_EXAMINATION_AGREEMENT",
        agreementId,
        null,
        Map.of(
            "organizationId", organizationId,
            "agreementId", agreementId,
            "participantCount", workbook.participantCount()));
    log.info(
        "Examination details exported: organizationId={}, agreementId={}, rows={}",
        organizationId,
        agreementId,
        workbook.participantCount());
    return new ExaminationDetailExportResponse(
        workbook.content(),
        ExportFileNames.of("chi-tiet-kham-", workbook.agreementCode(), ".xlsx"));
  }
}
