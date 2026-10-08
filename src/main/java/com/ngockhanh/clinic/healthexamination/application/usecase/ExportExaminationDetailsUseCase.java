package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailExcelWriter;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailExportData;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationServiceColumn;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailExportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ExportFileNames;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports the examination detail matrix of one health examination batch to an Excel workbook that
 * is also the template of the import.
 *
 * <p>The caller must hold the service read permission, checked before the batch is looked up. The
 * export always holds every active Participant of the batch, whatever filter the screen shows, so
 * the file can be imported again as it is. The identification number is masked before it reaches
 * the writer, and no phone, email or attendance note is exported. The batch, its services, the
 * Participants and the audit event {@code EXPORT_EXAMINATION_DETAILS} (the file holds Participant
 * names) are handled in one repeatable-read transaction, so the workbook is consistent and the
 * export is not served without its audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportExaminationDetailsUseCase {
  private final ExaminationDetailAccessPolicy access;
  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final ExaminationDetailReader reader;
  private final ServiceCatalogQuery catalog;
  private final ExaminationDetailExcelWriter writer;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Returns the workbook of the batch.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param principal authenticated staff account
   * @return the XLSX bytes and the download name
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the service read permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the organization or the batch is not found
   */
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public ExaminationDetailExportResponse execute(
      UUID organizationId, UUID batchId, UserPrincipal principal) {
    access.requireServiceExport(principal);
    if (organizationId == null || batchId == null)
      throw new IllegalArgumentException("Organization ID and batch ID are required");
    organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var batch =
        batches
            .findDetails(organizationId, batchId, false)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"))
            .batch();
    List<ExaminationDetailRow> rows =
        reader
            .readAllActive(organizationId, batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));

    Set<UUID> catalogIds = new HashSet<>();
    for (HealthExaminationBatchService service : batch.services())
      catalogIds.add(service.serviceId().value());
    Map<UUID, ServiceCatalogQuery.Service> names = new HashMap<>();
    if (!catalogIds.isEmpty())
      catalog.findByIds(catalogIds).forEach(service -> names.put(service.id(), service));
    List<ExaminationServiceColumn> columns = new ArrayList<>();
    for (HealthExaminationBatchService service : batch.services()) {
      var known = names.get(service.serviceId().value());
      columns.add(
          new ExaminationServiceColumn(
              service.id().value(), known == null ? "" : known.name()));
    }

    List<ExaminationDetailRow> masked = new ArrayList<>(rows.size());
    for (ExaminationDetailRow row : rows)
      masked.add(
          new ExaminationDetailRow(
              row.id(),
              row.participantCode(),
              row.fullName(),
              row.dateOfBirth(),
              row.sex(),
              ParticipantSummaryResponse.mask(row.identificationNumber()),
              row.departmentName(),
              row.positionName(),
              row.examinationDate(),
              row.attendanceStatus(),
              row.actualExaminationDate(),
              row.reconciliationStatus(),
              row.performedBatchServiceIds(),
              row.rowVersion()));

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    byte[] content =
        writer.write(
            new ExaminationDetailExportData(batchId, batch.code(), now, columns, masked));
    audit.record(
        principal.userId(),
        "EXPORT_EXAMINATION_DETAILS",
        "HEALTH_EXAMINATION_BATCH",
        batchId,
        null,
        Map.of(
            "organizationId", organizationId,
            "batchId", batchId,
            "participantCount", rows.size()));
    log.debug(
        "Examination details exported: organizationId={}, batchId={}, rows={}",
        organizationId,
        batchId,
        rows.size());
    return new ExaminationDetailExportResponse(
        content, ExportFileNames.of("chi-tiet-kham-", batch.code(), ".xlsx"));
  }
}
