package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.AssignParticipantsCommand;
import com.ngockhanh.clinic.healthexamination.application.response.AssignParticipantsResponse;
import com.ngockhanh.clinic.healthexamination.application.service.AssignmentScopeChecks;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.SessionAssignment;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schedules Participants into a session. Staff decide how many people go in a session; the system
 * only counts them.
 *
 * <p>The caller must hold the assignment create permission. The session row is locked and must be
 * open. Every Participant must be on the active roster of an agreement that still accepts changes
 * and that the session serves; a Participant already in the session is skipped and reported, and
 * the others are assigned. One invalid Participant rejects the whole request, so a screen never
 * half-applies a selection. The assignments and the audit event are written in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignParticipantsToSessionUseCase {
  static final int MAX_PARTICIPANTS = 1000;

  private final SessionAccessPolicy access;
  private final AssignmentScopeChecks checks;
  private final HealthExaminationSessionRepository sessions;
  private final HealthExaminationParticipantRepository participants;
  private final SessionAssignmentRepository assignments;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Assigns the Participants.
   *
   * @param organizationId owning organization; it must be active
   * @param sessionId session identifier
   * @param command Participants to schedule
   * @param principal authenticated staff account
   * @return who was assigned and who was skipped because they were already in the session
   * @throws ApplicationException {@code ACCESS_DENIED} without the create permission
   * @throws IllegalArgumentException when the list is empty, too long or has duplicates
   * @throws ResourceNotFoundException when the organization, the session or a Participant does not
   *     exist in the organization
   * @throws DomainRuleViolation when the organization is inactive, the session is cancelled or
   *     serves another agreement
   * @throws ConflictException when a Participant is cancelled or its agreement does not accept
   *     changes
   */
  @Transactional
  public AssignParticipantsResponse execute(
      UUID organizationId,
      UUID sessionId,
      AssignParticipantsCommand command,
      UserPrincipal principal) {
    access.require(principal, SessionAccessPolicy.ASSIGNMENT_CREATE);
    if (organizationId == null || sessionId == null || command == null)
      throw new IllegalArgumentException("Organization ID, session ID and command are required");
    List<UUID> requested = command.participantIds();
    if (requested == null || requested.isEmpty() || requested.size() > MAX_PARTICIPANTS)
      throw new IllegalArgumentException(
          "Between 1 and " + MAX_PARTICIPANTS + " Participants are required");
    Set<UUID> distinct = new HashSet<>(requested);
    if (distinct.size() != requested.size() || distinct.contains(null))
      throw new IllegalArgumentException("Participants must be present and distinct");

    checks.requireActiveOrganization(organizationId);
    var session =
        sessions
            .findById(organizationId, sessionId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Session"));
    session.requireOpen();

    List<AggregateId> ids = requested.stream().map(AggregateId::of).toList();
    var scopes = participants.findScopes(AggregateId.of(organizationId), ids);
    if (scopes.size() != ids.size()) throw new ResourceNotFoundException("Participant");
    for (var scope : scopes) checks.requireSchedulable(scope, session);

    Set<AggregateId> already = assignments.findLiveParticipantIds(sessionId, ids);
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    AggregateId actor = AggregateId.of(principal.userId());
    List<SessionAssignment> created = new ArrayList<>();
    List<UUID> skipped = new ArrayList<>();
    for (AggregateId id : ids) {
      if (already.contains(id)) {
        skipped.add(id.value());
        continue;
      }
      created.add(
          SessionAssignment.assign(
              AggregateId.of(UuidV7Generator.generate()),
              AggregateId.of(organizationId),
              id,
              session.id(),
              actor,
              now));
    }
    assignments.insertMany(created);
    audit.record(
        principal.userId(),
        "ASSIGN_PARTICIPANTS_TO_SESSION",
        "HEALTH_EXAMINATION_SESSION",
        sessionId,
        null,
        Map.of(
            "organizationId", organizationId,
            "requested", ids.size(),
            "assigned", created.size(),
            "skipped", skipped.size()));
    log.info(
        "Participants assignment pending commit: organizationId={}, sessionId={}, assigned={},"
            + " skipped={}",
        organizationId,
        sessionId,
        created.size(),
        skipped.size());
    return new AssignParticipantsResponse(
        sessionId,
        created.stream()
            .map(
                a ->
                    new AssignParticipantsResponse.Assigned(
                        a.participantId().value(), a.id().value()))
            .toList(),
        skipped);
  }
}
