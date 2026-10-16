package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.command.AgreementConfiguration.ServicePrice;
import com.ngockhanh.clinic.healthexamination.application.response.AgreementDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository.AgreementDetails;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Steps shared by the agreement use cases: building new service lines from the catalog and mapping
 * stored details to the response with catalog names. It holds no state and starts no transaction.
 */
@Component
@RequiredArgsConstructor
public class AgreementSupport {
  private static final String CURRENCY = "VND";

  private final ServiceCatalogQuery catalog;

  /**
   * Builds new service lines for the requested services. Each line captures the current catalog
   * price as its reference price and takes the negotiated price of the request.
   *
   * @param agreementId agreement the lines belong to
   * @param requested catalog services with their negotiated price, in display order
   * @param firstOrder display order of the first new line
   * @throws IllegalArgumentException when the list is empty or an entry is incomplete
   * @throws DomainRuleViolation when a service is listed twice or is not available in the catalog
   */
  public List<HealthExaminationAgreementService> newLines(
      AggregateId agreementId, List<ServicePrice> requested, int firstOrder) {
    if (requested == null || requested.isEmpty())
      throw new IllegalArgumentException("At least one service is required");
    Set<UUID> seen = new HashSet<>();
    for (var item : requested) {
      if (item == null || item.serviceId() == null || item.negotiatedPrice() == null)
        throw new IllegalArgumentException("Service and negotiated price are required");
      if (!seen.add(item.serviceId())) throw new DomainRuleViolation("Duplicate agreement service");
    }
    Map<UUID, ServiceCatalogQuery.Service> catalogServices = new HashMap<>();
    for (var service : catalog.findByIds(seen)) catalogServices.put(service.id(), service);

    List<HealthExaminationAgreementService> lines = new ArrayList<>();
    int displayOrder = firstOrder;
    for (var item : requested) {
      var found = catalogServices.get(item.serviceId());
      if (found == null || !found.active())
        throw new DomainRuleViolation("Service is not available for an agreement");
      lines.add(
          new HealthExaminationAgreementService(
              new AggregateId(UuidV7Generator.generate()),
              new AggregateId(item.serviceId()),
              agreementId,
              new Money(found.unitPrice(), CURRENCY),
              new Money(item.negotiatedPrice(), CURRENCY),
              displayOrder++,
              true,
              0));
    }
    return lines;
  }

  /** Maps stored details to the response, with the catalog code and name of each service. */
  public AgreementDetailResponse toResponse(AgreementDetails details) {
    Set<UUID> serviceIds = new HashSet<>();
    details.agreement().services().forEach(service -> serviceIds.add(service.serviceId().value()));
    Map<UUID, ServiceCatalogQuery.Service> byId = new HashMap<>();
    if (!serviceIds.isEmpty())
      catalog.findByIds(serviceIds).forEach(service -> byId.put(service.id(), service));
    return AgreementDetailResponse.from(details, byId);
  }
}
