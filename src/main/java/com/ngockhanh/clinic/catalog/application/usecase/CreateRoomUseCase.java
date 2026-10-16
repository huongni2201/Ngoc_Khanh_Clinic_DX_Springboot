package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.CreateRoomCommand;
import com.ngockhanh.clinic.catalog.application.response.RoomResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.application.service.CatalogAuditSnapshots;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.aggregate.Room;
import com.ngockhanh.clinic.catalog.domain.repository.DepartmentRepository;
import com.ngockhanh.clinic.catalog.domain.repository.RoomRepository;
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
public class CreateRoomUseCase {
  private final RoomRepository roomRepository;
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
   */
  @Transactional
  public RoomResponse execute(CreateRoomCommand command, UserPrincipal principal) {
    accessPolicy.requireRoomCreate(principal);
    if (command == null) throw new IllegalArgumentException("Create room command is required");

    Department department =
        departmentRepository
            .findByIdForUpdate(command.departmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

    if (!department.active()) {
      throw new ConflictException("Cannot create room in an inactive department");
    }

    UUID id = UuidV7Generator.generate();
    CatalogCode code = new CatalogCode(command.code());
    Room room = Room.create(id, code, command.name(), command.departmentId(), command.roomType());

    roomRepository.save(room);
    room =
        roomRepository
            .findById(room.id())
            .orElseThrow(() -> new IllegalStateException("Persisted catalog row not found"));

    audit.record(
        principal.userId(), "CREATE_ROOM", "ROOM", room.id(), null, CatalogAuditSnapshots.of(room));

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "CREATE_ROOM",
        room.id(),
        room.rowVersion());
    return new RoomResponse(
        room.id(),
        room.code().value(),
        room.name(),
        room.departmentId(),
        department.name(),
        room.roomType(),
        room.active(),
        room.rowVersion(),
        room.createdAt(),
        room.updatedAt());
  }
}
