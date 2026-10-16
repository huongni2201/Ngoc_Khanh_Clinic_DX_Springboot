package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.UpdateRoomCommand;
import com.ngockhanh.clinic.catalog.application.response.RoomResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.application.service.CatalogAuditSnapshots;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.aggregate.Room;
import com.ngockhanh.clinic.catalog.domain.repository.DepartmentRepository;
import com.ngockhanh.clinic.catalog.domain.repository.RoomRepository;
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
public class UpdateRoomUseCase {
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
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException if the expected version
   *     is stale
   */
  @Transactional
  public RoomResponse execute(UpdateRoomCommand command, UserPrincipal principal) {
    accessPolicy.requireRoomUpdate(principal);
    if (command == null) throw new IllegalArgumentException("Update room command is required");

    Room existing =
        roomRepository
            .findByIdForUpdate(command.id())
            .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

    if (existing.rowVersion() != command.rowVersion()) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Room was modified by another request");
    }

    Department dept =
        departmentRepository
            .findById(existing.departmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department not found"));

    Map<String, Object> before = CatalogAuditSnapshots.of(existing);

    existing.updateDetails(command.name(), command.roomType());

    boolean updated = roomRepository.update(existing, command.rowVersion());
    if (!updated) {
      throw new ConcurrentUpdateException(
          "CONCURRENT_UPDATE", "Room was modified by another request");
    }

    existing =
        roomRepository
            .findById(existing.id())
            .orElseThrow(() -> new IllegalStateException("Persisted catalog row not found"));
    long newVersion = existing.rowVersion();
    Map<String, Object> after = CatalogAuditSnapshots.of(existing);

    audit.record(principal.userId(), "UPDATE_ROOM", "ROOM", existing.id(), before, after);

    log.info(
        "Catalog write executed; commit pending: action={}, resourceId={}, rowVersion={}",
        "UPDATE_ROOM",
        existing.id(),
        existing.rowVersion());
    return new RoomResponse(
        existing.id(),
        existing.code().value(),
        existing.name(),
        existing.departmentId(),
        dept.name(),
        existing.roomType(),
        existing.active(),
        newVersion,
        existing.createdAt(),
        existing.updatedAt());
  }
}
