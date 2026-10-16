package com.ngockhanh.clinic.catalog.domain.repository;

import com.ngockhanh.clinic.catalog.domain.aggregate.CatalogService;
import java.util.Optional;
import java.util.UUID;

/** Write side (command) repository for CatalogService aggregate. */
public interface CatalogServiceRepository {
  void save(CatalogService service);

  boolean update(CatalogService service, long expectedVersion);

  Optional<CatalogService> findByIdForUpdate(UUID id);

  Optional<CatalogService> findById(UUID id);
}
