package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import java.time.LocalDate;
import java.util.*;

public final class HealthExaminationBatch {
  private final AggregateId id;
  private final AggregateId organizationId;
  private String code;
  private String name;
  private ExaminationSite site;
  private List<HealthExaminationBatchDay> days;
  private List<HealthExaminationBatchService> services;
  private BatchStatus status;
  private final long rowVersion;

  private HealthExaminationBatch(
      AggregateId id,
      AggregateId organizationId,
      String code,
      String name,
      ExaminationSite site,
      List<HealthExaminationBatchDay> days,
      List<HealthExaminationBatchService> services,
      BatchStatus status,
      long rowVersion) {
    if (id == null || organizationId == null || status == null || rowVersion < 0)
      throw new IllegalArgumentException("Invalid batch");
    this.id = id;
    this.organizationId = organizationId;
    this.status = status;
    this.rowVersion = rowVersion;
    configure(code, name, site, days, services);
  }

  public static HealthExaminationBatch createDraft(
      AggregateId id,
      AggregateId organizationId,
      String code,
      String name,
      ExaminationSite site,
      List<HealthExaminationBatchDay> days,
      List<HealthExaminationBatchService> services) {
    return new HealthExaminationBatch(
        id, organizationId, code, name, site, days, services, BatchStatus.DRAFT, 0);
  }

  public static HealthExaminationBatch restoreConfiguration(
      AggregateId id,
      AggregateId organizationId,
      String code,
      String name,
      ExaminationSite site,
      List<HealthExaminationBatchDay> days,
      List<HealthExaminationBatchService> services,
      BatchStatus status,
      long rowVersion) {
    return new HealthExaminationBatch(
        id, organizationId, code, name, site, days, services, status, rowVersion);
  }

  private void configure(
      String code,
      String name,
      ExaminationSite site,
      List<HealthExaminationBatchDay> days,
      List<HealthExaminationBatchService> services) {
    if (code == null
        || code.isBlank()
        || code.trim().length() > 50
        || name == null
        || name.isBlank()
        || name.trim().length() > 300
        || site == null
        || days == null
        || days.isEmpty()) throw new IllegalArgumentException("Invalid batch configuration");
    Set<UUID> dayIds = new HashSet<>();
    Set<LocalDate> dates = new HashSet<>();
    for (var day : days)
      if (day == null || !dayIds.add(day.id()) || !dates.add(day.examinationDate()))
        throw new IllegalArgumentException("Duplicate batch day");
    if (services == null || services.isEmpty())
      throw new DomainRuleViolation("Batch needs service scope");
    Set<AggregateId> ids = new HashSet<>();
    Set<AggregateId> catalogIds = new HashSet<>();
    Set<Integer> orders = new HashSet<>();
    for (var service : services)
      if (service == null
          || !id.equals(service.batchId())
          || !ids.add(service.id())
          || !catalogIds.add(service.serviceId())
          || !orders.add(service.displayOrder()))
        throw new DomainRuleViolation("Invalid or duplicate batch service");
    this.code = code.trim();
    this.name = name.trim();
    this.site = site;
    this.days =
        days.stream()
            .sorted(
                Comparator.comparing(HealthExaminationBatchDay::examinationDate)
                    .thenComparing(HealthExaminationBatchDay::id))
            .toList();
    this.services =
        services.stream()
            .sorted(
                Comparator.comparingInt(HealthExaminationBatchService::displayOrder)
                    .thenComparing(s -> s.id().value()))
            .toList();
  }

  public void markReady() {
    transition(BatchStatus.DRAFT, BatchStatus.READY);
  }

  public void finalizeBatch() {
    transition(BatchStatus.READY, BatchStatus.FINALIZED);
  }

  public void close() {
    transition(BatchStatus.FINALIZED, BatchStatus.CLOSED);
  }

  private void transition(BatchStatus expected, BatchStatus next) {
    if (status != expected) throw new DomainRuleViolation("Invalid batch transition");
    status = next;
  }

  public AggregateId id() {
    return id;
  }

  public AggregateId organizationId() {
    return organizationId;
  }

  public String code() {
    return code;
  }

  public String name() {
    return name;
  }

  public ExaminationSite site() {
    return site;
  }

  public List<HealthExaminationBatchDay> days() {
    return days;
  }

  public LocalDate startDate() {
    return days.getFirst().examinationDate();
  }

  public LocalDate endDate() {
    return days.getLast().examinationDate();
  }

  public List<HealthExaminationBatchService> services() {
    return services;
  }

  public HealthExaminationBatchService service(AggregateId serviceId) {
    return services.stream().filter(s -> s.id().equals(serviceId)).findFirst().orElse(null);
  }

  public BatchStatus status() {
    return status;
  }

  public long rowVersion() {
    return rowVersion;
  }
}
