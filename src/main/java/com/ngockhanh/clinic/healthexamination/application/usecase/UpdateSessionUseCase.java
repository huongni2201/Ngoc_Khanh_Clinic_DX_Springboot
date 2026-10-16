package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateSessionCommand;
import com.ngockhanh.clinic.healthexamination.application.response.SessionResponse;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.SessionInputs;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
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
 * Changes the date, activity and site of an open session using optimistic locking.
 *
 * <p>The caller must hold the session update permission. The session row is locked while the change
 * runs. A session where somebody already attended keeps its date: attendance is a fact of that day.
 * The change and the audit event are written in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateSessionUseCase {
  private final SessionAccessPolicy access;
  private final OrganizationRepository organizations;
  private final HealthExaminationSessionRepository sessions;
  private final AuditWriter audit;

  /**
   * Updates the session when the caller's expected row version is still current.
   *
   * @param organizationId owning organization; it may be inactive
   * @param sessionId session identifier
   * @param command replacement date, activity and site, and the expected row version
   * @param principal authenticated staff account
   * @return the session as stored, with the incremented row version
   * @throws ApplicationException {@code ACCESS_DENIED} without the update permission
   * @throws IllegalArgumentException when an argument is missing or a value is invalid
   * @throws ResourceNotFoundException when the organization or the session does not exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DomainRuleViolation when the session is cancelled
   * @throws ConflictException when somebody attended the session, or another open session already
   *     has the date, site and activity
   */
  @Transactional
  public SessionResponse execute(
      UUID organizationId, UUID sessionId, UpdateSessionCommand command, UserPrincipal principal) {
    access.require(principal, SessionAccessPolicy.SESSION_UPDATE);
    if (organizationId == null
        || sessionId == null
        || command == null
        || command.sessionDate() == null)
      throw new IllegalArgumentException(
          "Organization ID, session ID, command and date are required");
    Long expectedVersion = command.rowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");
    var activity = SessionInputs.activity(command.activity());
    var site = SessionInputs.site(command.siteType(), command.siteAddress());

    organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var session =
        sessions
            .findById(organizationId, sessionId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Session"));
    if (expectedVersion != session.rowVersion()) throw new ConcurrentUpdateException();
    session.requireOpen();
    var before =
        sessions
            .findSummary(organizationId, sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Session"));
    if (before.attendedCount() > 0)
      throw new ConflictException("A session where somebody attended cannot be changed");
    if (sessions.openSlotTaken(organizationId, command.sessionDate(), site, activity, sessionId))
      throw new ConflictException("A session already exists for this date, site and activity");

    session.reschedule(command.sessionDate(), activity, site);
    sessions.update(session, expectedVersion);
    audit.record(
        principal.userId(),
        "UPDATE_HEALTH_EXAMINATION_SESSION",
        "HEALTH_EXAMINATION_SESSION",
        sessionId,
        Map.of(
            "organizationId", organizationId,
            "sessionDate", before.sessionDate().toString(),
            "activity", before.activity().name(),
            "siteType", before.siteType().name(),
            "rowVersion", expectedVersion),
        Map.of(
            "organizationId", organizationId,
            "sessionDate", command.sessionDate().toString(),
            "activity", activity.name(),
            "siteType", site.type().name(),
            "rowVersion", expectedVersion + 1));
    log.info(
        "Session update pending commit: organizationId={}, sessionId={}",
        organizationId,
        sessionId);
    return SessionResponse.from(
        sessions
            .findSummary(organizationId, sessionId)
            .orElseThrow(
                () -> new IllegalStateException("Updated session could not be read back")));
  }
}
