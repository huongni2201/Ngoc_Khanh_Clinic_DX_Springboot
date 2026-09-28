package com.ngockhanh.clinic.identity.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.identity.infrastructure.persistence.record.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface StaffLoginMapper {
  UUID identify(String username);

  StaffLoginRow find(String username);

  List<RoleGrantRow> grants(@Param("userId") UUID userId, @Param("now") Instant now);

  int lastLogin(@Param("userId") UUID userId, @Param("now") Instant now);
}
