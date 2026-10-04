package com.ngockhanh.clinic.audit.infrastructure.persistence.mapper;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditMapper {
  void insert(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("action") String action,
      @Param("at") Instant at,
      @Param("correlation") UUID correlation);
}
