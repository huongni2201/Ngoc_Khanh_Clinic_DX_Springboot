package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.DeactivateMedicineCommand;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.application.service.CatalogAuditSnapshots;
import com.ngockhanh.clinic.catalog.domain.aggregate.Medicine;
import com.ngockhanh.clinic.catalog.domain.repository.MedicineRepository;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
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
public class DeactivateMedicineUseCase {
  private final MedicineRepository medicineRepository;
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
  public void execute(DeactivateMedicineCommand command, UserPrincipal principal) {
    accessPolicy.requireMedicineDeactivate(principal);
    if (command == null)
      throw new IllegalArgumentException("Deactivate medicine command is required");

    Medicine existing =
        medicineRepository
            .findByIdForUpdate(command.id())
            .orElseThrow(() -> new ResourceNotFoundException("Medicine not found"));

    if (existing.rowVersion() != command.rowVersion()) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Medicine was modified by another request");
    }

    if (!existing.active()) {
      log.debug("Medicine {} already inactive; no-op", existing.id());
      return;
    }

    Map<String, Object> before = CatalogAuditSnapshots.of(existing);

    existing.deactivate();

    boolean updated = medicineRepository.update(existing, command.rowVersion());
    if (!updated) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Medicine was modified by another request");
    }

    existing =
        medicineRepository
            .findById(existing.id())
            .orElseThrow(() -> new IllegalStateException("Persisted catalog row not found"));
    long newVersion = existing.rowVersion();
    Map<String, Object> after = CatalogAuditSnapshots.of(existing);

    audit.record(
        principal.userId(), "DEACTIVATE_MEDICINE", "MEDICINE", existing.id(), before, after);

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "DEACTIVATE_MEDICINE",
        existing.id(),
        existing.rowVersion());
  }
}
