package com.ngockhanh.clinic.identity.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.identity.infrastructure.persistence.projection.RoleGrantRow;
import com.ngockhanh.clinic.identity.infrastructure.persistence.projection.UserLoginRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface UserLoginMyBatisMapper {
    UUID identify(String username);

    UserLoginRow find(String username);

    List<RoleGrantRow> grants(@Param("userId") UUID userId, @Param("now") Instant now);

    int lastLogin(@Param("userId") UUID userId, @Param("now") Instant now);
}
