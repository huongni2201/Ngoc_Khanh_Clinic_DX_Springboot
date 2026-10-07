package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentSummaryReportResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.PaymentSummaryReportAssembler;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
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
 * Reads the payment summary of one health examination batch: per batch service, how many active
 * Participants were performed and the amount at the price snapshot.
 *
 * <p>The caller must hold the report read permission, checked before the batch is looked up. The
 * report is provisional while the batch is not FINALIZED. The batch, the counters and the
 * aggregates are read in one repeatable-read transaction so the figures agree with each other.
 * Nothing is written or audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetPaymentSummaryReportUseCase {
  private final ExaminationDetailAccessPolicy access;
  private final HealthExaminationBatchRepository batches;
  private final ExaminationDetailReader reader;
  private final ServiceCatalogQuery catalog;
  private final PaymentSummaryReportAssembler assembler;
  private final Clock clock;

  /**
   * Returns the report of the batch.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param principal authenticated staff account
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the report read permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the batch is not found in the organization
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PaymentSummaryReportResponse execute(
      UUID organizationId, UUID batchId, UserPrincipal principal) {
    access.requireReportRead(principal);
    if (organizationId == null || batchId == null)
      throw new IllegalArgumentException("Organization ID and batch ID are required");
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
    log.debug(
        "Payment summary read: organizationId={}, batchId={}, items={}",
        organizationId,
        batchId,
        report.items().size());
    return report;
  }
}
