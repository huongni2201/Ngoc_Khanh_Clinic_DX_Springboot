package com.ngockhanh.clinic.catalog.domain.repository;

import com.ngockhanh.clinic.catalog.domain.aggregate.Room;
import java.util.Optional;
import java.util.UUID;

/** Write side (command) repository for Room aggregate. */
public interface RoomRepository {
  void save(Room room);

  boolean update(Room room, long expectedVersion);

  Optional<Room> findByIdForUpdate(UUID id);

  Optional<Room> findById(UUID id);
}
