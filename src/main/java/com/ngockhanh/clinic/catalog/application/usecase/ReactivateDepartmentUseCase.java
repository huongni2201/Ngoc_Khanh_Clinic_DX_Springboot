package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.ReactivateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.response.DepartmentResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.application.service.CatalogAuditSnapshots;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.repository.DepartmentRepository;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reactivates catalog data for staff with the corresponding action permission. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactivateDepartmentUseCase {
  private final DepartmentRepository departmentRepository;
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
  public DepartmentResponse execute(ReactivateDepartmentCommand command, UserPrincipal principal) {
    accessPolicy.requireDepartmentReactivate(principal);
    if (command == null)
      throw new IllegalArgumentException("Reactivate department command is required");

    Department existing =
        departmentRepository
            .findByIdForUpdate(command.id())
            .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

    if (existing.rowVersion() != command.rowVersion()) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Department was modified by another request");
    }

    if (existing.active()) {
      log.debug("Department {} already active; no-op", existing.id());
      return DepartmentResponse.from(existing);
    }

    Map<String, Object> before = CatalogAuditSnapshots.of(existing);

    existing.reactivate();

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
        principal.userId(), "REACTIVATE_DEPARTMENT", "DEPARTMENT", existing.id(), before, after);

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "REACTIVATE_DEPARTMENT",
        existing.id(),
        existing.rowVersion());
    return new DepartmentResponse(
        existing.id(),
        existing.code().value(),
        existing.name(),
        existing.departmentType().name(),
        true,
        newVersion,
        existing.createdAt(),
        existing.updatedAt());
  }
}
