package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.*;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisHealthExaminationBatchRepository implements HealthExaminationBatchRepository {
  private final HealthExaminationBatchMyBatisMapper mapper;

  private List<HealthExaminationBatchDay> days(UUID id) {
    return mapper.findDays(id).stream()
        .map(r -> new HealthExaminationBatchDay(r.id(), r.examinationDate()))
        .toList();
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
                        r.rowVersion(),
                        r.deletedAt()),
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
        .deletedAt(b.deletedAt())
        .build();
  }

  @Override
  public void update(HealthExaminationBatch batch, long expectedRowVersion) {
    if (mapper.updateHeader(record(batch, null), expectedRowVersion) != 1)
      throw new ConcurrentUpdateException();
    syncDays(batch);
    syncServices(batch);
  }

  private void syncDays(HealthExaminationBatch batch) {
    UUID batchId = batch.id().value();
    Set<UUID> existing =
        mapper.findDays(batchId).stream()
            .map(HealthExaminationBatchDayRecord::id)
            .collect(Collectors.toSet());
    Set<UUID> target =
        batch.days().stream().map(HealthExaminationBatchDay::id).collect(Collectors.toSet());
    List<UUID> removed = existing.stream().filter(id -> !target.contains(id)).toList();
    if (!removed.isEmpty() && mapper.deleteDays(batchId, removed) != removed.size())
      throw new ConcurrentUpdateException();
    var added = batch.days().stream().filter(day -> !existing.contains(day.id())).toList();
    if (added.isEmpty()) return;
    var rows =
        added.stream()
            .map(
                d ->
                    HealthExaminationBatchDayRecord.builder()
                        .id(d.id())
                        .batchId(batchId)
                        .examinationDate(d.examinationDate())
                        .build())
            .toList();
    if (mapper.insertDays(rows) != rows.size())
      throw new IllegalStateException("Batch days were not inserted");
  }

  /**
   * Applies the service differences without recreating services that keep their identifier.
   *
   * <p>Display order is unique per batch, so a retained service whose order changes is first moved
   * above every old and new order and then written to its final order. The temporary move does not
   * change the row version; the final write increments it once.
   */
  private void syncServices(HealthExaminationBatch batch) {
    UUID batchId = batch.id().value();
    Map<UUID, HealthExaminationBatchServiceRecord> existing = new HashMap<>();
    for (var row : mapper.findServices(batchId)) existing.put(row.id(), row);
    var target = batch.services();
    Set<UUID> targetIds =
        target.stream().map(s -> s.id().value()).collect(Collectors.toSet());

    List<UUID> removed = existing.keySet().stream().filter(id -> !targetIds.contains(id)).toList();
    if (!removed.isEmpty() && mapper.deleteServices(batchId, removed) != removed.size())
      throw new ConcurrentUpdateException();

    var retained = target.stream().filter(s -> existing.containsKey(s.id().value())).toList();
    var reordered =
        retained.stream()
            .filter(s -> existing.get(s.id().value()).displayOrder() != s.displayOrder())
            .toList();
    long highestOrder = 0;
    for (var row : existing.values()) highestOrder = Math.max(highestOrder, row.displayOrder());
    for (var service : target) highestOrder = Math.max(highestOrder, service.displayOrder());
    for (var service : reordered) {
      long temporaryOrder = highestOrder + service.displayOrder();
      if (temporaryOrder > Integer.MAX_VALUE)
        throw new IllegalStateException("Batch service display order is out of range");
      if (mapper.moveServiceOrder(batchId, service.id().value(), (int) temporaryOrder) != 1)
        throw new ConcurrentUpdateException();
    }
    for (var service : retained) {
      var current = existing.get(service.id().value());
      boolean priceChanged =
          current.negotiatedPrice().compareTo(service.negotiatedPrice().amount()) != 0;
      boolean orderChanged = current.displayOrder() != service.displayOrder();
      if (!priceChanged && !orderChanged) continue;
      if (mapper.updateService(
              batchId,
              service.id().value(),
              service.negotiatedPrice().amount(),
              service.displayOrder(),
              current.rowVersion())
          != 1) throw new ConcurrentUpdateException();
    }

    var added = target.stream().filter(s -> !existing.containsKey(s.id().value())).toList();
    if (!added.isEmpty()) insertServices(added);
  }

  @Override
  public void softDelete(HealthExaminationBatch batch, long expectedRowVersion) {
    if (batch.deletedAt() == null)
      throw new IllegalArgumentException("Batch must be marked deleted before it is stored");
    if (mapper.softDelete(
            batch.id().value(),
            batch.organizationId().value(),
            expectedRowVersion,
            batch.deletedAt())
        != 1) throw new ConcurrentUpdateException();
  }

  @Override
  public Set<UUID> findReferencedDayIds(UUID batchId, Collection<UUID> dayIds) {
    if (dayIds.isEmpty()) return Set.of();
    return new HashSet<>(mapper.findReferencedDayIds(batchId, dayIds));
  }

  @Override
  public Set<UUID> findReferencedBatchServiceIds(UUID batchId, Collection<UUID> batchServiceIds) {
    if (batchServiceIds.isEmpty()) return Set.of();
    return new HashSet<>(mapper.findReferencedBatchServiceIds(batchId, batchServiceIds));
  }

  @Override
  public boolean hasParticipants(UUID batchId) {
    return mapper.hasParticipants(batchId);
  }

  @Override
  public List<BatchSummary> findPage(
      UUID org, long offset, int limit, String pattern, String key, String direction) {
    return mapper.findPage(org, offset, limit, pattern, key, direction).stream()
        .map(
            v ->
                new BatchSummary(
                    v.id(),
                    v.batchCode(),
                    v.batchName(),
                    v.startDate(),
                    v.endDate(),
                    v.status(),
                    v.createdAt(),
                    v.updatedAt(),
                    v.rowVersion()))
        .toList();
  }

  @Override
  public long count(UUID org, String pattern) {
    return mapper.count(org, pattern);
  }
}
