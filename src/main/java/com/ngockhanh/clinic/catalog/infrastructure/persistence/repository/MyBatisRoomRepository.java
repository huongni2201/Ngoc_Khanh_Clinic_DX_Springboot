package com.ngockhanh.clinic.catalog.infrastructure.persistence.repository;

import com.ngockhanh.clinic.catalog.application.port.out.RoomReadQuery;
import com.ngockhanh.clinic.catalog.domain.aggregate.Room;
import com.ngockhanh.clinic.catalog.domain.repository.RoomRepository;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.converter.RoomPersistenceConverter;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper.RoomMyBatisMapper;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.RoomRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisRoomRepository implements RoomRepository, RoomReadQuery {
  private final RoomMyBatisMapper mapper;

  @Override
  public void save(Room room) {
    RoomRecord record = RoomPersistenceConverter.toRecord(room);
    mapper.insert(record);
  }

  @Override
  public boolean update(Room room, long expectedVersion) {
    RoomRecord record = RoomPersistenceConverter.toRecord(room);
    return mapper.update(record, expectedVersion) == 1;
  }

  @Override
  public Optional<Room> findByIdForUpdate(UUID id) {
    RoomRecord record = mapper.findByIdForUpdate(id);
    return Optional.ofNullable(RoomPersistenceConverter.toDomain(record));
  }

  @Override
  public Optional<Room> findById(UUID id) {
    RoomRecord record = mapper.findRecordById(id);
    return Optional.ofNullable(RoomPersistenceConverter.toDomain(record));
  }

  // --- RoomReadQuery ---

  @Override
  public long count(String pattern, Boolean active, UUID departmentId, String roomType) {
    return mapper.count(pattern, active, departmentId, roomType);
  }

  @Override
  public List<Item> findPage(
      String pattern,
      Boolean active,
      UUID departmentId,
      String roomType,
      long offset,
      int limit,
      String sortKey,
      String sortBy) {
    return mapper
        .findPage(pattern, active, departmentId, roomType, offset, limit, sortKey, sortBy)
        .stream()
        .map(
            v ->
                new Item(
                    v.id(),
                    v.code(),
                    v.name(),
                    v.departmentId(),
                    v.departmentName(),
                    v.roomType(),
                    v.active(),
                    v.rowVersion(),
                    v.createdAt(),
                    v.updatedAt()))
        .toList();
  }

  @Override
  public Optional<Detail> findDetailById(UUID id) {
    return Optional.ofNullable(mapper.findResponseById(id))
        .map(
            v ->
                new Detail(
                    v.id(),
                    v.code(),
                    v.name(),
                    v.departmentId(),
                    v.departmentName(),
                    v.roomType(),
                    v.active(),
                    v.rowVersion(),
                    v.createdAt(),
                    v.updatedAt()));
  }
}
