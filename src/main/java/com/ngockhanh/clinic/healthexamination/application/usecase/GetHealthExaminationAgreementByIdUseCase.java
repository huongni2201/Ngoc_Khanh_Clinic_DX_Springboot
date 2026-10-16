package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.response.AgreementDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository.AgreementDetails;
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
 * Reads one health examination agreement of an organization.
 *
 * <p>The organization must exist but may be inactive. An agreement of another organization or a
 * deleted agreement is reported as not found. The header, services are read in one repeatable-read
 * transaction so the response is consistent. The read changes nothing and records no audit event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetHealthExaminationAgreementByIdUseCase {
  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final ServiceCatalogQuery catalog;

  /**
   * Returns the agreement with its services.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @return the stored agreement with its days, services and catalog display names
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the organization or the agreement does not exist
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AgreementDetailResponse execute(UUID organizationId, UUID agreementId) {
    if (organizationId == null || agreementId == null)
      throw new IllegalArgumentException("Organization ID and agreement ID are required");
    organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var details =
        agreements
            .findDetails(organizationId, agreementId, false)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"));
    log.info(
        "Health examination agreement retrieved: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    return toResponse(details);
  }

  private AgreementDetailResponse toResponse(AgreementDetails details) {
    Set<UUID> serviceIds = new HashSet<>();
    details.agreement().services().forEach(service -> serviceIds.add(service.serviceId().value()));
    Map<UUID, ServiceCatalogQuery.Service> byId = new HashMap<>();
    if (!serviceIds.isEmpty())
      catalog.findByIds(serviceIds).forEach(service -> byId.put(service.id(), service));
    return AgreementDetailResponse.from(details, byId);
  }
}
