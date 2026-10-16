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
import com.ngockhanh.clinic.catalog.application.command.CreateRoomCommand;
import com.ngockhanh.clinic.catalog.application.command.ReactivateRoomCommand;
import com.ngockhanh.clinic.catalog.application.response.RoomResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.aggregate.Room;
import com.ngockhanh.clinic.catalog.domain.enums.DepartmentType;
import com.ngockhanh.clinic.catalog.domain.repository.DepartmentRepository;
import com.ngockhanh.clinic.catalog.domain.repository.RoomRepository;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomUseCasesTest {
  private final RoomRepository roomRepository = mock(RoomRepository.class);
  private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
  private final CatalogAccessPolicy accessPolicy = new CatalogAccessPolicy();
  private final AuditWriter audit = mock(AuditWriter.class);

  private CreateRoomUseCase createUseCase;
  private UpdateRoomUseCase updateUseCase;
  private DeactivateRoomUseCase deactivateUseCase;
  private ReactivateRoomUseCase reactivateUseCase;

  @BeforeEach
  void setUp() {
    createUseCase =
        new CreateRoomUseCase(roomRepository, departmentRepository, accessPolicy, audit);
    updateUseCase =
        new UpdateRoomUseCase(roomRepository, departmentRepository, accessPolicy, audit);
    deactivateUseCase = new DeactivateRoomUseCase(roomRepository, accessPolicy, audit);
    reactivateUseCase =
        new ReactivateRoomUseCase(roomRepository, departmentRepository, accessPolicy, audit);
  }

  @Test
  void createRoomThrowsConflictWhenDepartmentIsInactive() {
    UserPrincipal principal = manager(CatalogAccessPolicy.ROOM_CREATE);
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

    CreateRoomCommand cmd = new CreateRoomCommand("P101", "Phòng 101", deptId, "EXAMINATION");

    assertThatThrownBy(() -> createUseCase.execute(cmd, principal))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("inactive department");

    verify(roomRepository, never()).save(any());
  }

  @Test
  void reactivateRoomThrowsConflictWhenDepartmentIsInactive() {
    UserPrincipal principal = manager(CatalogAccessPolicy.ROOM_REACTIVATE);
    UUID roomId = UUID.randomUUID();
    UUID deptId = UUID.randomUUID();
    Room room =
        Room.restore(
            roomId,
            new CatalogCode("P101"),
            "Phòng 101",
            deptId,
            "EXAMINATION",
            false,
            Instant.now(),
            Instant.now(),
            1L);
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

    when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
    when(departmentRepository.findByIdForUpdate(deptId)).thenReturn(Optional.of(dept));

    ReactivateRoomCommand cmd = new ReactivateRoomCommand(roomId, 1L);

    assertThatThrownBy(() -> reactivateUseCase.execute(cmd, principal))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("department is inactive");

    verify(roomRepository, never()).update(any(), any(Long.class));
  }

  @Test
  void reactivateRoomSucceedsWhenDepartmentIsActive() {
    UserPrincipal principal = manager(CatalogAccessPolicy.ROOM_REACTIVATE);
    UUID roomId = UUID.randomUUID();
    UUID deptId = UUID.randomUUID();
    Room room =
        Room.restore(
            roomId,
            new CatalogCode("P101"),
            "Phòng 101",
            deptId,
            "EXAMINATION",
            false,
            Instant.now(),
            Instant.now(),
            1L);
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

    when(roomRepository.findById(roomId))
        .thenReturn(
            Optional.of(room),
            Optional.of(
                Room.restore(
                    roomId,
                    room.code(),
                    room.name(),
                    deptId,
                    room.roomType(),
                    true,
                    room.createdAt(),
                    Instant.now(),
                    2L)));
    when(departmentRepository.findByIdForUpdate(deptId)).thenReturn(Optional.of(dept));
    when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
    when(roomRepository.update(any(), eq(1L))).thenReturn(true);

    ReactivateRoomCommand cmd = new ReactivateRoomCommand(roomId, 1L);

    RoomResponse res = reactivateUseCase.execute(cmd, principal);

    assertThat(res.active()).isTrue();
    assertThat(res.rowVersion()).isEqualTo(2L);
    verify(audit)
        .record(
            eq(principal.userId()), eq("REACTIVATE_ROOM"), eq("ROOM"), eq(roomId), any(), any());
  }
}
