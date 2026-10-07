package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.exception.ServiceOutsideBatchScope;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
public class HealthExaminationBatchParticipant {
  public record Roster(
      String participantCode,
      String fullName,
      LocalDate dateOfBirth,
      String sex,
      IdentificationNumber identificationNumber,
      String phone,
      String email,
      String departmentName,
      String positionName) {
    /** Accepted values of the participant's sex. */
    public static final List<String> SEX_VALUES = List.of("MALE", "FEMALE", "OTHER", "UNKNOWN");

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
      AttendanceStatus attendanceStatus,
      LocalDate actualExaminationDate,
      AggregateId attendanceRecordedBy,
      Instant attendanceRecordedAt,
      String attendanceNote,
      ReconciliationStatus reconciliationStatus,
      AggregateId reconciledBy,
      Instant reconciledAt,
      Instant preparedAt) {}

  private final AggregateId id;
  private final AggregateId batchId;
  private AggregateId batchDayId;
  private Roster roster;
  private AggregateId patientId;
  private RosterStatus rosterStatus;
  private AttendanceStatus attendanceStatus;
  private LocalDate actualExaminationDate;
  private AggregateId attendanceRecordedBy;
  private Instant attendanceRecordedAt;
  private String attendanceNote;
  private ReconciliationStatus reconciliationStatus;
  private AggregateId reconciledBy;
  private Instant reconciledAt;
  private final AggregateId importJobId;
  private final Integer sourceRowNumber;
  private Instant preparedAt;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final long rowVersion;
  private final Map<AggregateId, HealthExaminationBatchParticipantService> services =
      new HashMap<>();

  private HealthExaminationBatchParticipant(
      AggregateId id,
      AggregateId batch,
      AggregateId day,
      Roster roster,
      Progress p,
      AggregateId importJob,
      Integer sourceRow,
      Instant createdAt,
      Instant updatedAt,
      long version,
      List<HealthExaminationBatchParticipantService> services) {
    if (id == null
        || batch == null
        || day == null
        || roster == null
        || p == null
        || createdAt == null
        || updatedAt == null
        || version < 0
        || (importJob == null) != (sourceRow == null)
        || (sourceRow != null && sourceRow < 1)
        || p.rosterStatus() == null
        || p.attendanceStatus() == null
        || p.reconciliationStatus() == null
        || (p.attendanceStatus() == AttendanceStatus.ATTENDED)
            != (p.actualExaminationDate() != null)
        || (p.attendanceRecordedBy() == null) != (p.attendanceRecordedAt() == null)
        || (p.reconciliationStatus() == ReconciliationStatus.RECONCILED
            && (p.reconciledBy() == null || p.reconciledAt() == null)))
      throw new IllegalArgumentException("Invalid batch participant state");
    this.id = id;
    this.batchId = batch;
    this.batchDayId = day;
    this.roster = roster;
    this.patientId = p.patientId();
    this.rosterStatus = p.rosterStatus();
    this.attendanceStatus = p.attendanceStatus();
    this.actualExaminationDate = p.actualExaminationDate();
    this.attendanceRecordedBy = p.attendanceRecordedBy();
    this.attendanceRecordedAt = p.attendanceRecordedAt();
    this.attendanceNote = p.attendanceNote();
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
      if (!batch.equals(service.batchId())
          || !id.equals(service.batchParticipantId())
          || this.services.putIfAbsent(service.batchServiceId(), service) != null)
        throw new IllegalArgumentException("Invalid participant service scope");
    }
  }

  public static HealthExaminationBatchParticipant create(
      AggregateId id,
      AggregateId batch,
      AggregateId day,
      Roster roster,
      AggregateId importJob,
      Integer sourceRow,
      Instant now) {
    return restore(
        id,
        batch,
        day,
        roster,
        new Progress(
            null,
            RosterStatus.ACTIVE,
            AttendanceStatus.UNCONFIRMED,
            null,
            null,
            null,
            null,
            ReconciliationStatus.PENDING,
            null,
            null,
            null),
        importJob,
        sourceRow,
        now,
        now,
        0,
        List.of());
  }

  public static HealthExaminationBatchParticipant restore(
      AggregateId id,
      AggregateId batch,
      AggregateId day,
      Roster roster,
      Progress progress,
      AggregateId importJob,
      Integer sourceRow,
      Instant createdAt,
      Instant updatedAt,
      long version,
      List<HealthExaminationBatchParticipantService> services) {
    return new HealthExaminationBatchParticipant(
        id, batch, day, roster, progress, importJob, sourceRow, createdAt, updatedAt, version,
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

  public void recordAttendance(
      AttendanceStatus status, LocalDate actualDate, AggregateId actor, Instant at, String note) {
    requireActive();
    if (status == null
        || actor == null
        || at == null
        || (status == AttendanceStatus.ATTENDED) != (actualDate != null))
      throw new IllegalArgumentException(
          "Attended participants require an actual examination date");
    attendanceStatus = status;
    actualExaminationDate = actualDate;
    attendanceRecordedBy = actor;
    attendanceRecordedAt = at;
    attendanceNote = note;
  }

  /**
   * Replaces the roster details of an active Participant. Attendance, reconciliation and the
   * Patient link are untouched.
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
   * and its history are kept.
   *
   * @throws DomainRuleViolation when the Participant is already cancelled, was prepared for a
   *     visit, has attended, or has its services reconciled
   */
  public void cancel() {
    requireActive();
    if (preparedAt != null
        || attendanceStatus == AttendanceStatus.ATTENDED
        || reconciliationStatus == ReconciliationStatus.RECONCILED)
      throw new DomainRuleViolation(
          "Participant cannot be cancelled after preparation or attendance");
    rosterStatus = RosterStatus.CANCELLED;
  }

  /**
   * Returns a cancelled Participant to the active roster. Only the roster status changes; the row,
   * its provenance, attendance and reconciliation stay as they were at cancellation.
   *
   * @throws DomainRuleViolation when the Participant is not cancelled, or (defensively) was
   *     prepared, attended or reconciled
   */
  public void reactivate() {
    if (rosterStatus != RosterStatus.CANCELLED)
      throw new DomainRuleViolation("Participant is not cancelled");
    if (preparedAt != null
        || attendanceStatus == AttendanceStatus.ATTENDED
        || reconciliationStatus == ReconciliationStatus.RECONCILED)
      throw new DomainRuleViolation(
          "Participant cannot be reactivated after preparation or attendance");
    rosterStatus = RosterStatus.ACTIVE;
  }

  /** Whether the Participant was added by hand rather than created by an Excel import. */
  public boolean isManual() {
    return importJobId == null;
  }

  public void moveToDay(AggregateId day) {
    requireActive();
    if (day == null) throw new IllegalArgumentException("Examination day is required");
    batchDayId = day;
  }

  public void reconcileServices(
      List<HealthExaminationBatchParticipantService> actualServices,
      java.util.Collection<
              com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService>
          scope,
      AggregateId actor,
      Instant at) {
    requireActive();
    if (actualServices == null || scope == null || actor == null || at == null)
      throw new IllegalArgumentException("Reconciliation details are required");
    var allowed =
        scope.stream()
            .collect(
                java.util.stream.Collectors.toMap(service -> service.id(), service -> service));
    Map<AggregateId, HealthExaminationBatchParticipantService> reviewed = new HashMap<>();
    for (var s : actualServices) {
      if (!batchId.equals(s.batchId())
          || !id.equals(s.batchParticipantId())
          || !allowed.containsKey(s.batchServiceId())
          || !batchId.equals(allowed.get(s.batchServiceId()).batchId()))
        throw new ServiceOutsideBatchScope();
      var existing = services.get(s.batchServiceId());
      var batchService = allowed.get(s.batchServiceId());
      if (existing == null
          && (!batchService.active()
              || !s.performed()
              || s.unitPriceSnapshot().amount().compareTo(batchService.negotiatedPrice().amount())
                  != 0))
        throw new DomainRuleViolation(
            "New performed services must use an active batch service and its negotiated price");
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
      if (reviewed.putIfAbsent(s.batchServiceId(), s) != null)
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

  public List<HealthExaminationBatchParticipantService> services() {
    return services.values().stream()
        .sorted(java.util.Comparator.comparing(s -> s.id().value().toString()))
        .toList();
  }

  /**
   * Identifiers of the batch services whose reconciliation row is recorded as performed. Rows that
   * were unchecked ({@code performed = false}) are kept but are not part of the result.
   */
  public Set<AggregateId> performedBatchServiceIds() {
    return services.values().stream()
        .filter(HealthExaminationBatchParticipantService::performed)
        .map(HealthExaminationBatchParticipantService::batchServiceId)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Identifiers of every batch service that has a reconciliation row, performed or not. */
  public Set<AggregateId> reconciledBatchServiceIds() {
    return Set.copyOf(services.keySet());
  }

  private void requireActive() {
    if (rosterStatus != RosterStatus.ACTIVE)
      throw new DomainRuleViolation("Participant is cancelled");
  }
}
