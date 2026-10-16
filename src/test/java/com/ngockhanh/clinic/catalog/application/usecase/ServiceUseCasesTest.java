package com.ngockhanh.clinic.catalog.application.usecase;

import static com.ngockhanh.clinic.catalog.CatalogTestFixtures.manager;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.CreateServiceCommand;
import com.ngockhanh.clinic.catalog.application.command.UpdateServiceCommand;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.domain.aggregate.CatalogService;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.aggregate.Specialty;
import com.ngockhanh.clinic.catalog.domain.enums.DepartmentType;
import com.ngockhanh.clinic.catalog.domain.enums.ServiceType;
import com.ngockhanh.clinic.catalog.domain.repository.CatalogServiceRepository;
import com.ngockhanh.clinic.catalog.domain.repository.DepartmentRepository;
import com.ngockhanh.clinic.catalog.domain.repository.SpecialtyRepository;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceUseCasesTest {
  private final CatalogServiceRepository serviceRepository = mock(CatalogServiceRepository.class);
  private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
  private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
  private final CatalogAccessPolicy accessPolicy = new CatalogAccessPolicy();
  private final AuditWriter audit = mock(AuditWriter.class);

  private CreateManagedServiceUseCase createUseCase;
  private UpdateManagedServiceUseCase updateUseCase;
  private DeactivateManagedServiceUseCase deactivateUseCase;
  private ReactivateManagedServiceUseCase reactivateUseCase;

  @BeforeEach
  void setUp() {
    createUseCase =
        new CreateManagedServiceUseCase(
            serviceRepository, departmentRepository, specialtyRepository, accessPolicy, audit);
    updateUseCase =
        new UpdateManagedServiceUseCase(
            serviceRepository, departmentRepository, specialtyRepository, accessPolicy, audit);
    deactivateUseCase = new DeactivateManagedServiceUseCase(serviceRepository, accessPolicy, audit);
    reactivateUseCase =
        new ReactivateManagedServiceUseCase(
            serviceRepository, departmentRepository, specialtyRepository, accessPolicy, audit);
  }

  @Test
  void createServiceThrowsConflictWhenPerformingDepartmentIsInactive() {
    UserPrincipal principal = manager(CatalogAccessPolicy.SERVICE_CREATE);
    UUID deptId = UUID.randomUUID();
    Department dept =
        Department.restore(
            deptId,
            new CatalogCode("KHAM"),
            "Khoa Khám",
            DepartmentType.CLINICAL,
            false,
            Instant.now(),
            Instant.now(),
            0L);
    when(departmentRepository.findByIdForUpdate(deptId)).thenReturn(Optional.of(dept));

    CreateServiceCommand cmd =
        new CreateServiceCommand(
            "S1", "Dịch vụ 1", "CONSULTATION", deptId, null, new BigDecimal("100000.00"), null);

    assertThatThrownBy(() -> createUseCase.execute(cmd, principal))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("inactive department");

    verify(serviceRepository, never()).save(any());
  }

  @Test
  void createServiceThrowsConflictWhenSpecialtyIsInactive() {
    UserPrincipal principal = manager(CatalogAccessPolicy.SERVICE_CREATE);
    UUID deptId = UUID.randomUUID();
    UUID specId = UUID.randomUUID();
    Department dept =
        Department.restore(
            deptId,
            new CatalogCode("KHAM"),
            "Khoa Khám",
            DepartmentType.CLINICAL,
            true,
            Instant.now(),
            Instant.now(),
            0L);
    Specialty spec =
        Specialty.restore(
            specId, new CatalogCode("NOI"), "Nội", false, Instant.now(), Instant.now(), 0L);

    when(departmentRepository.findByIdForUpdate(deptId)).thenReturn(Optional.of(dept));
    when(specialtyRepository.findByIdForUpdate(specId)).thenReturn(Optional.of(spec));

    CreateServiceCommand cmd =
        new CreateServiceCommand(
            "S1", "Dịch vụ 1", "CONSULTATION", deptId, specId, new BigDecimal("100000.00"), null);

    assertThatThrownBy(() -> createUseCase.execute(cmd, principal))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("inactive specialty");

    verify(serviceRepository, never()).save(any());
  }

  @Test
  void updateServiceThrowsConflictWhenNewSpecialtyIsInactive() {
    UserPrincipal principal = manager(CatalogAccessPolicy.SERVICE_UPDATE);
    UUID serviceId = UUID.randomUUID();
    UUID specId = UUID.randomUUID();
    Specialty spec =
        Specialty.restore(
            specId, new CatalogCode("NOI"), "Nội", false, Instant.now(), Instant.now(), 0L);
    when(specialtyRepository.findByIdForUpdate(specId)).thenReturn(Optional.of(spec));

    UUID deptId = UUID.randomUUID();
    CatalogService existing =
        CatalogService.create(
            serviceId,
            new CatalogCode("S1"),
            "Existing",
            ServiceType.CONSULTATION,
            deptId,
            null,
            new BigDecimal("100000"),
            null);
    when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existing));
    when(serviceRepository.findByIdForUpdate(serviceId)).thenReturn(Optional.of(existing));
    when(departmentRepository.findByIdForUpdate(deptId))
        .thenReturn(
            Optional.of(
                Department.create(
                    deptId, new CatalogCode("D1"), "Department", DepartmentType.CLINICAL)));
    UpdateServiceCommand cmd =
        new UpdateServiceCommand(
            serviceId,
            "Dịch vụ mới",
            "CONSULTATION",
            specId,
            new BigDecimal("150000.00"),
            null,
            0L);

    assertThatThrownBy(() -> updateUseCase.execute(cmd, principal))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("inactive specialty");

    verify(serviceRepository, never()).update(any(), any(Long.class));
  }
}
