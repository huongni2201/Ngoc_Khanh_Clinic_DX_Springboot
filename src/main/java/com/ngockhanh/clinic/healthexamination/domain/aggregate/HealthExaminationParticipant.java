package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.exception.ServiceOutsideAgreementScope;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * A person registered in an agreement. The Participant holds the identity and employment snapshot,
 * the roster status and the service reconciliation. It knows nothing about when it is examined:
 * that is a set of session assignments, and attendance belongs to each assignment.
 */
@Getter
public class HealthExaminationParticipant {
  /**
   * Roster details of a Participant.
   *
   * @param participantCode system-generated code; {@code null} until the Participant is stored
   * @param identificationIssueDate date the identification number was issued, optional
   * @param identificationIssuePlace authority that issued it, optional
   * @param ethnicity optional
   * @param address residential address, optional
   * @param workplace employer or place of work, optional
   * @param note free note, optional
   */
  public record Roster(
      String participantCode,
      String fullName,
      LocalDate dateOfBirth,
      String sex,
      IdentificationNumber identificationNumber,
      LocalDate identificationIssueDate,
      String identificationIssuePlace,
      String ethnicity,
      String phone,
      String email,
      String address,
      String workplace,
      String departmentName,
      String positionName,
      String note) {
    /** Accepted values of the participant's sex. */
    public static final List<String> SEX_VALUES = List.of("MALE", "FEMALE", "OTHER", "UNKNOWN");

    /** Roster without the optional personal details, which stay empty. */
    public Roster(
        String participantCode,
        String fullName,
        LocalDate dateOfBirth,
        String sex,
        IdentificationNumber identificationNumber,
        String phone,
        String email,
        String departmentName,
        String positionName) {
      this(
          participantCode,
          fullName,
          dateOfBirth,
          sex,
          identificationNumber,
          null,
          null,
          null,
          phone,
          email,
          null,
          null,
          departmentName,
          positionName,
          null);
    }

    /** The same roster carrying the given system-generated participant code. */
    public Roster withParticipantCode(String code) {
      return new Roster(
          code,
          fullName,
          dateOfBirth,
          sex,
          identificationNumber,
          identificationIssueDate,
          identificationIssuePlace,
          ethnicity,
          phone,
          email,
          address,
          workplace,
          departmentName,
          positionName,
          note);
    }

    public Roster {
      if (fullName == null
          || fullName.isBlank()
          || fullName.length() > 200
          || dateOfBirth == null
          || sex == null
          || !SEX_VALUES.contains(sex)
          || identificationNumber == null
          || departmentName == null
          || departmentName.isBlank()
          || positionName == null
          || positionName.isBlank())
        throw new IllegalArgumentException(
            "Participant identity, department and position are required");
    }
  }

  public record Progress(
      AggregateId patientId,
      RosterStatus rosterStatus,
      ReconciliationStatus reconciliationStatus,
      AggregateId reconciledBy,
      Instant reconciledAt,
      Instant preparedAt) {}

  private final AggregateId id;
  private final AggregateId organizationId;
  private final AggregateId agreementId;
  private Roster roster;
  private AggregateId patientId;
  private RosterStatus rosterStatus;
  private ReconciliationStatus reconciliationStatus;
  private AggregateId reconciledBy;
  private Instant reconciledAt;
  private final AggregateId importJobId;
  private final Integer sourceRowNumber;
  private Instant preparedAt;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final long rowVersion;
  private final Map<AggregateId, HealthExaminationParticipantService> services = new HashMap<>();

  private HealthExaminationParticipant(
      AggregateId id,
      AggregateId organization,
      AggregateId agreement,
      Roster roster,
      Progress p,
      AggregateId importJob,
      Integer sourceRow,
      Instant createdAt,
      Instant updatedAt,
      long version,
      List<HealthExaminationParticipantService> services) {
    if (id == null
        || organization == null
        || agreement == null
        || roster == null
        || p == null
        || createdAt == null
        || updatedAt == null
        || version < 0
        || (importJob == null) != (sourceRow == null)
        || (sourceRow != null && sourceRow < 1)
        || p.rosterStatus() == null
        || p.reconciliationStatus() == null
        || (p.reconciliationStatus() == ReconciliationStatus.RECONCILED
            && (p.reconciledBy() == null || p.reconciledAt() == null)))
      throw new IllegalArgumentException("Invalid participant state");
    this.id = id;
    this.organizationId = organization;
    this.agreementId = agreement;
    this.roster = roster;
    this.patientId = p.patientId();
    this.rosterStatus = p.rosterStatus();
    this.reconciliationStatus = p.reconciliationStatus();
    this.reconciledBy = p.reconciledBy();
    this.reconciledAt = p.reconciledAt();
    this.preparedAt = p.preparedAt();
    this.importJobId = importJob;
    this.sourceRowNumber = sourceRow;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.rowVersion = version;
    for (var service : services) {
      if (!agreement.equals(service.agreementId())
          || !id.equals(service.participantId())
          || this.services.putIfAbsent(service.agreementServiceId(), service) != null)
        throw new IllegalArgumentException("Invalid participant service scope");
    }
  }

  public static HealthExaminationParticipant create(
      AggregateId id,
      AggregateId organization,
      AggregateId agreement,
      Roster roster,
      AggregateId importJob,
      Integer sourceRow,
      Instant now) {
    return restore(
        id,
        organization,
        agreement,
        roster,
        new Progress(null, RosterStatus.ACTIVE, ReconciliationStatus.PENDING, null, null, null),
        importJob,
        sourceRow,
        now,
        now,
        0,
        List.of());
  }

  public static HealthExaminationParticipant restore(
      AggregateId id,
      AggregateId organization,
      AggregateId agreement,
      Roster roster,
      Progress progress,
      AggregateId importJob,
      Integer sourceRow,
      Instant createdAt,
      Instant updatedAt,
      long version,
      List<HealthExaminationParticipantService> services) {
    return new HealthExaminationParticipant(
        id,
        organization,
        agreement,
        roster,
        progress,
        importJob,
        sourceRow,
        createdAt,
        updatedAt,
        version,
        services);
  }

  public void prepare(AggregateId patient, Instant at) {
    requireActive();
    if (patient == null || at == null)
      throw new IllegalArgumentException("Preparation identity and time are required");
    if (patientId != null && !patientId.equals(patient))
      throw new DomainRuleViolation("Patient relink forbidden");
    patientId = patient;
    if (preparedAt == null) preparedAt = at;
  }

  /**
   * Replaces the roster details of an active Participant. Reconciliation and the Patient link are
   * untouched.
   *
   * @throws DomainRuleViolation when the Participant is cancelled, or the identification number
   *     changes after the Participant was linked to a Patient
   * @throws IllegalArgumentException when the roster is missing
   */
  public void updateRoster(Roster next) {
    requireActive();
    if (next == null) throw new IllegalArgumentException("Roster is required");
    if (patientId != null && !roster.identificationNumber().equals(next.identificationNumber()))
      throw new DomainRuleViolation("Identification number is locked after visit preparation");
    roster = next;
  }

  /**
   * Cancels an active Participant. Nothing but the roster status changes; the row, its provenance
   * and its history are kept. Whether the Participant has attended a session is a fact of the
   * assignments, so the caller checks it before calling.
   *
   * @throws DomainRuleViolation when the Participant is already cancelled, was prepared for a
   *     visit, or has its services reconciled
   */
  public void cancel() {
    requireActive();
    if (preparedAt != null || reconciliationStatus == ReconciliationStatus.RECONCILED)
      throw new DomainRuleViolation(
          "Participant cannot be cancelled after preparation or attendance");
    rosterStatus = RosterStatus.CANCELLED;
  }

  /**
   * Returns a cancelled Participant to the active roster. Only the roster status changes; the row,
   * its provenance and reconciliation stay as they were at cancellation. Its cancelled assignments
   * stay cancelled: the Participant is scheduled again explicitly.
   *
   * @throws DomainRuleViolation when the Participant is not cancelled, or (defensively) was
   *     prepared or reconciled
   */
  public void reactivate() {
    if (rosterStatus != RosterStatus.CANCELLED)
      throw new DomainRuleViolation("Participant is not cancelled");
    if (preparedAt != null || reconciliationStatus == ReconciliationStatus.RECONCILED)
      throw new DomainRuleViolation(
          "Participant cannot be reactivated after preparation or attendance");
    rosterStatus = RosterStatus.ACTIVE;
  }

  /** Whether the Participant was added by hand rather than created by an Excel import. */
  public boolean isManual() {
    return importJobId == null;
  }

  public void reconcileServices(
      List<HealthExaminationParticipantService> actualServices,
      Collection<HealthExaminationAgreementService> scope,
      AggregateId actor,
      Instant at) {
    requireActive();
    if (actualServices == null || scope == null || actor == null || at == null)
      throw new IllegalArgumentException("Reconciliation details are required");
    var allowed =
        scope.stream().collect(Collectors.toMap(service -> service.id(), service -> service));
    Map<AggregateId, HealthExaminationParticipantService> reviewed = new HashMap<>();
    for (var s : actualServices) {
      if (!agreementId.equals(s.agreementId())
          || !id.equals(s.participantId())
          || !allowed.containsKey(s.agreementServiceId())
          || !agreementId.equals(allowed.get(s.agreementServiceId()).agreementId()))
        throw new ServiceOutsideAgreementScope();
      var existing = services.get(s.agreementServiceId());
      var agreementService = allowed.get(s.agreementServiceId());
      if (existing == null
          && (!agreementService.active()
              || !s.performed()
              || s.unitPriceSnapshot()
                      .amount()
                      .compareTo(agreementService.negotiatedPrice().amount())
                  != 0))
        throw new DomainRuleViolation(
            "New performed services must use an active agreement service and its negotiated price");
      if (existing != null
          && (!existing.id().equals(s.id())
              || existing.unitPriceSnapshot().amount().compareTo(s.unitPriceSnapshot().amount())
                  != 0
              || (existing.serviceRequestId() != null
                  && !existing.serviceRequestId().equals(s.serviceRequestId()))))
        throw new DomainRuleViolation(
            "Original service identity and price snapshot must be retained");
      if (!actor.equals(s.recordedBy()) || !at.equals(s.recordedAt()))
        throw new IllegalArgumentException(
            "Service reconciliation audit must match the participant");
      if (reviewed.putIfAbsent(s.agreementServiceId(), s) != null)
        throw new DomainRuleViolation("Duplicate reconciled service");
    }
    if (!reviewed.keySet().containsAll(services.keySet()))
      throw new DomainRuleViolation("Existing service rows must be retained when unchecked");
    services.clear();
    services.putAll(reviewed);
    reconciliationStatus = ReconciliationStatus.RECONCILED;
    reconciledBy = actor;
    reconciledAt = at;
  }

  public List<HealthExaminationParticipantService> services() {
    return services.values().stream()
        .sorted(Comparator.comparing(s -> s.id().value().toString()))
        .toList();
  }

  /**
   * Identifiers of the agreement services whose reconciliation row is recorded as performed. Rows
   * that were unchecked ({@code performed = false}) are kept but are not part of the result.
   */
  public Set<AggregateId> performedAgreementServiceIds() {
    return services.values().stream()
        .filter(HealthExaminationParticipantService::performed)
        .map(HealthExaminationParticipantService::agreementServiceId)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Identifiers of every agreement service that has a reconciliation row, performed or not. */
  public Set<AggregateId> reconciledAgreementServiceIds() {
    return Set.copyOf(services.keySet());
  }

  private void requireActive() {
    if (rosterStatus != RosterStatus.ACTIVE)
      throw new DomainRuleViolation("Participant is cancelled");
  }
}
