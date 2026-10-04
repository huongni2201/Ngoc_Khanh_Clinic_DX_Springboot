package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.integration.application.imports.ImportStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class MyBatisHealthExaminationImportJobRepository
    implements HealthExaminationImportJobRepository {
  private final ImportStore store;
  private final JsonMapper json;

  public Optional<HealthExaminationImportJob> findByIdAndBatchId(
      AggregateId id, AggregateId batch) {
    return find(id, batch, false);
  }

  public Optional<HealthExaminationImportJob> findByIdAndBatchIdForUpdate(
      AggregateId id, AggregateId batch) {
    return find(id, batch, true);
  }

  private Optional<HealthExaminationImportJob> find(
      AggregateId id, AggregateId batch, boolean lock) {
    return store
        .find(id.value(), batch.value(), lock)
        .filter(j -> "ORGANIZATION_PARTICIPANT".equals(j.importType()))
        .map(
            j -> {
              var cfg = json.readValue(j.configuration(), Configuration.class);
              var rows = store.rows(id.value()).stream().map(this::row).toList();
              return new HealthExaminationImportJob(
                  id,
                  batch,
                  AggregateId.of(j.createdBy()),
                  j.createdAt(),
                  cfg.selectedBatchDayIds().stream().map(AggregateId::of).toList(),
                  rows,
                  ImportStatus.valueOf(j.status()),
                  j.confirmedBy() == null ? null : AggregateId.of(j.confirmedBy()),
                  j.confirmedAt(),
                  j.cancelledAt(),
                  j.expiresAt(),
                  j.rowVersion(),
                  true,
                  j.confirmedResult() == null
                      ? null
                      : json.readValue(
                          j.confirmedResult(),
                          HealthExaminationImportJob.ConfirmationResult.class));
            });
  }

  private HealthExaminationImportRow row(ImportStore.Row r) {
    Payload p = json.readValue(r.normalizedPayload(), Payload.class);
    var row =
        new HealthExaminationImportRow(
            AggregateId.of(r.id()),
            r.rowNumber(),
            p.participantCode(),
            p.fullName(),
            p.dateOfBirth(),
            p.sex(),
            IdentificationNumber.of(p.identificationNumber()),
            p.phone(),
            p.email(),
            p.departmentName(),
            p.positionName(),
            List.of());
    row.assignDay(AggregateId.of(json.readValue(r.previewMetadata(), Preview.class).batchDayId()));
    if (r.committedResourceId() != null) row.resolve(AggregateId.of(r.committedResourceId()));
    return row;
  }

  public long countRowsByJobId(AggregateId id, String filter) {
    return filtered(id, filter).size();
  }

  public List<HealthExaminationImportRow> findRowsByJobId(
      AggregateId id, String filter, long offset, int limit) {
    return filtered(id, filter).stream().skip(offset).limit(limit).toList();
  }

  private List<HealthExaminationImportRow> filtered(AggregateId id, String filter) {
    if (filter != null && !List.of("VALID", "CREATE").contains(filter)) return List.of();
    return store.rows(id.value()).stream().map(this::row).toList();
  }

  public void save(HealthExaminationImportJob j) {
    var job =
        new ImportStore.Job(
            j.id().value(),
            j.type().name(),
            j.batchId().value(),
            json.writeValueAsString(
                new Configuration(
                    j.selectedBatchDayIds().stream().map(AggregateId::value).toList())),
            null,
            j.status().name(),
            j.createdByUserId().value(),
            j.confirmedByUserId() == null ? null : j.confirmedByUserId().value(),
            j.createdAt(),
            j.confirmedAt(),
            j.cancelledAt(),
            j.expiresAt(),
            j.confirmedResult() == null ? null : json.writeValueAsString(j.confirmedResult()),
            j.rowVersion());
    var rows =
        j.rows().stream()
            .map(
                r ->
                    new ImportStore.Row(
                        r.getId().value(),
                        j.id().value(),
                        r.getRowNumber(),
                        json.writeValueAsString(
                            new Payload(
                                r.getParticipantCode(),
                                r.getFullName(),
                                r.getDateOfBirth(),
                                r.getSex(),
                                r.getIdentificationNumber().value(),
                                r.getPhone(),
                                r.getEmail(),
                                r.getDepartmentName(),
                                r.getPositionName())),
                        json.writeValueAsString(new Preview(r.getBatchDayId().value())),
                        r.getResolvedBatchParticipantId() == null
                            ? null
                            : "HEALTH_EXAMINATION_BATCH_PARTICIPANT",
                        r.getResolvedBatchParticipantId() == null
                            ? null
                            : r.getResolvedBatchParticipantId().value(),
                        j.createdAt()))
            .toList();
    if (j.persisted()) store.update(job, rows, j.rowVersion());
    else store.insert(job, rows);
    j.markStored();
  }

  public record Configuration(List<UUID> selectedBatchDayIds) {}

  public record Preview(UUID batchDayId) {}

  public record Payload(
      String participantCode,
      String fullName,
      LocalDate dateOfBirth,
      String sex,
      String identificationNumber,
      String phone,
      String email,
      String departmentName,
      String positionName) {}
}
