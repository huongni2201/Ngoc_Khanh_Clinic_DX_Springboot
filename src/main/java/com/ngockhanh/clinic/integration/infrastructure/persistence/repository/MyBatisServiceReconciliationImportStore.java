package com.ngockhanh.clinic.integration.infrastructure.persistence.repository;

import com.ngockhanh.clinic.integration.application.imports.CommittedImportRow;
import com.ngockhanh.clinic.integration.application.imports.ImportRequestIdentity;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationImportStore;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationReceipt;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationReservation;
import com.ngockhanh.clinic.integration.application.imports.StagedReconciliationRow;
import com.ngockhanh.clinic.integration.application.imports.ValidatedServiceReconciliationImport;
import com.ngockhanh.clinic.integration.infrastructure.persistence.mapper.ParticipantImportMyBatisMapper;
import com.ngockhanh.clinic.integration.infrastructure.persistence.record.IdempotencyKeyRecord;
import com.ngockhanh.clinic.integration.infrastructure.persistence.record.ImportJobRecord;
import com.ngockhanh.clinic.integration.infrastructure.persistence.record.ImportRowRecord;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * PostgreSQL adapter of {@link ServiceReconciliationImportStore}. It joins the caller's
 * transaction, owns the JSONB shapes of the job, rows and receipt, and uses the existing import and
 * idempotency tables through the integration module's shared import SQL.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisServiceReconciliationImportStore implements ServiceReconciliationImportStore {
  static final String IMPORT_TYPE = "HEALTH_EXAMINATION_SERVICE_RECONCILIATION";
  static final String SCOPE_PREFIX = "EXAMINATION_DETAIL_IMPORT:";
  static final String RESULT_RESOURCE_TYPE = "IMPORT_JOB";
  private static final Duration RECEIPT_RETENTION = Duration.ofHours(24);
  private static final int ROW_CHUNK = 200;

  private final ParticipantImportMyBatisMapper mapper;
  private final JsonMapper json;
  private final Clock clock;

  @Override
  public ServiceReconciliationReservation reserve(ImportRequestIdentity request) {
    String scope = SCOPE_PREFIX + request.organizationId() + ":" + request.batchId();
    String actorKey = request.actorId().toString();
    String requestKey = request.requestKey().toString();
    UUID id = UuidV7Generator.generate();
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    int inserted =
        mapper.insertReservation(
            IdempotencyKeyRecord.builder()
                .id(id)
                .scope(scope)
                .actorKey(actorKey)
                .requestKey(requestKey)
                .requestHash(request.requestHash())
                .status("PROCESSING")
                .createdAt(now)
                .expiresAt(now.plus(RECEIPT_RETENTION))
                .build());
    if (inserted == 1) return new ServiceReconciliationReservation.New(id);

    var existing = mapper.findReservationForUpdate(scope, actorKey, requestKey);
    if (existing == null) throw new IllegalStateException("Idempotency key could not be read");
    if (!MessageDigest.isEqual(existing.requestHash(), request.requestHash()))
      throw new ConflictException("Idempotency key was used for another request");
    if (!"COMPLETED".equals(existing.status()) || existing.resultSummary() == null)
      throw new ConflictException("A request with this idempotency key is still being processed");
    return new ServiceReconciliationReservation.Replay(
        toReceipt(json.readValue(existing.resultSummary(), StoredReceipt.class)));
  }

  @Override
  public UUID createValidatedJob(ValidatedServiceReconciliationImport input) {
    UUID jobId = UuidV7Generator.generate();
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    Map<String, Object> configuration = new LinkedHashMap<>();
    configuration.put("templateVersion", input.templateVersion());
    configuration.put("rowCount", input.rows().size());
    int jobs =
        mapper.insertJob(
            ImportJobRecord.builder()
                .id(jobId)
                .importType(IMPORT_TYPE)
                .batchId(input.batchId())
                .configuration(json.writeValueAsString(configuration))
                .status("VALIDATED")
                .createdBy(input.actorId())
                .createdAt(now)
                .build());
    if (jobs != 1) throw new IllegalStateException("Import job was not inserted");

    List<ImportRowRecord> rows = new ArrayList<>(input.rows().size());
    for (StagedReconciliationRow row : input.rows())
      rows.add(
          ImportRowRecord.builder()
              .id(UuidV7Generator.generate())
              .jobId(jobId)
              .rowNumber(row.rowNumber())
              .normalizedPayload(json.writeValueAsString(payload(row)))
              .createdAt(now)
              .build());
    for (int from = 0; from < rows.size(); from += ROW_CHUNK) {
      var chunk = rows.subList(from, Math.min(from + ROW_CHUNK, rows.size()));
      if (mapper.insertRows(chunk) != chunk.size())
        throw new IllegalStateException("Import rows were not inserted");
    }
    return jobId;
  }

  @Override
  public void markRowsCommitted(UUID jobId, List<CommittedImportRow> rows) {
    for (int from = 0; from < rows.size(); from += ROW_CHUNK) {
      var chunk = rows.subList(from, Math.min(from + ROW_CHUNK, rows.size()));
      if (mapper.markRowsCommitted(jobId, chunk) != chunk.size())
        throw new IllegalStateException("Import rows were not linked to their resources");
    }
  }

  @Override
  public void confirmJob(UUID jobId, long expectedVersion, ServiceReconciliationReceipt receipt) {
    if (mapper.confirmJob(
            jobId, expectedVersion, receipt.completedAt(), json.writeValueAsString(stored(receipt)))
        != 1) throw new ConcurrentUpdateException();
  }

  @Override
  public void completeRequest(UUID reservationId, ServiceReconciliationReceipt receipt) {
    if (mapper.completeReservation(
            reservationId,
            RESULT_RESOURCE_TYPE,
            receipt.importJobId(),
            json.writeValueAsString(stored(receipt)),
            receipt.completedAt())
        != 1) throw new IllegalStateException("Idempotency reservation was not completed");
  }

  private static Map<String, Object> payload(StagedReconciliationRow row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("participantId", row.participantId().toString());
    payload.put(
        "performedBatchServiceIds",
        row.performedBatchServiceIds().stream().map(UUID::toString).toList());
    payload.put(
        "actualExaminationDate",
        row.actualExaminationDate() == null ? null : row.actualExaminationDate().toString());
    return payload;
  }

  private static StoredReceipt stored(ServiceReconciliationReceipt receipt) {
    return new StoredReceipt(
        receipt.importJobId(),
        receipt.batchId(),
        receipt.totalRows(),
        receipt.updatedParticipants(),
        receipt.unchangedParticipants(),
        receipt.performedItems(),
        receipt.completedAt().toString());
  }

  private static ServiceReconciliationReceipt toReceipt(StoredReceipt stored) {
    return new ServiceReconciliationReceipt(
        stored.importJobId(),
        stored.batchId(),
        stored.totalRows(),
        stored.updatedParticipants(),
        stored.unchangedParticipants(),
        stored.performedItems(),
        Instant.parse(stored.completedAt()));
  }

  /** JSONB shape of the receipt; the instant is kept as ISO text so replays are byte-identical. */
  record StoredReceipt(
      UUID importJobId,
      UUID batchId,
      int totalRows,
      int updatedParticipants,
      int unchangedParticipants,
      int performedItems,
      String completedAt) {}
}
