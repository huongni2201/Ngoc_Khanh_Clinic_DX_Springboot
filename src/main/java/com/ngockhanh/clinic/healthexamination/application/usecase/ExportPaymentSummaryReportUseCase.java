package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.port.out.PaymentReportDocumentWriter;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentReportDocument;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentReportDocumentResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ExportFileNames;
import com.ngockhanh.clinic.healthexamination.application.service.PaymentSummaryReportAssembler;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
 * Exports the payment summary of one health examination agreement to a Word document.
 *
 * <p>The caller must hold the report export permission, checked before the agreement is looked up.
 * The document is built from the same report object as the JSON report, so the figures never
 * differ, and holds no personal data of any Participant. It is a working summary, not an issued
 * official document: it is not stored and never versioned. The read and the audit event {@code
 * EXPORT_PAYMENT_REPORT} share one repeatable-read transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportPaymentSummaryReportUseCase {
  private final ExaminationDetailAccessPolicy access;
  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final ExaminationDetailReader reader;
  private final ServiceCatalogQuery catalog;
  private final PaymentSummaryReportAssembler assembler;
  private final PaymentReportDocumentWriter writer;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Returns the document of the agreement.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param from first session date of the range, or null for no lower bound
   * @param to last session date of the range, or null for no upper bound
   * @param principal authenticated staff account
   * @return the DOCX bytes and the download name
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the report export permission
   * @throws IllegalArgumentException when an identifier is null or the range is reversed
   * @throws ResourceNotFoundException when the organization or the agreement is not found
   */
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public PaymentReportDocumentResponse execute(
      UUID organizationId,
      UUID agreementId,
      LocalDate from,
      LocalDate to,
      UserPrincipal principal) {
    access.requireReportExport(principal);
    if (organizationId == null || agreementId == null)
      throw new IllegalArgumentException("Organization ID and agreement ID are required");
    if (from != null && to != null && from.isAfter(to))
      throw new IllegalArgumentException("The report range starts after it ends");
    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var agreement =
        agreements
            .findDetails(organizationId, agreementId, false)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"))
            .agreement();
    var aggregates =
        reader
            .readPaymentAggregates(organizationId, agreementId, from, to)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"));
    Set<UUID> catalogIds = new HashSet<>();
    agreement.services().forEach(service -> catalogIds.add(service.serviceId().value()));
    Map<UUID, ServiceCatalogQuery.Service> names = new HashMap<>();
    if (!catalogIds.isEmpty())
      catalog.findByIds(catalogIds).forEach(service -> names.put(service.id(), service));

    var report =
        assembler.assemble(
            agreement,
            aggregates,
            names,
            from,
            to,
            Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));
    byte[] content =
        writer.write(
            new PaymentReportDocument(
                report,
                organization.name(),
                organization.taxCode(),
                organization.address(),
                agreement.validFrom(),
                agreement.validTo()));
    audit.record(
        principal.userId(),
        "EXPORT_PAYMENT_REPORT",
        "HEALTH_EXAMINATION_AGREEMENT",
        agreementId,
        null,
        Map.of(
            "organizationId",
            organizationId,
            "agreementId",
            agreementId,
            "provisional",
            report.provisional(),
            "totalAmount",
            report.totalAmount().toPlainString()));
    log.info(
        "Payment summary exported: organizationId={}, agreementId={}, items={}",
        organizationId,
        agreementId,
        report.items().size());
    return new PaymentReportDocumentResponse(
        content, ExportFileNames.of("bao-cao-thanh-toan-", agreement.code(), ".docx"));
  }
}
