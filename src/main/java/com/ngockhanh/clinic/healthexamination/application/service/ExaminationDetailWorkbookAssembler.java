package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailExcelWriter;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailExportData;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationServiceColumn;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantSummaryResponse;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
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

/**
 * Builds the examination detail workbook of one agreement. The same workbook is the export and the
 * template of the import, so the export use case and the template download use case share this
 * class and cannot drift apart.
 *
 * <p>The workbook always holds every active Participant of the agreement, whatever filter the
 * screen shows. The identification number is masked before it reaches the writer, and no phone or
 * email is included. It is not a Spring bean: the calling use case owns the transaction and the
 * authorization, and passes its own collaborators.
 */
public final class ExaminationDetailWorkbookAssembler {
  /** The generated workbook, with the facts the caller needs for its file name and audit event. */
  public record Assembled(String agreementCode, byte[] content, int participantCount) {}

  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final ExaminationDetailReader reader;
  private final ServiceCatalogQuery catalog;
  private final ExaminationDetailExcelWriter writer;
  private final Clock clock;

  public ExaminationDetailWorkbookAssembler(
      OrganizationRepository organizations,
      HealthExaminationAgreementRepository agreements,
      ExaminationDetailReader reader,
      ServiceCatalogQuery catalog,
      ExaminationDetailExcelWriter writer,
      Clock clock) {
    this.organizations = organizations;
    this.agreements = agreements;
    this.reader = reader;
    this.catalog = catalog;
    this.writer = writer;
    this.clock = clock;
  }

  /**
   * Renders the workbook.
   *
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the organization or the agreement is not found
   */
  public Assembled assemble(UUID organizationId, UUID agreementId) {
    if (organizationId == null || agreementId == null)
      throw new IllegalArgumentException("Organization ID and agreement ID are required");
    organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var agreement =
        agreements
            .findDetails(organizationId, agreementId, false)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"))
            .agreement();
    List<ExaminationDetailRow> rows =
        reader
            .readAllActive(organizationId, agreementId)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"));

    Set<UUID> catalogIds = new HashSet<>();
    for (HealthExaminationAgreementService service : agreement.services())
      catalogIds.add(service.serviceId().value());
    Map<UUID, ServiceCatalogQuery.Service> names = new HashMap<>();
    if (!catalogIds.isEmpty())
      catalog.findByIds(catalogIds).forEach(service -> names.put(service.id(), service));
    List<ExaminationServiceColumn> columns = new ArrayList<>();
    for (HealthExaminationAgreementService service : agreement.services()) {
      var known = names.get(service.serviceId().value());
      columns.add(
          new ExaminationServiceColumn(service.id().value(), known == null ? "" : known.name()));
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
              row.nextSessionDate(),
              row.attendanceStatus(),
              row.lastAttendedDate(),
              row.reconciliationStatus(),
              row.performedAgreementServiceIds(),
              row.rowVersion()));

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    byte[] content =
        writer.write(
            new ExaminationDetailExportData(agreementId, agreement.code(), now, columns, masked));
    return new Assembled(agreement.code(), content, rows.size());
  }
}
