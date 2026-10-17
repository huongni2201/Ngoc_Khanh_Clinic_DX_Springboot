package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.CreateSessionCommand;
import com.ngockhanh.clinic.healthexamination.application.response.SessionResponse;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.SessionInputs;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationSession;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an open session (date, site, activity) for an active organization.
 *
 * <p>The caller must hold the session create permission. A session may serve one agreement, which
 * must still accept changes, or be shared by the agreements of the organization. Only one open
 * session may exist for an organization, date, site and activity; the people in it are decided by
 * the crew, so there is no capacity. The session and the audit event are written in one
 * transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateSessionUseCase {
  private final SessionAccessPolicy access;
  private final OrganizationRepository organizations;
  private final HealthExaminationAgreementRepository agreements;
  private final HealthExaminationSessionRepository sessions;
  private final AuditWriter audit;

  /**
   * Creates the session.
   *
   * @param organizationId owning organization
   * @param command date, activity, site and optional agreement
   * @param principal authenticated staff account
   * @return the session as stored, with zero Participants
   * @throws ApplicationException {@code ACCESS_DENIED} without the create permission
   * @throws IllegalArgumentException when an argument is missing or a value is invalid
   * @throws ResourceNotFoundException when the organization or the agreement does not exist
   * @throws DomainRuleViolation when the organization is inactive or the agreement does not accept
   *     changes
   * @throws ConflictException when an open session already exists for the date, site and activity
   */
  @Transactional
  public SessionResponse execute(
      UUID organizationId, CreateSessionCommand command, UserPrincipal principal) {
    access.require(principal, SessionAccessPolicy.SESSION_CREATE);
    if (organizationId == null || command == null || command.sessionDate() == null)
      throw new IllegalArgumentException("Organization ID, command and session date are required");
    var activity = SessionInputs.activity(command.activity());
    var site = SessionInputs.site(command.siteType(), command.siteAddress());

    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    if (organization.status() != OrganizationStatus.ACTIVE)
      throw new DomainRuleViolation("Organization is not active");
    AggregateId agreementId = null;
    if (command.agreementId() != null) {
      var agreement =
          agreements
              .findDetails(organizationId, command.agreementId(), false)
              .orElseThrow(() -> new ResourceNotFoundException("Health examination agreement"))
              .agreement();
      if (!agreement.acceptsParticipantChanges())
        throw new DomainRuleViolation("Agreement does not accept sessions");
      agreementId = agreement.id();
    }
    if (sessions.openSlotTaken(organizationId, command.sessionDate(), site, activity, null))
      throw new ConflictException("A session already exists for this date, site and activity");

    var session =
        HealthExaminationSession.open(
            AggregateId.of(UuidV7Generator.generate()),
            organization.id(),
            agreementId,
            command.sessionDate(),
            activity,
            site);
    sessions.insert(session, principal.userId());
    Map<String, Object> after = new HashMap<>();
    after.put("organizationId", organizationId);
    after.put("agreementId", command.agreementId());
    after.put("sessionDate", command.sessionDate().toString());
    after.put("activity", activity.name());
    after.put("siteType", site.type().name());
    audit.record(
        principal.userId(),
        "CREATE_HEALTH_EXAMINATION_SESSION",
        "HEALTH_EXAMINATION_SESSION",
        session.id().value(),
        null,
        after);
    log.info(
        "Session creation pending commit: organizationId={}, sessionId={}",
        organizationId,
        session.id().value());
    return SessionResponse.from(
        sessions
            .findSummary(organizationId, session.id().value())
            .orElseThrow(
                () -> new IllegalStateException("Created session could not be read back")));
  }
}
