package com.ngockhanh.clinic.catalog.application;

import java.util.*;

public interface ServiceCatalogQuery {
  List<Service> findByIds(Set<UUID> ids);

  record Service(
      UUID id, String code, String name, boolean active, boolean healthExaminationEligible) {}
}
