package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.CreateServiceCommand;
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
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
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
public class CreateManagedServiceUseCase {
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
   */
  @Transactional
  public ManagedServiceResponse execute(CreateServiceCommand command, UserPrincipal principal) {
    accessPolicy.requireServiceCreate(principal);
    if (command == null) throw new IllegalArgumentException("Create service command is required");

    ServiceType type;
    try {
      type = ServiceType.valueOf(command.serviceType().trim().toUpperCase(java.util.Locale.ROOT));
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid service type: " + command.serviceType());
    }

    // Lock department first
    Department department =
        departmentRepository
            .findByIdForUpdate(command.performingDepartmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Performing department not found"));

    if (!department.active()) {
      throw new ConflictException("Cannot create service in an inactive department");
    }

    // Lock specialty if provided
    Specialty specialty = null;
    if (command.specialtyId() != null) {
      specialty =
          specialtyRepository
              .findByIdForUpdate(command.specialtyId())
              .orElseThrow(() -> new ResourceNotFoundException("Specialty not found"));
      if (!specialty.active()) {
        throw new ConflictException("Cannot create service with an inactive specialty");
      }
    }

    UUID id = UuidV7Generator.generate();
    CatalogCode code = new CatalogCode(command.code());
    CatalogService service =
        CatalogService.create(
            id,
            code,
            command.name(),
            type,
            command.performingDepartmentId(),
            command.specialtyId(),
            command.unitPrice(),
            command.preparationInstruction());

    serviceRepository.save(service);
    service =
        serviceRepository
            .findById(service.id())
            .orElseThrow(() -> new IllegalStateException("Persisted catalog row not found"));

    audit.record(
        principal.userId(),
        "CREATE_SERVICE",
        "SERVICE",
        service.id(),
        null,
        CatalogAuditSnapshots.of(service));

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "CREATE_SERVICE",
        service.id(),
        service.rowVersion());
    return new ManagedServiceResponse(
        service.id(),
        service.code().value(),
        service.name(),
        service.serviceType().name(),
        service.performingDepartmentId(),
        department.name(),
        service.specialtyId(),
        specialty != null ? specialty.name() : null,
        service.unitPrice(),
        service.preparationInstruction(),
        service.active(),
        service.rowVersion(),
        service.createdAt(),
        service.updatedAt());
  }
}
