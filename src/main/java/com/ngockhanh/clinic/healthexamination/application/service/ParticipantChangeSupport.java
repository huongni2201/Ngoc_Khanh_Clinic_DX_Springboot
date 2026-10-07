package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Steps shared by the manual Participant use cases: resolving and locking the batch, checking that
 * the roster may still change, resolving the examination day, building the roster and writing the
 * audit event. It holds no state and starts no transaction; the calling use case owns it.
 */
@Component
@RequiredArgsConstructor
public class ParticipantChangeSupport {
  static final String ENTITY_TYPE = "HEALTH_EXAMINATION_BATCH_PARTICIPANT";

  /** Business time zone used to decide what "today" is for a date of birth. */
  static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final HealthExaminationBatchParticipantRepository participants;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Loads the batch of an organization without locking it, for a read.
   *
   * @throws ResourceNotFoundException when the batch does not exist, is deleted or belongs to
   *     another organization
   */
  public HealthExaminationBatch batchForRead(UUID organizationId, UUID batchId) {
    return batches
        .findDetails(organizationId, batchId, false)
        .orElseThrow(() -> new ResourceNotFoundException("Participant"))
        .batch();
  }

  /**
   * Locks the batch header and checks that the roster may change. The lock order is batch, then
   * Participant, the same as the Excel import.
   *
   * @throws ResourceNotFoundException when the organization or the batch does not exist
   * @throws ConflictException when the organization is not active or the batch is not a draft or
   *     ready batch
   */
  public HealthExaminationBatch lockBatchForChange(UUID organizationId, UUID batchId) {
    var batch =
        batches
            .findDetails(organizationId, batchId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Participant"))
            .batch();
    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Participant"));
    if (organization.status() != OrganizationStatus.ACTIVE || !batch.acceptsParticipantChanges())
      throw new ConflictException("Batch does not accept Participant changes");
    return batch;
  }

  /**
   * Resolves an examination day of the batch.
   *
   * @throws ConflictException when the day does not belong to the batch
   */
  public AggregateId requireDay(HealthExaminationBatch batch, UUID batchDayId) {
    boolean belongs = batch.days().stream().anyMatch(day -> day.id().equals(batchDayId));
    if (!belongs) throw new ConflictException("Examination day is not a day of this batch");
    return AggregateId.of(batchDayId);
  }

  /** Date of an examination day of the batch. */
  public static LocalDate examinationDate(HealthExaminationBatch batch, AggregateId dayId) {
    return batch.days().stream()
        .filter(day -> day.id().equals(dayId.value()))
        .map(day -> day.examinationDate())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Participant day is not a day of its batch"));
  }

  /**
   * Builds a roster from the caller's fields, trimming text and turning blank optional text into
   * {@code null}.
   *
   * @throws IllegalArgumentException when a field breaks a roster rule or the identification number
   *     is not 1 to 20 digits
   */
  public static Roster roster(
      String participantCode,
      String fullName,
      LocalDate dateOfBirth,
      String sex,
      String identificationNumber,
      String phone,
      String email,
      String departmentName,
      String positionName) {
    return new Roster(
        blankToNull(participantCode),
        trim(fullName),
        dateOfBirth,
        sex,
        IdentificationNumber.of(trim(identificationNumber)),
        blankToNull(phone),
        blankToNull(email),
        trim(departmentName),
        trim(positionName));
  }

  /**
   * Rejects a date of birth after today (business time zone). Today itself is accepted.
   *
   * @throws IllegalArgumentException when the date is in the future
   */
  public void requireDateOfBirthNotInFuture(LocalDate dateOfBirth) {
    if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now(clock.withZone(BUSINESS_ZONE))))
      throw new IllegalArgumentException("Date of birth must not be in the future");
  }

  /**
   * Rejects an identification number that another Participant of the batch already holds, whatever
   * that Participant's roster status.
   *
   * @param excludeId the Participant being edited, or {@code null} when adding
   * @throws ConflictException when the number is taken
   */
  public void requireIdentityFree(
      AggregateId batchId, IdentificationNumber identity, AggregateId excludeId) {
    if (participants.identityTakenByOther(batchId, identity, excludeId))
      throw new ConflictException("Participant identity already exists in this batch");
  }

  /**
   * Runs a domain operation and reports a rule violation as a conflict with the rule's own safe
   * message. Domain messages are fixed text and never contain personal data.
   */
  public static void applyingDomainRule(Runnable operation) {
    try {
      operation.run();
    } catch (DomainRuleViolation violation) {
      throw new ConflictException(violation.getMessage());
    }
  }

  /** Names of the roster fields and the examination day that differ, never their values. */
  public static List<String> changedFields(
      Roster before, AggregateId dayBefore, Roster after, AggregateId dayAfter) {
    List<String> changed = new ArrayList<>();
    if (!Objects.equals(before.participantCode(), after.participantCode()))
      changed.add("participantCode");
    if (!Objects.equals(before.fullName(), after.fullName())) changed.add("fullName");
    if (!Objects.equals(before.dateOfBirth(), after.dateOfBirth())) changed.add("dateOfBirth");
    if (!Objects.equals(before.sex(), after.sex())) changed.add("sex");
    if (!Objects.equals(before.identificationNumber(), after.identificationNumber()))
      changed.add("identificationNumber");
    if (!Objects.equals(before.phone(), after.phone())) changed.add("phone");
    if (!Objects.equals(before.email(), after.email())) changed.add("email");
    if (!Objects.equals(before.departmentName(), after.departmentName()))
      changed.add("departmentName");
    if (!Objects.equals(before.positionName(), after.positionName())) changed.add("positionName");
    if (!Objects.equals(dayBefore, dayAfter)) changed.add("examinationDay");
    return List.copyOf(changed);
  }

  /**
   * Writes the audit event in the caller's transaction. The snapshots hold identifiers, versions,
   * statuses and field names only: never the identification number, name, phone or email.
   */
  public void audit(
      UUID actor,
      String action,
      UUID organizationId,
      HealthExaminationBatchParticipant participant,
      Long versionBefore,
      long versionAfter,
      List<String> changedFields) {
    Map<String, Object> after = snapshot(organizationId, participant, versionAfter);
    after.put("changedFields", changedFields);
    audit.record(
        actor,
        action,
        ENTITY_TYPE,
        participant.getId().value(),
        versionBefore == null ? null : snapshot(organizationId, participant, versionBefore),
        after);
  }

  private static Map<String, Object> snapshot(
      UUID organizationId, HealthExaminationBatchParticipant participant, long version) {
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("organizationId", organizationId);
    snapshot.put("batchId", participant.getBatchId().value());
    snapshot.put("rowVersion", version);
    snapshot.put("source", participant.isManual() ? "MANUAL" : "IMPORT");
    return snapshot;
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
