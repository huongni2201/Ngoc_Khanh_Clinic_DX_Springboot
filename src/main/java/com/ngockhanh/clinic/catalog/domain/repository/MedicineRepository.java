package com.ngockhanh.clinic.catalog.domain.repository;

import com.ngockhanh.clinic.catalog.domain.aggregate.Medicine;
import java.util.Optional;
import java.util.UUID;

/** Write side (command) repository for Medicine aggregate. */
public interface MedicineRepository {
  void save(Medicine medicine);

  boolean update(Medicine medicine, long expectedVersion);

  Optional<Medicine> findByIdForUpdate(UUID id);

  Optional<Medicine> findById(UUID id);
}
