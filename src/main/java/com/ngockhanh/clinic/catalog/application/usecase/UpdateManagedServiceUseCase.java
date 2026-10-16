package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.UpdateServiceCommand;
import com.ngockhanh.clinic.catalog.application.response.ManagedServiceResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.application.service.CatalogAuditSnapshots;
import com.ngockhanh.clinic.catalog.domain.aggregate.CatalogService;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.aggregate.Specialty;
import com.ngockhanh.clinic.catalog.domain.enums.ServiceType;
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

/** Updates catalog data for staff with the corresponding action permission. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateManagedServiceUseCase {
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
  public ManagedServiceResponse execute(UpdateServiceCommand command, UserPrincipal principal) {
    accessPolicy.requireServiceUpdate(principal);
    if (command == null) throw new IllegalArgumentException("Update service command is required");

    ServiceType type;
    try {
      type = ServiceType.valueOf(command.serviceType().trim().toUpperCase(java.util.Locale.ROOT));
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid service type: " + command.serviceType());
    }

    var locked =
        com.ngockhanh.clinic.catalog.application.service.CatalogServiceLocks.acquire(
            serviceRepository,
            departmentRepository,
            specialtyRepository,
            command.id(),
            command.rowVersion(),
            command.specialtyId(),
            true);
    CatalogService existing = locked.service();
    Department dept = locked.department();
    Specialty specialty = locked.specialty();
    boolean keepingInactiveLink =
        !existing.active()
            && java.util.Objects.equals(existing.specialtyId(), command.specialtyId());
    if (specialty != null && !specialty.active() && !keepingInactiveLink) {
      throw new ConflictException("Cannot assign inactive specialty to service");
    }
    if (existing.active() && !dept.active()) {
      throw new ConflictException("Cannot update active service in an inactive department");
    }

    Map<String, Object> before = CatalogAuditSnapshots.of(existing);

    existing.updateDetails(
        command.name(),
        type,
        command.specialtyId(),
        command.unitPrice(),
        command.preparationInstruction());

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

    audit.record(principal.userId(), "UPDATE_SERVICE", "SERVICE", existing.id(), before, after);

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "UPDATE_SERVICE",
        existing.id(),
        existing.rowVersion());
    return new ManagedServiceResponse(
        existing.id(),
        existing.code().value(),
        existing.name(),
        existing.serviceType().name(),
        existing.performingDepartmentId(),
        dept.name(),
        existing.specialtyId(),
        specialty != null ? specialty.name() : null,
        existing.unitPrice(),
        existing.preparationInstruction(),
        existing.active(),
        newVersion,
        existing.createdAt(),
        existing.updatedAt());
  }
}
