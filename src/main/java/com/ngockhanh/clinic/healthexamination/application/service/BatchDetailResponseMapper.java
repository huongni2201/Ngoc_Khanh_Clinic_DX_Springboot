package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchDetails;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps stored batch details to {@link BatchDetailResponse}, adding the code and name of each
 * service from the catalog. Names are read-only display data; the stored price snapshots are never
 * taken from the catalog. The mapper has no transaction and writes no audit.
 */
@Component
@RequiredArgsConstructor
public class BatchDetailResponseMapper {
  private final ServiceCatalogQuery catalog;

  public BatchDetailResponse toResponse(BatchDetails details) {
    Set<UUID> serviceIds = new HashSet<>();
    details.batch().services().forEach(service -> serviceIds.add(service.serviceId().value()));
    Map<UUID, ServiceCatalogQuery.Service> byId = new HashMap<>();
    if (!serviceIds.isEmpty())
      catalog.findByIds(serviceIds).forEach(service -> byId.put(service.id(), service));
    return BatchDetailResponse.from(details, byId);
  }
}
