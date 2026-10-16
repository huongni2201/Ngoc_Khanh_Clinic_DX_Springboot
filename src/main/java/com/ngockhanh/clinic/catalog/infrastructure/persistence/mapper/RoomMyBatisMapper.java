package com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.RoomRecord;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.view.RoomDetailView;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.view.RoomItemView;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoomMyBatisMapper {
  int insert(RoomRecord record);

  int update(
      @Param("room") RoomRecord record, @Param("expectedRowVersion") long expectedRowVersion);

  RoomRecord findByIdForUpdate(@Param("id") UUID id);

  RoomRecord findRecordById(@Param("id") UUID id);

  RoomDetailView findResponseById(@Param("id") UUID id);

  long count(
      @Param("pattern") String pattern,
      @Param("active") Boolean active,
      @Param("departmentId") UUID departmentId,
      @Param("roomType") String roomType);

  List<RoomItemView> findPage(
      @Param("pattern") String pattern,
      @Param("active") Boolean active,
      @Param("departmentId") UUID departmentId,
      @Param("roomType") String roomType,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);
}
