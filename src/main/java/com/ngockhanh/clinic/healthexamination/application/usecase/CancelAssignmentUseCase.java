package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.CancelAssignmentCommand;
import com.ngockhanh.clinic.healthexamination.application.response.AssignmentResponse;
import com.ngockhanh.clinic.healthexamination.application.service.AssignmentScopeChecks;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels the assignment of a Participant who is still expected in a session. The assignment stays
 * as history; the Participant stays on the roster and can be scheduled again. An attended
 * assignment is a fact and cannot be cancelled.
 *
 * <p>The caller must hold the assignment cancel permission. The session and the assignment rows are
 * locked, and the cancellation and the audit event are written in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelAssignmentUseCase {
  static final int MAX_NOTE_LENGTH = 1000;

  private final SessionAccessPolicy access;
  private final AssignmentScopeChecks checks;
  private final HealthExaminationSessionRepository sessions;
  private final SessionAssignmentRepository assignments;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Cancels the assignment when the caller's expected row version is still current.
   *
   * @param organizationId owning organization; it must be active
   * @param sessionId session the assignment is in
   * @param assignmentId assignment to cancel
   * @param command the expected assignment version and an optional reason
   * @param principal authenticated staff account
   * @return the cancelled assignment
   * @throws ApplicationException {@code ACCESS_DENIED} without the cancel permission
   * @throws IllegalArgumentException when an argument is missing or the note is too long
   * @throws ResourceNotFoundException when the organization, the session or the assignment does not
   *     exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the assignment is not expected any more
   */
  @Transactional
  public AssignmentResponse execute(
      UUID organizationId,
      UUID sessionId,
      UUID assignmentId,
      CancelAssignmentCommand command,
      UserPrincipal principal) {
    access.require(principal, SessionAccessPolicy.ASSIGNMENT_CANCEL);
    if (organizationId == null || sessionId == null || assignmentId == null || command == null)
      throw new IllegalArgumentException(
          "Organization, session and assignment IDs and command are required");
    Long expectedVersion = command.rowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");
    String note = command.note() == null || command.note().isBlank() ? null : command.note().trim();
    if (note != null && note.length() > MAX_NOTE_LENGTH)
      throw new IllegalArgumentException("Note is too long");

    checks.requireActiveOrganization(organizationId);
    sessions
        .findById(organizationId, sessionId, true)
        .orElseThrow(() -> new ResourceNotFoundException("Session"));
    var assignment =
        assignments
            .findById(organizationId, sessionId, assignmentId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment"));
    if (expectedVersion != assignment.rowVersion()) throw new ConcurrentUpdateException();

    assignment.cancel(
        note,
        AggregateId.of(principal.userId()),
        Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));
    assignments.update(assignment, expectedVersion);
    audit.record(
        principal.userId(),
        "CANCEL_SESSION_ASSIGNMENT",
        "HEALTH_EXAMINATION_SESSION_ASSIGNMENT",
        assignmentId,
        Map.of("organizationId", organizationId, "sessionId", sessionId, "status", "ASSIGNED"),
        Map.of("organizationId", organizationId, "sessionId", sessionId, "status", "CANCELLED"));
    log.info(
        "Assignment cancellation pending commit: organizationId={}, assignmentId={}",
        organizationId,
        assignmentId);
    return AssignmentResponse.from(assignment, expectedVersion + 1);
  }
}
