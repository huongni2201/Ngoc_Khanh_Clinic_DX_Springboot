package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantDetailResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
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
 * Steps shared by the manual Participant use cases: resolving and locking the agreement, checking
 * that the roster may still change, building the roster and writing the audit event. It holds no
 * state and starts no transaction; the calling use case owns it.
 */
@Component
@RequiredArgsConstructor
public class ParticipantChangeSupport {
  static final String ENTITY_TYPE = "HEALTH_EXAMINATION_PARTICIPANT";

  /** Business time zone used to decide what "today" is for a date of birth. */
  static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final HealthExaminationParticipantRepository participants;
  private final SessionAssignmentRepository assignments;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Loads the agreement of an organization without locking it, for a read.
   *
   * @throws ResourceNotFoundException when the agreement does not exist, is deleted or belongs to
   *     another organization
   */
  public HealthExaminationAgreement agreementForRead(UUID organizationId, UUID agreementId) {
    return agreements
        .findDetails(organizationId, agreementId, false)
        .orElseThrow(() -> new ResourceNotFoundException("Participant"))
        .agreement();
  }

  /**
   * Locks the agreement header and checks that the roster may change. The lock order is agreement,
   * then Participant, the same as the Excel import.
   *
   * @throws ResourceNotFoundException when the organization or the agreement does not exist
   * @throws ConflictException when the organization is not active or the agreement is not a draft
   *     or active agreement
   */
  public HealthExaminationAgreement lockAgreementForChange(UUID organizationId, UUID agreementId) {
    var agreement =
        agreements
            .findDetails(organizationId, agreementId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Participant"))
            .agreement();
    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Participant"));
    if (organization.status() != OrganizationStatus.ACTIVE
        || !agreement.acceptsParticipantChanges())
      throw new ConflictException("Agreement does not accept Participant changes");
    return agreement;
  }

  /** Builds the full Participant response with its current schedule history. */
  public ParticipantDetailResponse detail(
      UUID organizationId, HealthExaminationParticipant participant) {
    return ParticipantDetailResponse.from(
        participant, assignments.findByParticipant(organizationId, participant.getId().value()));
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
    return new Roster(
        blankToNull(participantCode),
        trim(fullName),
        dateOfBirth,
        sex,
        IdentificationNumber.of(trim(identificationNumber)),
        identificationIssueDate,
        blankToNull(identificationIssuePlace),
        blankToNull(ethnicity),
        blankToNull(phone),
        blankToNull(email),
        blankToNull(address),
        blankToNull(workplace),
        trim(departmentName),
        trim(positionName),
        blankToNull(note));
  }

  /**
   * Generates the participant codes of {@code count} new Participants of the agreement: the
   * agreement code, a dash and a running number of at least four digits ({@code ABC01-0001}). The
   * number continues after every Participant already in the agreement, cancelled ones included, so
   * a code is never reused. The agreement must be locked by the caller, which makes the count
   * stable until commit.
   */
  public List<String> nextParticipantCodes(HealthExaminationAgreement agreement, int count) {
    long existing = participants.countInAgreement(agreement.id());
    List<String> codes = new ArrayList<>(count);
    for (int i = 1; i <= count; i++)
      codes.add(String.format("%s-%04d", agreement.code(), existing + i));
    return List.copyOf(codes);
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
   * Rejects an identification number that another Participant of the agreement already holds,
   * whatever that Participant's roster status.
   *
   * @param excludeId the Participant being edited, or {@code null} when adding
   * @throws ConflictException when the number is taken
   */
  public void requireIdentityFree(
      AggregateId agreementId, IdentificationNumber identity, AggregateId excludeId) {
    if (participants.identityTakenByOther(agreementId, identity, excludeId))
      throw new ConflictException("Participant identity already exists in this agreement");
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

  /** Names of the roster fields that differ, never their values. */
  public static List<String> changedFields(Roster before, Roster after) {
    List<String> changed = new ArrayList<>();
    if (!Objects.equals(before.participantCode(), after.participantCode()))
      changed.add("participantCode");
    if (!Objects.equals(before.fullName(), after.fullName())) changed.add("fullName");
    if (!Objects.equals(before.dateOfBirth(), after.dateOfBirth())) changed.add("dateOfBirth");
    if (!Objects.equals(before.sex(), after.sex())) changed.add("sex");
    if (!Objects.equals(before.identificationNumber(), after.identificationNumber()))
      changed.add("identificationNumber");
    if (!Objects.equals(before.identificationIssueDate(), after.identificationIssueDate()))
      changed.add("identificationIssueDate");
    if (!Objects.equals(before.identificationIssuePlace(), after.identificationIssuePlace()))
      changed.add("identificationIssuePlace");
    if (!Objects.equals(before.ethnicity(), after.ethnicity())) changed.add("ethnicity");
    if (!Objects.equals(before.phone(), after.phone())) changed.add("phone");
    if (!Objects.equals(before.email(), after.email())) changed.add("email");
    if (!Objects.equals(before.address(), after.address())) changed.add("address");
    if (!Objects.equals(before.workplace(), after.workplace())) changed.add("workplace");
    if (!Objects.equals(before.departmentName(), after.departmentName()))
      changed.add("departmentName");
    if (!Objects.equals(before.positionName(), after.positionName())) changed.add("positionName");
    if (!Objects.equals(before.note(), after.note())) changed.add("note");
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
      HealthExaminationParticipant participant,
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
      UUID organizationId, HealthExaminationParticipant participant, long version) {
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("organizationId", organizationId);
    snapshot.put("agreementId", participant.getAgreementId().value());
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
