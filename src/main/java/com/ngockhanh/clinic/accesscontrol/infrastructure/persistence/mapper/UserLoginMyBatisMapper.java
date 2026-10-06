package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection.RoleGrantRow;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection.UserLoginRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserLoginMyBatisMapper {
  UserLoginRow findByUsername(@Param("username") String username);

  List<RoleGrantRow> findActiveGrants(@Param("accountId") UUID accountId);
}
