package com.ngockhanh.clinic.document.infrastructure.persistence.repository;

import com.ngockhanh.clinic.document.application.contract.DocumentValuesSource;
import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.port.RenderJobStore;
import com.ngockhanh.clinic.document.infrastructure.persistence.mapper.ClaimedRow;
import com.ngockhanh.clinic.document.infrastructure.persistence.mapper.PartRow;
import com.ngockhanh.clinic.document.infrastructure.persistence.mapper.RenderJobMapper;
import com.ngockhanh.clinic.document.infrastructure.persistence.mapper.RenderJobRow;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class MyBatisRenderJobStore implements RenderJobStore {
  private final RenderJobMapper mapper;
  private final JsonMapper json;

  /** JSON shape of one skipped record in {@code document_render_jobs.skipped}. */
  record StoredSkipped(UUID id, String reason) {}

  @Override
  public Optional<Job> findById(UUID id) {
    return Optional.ofNullable(mapper.findById(id)).map(this::toJob);
  }

  @Override
  public Optional<Job> findByIdempotencyKey(UUID requestedBy, UUID idempotencyKey) {
    return Optional.ofNullable(mapper.findByIdempotencyKey(requestedBy, idempotencyKey))
        .map(this::toJob);
  }

  @Override
  public int countActive(UUID requestedBy) {
    return mapper.countActive(requestedBy);
  }

  @Override
  @Transactional
  public boolean insert(
      UUID id,
      String documentType,
      UUID requestedBy,
      UUID idempotencyKey,
      Map<String, String> context,
      List<UUID> recordIds,
      Instant now) {
    return mapper.insert(
            id,
            documentType,
            requestedBy,
            idempotencyKey,
            json.writeValueAsString(context),
            join(recordIds),
            recordIds.size(),
            now)
        == 1;
  }

  @Override
  public List<Part> findParts(UUID jobId) {
    return mapper.findParts(jobId).stream().map(MyBatisRenderJobStore::toPart).toList();
  }

  @Override
  @Transactional
  public int failExhausted(Instant now, int maxAttempts, Instant expiresAt) {
    return mapper.failExhausted(now, maxAttempts, expiresAt);
  }

  @Override
  @Transactional
  public Optional<Claimed> claimNext(Instant now, Instant leaseUntil, int maxAttempts) {
    UUID token = UUID.randomUUID();
    ClaimedRow row = mapper.claimNext(now, leaseUntil, token, maxAttempts);
    if (row == null) return Optional.empty();
    return Optional.of(
        new Claimed(
            row.id(),
            token,
            row.attemptCount(),
            row.documentType(),
            row.requestedBy(),
            context(row.context()),
            split(row.recordIds())));
  }

  @Override
  @Transactional
  public boolean recordTemplateVersion(UUID id, UUID lockToken, UUID templateVersionId) {
    return mapper.recordTemplateVersion(id, lockToken, templateVersionId) == 1;
  }

  @Override
  @Transactional
  public boolean progress(
      UUID id,
      UUID lockToken,
      int processed,
      List<DocumentValuesSource.Skipped> skipped,
      Instant leaseUntil) {
    return mapper.progress(id, lockToken, processed, skippedJson(skipped), leaseUntil) == 1;
  }

  @Override
  @Transactional
  public boolean addPart(
      UUID id, UUID lockToken, int partNo, int recordCount, NewFile file, Instant leaseUntil) {
    if (mapper.lockForPart(id, lockToken, leaseUntil) != 1) return false;
    mapper.insertFile(file);
    mapper.insertPart(id, partNo, file.id(), recordCount);
    return true;
  }

  @Override
  @Transactional
  public boolean finish(
      UUID id,
      UUID lockToken,
      boolean succeeded,
      String errorCode,
      int processed,
      List<DocumentValuesSource.Skipped> skipped,
      Instant now,
      Instant expiresAt) {
    return mapper.finish(
            id,
            lockToken,
            succeeded ? "SUCCEEDED" : "FAILED",
            errorCode,
            processed,
            skippedJson(skipped),
            now,
            expiresAt)
        == 1;
  }

  @Override
  public List<UUID> findExpired(Instant now, int limit) {
    return mapper.findExpired(now, limit);
  }

  @Override
  @Transactional
  public void deleteParts(UUID jobId) {
    List<UUID> fileIds = mapper.findParts(jobId).stream().map(PartRow::fileId).toList();
    mapper.deleteParts(jobId);
    if (!fileIds.isEmpty()) mapper.deleteFilesOfParts(fileIds);
  }

  @Override
  @Transactional
  public void markExpired(UUID jobId) {
    mapper.markExpired(jobId);
  }

  private Job toJob(RenderJobRow row) {
    return new Job(
        row.id(),
        row.documentType(),
        row.requestedBy(),
        row.idempotencyKey(),
        context(row.context()),
        split(row.recordIds()),
        row.status(),
        row.totalCount(),
        row.processedCount(),
        skipped(row.skipped()),
        row.errorCode(),
        row.createdAt(),
        row.finishedAt(),
        row.expiresAt());
  }

  private static Part toPart(PartRow row) {
    return new Part(
        row.partNo(),
        row.recordCount(),
        row.sizeBytes(),
        row.storageProvider(),
        row.storageKey(),
        row.checksum());
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> context(String text) {
    return text == null ? Map.of() : json.readValue(text, Map.class);
  }

  private List<DocumentValuesSource.Skipped> skipped(String text) {
    if (text == null || text.isBlank()) return List.of();
    return Arrays.stream(json.readValue(text, StoredSkipped[].class))
        .map(item -> new DocumentValuesSource.Skipped(item.id(), item.reason()))
        .toList();
  }

  private String skippedJson(List<DocumentValuesSource.Skipped> skipped) {
    return json.writeValueAsString(
        skipped.stream().map(item -> new StoredSkipped(item.id(), item.reason())).toList());
  }

  private static String join(List<UUID> ids) {
    return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
  }

  private static List<UUID> split(String text) {
    if (text == null || text.isBlank()) return List.of();
    return Arrays.stream(text.split(",")).map(UUID::fromString).toList();
  }
}
