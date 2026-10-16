package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The agreement with an organization: which services are offered, at which negotiated price, and
 * for how long. It owns no examination day and no site; those belong to sessions.
 *
 * <p>The services change only while the agreement is a draft; once it is active they are fixed. A
 * line retired earlier stays in the list so recorded prices keep their source.
 */
public class HealthExaminationAgreement {
  private final AggregateId id;
  private final AggregateId organizationId;
  private String code;
  private String name;
  private LocalDate validFrom;
  private LocalDate validTo;
  private List<HealthExaminationAgreementService> services;
  private AgreementStatus status;
  private final long rowVersion;
  private Instant deletedAt;

  private HealthExaminationAgreement(
      AggregateId id,
      AggregateId organizationId,
      String code,
      String name,
      LocalDate validFrom,
      LocalDate validTo,
      List<HealthExaminationAgreementService> services,
      AgreementStatus status,
      long rowVersion,
      Instant deletedAt) {
    if (id == null || organizationId == null || status == null || rowVersion < 0)
      throw new IllegalArgumentException("Invalid agreement");
    this.id = id;
    this.organizationId = organizationId;
    this.status = status;
    this.rowVersion = rowVersion;
    this.deletedAt = deletedAt;
    configure(code, name, validFrom, validTo, services);
  }

  public static HealthExaminationAgreement createDraft(
      AggregateId id,
      AggregateId organizationId,
      String code,
      String name,
      LocalDate validFrom,
      LocalDate validTo,
      List<HealthExaminationAgreementService> services) {
    return new HealthExaminationAgreement(
        id,
        organizationId,
        code,
        name,
        validFrom,
        validTo,
        services,
        AgreementStatus.DRAFT,
        0,
        null);
  }

  public static HealthExaminationAgreement restoreConfiguration(
      AggregateId id,
      AggregateId organizationId,
      String code,
      String name,
      LocalDate validFrom,
      LocalDate validTo,
      List<HealthExaminationAgreementService> services,
      AgreementStatus status,
      long rowVersion,
      Instant deletedAt) {
    return new HealthExaminationAgreement(
        id,
        organizationId,
        code,
        name,
        validFrom,
        validTo,
        services,
        status,
        rowVersion,
        deletedAt);
  }

  private void configure(
      String code,
      String name,
      LocalDate validFrom,
      LocalDate validTo,
      List<HealthExaminationAgreementService> services) {
    if (code == null
        || code.isBlank()
        || code.trim().length() > 50
        || name == null
        || name.isBlank()
        || name.trim().length() > 300
        || (validFrom != null && validTo != null && validFrom.isAfter(validTo)))
      throw new IllegalArgumentException("Invalid agreement configuration");
    if (services == null || services.isEmpty())
      throw new DomainRuleViolation("Agreement needs service scope");
    requireConsistentServices(services);
    this.code = code.trim();
    this.name = name.trim();
    this.validFrom = validFrom;
    this.validTo = validTo;
    this.services = sorted(services);
  }

  private void requireConsistentServices(List<HealthExaminationAgreementService> candidates) {
    Set<AggregateId> ids = new HashSet<>();
    Set<AggregateId> offered = new HashSet<>();
    Set<Integer> orders = new HashSet<>();
    for (var service : candidates)
      if (service == null
          || !id.equals(service.agreementId())
          || !ids.add(service.id())
          || (service.active() && !offered.add(service.serviceId()))
          || !orders.add(service.displayOrder()))
        throw new DomainRuleViolation("Invalid or duplicate agreement service");
  }

  private static List<HealthExaminationAgreementService> sorted(
      List<HealthExaminationAgreementService> services) {
    return services.stream()
        .sorted(
            Comparator.comparingInt(HealthExaminationAgreementService::displayOrder)
                .thenComparing(s -> s.id().value()))
        .toList();
  }

  /**
   * Replaces the whole configuration of a draft agreement.
   *
   * <p>The identifier, organization, code, status and row version are not changed here; the
   * repository increments the version when the change is stored.
   *
   * @throws DomainRuleViolation when the agreement is deleted or not a draft, or the services are
   *     invalid
   * @throws IllegalArgumentException when the name or the validity period is invalid
   */
  public void updateDraft(
      String name,
      LocalDate validFrom,
      LocalDate validTo,
      List<HealthExaminationAgreementService> services) {
    requireDraft();
    configure(code, name, validFrom, validTo, services);
  }

  /**
   * Marks the agreement as deleted at the given time. The agreement and its history are kept.
   *
   * @throws DomainRuleViolation when the agreement is already deleted or is not a draft
   * @throws IllegalArgumentException when the time is missing
   */
  public void softDelete(Instant at) {
    if (at == null) throw new IllegalArgumentException("Deletion time is required");
    requireDraft();
    deletedAt = at;
  }

  /**
   * Checks that the agreement is a draft that has not been deleted.
   *
   * @throws DomainRuleViolation otherwise
   */
  public void requireDraft() {
    if (deletedAt != null) throw new DomainRuleViolation("Agreement is deleted");
    if (status != AgreementStatus.DRAFT) throw new DomainRuleViolation("Agreement is not a draft");
  }

  /**
   * Whether new Participants may still be added to the roster: only an agreement that is not
   * deleted and is a draft or active accepts them.
   */
  public boolean acceptsParticipantImport() {
    return acceptsParticipantChanges();
  }

  /**
   * Whether the roster, the services and the examination details may still be changed: an agreement
   * that is not deleted and is a draft or active.
   */
  public boolean acceptsParticipantChanges() {
    return deletedAt == null
        && (status == AgreementStatus.DRAFT || status == AgreementStatus.ACTIVE);
  }

  /** Starts the agreement: DRAFT to ACTIVE. */
  public void activate() {
    transition(AgreementStatus.DRAFT, AgreementStatus.ACTIVE);
  }

  /** Ends the agreement: ACTIVE to ENDED. The roster and the recorded history stay readable. */
  public void end() {
    transition(AgreementStatus.ACTIVE, AgreementStatus.ENDED);
  }

  private void transition(AgreementStatus expected, AgreementStatus next) {
    if (deletedAt != null) throw new DomainRuleViolation("Agreement is deleted");
    if (status != expected) throw new DomainRuleViolation("Invalid agreement transition");
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

  /** First day the agreement applies, or {@code null} when it has no lower bound. */
  public LocalDate validFrom() {
    return validFrom;
  }

  /** Last day the agreement applies, or {@code null} when it has no upper bound. */
  public LocalDate validTo() {
    return validTo;
  }

  /** Every service line, retired ones included, in display order. */
  public List<HealthExaminationAgreementService> services() {
    return services;
  }

  /** The service lines that are still offered. */
  public List<HealthExaminationAgreementService> activeServices() {
    return services.stream().filter(HealthExaminationAgreementService::active).toList();
  }

  public HealthExaminationAgreementService service(AggregateId serviceLineId) {
    return services.stream().filter(s -> s.id().equals(serviceLineId)).findFirst().orElse(null);
  }

  public AgreementStatus status() {
    return status;
  }

  public long rowVersion() {
    return rowVersion;
  }

  /** Time the agreement was soft-deleted, or {@code null} while it is not deleted. */
  public Instant deletedAt() {
    return deletedAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}
