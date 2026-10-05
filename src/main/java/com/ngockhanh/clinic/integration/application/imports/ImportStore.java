package com.ngockhanh.clinic.integration.application.imports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable, type-neutral staging. Import handlers own validation and payload contracts. */
public interface ImportStore {
  Optional<Job> find(UUID id, UUID batchId, boolean forUpdate);

  List<Row> rows(UUID jobId);

  long countRows(UUID jobId);

  List<Row> pageRows(UUID jobId, long offset, int limit);

  void insert(Job job, List<Row> rows);

  void update(Job job, List<Row> rows, long expectedVersion);

  record Job(
      UUID id,
      String importType,
      UUID batchId,
      String configuration,
      UUID sourceFileId,
      String status,
      UUID createdBy,
      UUID confirmedBy,
      Instant createdAt,
      Instant confirmedAt,
      Instant cancelledAt,
      Instant expiresAt,
      String confirmedResult,
      long rowVersion) {}

  record Row(
      UUID id,
      UUID jobId,
      int rowNumber,
      String normalizedPayload,
      String previewMetadata,
      String committedResourceType,
      UUID committedResourceId,
      Instant createdAt) {}
}
