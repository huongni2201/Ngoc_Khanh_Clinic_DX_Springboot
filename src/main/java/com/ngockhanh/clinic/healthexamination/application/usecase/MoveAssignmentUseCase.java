package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.MoveAssignmentCommand;
import com.ngockhanh.clinic.healthexamination.application.response.AssignmentResponse;
import com.ngockhanh.clinic.healthexamination.application.service.AssignmentScopeChecks;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationSession;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.SessionAssignment;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves one Participant from a session to another. The old assignment becomes {@code MOVED} and
 * points to the new one; the Participant, its price snapshots, its reconciliation and its records
 * are untouched.
 *
 * <p>The caller must hold the assignment move permission. Both session rows are locked, in
 * identifier order so two opposite moves cannot deadlock. The target must be open and serve the
 * Participant's agreement, and the Participant must not already be in it. Only an assignment that
 * is still expected can be moved; an attended one is a fact. The new assignment, the old one and
 * the audit event are written in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoveAssignmentUseCase {
  private final SessionAccessPolicy access;
  private final AssignmentScopeChecks checks;
  private final HealthExaminationSessionRepository sessions;
  private final HealthExaminationParticipantRepository participants;
  private final SessionAssignmentRepository assignments;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Moves the assignment when the caller's expected row version is still current.
   *
   * @param organizationId owning organization; it must be active
   * @param sessionId session the assignment is in
   * @param assignmentId assignment to move
   * @param command target session and the expected assignment version
   * @param principal authenticated staff account
   * @return the new assignment in the target session
   * @throws ApplicationException {@code ACCESS_DENIED} without the move permission
   * @throws IllegalArgumentException when an argument is missing or the target is the same session
   * @throws ResourceNotFoundException when the organization, a session or the assignment does not
   *     exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the target is cancelled or serves another agreement, or the
   *     assignment is not expected any more
   * @throws ConflictException when the Participant is already in the target session or cannot be
   *     scheduled
   */
  @Transactional
  public AssignmentResponse execute(
      UUID organizationId,
      UUID sessionId,
      UUID assignmentId,
      MoveAssignmentCommand command,
      UserPrincipal principal) {
    access.require(principal, SessionAccessPolicy.ASSIGNMENT_MOVE);
    if (organizationId == null
        || sessionId == null
        || assignmentId == null
        || command == null
        || command.targetSessionId() == null)
      throw new IllegalArgumentException(
          "Organization, session and assignment IDs and the target session are required");
    Long expectedVersion = command.rowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");
    UUID targetSessionId = command.targetSessionId();
    if (targetSessionId.equals(sessionId))
      throw new IllegalArgumentException("The target session must be another session");

    checks.requireActiveOrganization(organizationId);
    HealthExaminationSession target = lockBoth(organizationId, sessionId, targetSessionId);
    var assignment =
        assignments
            .findById(organizationId, sessionId, assignmentId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment"));
    if (expectedVersion != assignment.rowVersion()) throw new ConcurrentUpdateException();
    target.requireOpen();

    var scopes =
        participants.findScopes(
            AggregateId.of(organizationId), List.of(assignment.participantId()));
    if (scopes.isEmpty()) throw new ResourceNotFoundException("Participant");
    checks.requireSchedulable(scopes.get(0), target);
    if (!assignments
        .findLiveParticipantIds(targetSessionId, List.of(assignment.participantId()))
        .isEmpty()) throw new ConflictException("The Participant is already in the target session");

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    AggregateId actor = AggregateId.of(principal.userId());
    var replacement =
        SessionAssignment.assign(
            AggregateId.of(UuidV7Generator.generate()),
            assignment.organizationId(),
            assignment.participantId(),
            target.id(),
            actor,
            now);
    assignment.moveTo(replacement.id(), actor, now);
    assignments.insertMany(List.of(replacement));
    assignments.update(assignment, expectedVersion);
    audit.record(
        principal.userId(),
        "MOVE_SESSION_ASSIGNMENT",
        "HEALTH_EXAMINATION_SESSION_ASSIGNMENT",
        assignmentId,
        Map.of("organizationId", organizationId, "sessionId", sessionId, "status", "ASSIGNED"),
        Map.of(
            "organizationId", organizationId,
            "sessionId", sessionId,
            "targetSessionId", targetSessionId,
            "replacedByAssignmentId", replacement.id().value(),
            "status", "MOVED"));
    log.info(
        "Assignment move pending commit: organizationId={}, assignmentId={}, targetSessionId={}",
        organizationId,
        assignmentId,
        targetSessionId);
    return AssignmentResponse.from(replacement, replacement.rowVersion());
  }

  /** Locks both sessions in identifier order and returns the target. */
  private HealthExaminationSession lockBoth(UUID organizationId, UUID sourceId, UUID targetId) {
    boolean sourceFirst = sourceId.compareTo(targetId) < 0;
    UUID first = sourceFirst ? sourceId : targetId;
    UUID second = sourceFirst ? targetId : sourceId;
    var one =
        sessions
            .findById(organizationId, first, true)
            .orElseThrow(() -> new ResourceNotFoundException("Session"));
    var two =
        sessions
            .findById(organizationId, second, true)
            .orElseThrow(() -> new ResourceNotFoundException("Session"));
    return sourceFirst ? two : one;
  }
}
