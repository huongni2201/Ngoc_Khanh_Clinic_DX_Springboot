package com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.MedicineRecord;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.view.MedicineDetailView;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.view.MedicineItemView;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MedicineMyBatisMapper {
  int insert(MedicineRecord record);

  int update(
      @Param("medicine") MedicineRecord record,
      @Param("expectedRowVersion") long expectedRowVersion);

  MedicineRecord findByIdForUpdate(@Param("id") UUID id);

  MedicineRecord findRecordById(@Param("id") UUID id);

  MedicineDetailView findResponseById(@Param("id") UUID id);

  long count(
      @Param("pattern") String pattern,
      @Param("active") Boolean active,
      @Param("dosageForm") String dosageForm);

  List<MedicineItemView> findPage(
      @Param("pattern") String pattern,
      @Param("active") Boolean active,
      @Param("dosageForm") String dosageForm,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);
}
