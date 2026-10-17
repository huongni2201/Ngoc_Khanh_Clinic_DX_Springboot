package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.response.SessionResponse;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads one session of an organization with the number of Participants in it. The caller must hold
 * the session detail permission. The organization may be inactive. Nothing is written or audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetSessionUseCase {
  private final SessionAccessPolicy access;
  private final OrganizationRepository organizations;
  private final HealthExaminationSessionRepository sessions;

  /**
   * Returns the session.
   *
   * @param organizationId owning organization
   * @param sessionId session identifier
   * @param principal authenticated staff account
   * @throws ApplicationException {@code ACCESS_DENIED} without the detail permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the organization or the session does not exist
   */
  @Transactional(readOnly = true)
  public SessionResponse execute(UUID organizationId, UUID sessionId, UserPrincipal principal) {
    access.require(principal, SessionAccessPolicy.SESSION_DETAIL_VIEW);
    if (organizationId == null || sessionId == null)
      throw new IllegalArgumentException("Organization ID and session ID are required");
    organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var session =
        sessions
            .findSummary(organizationId, sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Session"));
    log.info("Session read: organizationId={}, sessionId={}", organizationId, sessionId);
    return SessionResponse.from(session);
  }
}
