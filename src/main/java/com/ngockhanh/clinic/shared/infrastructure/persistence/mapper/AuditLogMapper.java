package com.ngockhanh.clinic.shared.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.shared.infrastructure.persistence.record.AuditLogRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper {
  int insert(AuditLogRecord record);
}
