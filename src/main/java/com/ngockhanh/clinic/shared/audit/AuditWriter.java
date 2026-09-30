package com.ngockhanh.clinic.shared.audit;

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
