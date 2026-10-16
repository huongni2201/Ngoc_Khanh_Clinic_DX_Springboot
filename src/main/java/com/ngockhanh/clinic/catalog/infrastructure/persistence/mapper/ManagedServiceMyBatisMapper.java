package com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.ServiceRecord;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.view.ManagedServiceDetailView;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.view.ManagedServiceItemView;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ManagedServiceMyBatisMapper {
  int insert(ServiceRecord record);

  int update(
      @Param("service") ServiceRecord record, @Param("expectedRowVersion") long expectedRowVersion);

  ServiceRecord findByIdForUpdate(@Param("id") UUID id);

  ServiceRecord findRecordById(@Param("id") UUID id);

  ManagedServiceDetailView findResponseById(@Param("id") UUID id);

  long count(
      @Param("pattern") String pattern,
      @Param("active") Boolean active,
      @Param("serviceType") String serviceType,
      @Param("performingDepartmentId") UUID performingDepartmentId,
      @Param("specialtyId") UUID specialtyId);

  List<ManagedServiceItemView> findPage(
      @Param("pattern") String pattern,
      @Param("active") Boolean active,
      @Param("serviceType") String serviceType,
      @Param("performingDepartmentId") UUID performingDepartmentId,
      @Param("specialtyId") UUID specialtyId,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);
}
