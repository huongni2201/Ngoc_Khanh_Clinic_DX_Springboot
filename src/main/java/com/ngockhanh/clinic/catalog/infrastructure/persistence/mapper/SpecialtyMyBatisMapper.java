package com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.SpecialtyRecord;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.view.SpecialtyDetailView;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.view.SpecialtyItemView;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SpecialtyMyBatisMapper {
  int insert(SpecialtyRecord record);

  int update(
      @Param("specialty") SpecialtyRecord record,
      @Param("expectedRowVersion") long expectedRowVersion);

  SpecialtyRecord findByIdForUpdate(@Param("id") UUID id);

  SpecialtyRecord findRecordById(@Param("id") UUID id);

  SpecialtyDetailView findResponseById(@Param("id") UUID id);

  long count(@Param("pattern") String pattern, @Param("active") Boolean active);

  List<SpecialtyItemView> findPage(
      @Param("pattern") String pattern,
      @Param("active") Boolean active,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);

  long countActiveServices(@Param("specialtyId") UUID specialtyId);
}
