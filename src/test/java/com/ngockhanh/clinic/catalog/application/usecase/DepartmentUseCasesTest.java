package com.ngockhanh.clinic.catalog.application.usecase;

import static com.ngockhanh.clinic.catalog.CatalogTestFixtures.manager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.CreateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.command.DeactivateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.command.ReactivateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.command.UpdateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.response.DepartmentResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.enums.DepartmentType;
import com.ngockhanh.clinic.catalog.domain.repository.DepartmentRepository;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DepartmentUseCasesTest {
  private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
  private final CatalogAccessPolicy accessPolicy = new CatalogAccessPolicy();
  private final AuditWriter audit = mock(AuditWriter.class);

  private CreateDepartmentUseCase createUseCase;
  private UpdateDepartmentUseCase updateUseCase;
  private DeactivateDepartmentUseCase deactivateUseCase;
  private ReactivateDepartmentUseCase reactivateUseCase;

  @BeforeEach
  void setUp() {
    createUseCase = new CreateDepartmentUseCase(departmentRepository, accessPolicy, audit);
    updateUseCase = new UpdateDepartmentUseCase(departmentRepository, accessPolicy, audit);
    deactivateUseCase = new DeactivateDepartmentUseCase(departmentRepository, accessPolicy, audit);
    reactivateUseCase = new ReactivateDepartmentUseCase(departmentRepository, accessPolicy, audit);
  }

  @Test
  void createDepartmentPersistsAndAudits() {
    UserPrincipal principal = manager(CatalogAccessPolicy.DEPARTMENT_CREATE);
    CreateDepartmentCommand cmd = new CreateDepartmentCommand("KHAM", "Khoa Khám Bệnh", "CLINICAL");

    org.mockito.Mockito.doAnswer(
            invocation -> {
              Department d = invocation.getArgument(0);
              Instant at = Instant.parse("2026-10-14T00:00:00Z");
              when(departmentRepository.findById(d.id()))
                  .thenReturn(
                      Optional.of(
                          Department.restore(
                              d.id(), d.code(), d.name(), d.departmentType(), true, at, at, 0)));
              return null;
            })
        .when(departmentRepository)
        .save(any());
    DepartmentResponse res = createUseCase.execute(cmd, principal);

    assertThat(res.code()).isEqualTo("KHAM");
    assertThat(res.name()).isEqualTo("Khoa Khám Bệnh");
    assertThat(res.departmentType()).isEqualTo("CLINICAL");
    assertThat(res.active()).isTrue();
    assertThat(res.rowVersion()).isZero();

    verify(departmentRepository).save(any(Department.class));
    verify(audit)
        .record(
            eq(principal.userId()), eq("CREATE_DEPARTMENT"), eq("DEPARTMENT"), any(), any(), any());
  }

  @Test
  void updateDepartmentThrowsOnRowVersionMismatch() {
    UserPrincipal principal = manager(CatalogAccessPolicy.DEPARTMENT_UPDATE);
    UUID id = UUID.randomUUID();
    Department existing =
        Department.restore(
            id,
            new CatalogCode("KHAM"),
            "Khoa Cũ",
            DepartmentType.CLINICAL,
            true,
            Instant.now(),
            Instant.now(),
            2L);
    when(departmentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));

    UpdateDepartmentCommand cmd = new UpdateDepartmentCommand(id, "Khoa Mới", "CLINICAL", 1L);

    assertThatThrownBy(() -> updateUseCase.execute(cmd, principal))
        .isInstanceOf(ConcurrentUpdateException.class);

    verify(departmentRepository, never()).update(any(), any(Long.class));
  }

  @Test
  void deactivateDepartmentThrowsConflictWhenActiveDependentsExist() {
    UserPrincipal principal = manager(CatalogAccessPolicy.DEPARTMENT_DEACTIVATE);
    UUID id = UUID.randomUUID();
    Department existing =
        Department.restore(
            id,
            new CatalogCode("KHAM"),
            "Khoa Khám",
            DepartmentType.CLINICAL,
            true,
            Instant.now(),
            Instant.now(),
            0L);
    when(departmentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
    when(departmentRepository.countActiveRooms(id)).thenReturn(2L);
    when(departmentRepository.countActiveServices(id)).thenReturn(1L);

    DeactivateDepartmentCommand cmd = new DeactivateDepartmentCommand(id, 0L);

    assertThatThrownBy(() -> deactivateUseCase.execute(cmd, principal))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining(
            "Cannot deactivate department with 2 active room(s) and 1 active service(s)");

    verify(departmentRepository, never()).update(any(), any(Long.class));
  }

  @Test
  void deactivateDepartmentIsNoOpWhenAlreadyInactive() {
    UserPrincipal principal = manager(CatalogAccessPolicy.DEPARTMENT_DEACTIVATE);
    UUID id = UUID.randomUUID();
    Department existing =
        Department.restore(
            id,
            new CatalogCode("KHAM"),
            "Khoa Khám",
            DepartmentType.CLINICAL,
            false,
            Instant.now(),
            Instant.now(),
            1L);
    when(departmentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));

    DeactivateDepartmentCommand cmd = new DeactivateDepartmentCommand(id, 1L);

    deactivateUseCase.execute(cmd, principal);

    verify(departmentRepository, never()).update(any(), any(Long.class));
    verify(audit, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  void reactivateDepartmentIsNoOpWhenAlreadyActive() {
    UserPrincipal principal = manager(CatalogAccessPolicy.DEPARTMENT_REACTIVATE);
    UUID id = UUID.randomUUID();
    Department existing =
        Department.restore(
            id,
            new CatalogCode("KHAM"),
            "Khoa Khám",
            DepartmentType.CLINICAL,
            true,
            Instant.now(),
            Instant.now(),
            0L);
    when(departmentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));

    ReactivateDepartmentCommand cmd = new ReactivateDepartmentCommand(id, 0L);

    DepartmentResponse res = reactivateUseCase.execute(cmd, principal);
    assertThat(res.active()).isTrue();
    assertThat(res.rowVersion()).isZero();

    verify(departmentRepository, never()).update(any(), any(Long.class));
  }
}
