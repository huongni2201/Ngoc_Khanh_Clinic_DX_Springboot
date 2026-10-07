package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public class HealthExaminationBatch {
  private final AggregateId id;
  private final AggregateId organizationId;
  private String code;
  private String name;
  private ExaminationSite site;
  private List<HealthExaminationBatchDay> days;
  private List<HealthExaminationBatchService> services;
  private BatchStatus status;
  private final long rowVersion;
  private Instant deletedAt;

  private HealthExaminationBatch(
      AggregateId id,
      AggregateId organizationId,
      String code,
      String name,
      ExaminationSite site,
      List<HealthExaminationBatchDay> days,
      List<HealthExaminationBatchService> services,
      BatchStatus status,
      long rowVersion,
      Instant deletedAt) {
    if (id == null || organizationId == null || status == null || rowVersion < 0)
      throw new IllegalArgumentException("Invalid batch");
    this.id = id;
    this.organizationId = organizationId;
    this.status = status;
    this.rowVersion = rowVersion;
    this.deletedAt = deletedAt;
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
        id, organizationId, code, name, site, days, services, BatchStatus.DRAFT, 0, null);
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
    return restoreConfiguration(
        id, organizationId, code, name, site, days, services, status, rowVersion, null);
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
      long rowVersion,
      Instant deletedAt) {
    return new HealthExaminationBatch(
        id, organizationId, code, name, site, days, services, status, rowVersion, deletedAt);
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

  /**
   * Replaces the whole configuration of a draft batch.
   *
   * <p>The identifier, organization, status and row version are not changed here; the repository
   * increments the version when the change is stored.
   *
   * @throws DomainRuleViolation when the batch is deleted or not a draft, or the services are
   *     invalid
   * @throws IllegalArgumentException when the code, name, site or days are invalid
   */
  public void updateDraft(
      String code,
      String name,
      ExaminationSite site,
      List<HealthExaminationBatchDay> days,
      List<HealthExaminationBatchService> services) {
    requireDraft();
    configure(code, name, site, days, services);
  }

  /**
   * Marks a draft batch as deleted at the given time. The batch and its history are kept.
   *
   * @throws DomainRuleViolation when the batch is already deleted or is not a draft
   * @throws IllegalArgumentException when the time is missing
   */
  public void softDelete(Instant at) {
    if (at == null) throw new IllegalArgumentException("Deletion time is required");
    requireDraft();
    deletedAt = at;
  }

  /**
   * Checks that the batch is a draft that has not been deleted.
   *
   * @throws DomainRuleViolation otherwise
   */
  public void requireDraft() {
    if (deletedAt != null) throw new DomainRuleViolation("Batch is deleted");
    if (status != BatchStatus.DRAFT) throw new DomainRuleViolation("Batch is not a draft");
  }

  /**
   * Whether new Participants may still be added to the roster: only a batch that is not deleted
   * and is a draft or ready accepts them.
   */
  public boolean acceptsParticipantImport() {
    return deletedAt == null && (status == BatchStatus.DRAFT || status == BatchStatus.READY);
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

  /** Time the batch was soft-deleted, or {@code null} while it is not deleted. */
  public Instant deletedAt() {
    return deletedAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}
