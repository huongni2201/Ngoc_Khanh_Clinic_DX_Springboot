package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.*;
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

  private List<HealthExaminationBatchDay> days(UUID id) {
    return mapper.findDays(id).stream()
        .map(r -> new HealthExaminationBatchDay(r.id(), r.examinationDate()))
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
    insertServices(batch.services());
  }

  private void saveDays(HealthExaminationBatch batch) {
    var rows =
        batch.days().stream()
            .map(
                d ->
                    HealthExaminationBatchDayRecord.builder()
                        .id(d.id())
                        .batchId(batch.id().value())
                        .examinationDate(d.examinationDate())
                        .build())
            .toList();
    if (mapper.insertDays(rows) != rows.size())
      throw new IllegalStateException("Batch days were not inserted");
  }

  private void insertServices(List<HealthExaminationBatchService> services) {
    var rows =
        services.stream()
            .map(
                s ->
                    HealthExaminationBatchServiceRecord.builder()
                        .id(s.id().value())
                        .batchId(s.batchId().value())
                        .serviceId(s.serviceId().value())
                        .referencePriceSnapshot(s.referencePriceSnapshot().amount())
                        .negotiatedPrice(s.negotiatedPrice().amount())
                        .displayOrder(s.displayOrder())
                        .active(s.active())
                        .createdAt(null)
                        .updatedAt(null)
                        .rowVersion(s.rowVersion())
                        .build())
            .toList();
    if (mapper.insertServices(rows) != rows.size())
      throw new IllegalStateException("Batch services were not inserted");
  }

  private HealthExaminationBatchRecord record(HealthExaminationBatch b, UUID actor) {
    return HealthExaminationBatchRecord.builder()
        .id(b.id().value())
        .organizationId(b.organizationId().value())
        .batchCode(b.code())
        .name(b.name())
        .examinationSiteType(b.site().type().name())
        .examinationSiteName(b.site().name())
        .examinationSiteAddress(b.site().address())
        .status(b.status().name())
        .createdBy(actor)
        .createdAt(null)
        .updatedAt(null)
        .rowVersion(b.rowVersion())
        .build();
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
