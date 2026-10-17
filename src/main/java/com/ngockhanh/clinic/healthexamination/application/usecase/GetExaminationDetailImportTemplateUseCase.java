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
 * Downloads the template of the examination detail import.
 *
 * <p>The template is the same workbook as the export: the import is an overwrite by file, so a
 * blank cell means "not examined" and an empty template would erase what is already recorded. The
 * file is therefore pre-filled with the current roster and the services already recorded, and
 * carries the hidden participant identifiers and row versions the import matches by. It has its own
 * permission (ADR-0015) so a role that may import can also fetch the file to fill in, and its own
 * audit event because the file holds Participant names. It is not restricted by agreement status,
 * like the export; the screen hides the button when the agreement no longer accepts imports.
 */
@Slf4j
@Service
public class GetExaminationDetailImportTemplateUseCase {
  private static final String FILE_NAME_PREFIX = "mau-nhap-chi-tiet-kham-";

  private final ExaminationDetailAccessPolicy access;
  private final ExaminationDetailWorkbookAssembler assembler;
  private final AuditWriter audit;

  public GetExaminationDetailImportTemplateUseCase(
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
   * Returns the template of the agreement.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param principal authenticated staff account
   * @return the XLSX bytes and the download name
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the template download
   *     permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the organization or the agreement is not found
   */
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public ExaminationDetailExportResponse execute(
      UUID organizationId, UUID agreementId, UserPrincipal principal) {
    access.requireServiceTemplateDownload(principal);
    var workbook = assembler.assemble(organizationId, agreementId);
    audit.record(
        principal.userId(),
        "DOWNLOAD_EXAMINATION_DETAIL_TEMPLATE",
        "HEALTH_EXAMINATION_AGREEMENT",
        agreementId,
        null,
        Map.of(
            "organizationId", organizationId,
            "agreementId", agreementId,
            "rowCount", workbook.participantCount()));
    log.info(
        "Examination detail template downloaded: organizationId={}, agreementId={}, rows={}",
        organizationId,
        agreementId,
        workbook.participantCount());
    return new ExaminationDetailExportResponse(
        workbook.content(),
        ExportFileNames.of(FILE_NAME_PREFIX, workbook.agreementCode(), ".xlsx"));
  }
}
