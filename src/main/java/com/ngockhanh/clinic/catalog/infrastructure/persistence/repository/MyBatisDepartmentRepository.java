package com.ngockhanh.clinic.catalog.infrastructure.persistence.repository;

import com.ngockhanh.clinic.catalog.application.port.out.DepartmentReadQuery;
import com.ngockhanh.clinic.catalog.domain.aggregate.Department;
import com.ngockhanh.clinic.catalog.domain.repository.DepartmentRepository;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.converter.DepartmentPersistenceConverter;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper.DepartmentMyBatisMapper;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.DepartmentRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisDepartmentRepository implements DepartmentRepository, DepartmentReadQuery {
  private final DepartmentMyBatisMapper mapper;

  @Override
  public void save(Department department) {
    DepartmentRecord record = DepartmentPersistenceConverter.toRecord(department);
    mapper.insert(record);
  }

  @Override
  public boolean update(Department department, long expectedVersion) {
    DepartmentRecord record = DepartmentPersistenceConverter.toRecord(department);
    return mapper.update(record, expectedVersion) == 1;
  }

  @Override
  public Optional<Department> findByIdForUpdate(UUID id) {
    DepartmentRecord record = mapper.findByIdForUpdate(id);
    return Optional.ofNullable(DepartmentPersistenceConverter.toDomain(record));
  }

  @Override
  public Optional<Department> findById(UUID id) {
    DepartmentRecord record = mapper.findRecordById(id);
    return Optional.ofNullable(DepartmentPersistenceConverter.toDomain(record));
  }

  @Override
  public long countActiveRooms(UUID departmentId) {
    return mapper.countActiveRooms(departmentId);
  }

  @Override
  public long countActiveServices(UUID departmentId) {
    return mapper.countActiveServices(departmentId);
  }

  // --- DepartmentReadQuery ---

  @Override
  public long count(String pattern, Boolean active, String departmentType) {
    return mapper.count(pattern, active, departmentType);
  }

  @Override
  public List<Item> findPage(
      String pattern,
      Boolean active,
      String departmentType,
      long offset,
      int limit,
      String sortKey,
      String sortBy) {
    return mapper.findPage(pattern, active, departmentType, offset, limit, sortKey, sortBy).stream()
        .map(
            v ->
                new Item(
                    v.id(),
                    v.code(),
                    v.name(),
                    v.departmentType(),
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
                    v.departmentType(),
                    v.active(),
                    v.rowVersion(),
                    v.createdAt(),
                    v.updatedAt()));
  }
}
