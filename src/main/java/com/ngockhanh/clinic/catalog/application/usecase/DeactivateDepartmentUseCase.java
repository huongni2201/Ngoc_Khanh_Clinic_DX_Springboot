package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.DeactivateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.application.service.CatalogAuditSnapshots;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.repository.DepartmentRepository;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deactivates catalog data for staff with the corresponding action permission. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateDepartmentUseCase {
  private final DepartmentRepository departmentRepository;
  private final CatalogAccessPolicy accessPolicy;
  private final AuditWriter audit;

  /**
   * Executes the authorized mutation and records audit in the same transaction.
   *
   * @param command requested mutation including expected version where applicable
   * @param principal authenticated staff principal with the action permission
   * @throws com.ngockhanh.clinic.shared.exception.ApplicationException if access is denied
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException if the expected version
   *     is stale
   */
  @Transactional
  public void execute(DeactivateDepartmentCommand command, UserPrincipal principal) {
    accessPolicy.requireDepartmentDeactivate(principal);
    if (command == null)
      throw new IllegalArgumentException("Deactivate department command is required");

    Department existing =
        departmentRepository
            .findByIdForUpdate(command.id())
            .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

    if (existing.rowVersion() != command.rowVersion()) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Department was modified by another request");
    }

    if (!existing.active()) {
      log.debug("Department {} already inactive; no-op", existing.id());
      return;
    }

    long activeRooms = departmentRepository.countActiveRooms(existing.id());
    long activeServices = departmentRepository.countActiveServices(existing.id());
    long totalDependents = activeRooms + activeServices;
    if (totalDependents > 0) {
      throw new ConflictException(
          "Cannot deactivate department with "
              + activeRooms
              + " active room(s) and "
              + activeServices
              + " active service(s)");
    }

    Map<String, Object> before = CatalogAuditSnapshots.of(existing);

    existing.deactivate();

    boolean updated = departmentRepository.update(existing, command.rowVersion());
    if (!updated) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Department was modified by another request");
    }

    existing =
        departmentRepository
            .findById(existing.id())
            .orElseThrow(() -> new IllegalStateException("Persisted catalog row not found"));
    long newVersion = existing.rowVersion();
    Map<String, Object> after = CatalogAuditSnapshots.of(existing);

    audit.record(
        principal.userId(), "DEACTIVATE_DEPARTMENT", "DEPARTMENT", existing.id(), before, after);

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "DEACTIVATE_DEPARTMENT",
        existing.id(),
        existing.rowVersion());
  }
}
