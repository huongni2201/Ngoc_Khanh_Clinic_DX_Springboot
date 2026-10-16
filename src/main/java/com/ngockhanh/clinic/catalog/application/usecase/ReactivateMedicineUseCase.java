package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.ReactivateMedicineCommand;
import com.ngockhanh.clinic.catalog.application.response.MedicineResponse;
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

/** Reactivates catalog data for staff with the corresponding action permission. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactivateMedicineUseCase {
  private final MedicineRepository medicineRepository;
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
  public MedicineResponse execute(ReactivateMedicineCommand command, UserPrincipal principal) {
    accessPolicy.requireMedicineReactivate(principal);
    if (command == null)
      throw new IllegalArgumentException("Reactivate medicine command is required");

    Medicine existing =
        medicineRepository
            .findByIdForUpdate(command.id())
            .orElseThrow(() -> new ResourceNotFoundException("Medicine not found"));

    if (existing.rowVersion() != command.rowVersion()) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Medicine was modified by another request");
    }

    if (existing.active()) {
      log.debug("Medicine {} already active; no-op", existing.id());
      return MedicineResponse.from(existing);
    }

    Map<String, Object> before = CatalogAuditSnapshots.of(existing);

    existing.reactivate();

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
        principal.userId(), "REACTIVATE_MEDICINE", "MEDICINE", existing.id(), before, after);

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "REACTIVATE_MEDICINE",
        existing.id(),
        existing.rowVersion());
    return new MedicineResponse(
        existing.id(),
        existing.code().value(),
        existing.genericName(),
        existing.brandName(),
        existing.strength(),
        existing.dosageForm(),
        existing.unit(),
        true,
        newVersion,
        existing.createdAt(),
        existing.updatedAt());
  }
}
