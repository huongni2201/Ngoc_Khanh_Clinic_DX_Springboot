package com.ngockhanh.clinic.document.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.document.application.port.NewFile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis access to {@code document_render_jobs} and {@code document_render_job_outputs}. */
@Mapper
public interface RenderJobMapper {
  RenderJobRow findById(@Param("id") UUID id);

  RenderJobRow findByIdempotencyKey(
      @Param("requestedBy") UUID requestedBy, @Param("idempotencyKey") UUID idempotencyKey);

  int countActive(@Param("requestedBy") UUID requestedBy);

  int insert(
      @Param("id") UUID id,
      @Param("documentType") String documentType,
      @Param("requestedBy") UUID requestedBy,
      @Param("idempotencyKey") UUID idempotencyKey,
      @Param("context") String contextJson,
      @Param("recordIds") String recordIds,
      @Param("totalCount") int totalCount,
      @Param("now") Instant now);

  List<PartRow> findParts(@Param("jobId") UUID jobId);

  int failExhausted(
      @Param("now") Instant now,
      @Param("maxAttempts") int maxAttempts,
      @Param("expiresAt") Instant expiresAt);

  ClaimedRow claimNext(
      @Param("now") Instant now,
      @Param("leaseUntil") Instant leaseUntil,
      @Param("lockToken") UUID lockToken,
      @Param("maxAttempts") int maxAttempts);

  int recordTemplateVersion(
      @Param("id") UUID id,
      @Param("lockToken") UUID lockToken,
      @Param("templateVersionId") UUID templateVersionId);

  int progress(
      @Param("id") UUID id,
      @Param("lockToken") UUID lockToken,
      @Param("processed") int processed,
      @Param("skipped") String skippedJson,
      @Param("leaseUntil") Instant leaseUntil);

  int lockForPart(
      @Param("id") UUID id,
      @Param("lockToken") UUID lockToken,
      @Param("leaseUntil") Instant leaseUntil);

  int insertFile(NewFile file);

  int insertPart(
      @Param("jobId") UUID jobId,
      @Param("partNo") int partNo,
      @Param("fileId") UUID fileId,
      @Param("recordCount") int recordCount);

  int finish(
      @Param("id") UUID id,
      @Param("lockToken") UUID lockToken,
      @Param("status") String status,
      @Param("errorCode") String errorCode,
      @Param("processed") int processed,
      @Param("skipped") String skippedJson,
      @Param("now") Instant now,
      @Param("expiresAt") Instant expiresAt);

  List<UUID> findExpired(@Param("now") Instant now, @Param("limit") int limit);

  int deleteParts(@Param("jobId") UUID jobId);

  int deleteFilesOfParts(@Param("fileIds") List<UUID> fileIds);

  int markExpired(@Param("id") UUID id);
}
