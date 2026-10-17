package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.CancelSessionCommand;
import com.ngockhanh.clinic.healthexamination.application.response.SessionResponse;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels an open session nobody is assigned to. A session with Participants must first have them
 * moved or cancelled, so the schedule never silently loses people.
 *
 * <p>The caller must hold the session cancel permission. The session row is locked, and the
 * cancellation and the audit event are written in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelSessionUseCase {
  private final SessionAccessPolicy access;
  private final OrganizationRepository organizations;
  private final HealthExaminationSessionRepository sessions;
  private final SessionAssignmentRepository assignments;
  private final AuditWriter audit;

  /**
   * Cancels the session when the caller's expected row version is still current.
   *
   * @param organizationId owning organization; it may be inactive
   * @param sessionId session identifier
   * @param command carries the row version the caller last read
   * @param principal authenticated staff account
   * @return the cancelled session
   * @throws ApplicationException {@code ACCESS_DENIED} without the cancel permission
   * @throws IllegalArgumentException when an argument is missing or the version is negative
   * @throws ResourceNotFoundException when the organization or the session does not exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the session is already cancelled
   * @throws ConflictException when Participants are still assigned to the session
   */
  @Transactional
  public SessionResponse execute(
      UUID organizationId, UUID sessionId, CancelSessionCommand command, UserPrincipal principal) {
    access.require(principal, SessionAccessPolicy.SESSION_CANCEL);
    if (organizationId == null || sessionId == null || command == null)
      throw new IllegalArgumentException("Organization ID, session ID and command are required");
    Long expectedVersion = command.rowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");

    organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var session =
        sessions
            .findById(organizationId, sessionId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Session"));
    if (expectedVersion != session.rowVersion()) throw new ConcurrentUpdateException();
    session.requireOpen();
    if (assignments.countLive(sessionId) > 0)
      throw new ConflictException("Move or cancel the Participants of this session first");

    session.cancel();
    sessions.updateStatus(session, SessionStatus.OPEN, expectedVersion);
    audit.record(
        principal.userId(),
        "CANCEL_HEALTH_EXAMINATION_SESSION",
        "HEALTH_EXAMINATION_SESSION",
        sessionId,
        Map.of("organizationId", organizationId, "status", "OPEN", "rowVersion", expectedVersion),
        Map.of(
            "organizationId",
            organizationId,
            "status",
            "CANCELLED",
            "rowVersion",
            expectedVersion + 1));
    log.info(
        "Session cancellation pending commit: organizationId={}, sessionId={}",
        organizationId,
        sessionId);
    return SessionResponse.from(
        sessions
            .findSummary(organizationId, sessionId)
            .orElseThrow(
                () -> new IllegalStateException("Cancelled session could not be read back")));
  }
}
