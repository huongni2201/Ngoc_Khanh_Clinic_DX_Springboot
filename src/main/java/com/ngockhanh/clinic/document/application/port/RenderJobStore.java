package com.ngockhanh.clinic.document.application.port;

import com.ngockhanh.clinic.document.application.contract.DocumentValuesSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage port of background print jobs. Every method that changes a running job takes the lease
 * token it was claimed with and reports {@code false} when the lease was lost, so a job taken over
 * by another worker is never written by two.
 */
public interface RenderJobStore {
  /** A job as stored. */
  record Job(
      UUID id,
      String documentType,
      UUID requestedBy,
      UUID idempotencyKey,
      Map<String, String> context,
      List<UUID> recordIds,
      String status,
      int totalCount,
      int processedCount,
      List<DocumentValuesSource.Skipped> skipped,
      String errorCode,
      Instant createdAt,
      Instant finishedAt,
      Instant expiresAt) {}

  /** A job taken by a worker. */
  record Claimed(
      UUID id,
      UUID lockToken,
      int attempt,
      String documentType,
      UUID requestedBy,
      Map<String, String> context,
      List<UUID> recordIds) {}

  /** One merged PDF part of a job and where its file lives. */
  record Part(
      int partNo,
      int recordCount,
      long sizeBytes,
      String storageProvider,
      String storageKey,
      byte[] checksum) {}

  Optional<Job> findById(UUID id);

  Optional<Job> findByIdempotencyKey(UUID requestedBy, UUID idempotencyKey);

  /** Number of jobs of the requester that are queued or running. */
  int countActive(UUID requestedBy);

  /**
   * Inserts a queued job.
   *
   * @return false when the requester and key already have a job (a concurrent retry)
   */
  boolean insert(
      UUID id,
      String documentType,
      UUID requestedBy,
      UUID idempotencyKey,
      Map<String, String> context,
      List<UUID> recordIds,
      Instant now);

  /** Parts of a job in order, with the files that hold them. */
  List<Part> findParts(UUID jobId);

  /**
   * Fails running jobs whose lease ran out after the last allowed attempt.
   *
   * @return how many were failed
   */
  int failExhausted(Instant now, int maxAttempts, Instant expiresAt);

  /** Takes the oldest queued job, or a running job whose lease ran out, for {@code lease}. */
  Optional<Claimed> claimNext(Instant now, Instant leaseUntil, int maxAttempts);

  /** Records the template version the job prints with. */
  boolean recordTemplateVersion(UUID id, UUID lockToken, UUID templateVersionId);

  /** Saves progress and extends the lease. */
  boolean progress(
      UUID id,
      UUID lockToken,
      int processed,
      List<DocumentValuesSource.Skipped> skipped,
      Instant leaseUntil);

  /** Stores a finished part: its file and its place in the job. */
  boolean addPart(
      UUID id,
      UUID lockToken,
      int partNo,
      int recordCount,
      NewFile file,
      Instant leaseUntil);

  /**
   * Ends the job. {@code errorCode} is null for success.
   *
   * @return false when the lease was lost
   */
  boolean finish(
      UUID id,
      UUID lockToken,
      boolean succeeded,
      String errorCode,
      int processed,
      List<DocumentValuesSource.Skipped> skipped,
      Instant now,
      Instant expiresAt);

  /** Jobs that are finished and past their expiry. */
  List<UUID> findExpired(Instant now, int limit);

  /** Removes the part rows and file rows of a job. */
  void deleteParts(UUID jobId);

  /** Marks an expired job {@code EXPIRED} after its files are gone. */
  void markExpired(UUID jobId);
}
