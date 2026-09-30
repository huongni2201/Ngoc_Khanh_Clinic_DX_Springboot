package com.ngockhanh.clinic.shared.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.shared.infrastructure.persistence.record.AuditLogRecord;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditLogMapper {
  @Insert(
      "INSERT INTO"
          + " public.audit_logs(id,occurred_at,actor_user_id,action,entity_type,entity_id,before_json,after_json)"
          + " VALUES(#{id},CURRENT_TIMESTAMP,#{actorUserId},#{action},#{entityType},#{entityId},#{beforeJson},#{afterJson})")
  int insert(AuditLogRecord record);
}
