package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.MoveSessionAssignmentsCommand;
import com.ngockhanh.clinic.healthexamination.application.response.MoveSessionAssignmentsResponse;
import com.ngockhanh.clinic.healthexamination.application.service.AssignmentScopeChecks;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationSession;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.SessionAssignment;
import com.ngockhanh.clinic.healthexamination.domain.enums.AssignmentStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository.ParticipantScope;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves every expected Participant of a session to another session, for example when the company
 * postpones the whole day. Attended and no-show assignments are not touched.
 *
 * <p>The caller must hold the assignment move-all permission. Both session rows are locked in
 * identifier order, then the expected assignments of the source in identifier order. The target
 * must be open and serve the agreement of every Participant moved. A Participant who is already in
 * the target stays where they are and is reported. The new assignments, the old ones and the audit
 * event are written in one transaction, so the move is all or nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoveSessionAssignmentsUseCase {
  private final SessionAccessPolicy access;
  private final AssignmentScopeChecks checks;
  private final HealthExaminationSessionRepository sessions;
  private final HealthExaminationParticipantRepository participants;
  private final SessionAssignmentRepository assignments;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Moves the expected Participants.
   *
   * @param organizationId owning organization; it must be active
   * @param sessionId source session
   * @param command target session
   * @param principal authenticated staff account
   * @return how many Participants moved and who was skipped
   * @throws ApplicationException {@code ACCESS_DENIED} without the move-all permission
   * @throws IllegalArgumentException when an argument is missing or the target is the same session
   * @throws ResourceNotFoundException when the organization or a session does not exist
   * @throws DomainRuleViolation when the target is cancelled or serves another agreement
   * @throws ConflictException when an agreement of a Participant does not accept changes
   */
  @Transactional
  public MoveSessionAssignmentsResponse execute(
      UUID organizationId,
      UUID sessionId,
      MoveSessionAssignmentsCommand command,
      UserPrincipal principal) {
    access.require(principal, SessionAccessPolicy.ASSIGNMENT_MOVE_ALL);
    if (organizationId == null
        || sessionId == null
        || command == null
        || command.targetSessionId() == null)
      throw new IllegalArgumentException(
          "Organization ID, session ID and target session are required");
    UUID targetSessionId = command.targetSessionId();
    if (targetSessionId.equals(sessionId))
      throw new IllegalArgumentException("The target session must be another session");

    checks.requireActiveOrganization(organizationId);
    boolean sourceFirst = sessionId.compareTo(targetSessionId) < 0;
    HealthExaminationSession first =
        sessions
            .findById(organizationId, sourceFirst ? sessionId : targetSessionId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Session"));
    HealthExaminationSession second =
        sessions
            .findById(organizationId, sourceFirst ? targetSessionId : sessionId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Session"));
    HealthExaminationSession target = sourceFirst ? second : first;
    target.requireOpen();

    List<SessionAssignment> expected =
        assignments.findByStatusForUpdate(organizationId, sessionId, AssignmentStatus.ASSIGNED);
    List<AggregateId> participantIds =
        expected.stream().map(SessionAssignment::participantId).toList();
    Set<AggregateId> alreadyInTarget =
        assignments.findLiveParticipantIds(targetSessionId, participantIds);
    Map<AggregateId, ParticipantScope> scopes = new HashMap<>();
    for (var scope : participants.findScopes(AggregateId.of(organizationId), participantIds))
      scopes.put(scope.id(), scope);

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    AggregateId actor = AggregateId.of(principal.userId());
    List<SessionAssignment> replacements = new ArrayList<>();
    List<UUID> skipped = new ArrayList<>();
    for (SessionAssignment assignment : expected) {
      if (alreadyInTarget.contains(assignment.participantId())) {
        skipped.add(assignment.participantId().value());
        continue;
      }
      var scope = scopes.get(assignment.participantId());
      if (scope == null) throw new ResourceNotFoundException("Participant");
      checks.requireSchedulable(scope, target);
      var replacement =
          SessionAssignment.assign(
              AggregateId.of(UuidV7Generator.generate()),
              assignment.organizationId(),
              assignment.participantId(),
              target.id(),
              actor,
              now);
      replacements.add(replacement);
    }
    assignments.insertMany(replacements);
    int next = 0;
    for (SessionAssignment assignment : expected) {
      if (alreadyInTarget.contains(assignment.participantId())) continue;
      long version = assignment.rowVersion();
      assignment.moveTo(replacements.get(next++).id(), actor, now);
      assignments.update(assignment, version);
    }
    audit.record(
        principal.userId(),
        "MOVE_SESSION_ASSIGNMENTS",
        "HEALTH_EXAMINATION_SESSION",
        sessionId,
        null,
        Map.of(
            "organizationId",
            organizationId,
            "targetSessionId",
            targetSessionId,
            "moved",
            replacements.size(),
            "skipped",
            skipped.size()));
    log.info(
        "Session assignments move pending commit: organizationId={}, sessionId={},"
            + " targetSessionId={}, moved={}, skipped={}",
        organizationId,
        sessionId,
        targetSessionId,
        replacements.size(),
        skipped.size());
    return new MoveSessionAssignmentsResponse(
        sessionId, targetSessionId, replacements.size(), skipped);
  }
}
