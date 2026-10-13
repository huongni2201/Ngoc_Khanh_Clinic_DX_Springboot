package com.ngockhanh.clinic.document.application.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A print job as the caller who created it sees it. Skipped records are identified by id and reason
 * only.
 *
 * @param id job identifier
 * @param documentType document type printed
 * @param status {@code QUEUED}, {@code RUNNING}, {@code SUCCEEDED}, {@code FAILED} or {@code EXPIRED}
 * @param totalCount records asked for
 * @param processedCount records handled so far, printed or skipped
 * @param errorCode why a failed job failed, otherwise null
 * @param skipped records left out
 * @param parts PDF parts ready to download
 * @param createdAt when the job was queued
 * @param finishedAt when it ended, or null
 * @param expiresAt when its files are deleted, or null
 */
public record RenderJobResponse(
    UUID id,
    String documentType,
    String status,
    int totalCount,
    int processedCount,
    String errorCode,
    List<Skipped> skipped,
    List<Part> parts,
    Instant createdAt,
    Instant finishedAt,
    Instant expiresAt) {
  public RenderJobResponse {
    skipped = List.copyOf(skipped);
    parts = List.copyOf(parts);
  }

  /** A record left out of the output. */
  public record Skipped(UUID id, String reason) {}

  /** One merged PDF part. */
  public record Part(int partNo, int recordCount, long sizeBytes) {}
}
