package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.RoleRecord;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMyBatisMapper {
  /** Active roles whose code is in {@code codes}, ordered by code. */
  List<RoleRecord> findActiveByCodes(@Param("codes") Collection<String> codes);

  /** Active roles other than {@code PATIENT}, ordered by code. */
  List<RoleRecord> findAssignable();
}
