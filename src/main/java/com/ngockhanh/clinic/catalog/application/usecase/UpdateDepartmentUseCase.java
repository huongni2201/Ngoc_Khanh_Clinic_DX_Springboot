package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.UpdateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.response.DepartmentResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.application.service.CatalogAuditSnapshots;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.enums.DepartmentType;
import com.ngockhanh.clinic.catalog.domain.repository.DepartmentRepository;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Updates catalog data for staff with the corresponding action permission. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateDepartmentUseCase {
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
  public DepartmentResponse execute(UpdateDepartmentCommand command, UserPrincipal principal) {
    accessPolicy.requireDepartmentUpdate(principal);
    if (command == null)
      throw new IllegalArgumentException("Update department command is required");

    DepartmentType newType;
    try {
      newType =
          DepartmentType.valueOf(
              command.departmentType().trim().toUpperCase(java.util.Locale.ROOT));
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid department type: " + command.departmentType());
    }

    Department existing =
        departmentRepository
            .findByIdForUpdate(command.id())
            .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

    if (existing.rowVersion() != command.rowVersion()) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Department was modified by another request");
    }

    Map<String, Object> before = CatalogAuditSnapshots.of(existing);

    existing.updateDetails(command.name(), newType);

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
        principal.userId(), "UPDATE_DEPARTMENT", "DEPARTMENT", existing.id(), before, after);

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "UPDATE_DEPARTMENT",
        existing.id(),
        existing.rowVersion());
    return new DepartmentResponse(
        existing.id(),
        existing.code().value(),
        existing.name(),
        existing.departmentType().name(),
        existing.active(),
        newVersion,
        existing.createdAt(),
        existing.updatedAt());
  }
}
