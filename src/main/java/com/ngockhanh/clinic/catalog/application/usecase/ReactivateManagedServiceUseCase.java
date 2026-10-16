package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.ReactivateServiceCommand;
import com.ngockhanh.clinic.catalog.application.response.ManagedServiceResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.application.service.CatalogAuditSnapshots;
import com.ngockhanh.clinic.catalog.domain.aggregate.CatalogService;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.aggregate.Specialty;
import com.ngockhanh.clinic.catalog.domain.repository.CatalogServiceRepository;
import com.ngockhanh.clinic.catalog.domain.repository.DepartmentRepository;
import com.ngockhanh.clinic.catalog.domain.repository.SpecialtyRepository;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reactivates catalog data for staff with the corresponding action permission. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactivateManagedServiceUseCase {
  private final CatalogServiceRepository serviceRepository;
  private final DepartmentRepository departmentRepository;
  private final SpecialtyRepository specialtyRepository;
  private final CatalogAccessPolicy accessPolicy;
  private final AuditWriter audit;

  /**
   * Executes the authorized mutation and records audit in the same transaction.
   *
   * @param command requested mutation including expected version where applicable
   * @param principal authenticated staff principal with the action permission
   * @return persisted catalog response or current state on a no-op
   * @throws com.ngockhanh.clinic.shared.exception.ApplicationException if access is denied
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException if the expected version
   *     is stale
   */
  @Transactional
  public ManagedServiceResponse execute(ReactivateServiceCommand command, UserPrincipal principal) {
    accessPolicy.requireServiceReactivate(principal);
    if (command == null)
      throw new IllegalArgumentException("Reactivate service command is required");

    var locked =
        com.ngockhanh.clinic.catalog.application.service.CatalogServiceLocks.acquire(
            serviceRepository,
            departmentRepository,
            specialtyRepository,
            command.id(),
            command.rowVersion(),
            null,
            false);
    CatalogService existing = locked.service();
    Department department = locked.department();
    Specialty specialty = locked.specialty();
    if (!department.active()) {
      throw new ConflictException(
          "Cannot reactivate service because its performing department is inactive");
    }
    if (specialty != null && !specialty.active()) {
      throw new ConflictException("Cannot reactivate service because its specialty is inactive");
    }

    if (existing.active()) {
      log.debug("Service {} already active; no-op", existing.id());
      return new ManagedServiceResponse(
          existing.id(),
          existing.code().value(),
          existing.name(),
          existing.serviceType().name(),
          existing.performingDepartmentId(),
          department.name(),
          existing.specialtyId(),
          specialty != null ? specialty.name() : null,
          existing.unitPrice(),
          existing.preparationInstruction(),
          true,
          existing.rowVersion(),
          existing.createdAt(),
          existing.updatedAt());
    }

    Map<String, Object> before = CatalogAuditSnapshots.of(existing);

    existing.reactivate();

    boolean updated = serviceRepository.update(existing, command.rowVersion());
    if (!updated) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Service was modified by another request");
    }

    existing =
        serviceRepository
            .findById(existing.id())
            .orElseThrow(() -> new IllegalStateException("Persisted catalog row not found"));
    long newVersion = existing.rowVersion();
    Map<String, Object> after = CatalogAuditSnapshots.of(existing);

    audit.record(principal.userId(), "REACTIVATE_SERVICE", "SERVICE", existing.id(), before, after);

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "REACTIVATE_SERVICE",
        existing.id(),
        existing.rowVersion());
    return new ManagedServiceResponse(
        existing.id(),
        existing.code().value(),
        existing.name(),
        existing.serviceType().name(),
        existing.performingDepartmentId(),
        department.name(),
        existing.specialtyId(),
        specialty != null ? specialty.name() : null,
        existing.unitPrice(),
        existing.preparationInstruction(),
        true,
        newVersion,
        existing.createdAt(),
        existing.updatedAt());
  }
}
