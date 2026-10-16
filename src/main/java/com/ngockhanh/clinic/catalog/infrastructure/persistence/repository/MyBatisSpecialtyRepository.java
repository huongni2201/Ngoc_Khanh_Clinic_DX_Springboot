package com.ngockhanh.clinic.catalog.infrastructure.persistence.repository;

import com.ngockhanh.clinic.catalog.application.port.out.SpecialtyReadQuery;
import com.ngockhanh.clinic.catalog.domain.aggregate.Specialty;
import com.ngockhanh.clinic.catalog.domain.repository.SpecialtyRepository;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.converter.SpecialtyPersistenceConverter;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper.SpecialtyMyBatisMapper;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.SpecialtyRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisSpecialtyRepository implements SpecialtyRepository, SpecialtyReadQuery {
  private final SpecialtyMyBatisMapper mapper;

  @Override
  public void save(Specialty specialty) {
    SpecialtyRecord record = SpecialtyPersistenceConverter.toRecord(specialty);
    mapper.insert(record);
  }

  @Override
  public boolean update(Specialty specialty, long expectedVersion) {
    SpecialtyRecord record = SpecialtyPersistenceConverter.toRecord(specialty);
    return mapper.update(record, expectedVersion) == 1;
  }

  @Override
  public Optional<Specialty> findByIdForUpdate(UUID id) {
    SpecialtyRecord record = mapper.findByIdForUpdate(id);
    return Optional.ofNullable(SpecialtyPersistenceConverter.toDomain(record));
  }

  @Override
  public Optional<Specialty> findById(UUID id) {
    SpecialtyRecord record = mapper.findRecordById(id);
    return Optional.ofNullable(SpecialtyPersistenceConverter.toDomain(record));
  }

  @Override
  public long countActiveServices(UUID specialtyId) {
    return mapper.countActiveServices(specialtyId);
  }

  // --- SpecialtyReadQuery ---

  @Override
  public long count(String pattern, Boolean active) {
    return mapper.count(pattern, active);
  }

  @Override
  public List<Item> findPage(
      String pattern, Boolean active, long offset, int limit, String sortKey, String sortBy) {
    return mapper.findPage(pattern, active, offset, limit, sortKey, sortBy).stream()
        .map(
            v ->
                new Item(
                    v.id(),
                    v.code(),
                    v.name(),
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
                    v.active(),
                    v.rowVersion(),
                    v.createdAt(),
                    v.updatedAt()));
  }
}
