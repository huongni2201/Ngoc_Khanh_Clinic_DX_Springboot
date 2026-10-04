package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.*;
import com.ngockhanh.clinic.shared.exception.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisHealthExaminationBatchRepository implements HealthExaminationBatchRepository {
  private final HealthExaminationBatchMyBatisMapper mapper;

  @Override
  public Optional<HealthExaminationBatchReference> findByIdAndOrganizationId(
      AggregateId id, AggregateId org) {
    return Optional.ofNullable(mapper.findByIdAndOrganizationId(id.value(), org.value()))
        .map(this::reference);
  }

  @Override
  public Optional<HealthExaminationBatchReference> findByIdAndOrganizationIdForUpdate(
      AggregateId id, AggregateId org) {
    return Optional.ofNullable(mapper.findByIdAndOrganizationIdForUpdate(id.value(), org.value()))
        .map(this::reference);
  }

  private List<BatchDay> days(UUID id) {
    return mapper.findDays(id).stream()
        .map(r -> new BatchDay(r.id(), r.examinationDate()))
        .toList();
  }

  private HealthExaminationBatchReference reference(HealthExaminationBatchRecord r) {
    return new HealthExaminationBatchReference(
        new AggregateId(r.id()),
        new AggregateId(r.organizationId()),
        days(r.id()),
        BatchStatus.valueOf(r.status()),
        r.rowVersion());
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
                        r.name(),
                        new ExaminationSite(
                            ExaminationSiteType.valueOf(r.examinationSiteType()),
                            r.examinationSiteName(),
                            r.examinationSiteAddress()),
                        days(id),
                        mapper.findServices(id).stream()
                            .map(
                                s ->
                                    new HealthExaminationBatchService(
                                        new AggregateId(s.id()),
                                        new AggregateId(s.serviceId()),
                                        new AggregateId(s.batchId()),
                                        new Money(s.referencePriceSnapshot(), "VND"),
                                        new Money(s.negotiatedPrice(), "VND"),
                                        s.displayOrder(),
                                        s.active(),
                                        s.rowVersion()))
                            .toList(),
                        BatchStatus.valueOf(r.status()),
                        r.rowVersion()),
                    r.createdBy(),
                    r.createdAt(),
                    r.updatedAt()));
  }

  @Override
  public void insert(HealthExaminationBatch batch, UUID actor) {
    if (mapper.insert(record(batch, actor)) != 1)
      throw new IllegalStateException("Batch was not inserted");
    saveDays(batch);
    saveServices(batch.services());
  }

  @Override
  public void update(HealthExaminationBatch batch) {
    var services = batch.services().stream().map(s -> s.id().value()).toList();
    var days = batch.days().stream().map(BatchDay::id).toList();
    if (mapper.hasReferencedRemoved(batch.id().value(), services)
        || mapper.hasReferencedRemovedDays(batch.id().value(), days))
      throw new BusinessRuleException("Batch day or service has dependent records");
    if (mapper.update(record(batch, null)) != 1) throw new ConcurrentUpdateException();
    mapper.deleteRemoved(batch.id().value(), services);
    mapper.deleteRemovedDays(batch.id().value(), days);
    // Move retained orders above the old range so swaps satisfy the immediate unique constraint.
    mapper.reserveDisplayOrders(batch.id().value(), batch.services().size());
    saveServices(batch.services());
    saveDays(batch);
  }

  private void saveDays(HealthExaminationBatch batch) {
    var rows =
        batch.days().stream()
            .map(
                d ->
                    new HealthExaminationBatchDayRecord(
                        d.id(), batch.id().value(), d.examinationDate()))
            .toList();
    mapper.insertDays(rows);
  }

  private void saveServices(List<HealthExaminationBatchService> services) {
    var rows =
        services.stream()
            .map(
                s ->
                    new HealthExaminationBatchServiceRecord(
                        s.id().value(),
                        s.batchId().value(),
                        s.serviceId().value(),
                        s.referencePriceSnapshot().amount(),
                        s.negotiatedPrice().amount(),
                        s.displayOrder(),
                        s.active(),
                        null,
                        null,
                        s.rowVersion()))
            .toList();
    // Inserts start at zero; an existing row receives a single increment when its final values
    // change.
    if (mapper.upsertServices(rows) != rows.size()) throw new ConcurrentUpdateException();
  }

  private HealthExaminationBatchRecord record(HealthExaminationBatch b, UUID actor) {
    return new HealthExaminationBatchRecord(
        b.id().value(),
        b.organizationId().value(),
        b.code(),
        b.name(),
        b.site().type().name(),
        b.site().name(),
        b.site().address(),
        b.status().name(),
        actor,
        null,
        null,
        b.rowVersion());
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
}
