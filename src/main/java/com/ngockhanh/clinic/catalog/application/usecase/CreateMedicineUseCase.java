package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.CreateMedicineCommand;
import com.ngockhanh.clinic.catalog.application.response.MedicineResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.application.service.CatalogAuditSnapshots;
import com.ngockhanh.clinic.catalog.domain.aggregate.Medicine;
import com.ngockhanh.clinic.catalog.domain.repository.MedicineRepository;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates catalog data for staff with the corresponding action permission. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateMedicineUseCase {
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
   */
  @Transactional
  public MedicineResponse execute(CreateMedicineCommand command, UserPrincipal principal) {
    accessPolicy.requireMedicineCreate(principal);
    if (command == null) throw new IllegalArgumentException("Create medicine command is required");

    UUID id = UuidV7Generator.generate();
    CatalogCode code = new CatalogCode(command.code());
    Medicine medicine =
        Medicine.create(
            id,
            code,
            command.genericName(),
            command.brandName(),
            command.strength(),
            command.dosageForm(),
            command.unit());

    medicineRepository.save(medicine);
    medicine =
        medicineRepository
            .findById(medicine.id())
            .orElseThrow(() -> new IllegalStateException("Persisted catalog row not found"));

    audit.record(
        principal.userId(),
        "CREATE_MEDICINE",
        "MEDICINE",
        medicine.id(),
        null,
        CatalogAuditSnapshots.of(medicine));

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "CREATE_MEDICINE",
        medicine.id(),
        medicine.rowVersion());
    return MedicineResponse.from(medicine);
  }
}
