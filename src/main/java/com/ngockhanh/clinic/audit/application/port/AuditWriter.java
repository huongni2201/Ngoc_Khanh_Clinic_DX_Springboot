package com.ngockhanh.clinic.audit.application.port;

import java.util.UUID;

public interface AuditWriter {
  void record(
      UUID actor,
      String action,
      String entityType,
      UUID entityId,
      Object beforeSnapshot,
      Object afterSnapshot);
}
