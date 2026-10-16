package com.ngockhanh.clinic.catalog.application.service;

import com.ngockhanh.clinic.catalog.domain.aggregate.CatalogService;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.aggregate.Specialty;
import com.ngockhanh.clinic.catalog.domain.repository.CatalogServiceRepository;
import com.ngockhanh.clinic.catalog.domain.repository.DepartmentRepository;
import com.ngockhanh.clinic.catalog.domain.repository.SpecialtyRepository;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/** Acquires catalog parent locks before the service lock in the caller's write transaction. */
public final class CatalogServiceLocks {
  private CatalogServiceLocks() {}

  public record Locked(CatalogService service, Department department, Specialty specialty) {}

  /**
   * Locks department, old/new specialties in UUID order, then service, and revalidates the probe.
   *
   * @param services service repository participating in the caller's transaction
   * @param departments department repository
   * @param specialties specialty repository
   * @param id service identifier
   * @param version expected version
   * @param nextSpecialtyId requested specialty for update; ignored on reactivation
   * @param changingSpecialty whether the caller is replacing the specialty link
   * @return locked aggregate and parents for the requested relationship
   * @throws ConcurrentUpdateException if the probe/version changed before all locks were obtained
   * @throws ResourceNotFoundException if a referenced row is missing
   */
  public static Locked acquire(
      CatalogServiceRepository services,
      DepartmentRepository departments,
      SpecialtyRepository specialties,
      UUID id,
      long version,
      UUID nextSpecialtyId,
      boolean changingSpecialty) {
    if (id == null || version < 0)
      throw new IllegalArgumentException("Invalid service version or ID");
    CatalogService probe =
        services.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service not found"));
    checkVersion(probe, version);
    Department department =
        departments
            .findByIdForUpdate(probe.performingDepartmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Performing department not found"));
    UUID requested = changingSpecialty ? nextSpecialtyId : probe.specialtyId();
    var ids = new TreeSet<UUID>();
    if (probe.specialtyId() != null) ids.add(probe.specialtyId());
    if (requested != null) ids.add(requested);
    var parents = new TreeMap<UUID, Specialty>();
    for (UUID parent : ids) {
      parents.put(
          parent,
          specialties
              .findByIdForUpdate(parent)
              .orElseThrow(() -> new ResourceNotFoundException("Specialty not found")));
    }
    CatalogService current =
        services
            .findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
    checkVersion(current, version);
    // The initial read only determines which parents to lock; it never authorizes a stale link.
    if (!Objects.equals(current.specialtyId(), probe.specialtyId())
        || !current.performingDepartmentId().equals(probe.performingDepartmentId())) {
      throw new ConcurrentUpdateException("CONCURRENT_UPDATE", "Service relationships changed");
    }
    return new Locked(current, department, requested == null ? null : parents.get(requested));
  }

  private static void checkVersion(CatalogService service, long version) {
    if (service.rowVersion() != version) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Service was modified by another request");
    }
  }
}
