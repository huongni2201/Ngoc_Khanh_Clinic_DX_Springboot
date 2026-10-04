package com.ngockhanh.clinic.audit.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.audit.infrastructure.persistence.record.AuditEventRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditEventMapper {
  int insert(AuditEventRecord record);
}
