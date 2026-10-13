package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.RoleRecord;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Role reads of account creation (UC-005). */
@Mapper
public interface RoleMyBatisMapper {
  List<RoleRecord> lockByIds(@Param("ids") Collection<UUID> ids);

  RoleRecord findByCode(@Param("code") String code);

  List<RoleRecord> findActiveExcept(@Param("excludedCode") String excludedCode);
}
