package com.ngockhanh.clinic.catalog.infrastructure.persistence.repository;

import com.ngockhanh.clinic.catalog.application.port.out.ManagedServiceReadQuery;
import com.ngockhanh.clinic.catalog.domain.aggregate.CatalogService;
import com.ngockhanh.clinic.catalog.domain.repository.CatalogServiceRepository;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.converter.ServicePersistenceConverter;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper.ManagedServiceMyBatisMapper;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.ServiceRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisManagedServiceRepository
    implements CatalogServiceRepository, ManagedServiceReadQuery {
  private final ManagedServiceMyBatisMapper mapper;

  @Override
  public void save(CatalogService service) {
    ServiceRecord record = ServicePersistenceConverter.toRecord(service);
    mapper.insert(record);
  }

  @Override
  public boolean update(CatalogService service, long expectedVersion) {
    ServiceRecord record = ServicePersistenceConverter.toRecord(service);
    return mapper.update(record, expectedVersion) == 1;
  }

  @Override
  public Optional<CatalogService> findByIdForUpdate(UUID id) {
    ServiceRecord record = mapper.findByIdForUpdate(id);
    return Optional.ofNullable(ServicePersistenceConverter.toDomain(record));
  }

  @Override
  public Optional<CatalogService> findById(UUID id) {
    ServiceRecord record = mapper.findRecordById(id);
    return Optional.ofNullable(ServicePersistenceConverter.toDomain(record));
  }

  // --- ManagedServiceReadQuery ---

  @Override
  public long count(
      String pattern,
      Boolean active,
      String serviceType,
      UUID performingDepartmentId,
      UUID specialtyId) {
    return mapper.count(pattern, active, serviceType, performingDepartmentId, specialtyId);
  }

  @Override
  public List<Item> findPage(
      String pattern,
      Boolean active,
      String serviceType,
      UUID performingDepartmentId,
      UUID specialtyId,
      long offset,
      int limit,
      String sortKey,
      String sortBy) {
    return mapper
        .findPage(
            pattern,
            active,
            serviceType,
            performingDepartmentId,
            specialtyId,
            offset,
            limit,
            sortKey,
            sortBy)
        .stream()
        .map(
            v ->
                new Item(
                    v.id(),
                    v.code(),
                    v.name(),
                    v.serviceType(),
                    v.performingDepartmentId(),
                    v.performingDepartmentName(),
                    v.specialtyId(),
                    v.specialtyName(),
                    v.unitPrice(),
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
                    v.serviceType(),
                    v.performingDepartmentId(),
                    v.performingDepartmentName(),
                    v.specialtyId(),
                    v.specialtyName(),
                    v.unitPrice(),
                    v.preparationInstruction(),
                    v.active(),
                    v.rowVersion(),
                    v.createdAt(),
                    v.updatedAt()));
  }
}
