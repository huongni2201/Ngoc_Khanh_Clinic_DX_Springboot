package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchServiceRecord;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisHealthExaminationBatchRepository implements HealthExaminationBatchRepository {
  private final HealthExaminationBatchMyBatisMapper mapper;

  @Override
  public Optional<HealthExaminationBatchReference> findByIdAndOrganizationId(
      AggregateId batchId, AggregateId organizationId) {
    HealthExaminationBatchRecord record =
        mapper.findByIdAndOrganizationId(batchId.value(), organizationId.value());
    return Optional.ofNullable(record).map(Converter::toDomain);
  }

  @Override
  public Optional<HealthExaminationBatchReference> findByIdAndOrganizationIdForUpdate(
      AggregateId batchId, AggregateId organizationId) {
    HealthExaminationBatchRecord record =
        mapper.findByIdAndOrganizationIdForUpdate(batchId.value(), organizationId.value());
    return Optional.ofNullable(record).map(Converter::toDomain);
  }

  @Override
  public Optional<BatchDetails> findDetails(UUID org, UUID id, boolean lock) {
    return Optional.ofNullable(mapper.findScoped(org, id, lock))
        .map(
            r ->
                new BatchDetails(
                    HealthExaminationBatch.restoreConfiguration(
                        new AggregateId(r.id()),
                        new AggregateId(r.organizationId()),
                        r.batchCode(),
                        r.batchName(),
                        r.startDate(),
                        r.endDate(),
                        r.reason(),
                        r.payerType(),
                        new com.ngockhanh.clinic.healthexamination.domain.valueobject
                            .ExaminationSite(
                            com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType
                                .valueOf(r.examinationSiteType()),
                            r.examinationSiteName(),
                            r.examinationSiteAddress()),
                        new AggregateId(r.masterTemplateVersionId()),
                        BatchStatus.valueOf(r.status()),
                        mapper.findServices(id).stream()
                            .map(
                                v ->
                                    com.ngockhanh.clinic.healthexamination.domain.entity
                                        .HealthExaminationBatchService.create(
                                        new AggregateId(v.id()),
                                        new AggregateId(v.serviceId()),
                                        new AggregateId(v.healthExaminationBatchId()),
                                        v.serviceCodeSnapshot(),
                                        v.serviceNameSnapshot(),
                                        new com.ngockhanh.clinic.healthexamination.domain
                                            .valueobject.Money(
                                            v.negotiatedUnitPrice(), v.currency()),
                                        v.documentTemplateVersionId() == null
                                            ? null
                                            : new AggregateId(v.documentTemplateVersionId()),
                                        v.displayOrder(),
                                        v.status()))
                            .toList(),
                        r.finalizedAt(),
                        r.closedAt()),
                    r.createdByUserId(),
                    r.createdAt(),
                    r.updatedAt()));
  }

  @Override
  public void insert(HealthExaminationBatch batch, UUID actor) {
    if (mapper.insert(record(batch, actor)) != 1)
      throw new IllegalStateException("Batch was not inserted");
    saveServices(batch.services());
  }

  @Override
  public void update(HealthExaminationBatch batch) {
    var retained = batch.services().stream().map(s -> s.id().value()).toList();
    if (batch.status() != BatchStatus.DELETED
        && mapper.hasReferencedRemoved(batch.id().value(), retained))
      throw new BusinessRuleException("Batch service has dependent records");
    if (mapper.update(record(batch, null)) != 1) throw new ConcurrentUpdateException();
    if (batch.status() != BatchStatus.DELETED) {
      mapper.deleteRemoved(batch.id().value(), retained);
      saveServices(batch.services());
    }
  }

  private void saveServices(List<HealthExaminationBatchService> services) {
    if (services.isEmpty()) return;
    var records =
        services.stream()
            .map(
                s ->
                    new HealthExaminationBatchServiceRecord(
                        s.id().value(),
                        s.batchId().value(),
                        s.serviceId().value(),
                        s.templateVersionId() == null ? null : s.templateVersionId().value(),
                        s.serviceCode(),
                        s.serviceName(),
                        s.negotiatedPrice().amount(),
                        s.negotiatedPrice().currency(),
                        s.displayOrder(),
                        s.status(),
                        null,
                        null))
            .toList();
    if (mapper.upsertServices(records) != records.size())
      throw new IllegalStateException("Batch services were not saved");
  }

  private HealthExaminationBatchRecord record(HealthExaminationBatch b, UUID actor) {
    return new HealthExaminationBatchRecord(
        b.id().value(),
        b.organizationId().value(),
        b.code(),
        b.name(),
        b.startDate(),
        b.endDate(),
        b.reason(),
        b.payerType(),
        b.site().type().name(),
        b.site().name(),
        b.site().address(),
        b.masterTemplateVersionId().value(),
        b.status().name(),
        b.finalizedAt(),
        b.closedAt(),
        actor,
        null,
        null);
  }

  @Override
  public boolean hasDependents(UUID id) {
    return mapper.hasDependents(id);
  }

  @Override
  public List<BatchSummary> findPage(
      UUID org, long offset, int limit, String pattern, String key, String direction) {
    return mapper.findPage(org, offset, limit, pattern, key, direction);
  }

  @Override
  public long count(UUID org, String pattern) {
    return mapper.count(org, pattern);
  }

  private static final class Converter {
    static HealthExaminationBatchReference toDomain(HealthExaminationBatchRecord record) {
      return new HealthExaminationBatchReference(
          new AggregateId(record.id()),
          new AggregateId(record.organizationId()),
          record.startDate(),
          BatchStatus.valueOf(record.status()));
    }
  }
}
