package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentSummaryReportResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.PaymentSummaryReportAssembler;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
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
 * Reads the payment summary of one health examination agreement: per agreement service, how many
 * active Participants were performed and the amount at the price snapshot.
 *
 * <p>The caller must hold the report read permission, checked before the agreement is looked up.
 * The report is provisional until the agreement has ended. An optional session date range limits
 * the scope to the Participants expected or attended in a session of that range; the report states
 * the range and the time it was generated. The agreement, the counters and the aggregates are read
 * in one repeatable-read transaction so the figures agree with each other. Nothing is written or
 * audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetPaymentSummaryReportUseCase {
  private final ExaminationDetailAccessPolicy access;
  private final HealthExaminationAgreementRepository agreements;
  private final ExaminationDetailReader reader;
  private final ServiceCatalogQuery catalog;
  private final PaymentSummaryReportAssembler assembler;
  private final Clock clock;

  /**
   * Returns the report of the agreement.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param from first session date of the range, or null for no lower bound
   * @param to last session date of the range, or null for no upper bound
   * @param principal authenticated staff account
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the report read permission
   * @throws IllegalArgumentException when an identifier is null or the range is reversed
   * @throws ResourceNotFoundException when the agreement is not found in the organization
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PaymentSummaryReportResponse execute(
      UUID organizationId,
      UUID agreementId,
      LocalDate from,
      LocalDate to,
      UserPrincipal principal) {
    access.requireReportRead(principal);
    if (organizationId == null || agreementId == null)
      throw new IllegalArgumentException("Organization ID and agreement ID are required");
    if (from != null && to != null && from.isAfter(to))
      throw new IllegalArgumentException("The report range starts after it ends");
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
    log.info(
        "Payment summary read: organizationId={}, agreementId={}, items={}",
        organizationId,
        agreementId,
        report.items().size());
    return report;
  }
}
