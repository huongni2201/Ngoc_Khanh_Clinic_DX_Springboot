package com.ngockhanh.clinic.integration.infrastructure.persistence.repository;

import com.ngockhanh.clinic.integration.application.imports.ImportStore;
import com.ngockhanh.clinic.integration.infrastructure.persistence.mapper.ImportMapper;
import com.ngockhanh.clinic.integration.infrastructure.persistence.record.ImportJobRecord;
import com.ngockhanh.clinic.integration.infrastructure.persistence.record.ImportRowRecord;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisImportStore implements ImportStore {
  private final ImportMapper mapper;

  public Optional<Job> find(UUID id, UUID batchId, boolean forUpdate) {
    return Optional.ofNullable(mapper.find(id, batchId, forUpdate))
        .map(
            r ->
                new Job(
                    r.id(),
                    r.importType(),
                    r.batchId(),
                    r.configuration(),
                    r.sourceFileId(),
                    r.status(),
                    r.createdBy(),
                    r.confirmedBy(),
                    r.createdAt(),
                    r.confirmedAt(),
                    r.cancelledAt(),
                    r.expiresAt(),
                    r.confirmedResult(),
                    r.rowVersion()));
  }

  public List<Row> rows(UUID jobId) {
    return mapper.rows(jobId).stream().map(this::row).toList();
  }

  public long countRows(UUID jobId) {
    return mapper.countRows(jobId);
  }

  public List<Row> pageRows(UUID jobId, long offset, int limit) {
    return mapper.pageRows(jobId, offset, limit).stream().map(this::row).toList();
  }

  private Row row(ImportRowRecord r) {
    return new Row(
        r.id(),
        r.jobId(),
        r.rowNumber(),
        r.normalizedPayload(),
        r.previewMetadata(),
        r.committedResourceType(),
        r.committedResourceId(),
        r.createdAt());
  }

  public void insert(Job job, List<Row> rows) {
    if (mapper.insertJob(record(job)) != 1)
      throw new IllegalStateException("Import job was not inserted");
    saveRows(rows, false);
  }

  public void update(Job job, List<Row> rows, long expectedVersion) {
    // The compare-and-set statement increments the version exactly once.
    if (mapper.updateJob(record(job), expectedVersion) != 1) throw new ConcurrentUpdateException();
    saveRows(rows, true);
  }

  private void saveRows(List<Row> rows, boolean update) {
    for (int start = 0; start < rows.size(); start += 500) {
      var chunk =
          rows.subList(start, Math.min(start + 500, rows.size())).stream()
              .map(
                  r ->
                      new ImportRowRecord(
                          r.id(),
                          r.jobId(),
                          r.rowNumber(),
                          r.normalizedPayload(),
                          r.previewMetadata(),
                          r.committedResourceType(),
                          r.committedResourceId(),
                          r.createdAt()))
              .toList();
      if ((update ? mapper.updateRows(chunk) : mapper.saveRows(chunk)) != chunk.size())
        throw new IllegalStateException("Import rows were not saved");
    }
  }

  private static ImportJobRecord record(Job j) {
    return new ImportJobRecord(
        j.id(),
        j.importType(),
        j.batchId(),
        j.configuration(),
        j.sourceFileId(),
        j.status(),
        j.createdBy(),
        j.confirmedBy(),
        j.createdAt(),
        j.confirmedAt(),
        j.cancelledAt(),
        j.expiresAt(),
        j.confirmedResult(),
        j.rowVersion());
  }
}
