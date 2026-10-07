package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDetails;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
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
 * Reads one health examination batch of an organization.
 *
 * <p>The organization must exist but may be inactive. A batch of another organization or a deleted
 * batch is reported as not found. The header, days and services are read in one repeatable-read
 * transaction so the response is consistent. The read changes nothing and records no audit event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetHealthExaminationBatchByIdUseCase {
  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final ServiceCatalogQuery catalog;

  /**
   * Returns the batch with its days and services.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @return the stored batch with its days, services and catalog display names
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the organization or the batch does not exist
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public BatchDetailResponse execute(UUID organizationId, UUID batchId) {
    if (organizationId == null || batchId == null)
      throw new IllegalArgumentException("Organization ID and batch ID are required");
    organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var details =
        batches
            .findDetails(organizationId, batchId, false)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    log.debug(
        "Health examination batch retrieved: organizationId={}, batchId={}",
        organizationId,
        batchId);
    return toResponse(details);
  }

  private BatchDetailResponse toResponse(BatchDetails details) {
    Set<UUID> serviceIds = new HashSet<>();
    details.batch().services().forEach(service -> serviceIds.add(service.serviceId().value()));
    Map<UUID, ServiceCatalogQuery.Service> byId = new HashMap<>();
    if (!serviceIds.isEmpty())
      catalog.findByIds(serviceIds).forEach(service -> byId.put(service.id(), service));
    return BatchDetailResponse.from(details, byId);
  }
}
