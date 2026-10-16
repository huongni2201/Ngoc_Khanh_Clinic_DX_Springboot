package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.DeactivateSpecialtyCommand;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.application.service.CatalogAuditSnapshots;
import com.ngockhanh.clinic.catalog.domain.aggregate.Specialty;
import com.ngockhanh.clinic.catalog.domain.repository.SpecialtyRepository;
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
public class DeactivateSpecialtyUseCase {
  private final SpecialtyRepository specialtyRepository;
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
  public void execute(DeactivateSpecialtyCommand command, UserPrincipal principal) {
    accessPolicy.requireSpecialtyDeactivate(principal);
    if (command == null)
      throw new IllegalArgumentException("Deactivate specialty command is required");

    Specialty existing =
        specialtyRepository
            .findByIdForUpdate(command.id())
            .orElseThrow(() -> new ResourceNotFoundException("Specialty not found"));

    if (existing.rowVersion() != command.rowVersion()) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Specialty was modified by another request");
    }

    if (!existing.active()) {
      log.debug("Specialty {} already inactive; no-op", existing.id());
      return;
    }

    long activeServices = specialtyRepository.countActiveServices(existing.id());
    if (activeServices > 0) {
      throw new ConflictException(
          "Cannot deactivate specialty with " + activeServices + " active service(s)");
    }

    Map<String, Object> before = CatalogAuditSnapshots.of(existing);

    existing.deactivate();

    boolean updated = specialtyRepository.update(existing, command.rowVersion());
    if (!updated) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Specialty was modified by another request");
    }

    existing =
        specialtyRepository
            .findById(existing.id())
            .orElseThrow(() -> new IllegalStateException("Persisted catalog row not found"));
    long newVersion = existing.rowVersion();
    Map<String, Object> after = CatalogAuditSnapshots.of(existing);

    audit.record(
        principal.userId(), "DEACTIVATE_SPECIALTY", "SPECIALTY", existing.id(), before, after);

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "DEACTIVATE_SPECIALTY",
        existing.id(),
        existing.rowVersion());
  }
}
