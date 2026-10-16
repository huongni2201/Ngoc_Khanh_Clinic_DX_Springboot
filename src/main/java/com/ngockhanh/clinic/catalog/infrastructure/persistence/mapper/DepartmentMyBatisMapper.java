package com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.DepartmentRecord;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.view.DepartmentDetailView;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.view.DepartmentItemView;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DepartmentMyBatisMapper {
  int insert(DepartmentRecord record);

  int update(
      @Param("department") DepartmentRecord record,
      @Param("expectedRowVersion") long expectedRowVersion);

  DepartmentRecord findByIdForUpdate(@Param("id") UUID id);

  DepartmentRecord findRecordById(@Param("id") UUID id);

  DepartmentDetailView findResponseById(@Param("id") UUID id);

  long count(
      @Param("pattern") String pattern,
      @Param("active") Boolean active,
      @Param("departmentType") String departmentType);

  List<DepartmentItemView> findPage(
      @Param("pattern") String pattern,
      @Param("active") Boolean active,
      @Param("departmentType") String departmentType,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);

  long countActiveRooms(@Param("departmentId") UUID departmentId);

  long countActiveServices(@Param("departmentId") UUID departmentId);
}
