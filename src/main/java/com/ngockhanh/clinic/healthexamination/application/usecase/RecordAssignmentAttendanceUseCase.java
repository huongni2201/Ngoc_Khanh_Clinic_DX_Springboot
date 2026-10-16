package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.RecordAttendanceCommand;
import com.ngockhanh.clinic.healthexamination.application.response.AssignmentResponse;
import com.ngockhanh.clinic.healthexamination.application.service.AssignmentScopeChecks;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDateRules;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.enums.AssignmentStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
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
 * Records what happened to one assignment: attended, did not show, or back to expected. Attendance
 * is a fact of one session, so a Participant who missed one session and came to a make-up keeps
 * both facts.
 *
 * <p>The caller must hold the attendance record permission. The session must be open and not in the
 * future (business time) for an attendance, the Participant must still be on the active roster of
 * an agreement that accepts changes, and the assignment must not have been moved or cancelled.
 * Recording the state the assignment already has changes nothing, so a retry after a lost response
 * is safe. The change and the audit event are written in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordAssignmentAttendanceUseCase {
  static final int MAX_NOTE_LENGTH = 1000;

  private final SessionAccessPolicy access;
  private final AssignmentScopeChecks checks;
  private final HealthExaminationSessionRepository sessions;
  private final HealthExaminationParticipantRepository participants;
  private final SessionAssignmentRepository assignments;
  private final AuditWriter audit;
  private final Clock clock;

  /**
   * Records the attendance when the caller's expected row version is still current.
   *
   * @param organizationId owning organization; it must be active
   * @param sessionId session the assignment is in
   * @param assignmentId assignment to update
   * @param command {@code ATTENDED}, {@code NO_SHOW} or {@code ASSIGNED}, the version and a note
   * @param principal authenticated staff account
   * @return the assignment as stored afterwards
   * @throws ApplicationException {@code ACCESS_DENIED} without the attendance permission
   * @throws IllegalArgumentException when an argument is missing, the status is not allowed or the
   *     note is too long
   * @throws ResourceNotFoundException when the organization, the session, the assignment or the
   *     Participant does not exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the session is cancelled or the assignment was moved or
   *     cancelled
   * @throws ConflictException when the session is in the future for an attendance, or the
   *     Participant cannot take changes
   */
  @Transactional
  public AssignmentResponse execute(
      UUID organizationId,
      UUID sessionId,
      UUID assignmentId,
      RecordAttendanceCommand command,
      UserPrincipal principal) {
    access.require(principal, SessionAccessPolicy.ASSIGNMENT_ATTENDANCE_RECORD);
    if (organizationId == null
        || sessionId == null
        || assignmentId == null
        || command == null
        || command.status() == null)
      throw new IllegalArgumentException(
          "Organization, session and assignment IDs, status and command are required");
    Long expectedVersion = command.rowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");
    AssignmentStatus target = AssignmentStatus.valueOf(command.status());
    String note = command.note() == null || command.note().isBlank() ? null : command.note().trim();
    if (note != null && note.length() > MAX_NOTE_LENGTH)
      throw new IllegalArgumentException("Note is too long");

    checks.requireActiveOrganization(organizationId);
    var session =
        sessions
            .findById(organizationId, sessionId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Session"));
    session.requireOpen();
    var assignment =
        assignments
            .findById(organizationId, sessionId, assignmentId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment"));
    if (expectedVersion != assignment.rowVersion()) throw new ConcurrentUpdateException();
    var scopes =
        participants.findScopes(
            AggregateId.of(organizationId), List.of(assignment.participantId()));
    if (scopes.isEmpty()) throw new ResourceNotFoundException("Participant");
    checks.requireSchedulable(scopes.get(0), session);
    if (target == AssignmentStatus.ATTENDED)
      ExaminationDateRules.requireSessionStarted(session.sessionDate(), clock);

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    String before = assignment.status().name();
    boolean changed =
        assignment.recordAttendance(target, note, AggregateId.of(principal.userId()), now);
    if (changed) {
      assignments.update(assignment, expectedVersion);
      audit.record(
          principal.userId(),
          "RECORD_SESSION_ATTENDANCE",
          "HEALTH_EXAMINATION_SESSION_ASSIGNMENT",
          assignmentId,
          Map.of("organizationId", organizationId, "sessionId", sessionId, "status", before),
          Map.of(
              "organizationId", organizationId,
              "sessionId", sessionId,
              "status", target.name()));
    }
    log.info(
        "Attendance recorded: organizationId={}, assignmentId={}, result={}",
        organizationId,
        assignmentId,
        changed ? "UPDATED" : "UNCHANGED");
    return AssignmentResponse.from(assignment, changed ? expectedVersion + 1 : expectedVersion);
  }
}
