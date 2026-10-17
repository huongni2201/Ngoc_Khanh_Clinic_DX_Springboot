package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.healthexamination.application.contract.ExaminationFormSource;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads, in one short read-only transaction, the facts needed to print one Participant's form. It
 * is separate from the use case so the database connection is released before the slow PDF
 * conversion.
 *
 * <p>The form shows an examination date: the date of the session the caller names, else the
 * Participant's next expected session (or, when it has none left, the first one it attended), else
 * nothing, and the form then prints the date of printing.
 */
@Component
@RequiredArgsConstructor
public class ExaminationFormSourceLoader {
  private final ParticipantChangeSupport support;
  private final OrganizationRepository organizations;
  private final HealthExaminationParticipantRepository participants;
  private final HealthExaminationSessionRepository sessions;
  private final SessionAssignmentRepository assignments;
  private final Clock clock;

  /** Reason a Participant is left out of a bulk print because it is not in the agreement. */
  public static final String NOT_FOUND = "NOT_FOUND";

  /** Reason a Participant is left out of a bulk print because it was cancelled. */
  public static final String CANCELLED = "CANCELLED";

  /** The forms of many Participants. */
  public record Forms(List<Form> forms, List<Left> left) {}

  /** One printable Participant. */
  public record Form(UUID participantId, ExaminationFormSource source) {}

  /** One Participant left out, with the reason. */
  public record Left(UUID participantId, String reason) {}

  /**
   * Loads the form facts of an active Participant of an active or ended agreement.
   *
   * @param sessionId session whose date the form shows, or null for the Participant's own schedule
   * @throws ResourceNotFoundException when the agreement, the organization, the session or the
   *     Participant is not found
   * @throws ConflictException when the agreement is a draft, the session belongs to another
   *     agreement, or the Participant is cancelled
   */
  @Transactional(readOnly = true)
  public ExaminationFormSource load(
      UUID organizationId, UUID agreementId, UUID participantId, UUID sessionId) {
    var agreement = printableAgreement(organizationId, agreementId);
    var participant =
        participants
            .findInAgreement(AggregateId.of(agreementId), AggregateId.of(participantId))
            .orElseThrow(() -> new ResourceNotFoundException("Participant"));
    if (participant.getRosterStatus() != RosterStatus.ACTIVE)
      throw new ConflictException("A cancelled Participant has no examination form");
    LocalDate sessionDate = sessionDate(organizationId, agreement, sessionId);
    LocalDate own =
        sessionDate != null
            ? sessionDate
            : assignments
                .findExaminationDates(organizationId, List.of(participant.getId()))
                .get(participant.getId());
    return source(agreement, organization(organizationId), participant, own);
  }

  /**
   * Checks that forms may be printed for the agreement now, without reading any Participant.
   *
   * @return the business code of the agreement
   * @throws ResourceNotFoundException when the agreement or the organization is not found
   * @throws ConflictException when the agreement is a draft
   */
  @Transactional(readOnly = true)
  public String requirePrintableAgreement(UUID organizationId, UUID agreementId) {
    var agreement = printableAgreement(organizationId, agreementId);
    organization(organizationId);
    return agreement.code();
  }

  /**
   * Loads the form facts of many Participants of one agreement in a short read-only transaction,
   * without locking any row. A Participant that is not in the agreement or was cancelled is
   * reported in {@link Forms#left()} instead of failing the others. The forms come in the order of
   * the given identifiers.
   *
   * @param sessionId session whose date every form shows, or null for each Participant's own
   *     schedule
   * @throws ResourceNotFoundException when the agreement, the organization or the session is not
   *     found
   * @throws ConflictException when the agreement is a draft or the session belongs to another
   *     agreement
   */
  @Transactional(readOnly = true)
  public Forms loadMany(
      UUID organizationId, UUID agreementId, UUID sessionId, List<UUID> participantIds) {
    var agreement = printableAgreement(organizationId, agreementId);
    var organization = organization(organizationId);
    LocalDate sessionDate = sessionDate(organizationId, agreement, sessionId);
    List<AggregateId> ids = participantIds.stream().map(AggregateId::of).toList();
    Map<UUID, HealthExaminationParticipant> found = new HashMap<>();
    for (var participant : participants.findManyInAgreement(AggregateId.of(agreementId), ids))
      found.put(participant.getId().value(), participant);
    Map<AggregateId, LocalDate> ownDates =
        sessionDate != null ? Map.of() : assignments.findExaminationDates(organizationId, ids);
    List<Form> forms = new ArrayList<>();
    List<Left> left = new ArrayList<>();
    for (UUID id : participantIds) {
      var participant = found.get(id);
      if (participant == null) left.add(new Left(id, NOT_FOUND));
      else if (participant.getRosterStatus() != RosterStatus.ACTIVE)
        left.add(new Left(id, CANCELLED));
      else {
        LocalDate date = sessionDate != null ? sessionDate : ownDates.get(AggregateId.of(id));
        forms.add(new Form(id, source(agreement, organization, participant, date)));
      }
    }
    return new Forms(forms, left);
  }

  private HealthExaminationAgreement printableAgreement(UUID organizationId, UUID agreementId) {
    var agreement = support.agreementForRead(organizationId, agreementId);
    if (agreement.status() != AgreementStatus.ACTIVE && agreement.status() != AgreementStatus.ENDED)
      throw new ConflictException("The agreement status does not allow printing examination forms");
    return agreement;
  }

  /** Date of the named session, which must serve the agreement; null when no session is named. */
  private LocalDate sessionDate(
      UUID organizationId, HealthExaminationAgreement agreement, UUID sessionId) {
    if (sessionId == null) return null;
    var session =
        sessions
            .findById(organizationId, sessionId, false)
            .orElseThrow(() -> new ResourceNotFoundException("Session"));
    if (session.agreementId() != null && !session.agreementId().equals(agreement.id()))
      throw new ConflictException("The session belongs to another agreement");
    return session.sessionDate();
  }

  private Organization organization(UUID organizationId) {
    return organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
  }

  private ExaminationFormSource source(
      HealthExaminationAgreement agreement,
      Organization organization,
      HealthExaminationParticipant participant,
      LocalDate examinationDate) {
    var roster = participant.getRoster();
    return new ExaminationFormSource(
        roster.participantCode(),
        roster.fullName(),
        roster.dateOfBirth(),
        roster.sex(),
        roster.identificationNumber() == null ? null : roster.identificationNumber().value(),
        roster.identificationIssueDate(),
        roster.identificationIssuePlace(),
        roster.ethnicity(),
        roster.phone(),
        roster.address(),
        roster.workplace(),
        roster.departmentName(),
        roster.positionName(),
        organization.name(),
        organization.address(),
        agreement.code(),
        agreement.name(),
        examinationDate,
        LocalDate.now(clock.withZone(ParticipantChangeSupport.BUSINESS_ZONE)));
  }
}
