package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.port.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.port.PaymentReportDocumentWriter;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentReportDocument;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentReportDocumentResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ExportFileNames;
import com.ngockhanh.clinic.healthexamination.application.service.PaymentSummaryReportAssembler;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports the payment summary of one health examination batch to a Word document.
 *
 * <p>The caller must hold the report read permission, checked before the batch is looked up. The
 * document is built from the same report object as the JSON report, so the figures never differ,
 * and holds no personal data of any Participant. It is a working summary, not an issued official
 * document: it is not stored and never versioned. The read and the audit event {@code
 * EXPORT_PAYMENT_REPORT} share one repeatable-read transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportPaymentSummaryReportUseCase {
  private final ExaminationDetailAccessPolicy access;
  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final ExaminationDetailReader reader;
  private final ServiceCatalogQuery catalog;
  private final PaymentSummaryReportAssembler assembler;
  private final PaymentReportDocumentWriter writer;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Returns the document of the batch.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param principal authenticated staff account
   * @return the DOCX bytes and the download name
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the report read permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the organization or the batch is not found
   */
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public PaymentReportDocumentResponse execute(
      UUID organizationId, UUID batchId, UserPrincipal principal) {
    access.requireReportRead(principal);
    if (organizationId == null || batchId == null)
      throw new IllegalArgumentException("Organization ID and batch ID are required");
    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var batch =
        batches
            .findDetails(organizationId, batchId, false)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"))
            .batch();
    var aggregates =
        reader
            .readPaymentAggregates(organizationId, batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    Set<UUID> catalogIds = new HashSet<>();
    batch.services().forEach(service -> catalogIds.add(service.serviceId().value()));
    Map<UUID, ServiceCatalogQuery.Service> names = new HashMap<>();
    if (!catalogIds.isEmpty())
      catalog.findByIds(catalogIds).forEach(service -> names.put(service.id(), service));

    var report =
        assembler.assemble(
            batch, aggregates, names, Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));
    byte[] content =
        writer.write(
            new PaymentReportDocument(
                report,
                organization.name(),
                organization.taxCode(),
                organization.address(),
                batch.startDate(),
                batch.endDate(),
                batch.site().name(),
                batch.site().address()));
    audit.record(
        principal.userId(),
        "EXPORT_PAYMENT_REPORT",
        "HEALTH_EXAMINATION_BATCH",
        batchId,
        null,
        Map.of(
            "organizationId", organizationId,
            "batchId", batchId,
            "provisional", report.provisional(),
            "totalAmount", report.totalAmount().toPlainString()));
    log.debug(
        "Payment summary exported: organizationId={}, batchId={}, items={}",
        organizationId,
        batchId,
        report.items().size());
    return new PaymentReportDocumentResponse(
        content, ExportFileNames.of("bao-cao-thanh-toan-", batch.code(), ".docx"));
  }
}
