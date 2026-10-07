package com.ngockhanh.clinic.integration.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.integration.application.imports.CommittedImportRow;
import com.ngockhanh.clinic.integration.infrastructure.persistence.record.IdempotencyKeyRecord;
import com.ngockhanh.clinic.integration.infrastructure.persistence.record.ImportJobRecord;
import com.ngockhanh.clinic.integration.infrastructure.persistence.record.ImportRowRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ParticipantImportMyBatisMapper {
  /** Inserts a PROCESSING key; returns 0 without error when the key already exists. */
  int insertReservation(@Param("r") IdempotencyKeyRecord reservation);

  IdempotencyKeyRecord findReservationForUpdate(
      @Param("scope") String scope,
      @Param("actorKey") String actorKey,
      @Param("requestKey") String requestKey);

  int completeReservation(
      @Param("id") UUID id,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("summary") String summary,
      @Param("completedAt") Instant completedAt);

  int insertJob(@Param("j") ImportJobRecord job);

  int insertRows(@Param("rows") List<ImportRowRecord> rows);

  int markRowsCommitted(@Param("jobId") UUID jobId, @Param("rows") List<CommittedImportRow> rows);

  int confirmJob(
      @Param("id") UUID id,
      @Param("expectedVersion") long expectedVersion,
      @Param("confirmedAt") Instant confirmedAt,
      @Param("result") String result);
}
