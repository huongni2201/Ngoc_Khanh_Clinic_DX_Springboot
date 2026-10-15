package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.RoleRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMyBatisMapper {
  /** Active role with exactly this code, or null. */
  RoleRecord findActiveByCode(@Param("code") String code);

  /** Active roles other than {@code PATIENT}, ordered by code. */
  List<RoleRecord> findAssignable();
}
