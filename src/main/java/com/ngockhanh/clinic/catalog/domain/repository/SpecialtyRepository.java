package com.ngockhanh.clinic.catalog.domain.repository;

import com.ngockhanh.clinic.catalog.domain.aggregate.Specialty;
import java.util.Optional;
import java.util.UUID;

/** Write side (command) repository for Specialty aggregate. */
public interface SpecialtyRepository {
  void save(Specialty specialty);

  boolean update(Specialty specialty, long expectedVersion);

  Optional<Specialty> findByIdForUpdate(UUID id);

  Optional<Specialty> findById(UUID id);

  /** Returns the number of active services linked to this specialty. */
  long countActiveServices(UUID specialtyId);
}
