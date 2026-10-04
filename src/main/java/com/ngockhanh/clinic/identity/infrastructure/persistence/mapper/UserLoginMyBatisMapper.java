package com.ngockhanh.clinic.identity.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.identity.infrastructure.persistence.projection.RoleGrantRow;
import com.ngockhanh.clinic.identity.infrastructure.persistence.projection.UserLoginRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserLoginMyBatisMapper {
  UUID identify(String username);

  UserLoginRow find(String username);

  List<RoleGrantRow> grants(@Param("accountId") UUID accountId);

  UUID lockEligibleAccount(@Param("accountId") UUID accountId);
}
