package com.ngockhanh.clinic.catalog.infrastructure.persistence.repository;

import com.ngockhanh.clinic.catalog.application.port.out.MedicineReadQuery;
import com.ngockhanh.clinic.catalog.domain.aggregate.Medicine;
import com.ngockhanh.clinic.catalog.domain.repository.MedicineRepository;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.converter.MedicinePersistenceConverter;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper.MedicineMyBatisMapper;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.MedicineRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisMedicineRepository implements MedicineRepository, MedicineReadQuery {
  private final MedicineMyBatisMapper mapper;

  @Override
  public void save(Medicine medicine) {
    MedicineRecord record = MedicinePersistenceConverter.toRecord(medicine);
    mapper.insert(record);
  }

  @Override
  public boolean update(Medicine medicine, long expectedVersion) {
    MedicineRecord record = MedicinePersistenceConverter.toRecord(medicine);
    return mapper.update(record, expectedVersion) == 1;
  }

  @Override
  public Optional<Medicine> findByIdForUpdate(UUID id) {
    MedicineRecord record = mapper.findByIdForUpdate(id);
    return Optional.ofNullable(MedicinePersistenceConverter.toDomain(record));
  }

  @Override
  public Optional<Medicine> findById(UUID id) {
    MedicineRecord record = mapper.findRecordById(id);
    return Optional.ofNullable(MedicinePersistenceConverter.toDomain(record));
  }

  // --- MedicineReadQuery ---

  @Override
  public long count(String pattern, Boolean active, String dosageForm) {
    return mapper.count(pattern, active, dosageForm);
  }

  @Override
  public List<Item> findPage(
      String pattern,
      Boolean active,
      String dosageForm,
      long offset,
      int limit,
      String sortKey,
      String sortBy) {
    return mapper.findPage(pattern, active, dosageForm, offset, limit, sortKey, sortBy).stream()
        .map(
            v ->
                new Item(
                    v.id(),
                    v.code(),
                    v.genericName(),
                    v.brandName(),
                    v.strength(),
                    v.dosageForm(),
                    v.unit(),
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
                    v.genericName(),
                    v.brandName(),
                    v.strength(),
                    v.dosageForm(),
                    v.unit(),
                    v.active(),
                    v.rowVersion(),
                    v.createdAt(),
                    v.updatedAt()));
  }
}
